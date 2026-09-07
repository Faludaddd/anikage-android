package com.anikage.app.core.data

import android.content.Context
import android.util.Log
import com.anikage.app.Config
import com.anikage.app.core.data.api.AniListApi
import com.anikage.app.core.data.api.AnikageApi
import com.anikage.app.core.data.api.AnikageBrowseResponse
import com.anikage.app.core.data.api.AnikageComment
import com.anikage.app.core.data.api.AnikageEpisode
import com.anikage.app.core.data.api.AnikageInfoAnime
import com.anikage.app.core.data.api.AnikageInfoResponse
import com.anikage.app.core.data.api.AnikageMusicAnime
import com.anikage.app.core.data.api.AnikageRelationRef
import com.anikage.app.core.data.api.AnikageScheduleElement
import com.anikage.app.core.data.api.AnikageServer
import com.anikage.app.core.data.api.AnikageSourcesResponse
import com.anikage.app.core.data.db.AnikageDatabase
import com.anikage.app.core.data.db.AnimeCacheEntity
import com.anikage.app.core.data.db.AnimeListEntity
import com.anikage.app.core.data.db.DownloadedEpisodeEntity
import com.anikage.app.core.data.db.DetailCacheEntity
import com.anikage.app.core.data.db.RecentlyViewedEntity
import com.anikage.app.core.data.db.SubscriptionEntity
import com.anikage.app.core.data.db.WatchProgressEntity
import com.anikage.app.core.data.model.AiringEpisode
import com.anikage.app.core.data.model.AiringSchedule
import com.anikage.app.core.data.model.Anime
import com.anikage.app.core.data.model.AnimeDetails
import com.anikage.app.core.data.model.AnimeTitle
import com.anikage.app.core.data.model.CoverImage
import com.anikage.app.core.data.model.FuzzyDate
import com.anikage.app.core.data.model.HomeFeed
import com.anikage.app.core.data.model.PageInfo
import com.anikage.app.core.data.model.Character
import com.anikage.app.core.data.model.CharacterConnection
import com.anikage.app.core.data.model.CharacterImage
import com.anikage.app.core.data.model.CharacterName
import com.anikage.app.core.data.model.RecommendationConnection
import com.anikage.app.core.data.model.RecommendationNode
import com.anikage.app.core.data.model.RelationConnection
import com.anikage.app.core.data.model.Studio
import com.anikage.app.core.data.model.StudioConnection
import com.anikage.app.core.data.model.Trailer
import com.anikage.app.core.log.AppLogger
import com.anikage.app.core.log.LogCategory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * The repository. ViewModels call these suspend functions; the repository
 * talks to the Anikage REST API (anikage.cc — browse / episodes / sources /
 * comments) and the AniList GraphQL API (metadata / lists / schedule), with
 * a Room cache as offline fallback.
 *
 * ## Duplicate-request protection
 *
 * A previous release shipped a bug where several widgets independently
 * fetched the same endpoint at startup (5x popularity browse, 2x Trending,
 * 2x TopRated, 2x AnimeDetails). This repository now guarantees, by
 * construction:
 *
 *  - **One repository per app** — [get] is a process-wide singleton, so there
 *    is exactly one OkHttp client, one in-flight table and one TTL cache
 *    shared by every screen.
 *  - **Single-flight** — concurrent callers of the same key (same list, same
 *    anime details, same comments page) share ONE network request. The first
 *    caller launches it; everyone else awaits the same Deferred.
 *  - **TTL memoisation** — successful responses are reused for a short
 *    window (list data: 60s, details: 5min, comments: 15s), so navigating
 *    Home -> Details -> Watch -> Details doesn't refetch the world.
 *
 * [forceRefresh] bypasses the TTL cache (but still dedups in-flight calls)
 * for explicit user refreshes.
 */
class AnikageRepository private constructor(
    context: Context,
    private val api: AniListApi = AniListApi(AniListApi.defaultClient()),
    private val anikage: AnikageApi? = if (Config.ANIKAGE_API_BASE_URL != null) {
        try { AnikageApi() } catch (e: Exception) { null }
    } else null,
) {

    private val db = AnikageDatabase.get(context)
    private val animeDao = db.animeDao()
    private val detailDao = db.detailDao()
    private val recentlyViewedDao = db.recentlyViewedDao()
    private val watchProgressDao = db.watchProgressDao()
    private val json = AniListApi.defaultJson

    private val tag = "AnikageRepository"

    // -------------------------------------------------------------------------
    //  Single-flight + TTL cache machinery
    // -------------------------------------------------------------------------

    private class CacheEntry(val value: Any?, val expiresAt: Long)

    /** Repository-owned scope: shared requests outlive any single caller. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flightMutex = Mutex()
    private val cacheMutex = Mutex()
    private val inFlight = mutableMapOf<String, Deferred<Any?>>()
    private val ttlCache = mutableMapOf<String, CacheEntry>()

    /**
     * Run [block] at most once concurrently per [key]; memoise the result for
     * [ttlMs]. Failures are never cached (retry is allowed), and the in-flight
     * entry is always removed, so a failed request can be retried immediately.
     */
    private suspend fun <T> singleFlight(
        key: String,
        ttlMs: Long,
        forceRefresh: Boolean = false,
        block: suspend () -> T,
    ): T {
        if (!forceRefresh && ttlMs > 0) {
            cacheMutex.withLock {
                val hit = ttlCache[key]
                if (hit != null && hit.expiresAt > System.currentTimeMillis()) {
                    AppLogger.v(LogCategory.DATA, "Cache hit: $key")
                    @Suppress("UNCHECKED_CAST")
                    return hit.value as T
                }
            }
        }

        val deferred: Deferred<Any?> = flightMutex.withLock {
            inFlight[key] ?: scope.async(start = CoroutineStart.LAZY) {
                try {
                    block()
                } finally {
                    flightMutex.withLock { inFlight.remove(key) }
                }
            }.also {
                inFlight[key] = it
                it.start()
            }
        }

        @Suppress("UNCHECKED_CAST")
        val result = deferred.await() as T
        // Only memoise successful (non-null) results: a null return (e.g. a
        // transient slug-resolution failure) must stay retryable.
        if (ttlMs > 0 && result != null) {
            cacheMutex.withLock {
                ttlCache[key] = CacheEntry(result, System.currentTimeMillis() + ttlMs)
            }
        }
        return result
    }

    // -------------------------------------------------------------------------
    //  Helpers — cache (entity) ↔ model (Anime)
    // -------------------------------------------------------------------------

    private fun Anime.toEntity() = AnimeCacheEntity(
        id = id,
        titleRomaji = title.romaji,
        titleEnglish = title.english,
        titleNative = title.native,
        coverLarge = coverImage.large,
        coverExtraLarge = coverImage.extraLarge,
        coverMedium = coverImage.medium,
        coverColor = coverImage.color,
        bannerImage = bannerImage,
        averageScore = averageScore,
        popularity = popularity,
        favourites = favourites,
        format = format,
        status = status,
        episodes = episodes,
        duration = duration,
        season = season,
        seasonYear = seasonYear,
        genres = genres.joinToString(","),
        cachedAt = System.currentTimeMillis(),
    )

    private fun AnimeCacheEntity.toModel() = Anime(
        id = id,
        title = AnimeTitle(
            romaji = titleRomaji,
            english = titleEnglish,
            native = titleNative,
        ),
        coverImage = CoverImage(
            large = coverLarge,
            extraLarge = coverExtraLarge,
            medium = coverMedium,
            color = coverColor,
        ),
        bannerImage = bannerImage,
        averageScore = averageScore,
        popularity = popularity,
        favourites = favourites,
        format = format,
        status = status,
        episodes = episodes,
        duration = duration,
        season = season,
        seasonYear = seasonYear,
        genres = genres?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
    )

    private suspend fun cacheAll(items: List<Anime>) {
        if (items.isEmpty()) return
        // Every list payload carries anilistId + slug — remember the pairing
        // so later detail loads can hit the Anikage info endpoint directly.
        items.forEach { rememberSlug(it.id, it.slug) }
        animeDao.upsertAll(items.map { it.toEntity() })
    }

    // -------------------------------------------------------------------------
    //  Home screen — sections (TTL 60s, single-flight)
    // -------------------------------------------------------------------------

    /**
     * The website's own homepage payload (GET /api/media/anime/home):
     * spotlight hero slides (with TVDB fanart + clearLogo artwork), the
     * Editor's Pick featured card, and every rail in site order. This is
     * the source of truth for a 1:1 home screen — AniList fallbacks are
     * used only when the Anikage API is unreachable.
     */
    suspend fun homeFeed(forceRefresh: Boolean = false): Result<HomeFeed> =
        singleFlight("anikage:homeFeed", TTL_LIST, forceRefresh) { fetchHomeFeed() }

    private suspend fun fetchHomeFeed(): Result<HomeFeed> =
        withContext(Dispatchers.IO) {
            anikage?.let { api ->
                try {
                    val response = api.home()
                    val feed = HomeFeed(
                        spotlight = response.spotlight.map { it.toAnime() },
                        featured = response.featured?.toAnime(),
                        trending = response.trending.map { it.toAnime() },
                        seasonal = response.seasonal.map { it.toAnime() },
                        favorites = response.favorites.map { it.toAnime() },
                        top10 = response.top10.map { it.toAnime() },
                        popularMovies = response.popularMovies.map { it.toAnime() },
                        upcoming = response.upcoming.map { it.toAnime() },
                    )
                    if (feed.spotlight.isNotEmpty() || feed.trending.isNotEmpty()) {
                        cacheAll(feed.trending + feed.seasonal + feed.favorites)
                        return@withContext Result.success(feed)
                    }
                } catch (e: Exception) {
                    AppLogger.w(LogCategory.DATA, "Anikage home feed failed — falling back to AniList", e)
                }
            }
            // AniList fallback (no hero artwork available in this mode).
            try {
                val trending = api.trending(perPage = 20).first
                val seasonal = api.popularThisSeason(currentSeason(), currentYear(), perPage = 20).first
                val favorites = api.topRated(perPage = 20).first
                val upcoming = api.upcoming(perPage = 20).first
                cacheAll(trending + seasonal + favorites)
                Result.success(
                    HomeFeed(
                        spotlight = emptyList(),
                        featured = null,
                        trending = trending,
                        seasonal = seasonal,
                        favorites = favorites,
                        top10 = favorites.take(10),
                        popularMovies = emptyList(),
                        upcoming = upcoming,
                    )
                )
            } catch (e: Exception) {
                Log.w(tag, "homeFeed fallback failed: ${e.message}")
                Result.failure(e)
            }
        }

    suspend fun trending(forceRefresh: Boolean = false): Result<List<Anime>> =
        singleFlight("anikage:trending", TTL_LIST, forceRefresh) { fetchTrending() }

    private suspend fun fetchTrending(): Result<List<Anime>> {
        // Prefer the site's own trending ordering when the Anikage API is on.
        anikage?.let { api ->
            try {
                val response: AnikageBrowseResponse = api.browse(sort = "trending", page = 1, limit = 20)
                val items = response.data.map { it.toAnime() }
                if (items.isNotEmpty()) {
                    cacheAll(items)
                    return Result.success(items)
                }
            } catch (e: Exception) {
                AppLogger.w(LogCategory.DATA, "Anikage trending failed — falling back to AniList", e)
            }
        }
        return withContext(Dispatchers.IO) {
            try {
                val (items, _) = api.trending(perPage = 20)
                cacheAll(items)
                Result.success(items)
            } catch (e: Exception) {
                Log.w(tag, "trending failed: ${e.message}")
                val cached = animeDao.recent(20).map { it.toModel() }
                if (cached.isNotEmpty()) Result.success(cached)
                else Result.failure(e)
            }
        }
    }

    suspend fun popularThisSeason(forceRefresh: Boolean = false): Result<List<Anime>> =
        singleFlight("anikage:popularSeason", TTL_LIST, forceRefresh) { fetchPopularThisSeason() }

    private suspend fun fetchPopularThisSeason(): Result<List<Anime>> =
        withContext(Dispatchers.IO) {
            try {
                val (items, _) = api.popularThisSeason(currentSeason(), currentYear(), perPage = 20)
                cacheAll(items)
                Result.success(items)
            } catch (e: Exception) {
                Log.w(tag, "popularThisSeason failed: ${e.message}")
                Result.failure(e)
            }
        }

    suspend fun topRated(forceRefresh: Boolean = false): Result<List<Anime>> =
        singleFlight("anikage:topRated", TTL_LIST, forceRefresh) { fetchTopRated() }

    private suspend fun fetchTopRated(): Result<List<Anime>> =
        withContext(Dispatchers.IO) {
            try {
                val (items, _) = api.topRated(perPage = 20)
                cacheAll(items)
                Result.success(items)
            } catch (e: Exception) {
                Log.w(tag, "topRated failed: ${e.message}")
                Result.failure(e)
            }
        }

    suspend fun upcoming(forceRefresh: Boolean = false): Result<List<Anime>> =
        singleFlight("anikage:upcoming", TTL_LIST, forceRefresh) { fetchUpcoming() }

    private suspend fun fetchUpcoming(): Result<List<Anime>> =
        withContext(Dispatchers.IO) {
            try {
                val (items, _) = api.upcoming(perPage = 20)
                cacheAll(items)
                Result.success(items)
            } catch (e: Exception) {
                Log.w(tag, "upcoming failed: ${e.message}")
                Result.failure(e)
            }
        }

    // -------------------------------------------------------------------------
    //  Browse — filters (TTL 60s, single-flight)
    // -------------------------------------------------------------------------

    /**
     * The site's own browse pipeline: GET /api/media/anime/browse with the
     * site's exact filter params. Falls back to AniList GraphQL (mapping the
     * same filter values) when the Anikage API is unavailable.
     */
    suspend fun browseCatalogue(
        query: String? = null,
        sort: String = "popularity",
        genres: String? = null,
        season: String? = null,
        year: Int? = null,
        format: String? = null,
        status: String? = null,
        country: String? = null,
        page: Int = 1,
        limit: Int = 30,
    ): Result<Triple<List<Anime>, Int, Boolean>> =
        singleFlight(
            "anikage:catalogue:$query:$sort:$genres:$season:$year:$format:$status:$country:$page:$limit",
            TTL_NONE,
        ) {
            fetchCatalogue(query, sort, genres, season, year, format, status, country, page, limit)
        }

    private suspend fun fetchCatalogue(
        query: String?, sort: String, genres: String?, season: String?, year: Int?,
        format: String?, status: String?, country: String?, page: Int, limit: Int,
    ): Result<Triple<List<Anime>, Int, Boolean>> = withContext(Dispatchers.IO) {
        val ak = anikage
        if (ak != null) {
            try {
                val response = ak.browse(
                    query = query, sort = sort, page = page, limit = limit,
                    genres = genres, season = season, year = year,
                    format = format, status = status, country = country,
                    adult = com.anikage.app.core.settings.SettingsState.showAdultContent,
                )
                val items = response.data.map { it.toAnime() }.distinctBy { it.id to it.displayTitle() }
                if (items.isNotEmpty() || page > 1) {
                    cacheAll(items)
                    return@withContext Result.success(Triple(items, response.total, response.hasNext))
                }
            } catch (e: Exception) {
                AppLogger.w(LogCategory.DATA, "Anikage catalogue failed — falling back to AniList", e)
            }
        }
        // AniList fallback with the same filters mapped to GraphQL.
        try {
            val anilistSort = when (sort) {
                "trending" -> "TRENDING_DESC"
                "score" -> "SCORE_DESC"
                "favourites" -> "FAVOURITES_DESC"
                "newest" -> "START_DATE_DESC"
                else -> "POPULARITY_DESC"
            }
            val (items, info) = api.browse(
                page = page, perPage = limit,
                season = season, year = year,
                genre = genres?.split(",")?.firstOrNull(),
                format = format?.split(",")?.firstOrNull(),
                status = status?.split(",")?.firstOrNull(),
                sort = anilistSort,
            )
            cacheAll(items)
            Result.success(Triple(items, info.total, info.hasNextPage))
        } catch (e: Exception) {
            Log.w(tag, "catalogue fallback failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun browse(
        page: Int = 1,
        perPage: Int = 24,
        season: String? = null,
        year: Int? = null,
        genre: String? = null,
        format: String? = null,
        status: String? = null,
        sort: String = "POPULARITY_DESC",
        forceRefresh: Boolean = false,
    ): Result<Pair<List<Anime>, PageInfo>> =
        singleFlight("anikage:browse:$page:$perPage:$season:$year:$genre:$format:$status:$sort", TTL_LIST, forceRefresh) {
            fetchBrowse(page, perPage, season, year, genre, format, status, sort)
        }

    private suspend fun fetchBrowse(
        page: Int, perPage: Int, season: String?, year: Int?, genre: String?,
        format: String?, status: String?, sort: String,
    ): Result<Pair<List<Anime>, PageInfo>> = withContext(Dispatchers.IO) {
        try {
            val (items, info) = api.browse(page, perPage, season, year, genre, format, status, sort)
            cacheAll(items)
            Result.success(items to info)
        } catch (e: Exception) {
            Log.w(tag, "browse failed: ${e.message}")
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    //  Search (no TTL — user-driven; single-flight still applies)
    // -------------------------------------------------------------------------

    suspend fun search(
        query: String,
        page: Int = 1,
        perPage: Int = 24,
    ): Result<Pair<List<Anime>, PageInfo>> =
        singleFlight("anikage:search:$query:$page:$perPage", TTL_NONE) {
            fetchSearch(query, page, perPage)
        }

    private suspend fun fetchSearch(
        query: String, page: Int, perPage: Int,
    ): Result<Pair<List<Anime>, PageInfo>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext Result.success(emptyList<Anime>() to PageInfo())
        }
        // PRIMARY — the site's own search (browse?q=), the exact query the
        // website runs; served by the Anikage backend, so search keeps
        // working even while the public AniList API is unavailable.
        val ak = anikage
        if (ak != null) {
            try {
                val response = ak.browse(
                    query = query,
                    page = page,
                    limit = perPage,
                    adult = com.anikage.app.core.settings.SettingsState.showAdultContent,
                )
                val items = response.data.map { it.toAnime() }
                if (items.isNotEmpty()) {
                    cacheAll(items)
                    return@withContext Result.success(
                        items to PageInfo(
                            total = response.total,
                            currentPage = page,
                            hasNextPage = response.hasNext,
                            perPage = perPage,
                        )
                    )
                }
            } catch (e: Exception) {
                AppLogger.w(LogCategory.DATA, "Anikage search failed for '$query' — falling back to AniList", e)
            }
        }
        // FALLBACK — AniList GraphQL SEARCH.
        try {
            val (items, info) = api.search(query, page, perPage)
            cacheAll(items)
            Result.success(items to info)
        } catch (e: Exception) {
            Log.w(tag, "search failed: ${e.message}")
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    //  Schedule (TTL 5min, single-flight)
    // -------------------------------------------------------------------------

    suspend fun scheduleWeek(
        // Start at TODAY'S MIDNIGHT (not "now") so today's already-aired
        // episodes appear with the site's "Aired" badge instead of dropping.
        startAt: Long = run {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            cal.timeInMillis / 1000
        },
        forceRefresh: Boolean = false,
    ): Result<List<AiringSchedule>> =
        singleFlight("anikage:schedule:$startAt", TTL_LONG, forceRefresh) { fetchScheduleWeek(startAt) }

    private suspend fun fetchScheduleWeek(startAt: Long): Result<List<AiringSchedule>> =
        withContext(Dispatchers.IO) {
            // PRIMARY — the site's own schedule payload (GET /api/media/anime/
            // schedule), served by the Anikage backend: the same data the
            // website renders, with no dependency on the public AniList API.
            val ak = anikage
            if (ak != null) {
                try {
                    val elements: List<AnikageScheduleElement> = ak.schedule()
                    val entries = elements.flatMap { el ->
                        el.schedule.map { entry -> entry.toAiringSchedule() }
                    }
                    if (entries.isNotEmpty()) {
                        entries.forEach { rememberSlug(it.media.id, it.media.slug) }
                        AppLogger.i(
                            LogCategory.DATA,
                            "Schedule loaded from Anikage (${entries.size} entries across 7 days)",
                        )
                        return@withContext Result.success(entries)
                    }
                } catch (e: Exception) {
                    AppLogger.w(LogCategory.DATA, "Anikage schedule failed — falling back to AniList", e)
                }
            }
            // FALLBACK — AniList GraphQL airingSchedules.
            try {
                val endAt = startAt + 7 * 24 * 60 * 60
                val (items, _) = api.schedule(startAt, endAt, page = 1, perPage = 50)
                Result.success(items)
            } catch (e: Exception) {
                Log.w(tag, "schedule failed: ${e.message}")
                Result.failure(e)
            }
        }

    // -------------------------------------------------------------------------
    //  Anime details (TTL 5min, single-flight — shared by Details & Watch)
    //
    //  PRIMARY:   Anikage info (GET /api/media/anime/{slug}) — the payload
    //             the site's own /anime/info page renders from.
    //  SECONDARY: AniList GraphQL (only reachable when their public API is
    //             up — it was globally disabled with HTTP 403 when the
    //             Anikage path was introduced).
    //  LAST:      Room detail cache (last known good).
    // -------------------------------------------------------------------------

    /** anilistId -> catalogue slug, remembered from every payload carrying both. */
    private val slugMemory = mutableMapOf<Int, String>()

    private fun rememberSlug(anilistId: Int?, slug: String?) {
        if (anilistId == null || anilistId <= 0 || slug.isNullOrBlank()) return
        slugMemory[anilistId] = slug
    }

    /** Best known catalogue slug for an AniList id (preview store -> memory -> cached details). */
    private suspend fun slugFor(anilistId: Int): String? {
        AnimePreviewStore.slugFor(anilistId)?.let { return it }
        slugMemory[anilistId]?.let { return it }
        detailDao.get(anilistId)?.let { cached ->
            try {
                json.decodeFromString(AnimeDetails.serializer(), cached.json).slug?.let { return it }
            } catch (_: Exception) {
            }
        }
        return null
    }

    suspend fun animeDetails(id: Int, forceRefresh: Boolean = false): Result<AnimeDetails> =
        singleFlight("anikage:details:$id", TTL_LONG, forceRefresh) { fetchAnimeDetails(id) }

    private suspend fun fetchAnimeDetails(id: Int): Result<AnimeDetails> = withContext(Dispatchers.IO) {
        // Resolve the catalogue slug from any source we already have; when
        // only a title is known (list preview), resolve via the catalogue
        // search — never via AniList.
        var known: String? = slugFor(id)
        if (known == null) {
            AnimePreviewStore.byId(id)?.let { preview ->
                known = resolveSlug(id, preview.title.english, preview.title.romaji)
            }
        }
        val slug = known
        val ak = anikage
        if (ak != null && slug != null) {
            try {
                val info: AnikageInfoResponse = ak.animeInfo(slug)
                if (!info.banned && info.anime != null) {
                    val details = info.anime.toAnimeDetails().let { mapped ->
                        if (mapped.id > 0) mapped else mapped.copy(id = id)
                    }
                    rememberSlug(id, details.slug)
                    detailDao.upsert(
                        DetailCacheEntity(
                            animeId = id,
                            json = json.encodeToString(AnimeDetails.serializer(), details),
                        )
                    )
                    AppLogger.i(LogCategory.DATA, "Details loaded from Anikage info (id=$id, slug=$slug)")
                    return@withContext Result.success(details)
                }
            } catch (e: Exception) {
                AppLogger.w(LogCategory.DATA, "Anikage info failed for slug=$slug — trying AniList", e)
            }
        }
        // SECONDARY — AniList GraphQL.
        try {
            val details = api.animeDetails(id)
                ?: return@withContext Result.failure(IllegalArgumentException("Anime $id not found"))
            detailDao.upsert(
                DetailCacheEntity(
                    animeId = id,
                    json = json.encodeToString(AnimeDetails.serializer(), details),
                )
            )
            Result.success(details)
        } catch (e: Exception) {
            Log.w(tag, "animeDetails failed: ${e.message}")
            // LAST — Room cache (last known good).
            val cached = detailDao.get(id)
            if (cached != null) {
                try {
                    val details = json.decodeFromString(AnimeDetails.serializer(), cached.json)
                    Result.success(details)
                } catch (_: Exception) {
                    Result.failure(e)
                }
            } else {
                Result.failure(e)
            }
        }
    }

    // -------------------------------------------------------------------------
    //  Anikage watch pipeline — slug, episodes, sources (stream), views
    // -------------------------------------------------------------------------

    /**
     * Resolve the Anikage slug for an AniList id. Slugs are stable, so the
     * mapping is memoised for a long time (30 min). Resolution searches the
     * site catalogue by title and matches the exact anilistId.
     */
    suspend fun resolveSlug(anilistId: Int, titleEnglish: String?, titleRomaji: String?): String? =
        singleFlight("anikage:slug:$anilistId", TTL_SLUG) {
            val api = anikage ?: return@singleFlight null
            val candidates = listOfNotNull(titleEnglish, titleRomaji).filter { it.isNotBlank() }
            for (title in candidates) {
                try {
                    val response = api.browse(query = title, limit = 50)
                    val exact = response.data.firstOrNull { it.anilistId == anilistId }
                    if (exact != null) {
                        AppLogger.i(LogCategory.DATA, "Slug resolved: $anilistId -> ${exact.slug} ('$title')")
                        return@singleFlight exact.slug
                    }
                } catch (e: Exception) {
                    AppLogger.w(LogCategory.DATA, "Slug search failed for '$title'", e)
                }
            }
            AppLogger.w(LogCategory.DATA, "Slug not found for anilistId=$anilistId (searched: $candidates)")
            null
        }

    /** Real episode metadata for an anime slug (titles, thumbnails, air dates). TTL 5min. */
    suspend fun anikageEpisodes(slug: String): Result<List<AnikageEpisode>> =
        singleFlight("anikage:episodes:$slug", TTL_LONG) {
            val api = anikage
                ?: return@singleFlight Result.failure(IllegalStateException("Anikage API disabled"))
            try {
                Result.success(api.episodes(slug))
            } catch (e: Exception) {
                AppLogger.w(LogCategory.DATA, "Episodes failed for $slug", e)
                Result.failure(e)
            }
        }

    /** Stream sources for an episode — tokens resolve via [AnikageApi.resolveStreamUrl]. TTL 30s. */
    suspend fun anikageSources(
        slug: String,
        episode: Int,
        provider: String = Config.DEFAULT_STREAM_PROVIDER,
        lang: String = Config.DEFAULT_STREAM_LANG,
        refresh: Boolean = false,
    ): Result<AnikageSourcesResponse> {
        // refresh=1 must always hit the network (site's "Fix it" flow).
        if (refresh) {
            val api = anikage ?: return Result.failure(IllegalStateException("Anikage API disabled"))
            return try {
                Result.success(api.sources(slug, episode, provider, lang, null, true))
            } catch (e: Exception) {
                AppLogger.w(LogCategory.DATA, "Sources refresh failed for $slug ep $episode", e)
                Result.failure(e)
            }
        }
        return singleFlight("anikage:sources:$slug:$episode:$provider:$lang", TTL_STREAM) {
            val api = anikage
                ?: return@singleFlight Result.failure(IllegalStateException("Anikage API disabled"))
            try {
                val response = api.sources(slug, episode, provider, lang)
                if (response.sources.isEmpty()) {
                    AppLogger.w(LogCategory.PLAYER, "No stream sources for $slug ep $episode")
                } else {
                    AppLogger.i(
                        LogCategory.PLAYER,
                        "Resolved ${response.sources.size} source(s) for $slug ep $episode " +
                            "(provider=${response.providerId}, quality=${response.sources.firstOrNull()?.quality})",
                    )
                }
                Result.success(response)
            } catch (e: Exception) {
                AppLogger.w(LogCategory.DATA, "Sources failed for $slug ep $episode", e)
                Result.failure(e)
            }
        }
    }

    /** Full HLS URL for the best source, or null when the provider chain fails. */
    suspend fun anikageStreamUrl(
        slug: String,
        episode: Int,
        provider: String = Config.DEFAULT_STREAM_PROVIDER,
        lang: String = Config.DEFAULT_STREAM_LANG,
    ): String? {
        val api = anikage ?: return null
        val result = anikageSources(slug, episode, provider, lang)
        val response = result.getOrNull() ?: return null
        val best = response.sources.firstOrNull { it.isM3U8 } ?: response.sources.firstOrNull()
        val token = best?.url ?: return null
        return api.resolveStreamUrl(token)
    }

    /**
     * Playback servers for an episode — the raw server list (provider ids +
     * subTypes) so the watch screen can disable servers that don't offer the
     * current SUB/DUB language, exactly like the site's server panel.
     */
    /** Servers + embeds (E-server chips) — the raw servers response. */
    suspend fun anikageServersResponse(
        slug: String,
        episode: Int,
    ): Result<com.anikage.app.core.data.api.AnikageServersResponse> =
        singleFlight("anikage:servers2:$slug:$episode", TTL_LIST) {
            val api = anikage
                ?: return@singleFlight Result.failure(IllegalStateException("Anikage API disabled"))
            try {
                Result.success(api.servers(slug, episode))
            } catch (e: Exception) {
                AppLogger.w(LogCategory.DATA, "Servers failed for $slug ep $episode", e)
                Result.failure(e)
            }
        }

    suspend fun anikageServers(slug: String, episode: Int): Result<List<AnikageServer>> =
        singleFlight("anikage:servers:$slug:$episode", TTL_LIST) {
            val api = anikage
                ?: return@singleFlight Result.failure(IllegalStateException("Anikage API disabled"))
            try {
                val response = api.servers(slug, episode)
                Result.success(response.servers)
            } catch (e: Exception) {
                AppLogger.w(LogCategory.DATA, "Servers failed for $slug ep $episode", e)
                Result.failure(e)
            }
        }

    // ---------------------------------------------------------------------
    //  Music — the site's music tab (/api/animethemes proxy)
    // ---------------------------------------------------------------------

    /** Search anime by title -> themes (song titles) + cover art. */
    suspend fun musicSearch(query: String): Result<List<AnikageMusicAnime>> =
        singleFlight("anikage:music:$query", TTL_COMMENTS) {
            val api = anikage
                ?: return@singleFlight Result.failure(IllegalStateException("Anikage API disabled"))
            try {
                val response = api.musicSearch(query)
                Result.success(response.search.anime)
            } catch (e: Exception) {
                AppLogger.w(LogCategory.NETWORK, "Music search failed for '$query'", e)
                Result.failure(e)
            }
        }

    /** Full theme set for one anime (videos + audio links + AniList id). */
    suspend fun musicAnime(slug: String): Result<AnikageMusicAnime> =
        singleFlight("anikage:music-anime:$slug", TTL_STREAM) {
            val api = anikage
                ?: return@singleFlight Result.failure(IllegalStateException("Anikage API disabled"))
            try {
                Result.success(api.musicAnime(slug))
            } catch (e: Exception) {
                AppLogger.w(LogCategory.NETWORK, "Music themes failed for $slug", e)
                Result.failure(e)
            }
        }

    /** Comments for an episode. TTL 15s so a re-open refreshes but rapid switches don't refetch. */
    suspend fun anikageComments(
        animeId: Int,
        episode: Int,
        limit: Int = 20,
        sort: String = "newest",
    ): Result<List<AnikageComment>> =
        singleFlight("anikage:comments:$animeId:$episode:$limit:$sort", TTL_COMMENTS) {
            val api = anikage
                ?: return@singleFlight Result.failure(IllegalStateException("Anikage API disabled"))
            try {
                Result.success(api.comments(animeId, episode, limit, sort).comments)
            } catch (e: Exception) {
                AppLogger.w(LogCategory.DATA, "Comments failed for anime=$animeId ep=$episode", e)
                Result.failure(e)
            }
        }

    /** View count for an episode. TTL 30s. */
    suspend fun anikageViews(slug: String, episode: Int): Long? =
        singleFlight("anikage:views:$slug:$episode", TTL_STREAM) {
            val api = anikage ?: return@singleFlight null
            try {
                api.views(slug, episode).viewCount
            } catch (e: Exception) {
                AppLogger.w(LogCategory.DATA, "Views failed for $slug ep $episode", e)
                null
            }
        }

    /**
     * Record an episode view (the site player's once-per-episode POST).
     * Returns true when the server counted it; failures are silent (the
     * site itself just disables retries for the session).
     */
    suspend fun anikageRecordView(slug: String, episode: Int, aniId: Int): Boolean =
        try {
            anikage?.recordView(slug, episode, aniId) == true
        } catch (e: Exception) {
            AppLogger.w(LogCategory.DATA, "Record view failed for $slug ep $episode", e)
            false
        }

    /** Download links for an episode (the site's Download dialog). */
    suspend fun anikageDownloads(
        slug: String,
        episode: Int,
    ): Result<com.anikage.app.core.data.api.AnikageDownloadsResponse> =
        singleFlight("anikage:downloads:$slug:$episode", TTL_STREAM) {
            val api = anikage
                ?: return@singleFlight Result.failure(IllegalStateException("Anikage API disabled"))
            try {
                Result.success(api.downloads(slug, episode))
            } catch (e: Exception) {
                AppLogger.w(LogCategory.DATA, "Downloads failed for $slug ep $episode", e)
                Result.failure(e)
            }
        }

    /** Report an episode problem (the site's Report dialog POST). */
    suspend fun anikageReport(
        slug: String,
        type: String,
        provider: String? = null,
        episode: Int? = null,
        note: String? = null,
    ): Result<Unit> {
        val api = anikage ?: return Result.failure(IllegalStateException("Anikage API disabled"))
        return try {
            api.report(slug, type, provider, episode, note)
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.w(LogCategory.DATA, "Report failed for $slug ($type)", e)
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    //  Continue Watching — recently viewed anime
    // -------------------------------------------------------------------------

    fun observeRecent(): Flow<List<RecentlyViewedEntity>> =
        recentlyViewedDao.observe(20)

    suspend fun markRecentlyViewed(anime: Anime, episode: Int = 1) =
        withContext(Dispatchers.IO) {
            recentlyViewedDao.upsert(
                RecentlyViewedEntity(
                    animeId = anime.id,
                    lastViewedAt = System.currentTimeMillis(),
                    titleRomaji = anime.title.romaji,
                    titleEnglish = anime.title.english,
                    coverUrl = anime.coverUrl(),
                    lastEpisode = episode,
                )
            )
        }

    // -------------------------------------------------------------------------
    //  Watch progress — per episode
    // -------------------------------------------------------------------------

    suspend fun saveProgress(animeId: Int, episode: Int, positionMs: Long, durationMs: Long) =
        withContext(Dispatchers.IO) {
            watchProgressDao.upsert(
                WatchProgressEntity(
                    episodeKey = "$animeId-$episode",
                    animeId = animeId,
                    episode = episode,
                    positionMs = positionMs,
                    durationMs = durationMs,
                )
            )
        }

    suspend fun loadProgress(animeId: Int, episode: Int): WatchProgressEntity? =
        withContext(Dispatchers.IO) {
            watchProgressDao.get("$animeId-$episode")
        }

    suspend fun loadProgressForAnime(animeId: Int): List<WatchProgressEntity> =
        withContext(Dispatchers.IO) {
            watchProgressDao.forAnime(animeId)
        }

    // -------------------------------------------------------------------------
    //  Downloaded episodes + local anime list (in-app download engine + UI)
    // -------------------------------------------------------------------------

    suspend fun downloadedEpisode(animeId: Int, episode: Int): DownloadedEpisodeEntity? =
        withContext(Dispatchers.IO) {
            db.downloadedEpisodeDao().forEpisode(animeId, episode)
        }

    suspend fun allDownloadedEpisodes(): List<DownloadedEpisodeEntity> =
        withContext(Dispatchers.IO) {
            db.downloadedEpisodeDao().all()
        }

    suspend fun downloadedForAnime(animeId: Int): List<DownloadedEpisodeEntity> =
        withContext(Dispatchers.IO) {
            db.downloadedEpisodeDao().forAnime(animeId)
        }

    suspend fun getListStatus(animeId: Int): String? =
        withContext(Dispatchers.IO) {
            db.animeListDao().get(animeId)?.status
        }

    suspend fun setListStatus(
        animeId: Int,
        status: String?,
        titleRomaji: String? = null,
        titleEnglish: String? = null,
        posterUrl: String? = null,
        coverColor: String? = null,
    ) = withContext(Dispatchers.IO) {
        if (status == null) {
            db.animeListDao().delete(animeId)
        } else {
            db.animeListDao().upsert(
                AnimeListEntity(
                    animeId = animeId,
                    status = status,
                    titleRomaji = titleRomaji,
                    titleEnglish = titleEnglish,
                    posterUrl = posterUrl,
                    coverColor = coverColor,
                )
            )
        }
    }

    suspend fun localAnimeList(): List<AnimeListEntity> =
        withContext(Dispatchers.IO) {
            db.animeListDao().all()
        }

    /** Settings -> Account -> Clear watch history: removes progress rows. */
    suspend fun clearWatchData() = withContext(Dispatchers.IO) {
        runCatching {
            db.openHelper.writableDatabase.apply {
                execSQL("DELETE FROM watch_progress")
                execSQL("DELETE FROM recently_viewed")
            }
            AppLogger.i(LogCategory.DATA, "Watch history + progress cleared (user action)")
        }
    }

    // -------------------------------------------------------------------------
    //  Subscriptions (new-episode notifications) — user directive #12/#13
    // -------------------------------------------------------------------------

    /** Live anime info by slug (used by the subscription checker). */
    suspend fun animeInfoBySlug(slug: String): AnikageInfoAnime? = withContext(Dispatchers.IO) {
        runCatching { anikage?.animeInfo(slug)?.anime }.getOrNull()
    }

    fun observeSubscriptions(): Flow<List<SubscriptionEntity>> = db.subscriptionDao().observeAll()

    suspend fun allSubscriptions(): List<SubscriptionEntity> =
        withContext(Dispatchers.IO) { db.subscriptionDao().all() }

    suspend fun getSubscription(animeId: Int): SubscriptionEntity? =
        withContext(Dispatchers.IO) { db.subscriptionDao().get(animeId) }

    /**
     * Subscribe: baseline episode count comes from the live info payload so
     * only FUTURE releases notify (never a notification for episodes that
     * existed before the user subscribed).
     */
    suspend fun subscribe(
        animeId: Int,
        slug: String?,
        titleRomaji: String?,
        titleEnglish: String?,
        posterUrl: String?,
        coverColor: String?,
        status: String?,
        knownEpisodes: Int,
        nextAiringEpisode: Int?,
    ) = withContext(Dispatchers.IO) {
        val current = db.subscriptionDao().get(animeId)
        val baseline = current?.lastNotifiedEpisode ?: maxOf(knownEpisodes, 0)
        db.subscriptionDao().upsert(
            SubscriptionEntity(
                animeId = animeId,
                slug = slug,
                titleRomaji = titleRomaji,
                titleEnglish = titleEnglish,
                posterUrl = posterUrl,
                coverColor = coverColor,
                releaseStatus = status,
                lastKnownEpisodes = knownEpisodes,
                lastNotifiedEpisode = baseline,
                nextAiringEpisode = nextAiringEpisode,
                subscribedAt = current?.subscribedAt ?: System.currentTimeMillis(),
                lastCheckedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun unsubscribe(animeId: Int) = withContext(Dispatchers.IO) {
        db.subscriptionDao().delete(animeId)
    }

    /** Record a notification + the fresh baseline (called by the worker). */
    suspend fun markSubscriptionNotified(
        animeId: Int,
        episode: Int,
        episodeCount: Int,
        nextAiring: Int?,
        status: String?,
    ) = withContext(Dispatchers.IO) {
        db.subscriptionDao().updateCheck(animeId, episodeCount, episode, nextAiring, status, System.currentTimeMillis())
    }

    /** Silent baseline refresh (no new episodes found). */
    suspend fun updateSubscriptionCheck(
        animeId: Int,
        episodeCount: Int,
        nextAiring: Int?,
        status: String?,
    ) = withContext(Dispatchers.IO) {
        db.subscriptionDao().get(animeId)?.let { current ->
            db.subscriptionDao().updateCheck(
                animeId, episodeCount, current.lastNotifiedEpisode, nextAiring, status,
                System.currentTimeMillis(),
            )
        }
    }

    // -------------------------------------------------------------------------
    //  Comment posting (requires a real auth session)
    // -------------------------------------------------------------------------

    /**
     * Post a comment for an episode through the site's own API. Returns
     * the created comment on success; on 401 the caller offers sign-in.
     */
    suspend fun postComment(
        animeId: Int,
        slug: String?,
        episode: Int,
        content: String,
        isSpoiler: Boolean = false,
        aniTitle: String? = null,
        aniImage: String? = null,
    ): Result<AnikageComment> = withContext(Dispatchers.IO) {
        val client = anikage ?: return@withContext Result.failure(
            IllegalStateException("Anikage API not configured"),
        )
        client.postComment(animeId, slug, episode, content, isSpoiler, aniTitle, aniImage)
    }

    // -------------------------------------------------------------------------
    //  Singleton access
    // -------------------------------------------------------------------------

    companion object {
        private const val TTL_NONE = 0L
        private const val TTL_LIST = 60_000L
        private const val TTL_COMMENTS = 15_000L
        private const val TTL_STREAM = 30_000L
        private const val TTL_LONG = 5 * 60_000L
        private const val TTL_SLUG = 30 * 60_000L

        @Volatile
        private var instance: AnikageRepository? = null

        /** Process-wide repository — one client, one in-flight table, one cache. */
        fun get(context: Context): AnikageRepository =
            instance ?: synchronized(this) {
                instance ?: AnikageRepository(context.applicationContext).also { instance = it }
            }
    }
}

// ---------------------------------------------------------------------------
//  Anikage media (REST DTO) → shared Anime model, so UI code is source-agnostic
// ---------------------------------------------------------------------------

private fun com.anikage.app.core.data.api.AnikageMedia.toAnime() = Anime(
    id = anilistId ?: 0,
    slug = slug,
    title = com.anikage.app.core.data.model.AnimeTitle(
        romaji = title.romaji,
        english = title.english,
        native = title.native,
    ),
    coverImage = com.anikage.app.core.data.model.CoverImage(
        large = coverImage.large,
        extraLarge = coverImage.extraLarge,
        medium = coverImage.medium,
        color = coverColor,
    ),
    bannerImage = bannerImage,
    fanartUrl = fanart,
    clearLogoUrl = clearLogo,
    trailerId = trailerId,
    description = description,
    averageScore = averageScore,
    meanScore = meanScore,
    popularity = popularity,
    favourites = favourites,
    format = format,
    status = status,
    episodes = totalEpisodes,
    duration = duration,
    season = season,
    seasonYear = year,
    genres = genres,
    nextAiringEpisode = nextAiringEpisode?.let {
        AiringEpisode(
            id = 0,
            airingAt = it.airingAt ?: 0L,
            timeUntilAiring = it.timeUntilAiring ?: 0L,
            episode = it.episode ?: 0,
        )
    },
)

/**
 * Anikage info payload (the site's /anime/info data) -> the shared
 * AnimeDetails model. Everything the AniList path provided is available
 * here: description, characters, relations, recommendations, studios,
 * genres, airing info, artwork.
 */
private fun AnikageInfoAnime.toAnimeDetails(): AnimeDetails = AnimeDetails(
    id = anilistId ?: 0,
    slug = slug,
    title = AnimeTitle(romaji = title.romaji, english = title.english, native = title.native),
    coverImage = CoverImage(
        large = coverImage.large,
        extraLarge = coverImage.extraLarge,
        medium = coverImage.medium,
        color = coverColor ?: coverImage.color,
    ),
    bannerImage = bannerImage,
    fanartUrl = fanart,
    clearLogoUrl = clearLogo,
    trailerId = trailerId,
    trailer = trailerId?.let { Trailer(id = it, site = "youtube") },
    description = description,
    averageScore = averageScore,
    meanScore = meanScore,
    popularity = popularity,
    favourites = favourites,
    format = format,
    status = status,
    episodes = totalEpisodes,
    duration = duration,
    season = season,
    seasonYear = year,
    startDate = parseWebDate(startDate),
    endDate = parseWebDate(endDate),
    genres = genres,
    studios = StudioConnection(
        studios.map {
            Studio(id = it.anilistId ?: 0, name = it.name ?: "", isAnimationStudio = it.isAnimationStudio)
        }
    ),
    nextAiringEpisode = nextAiringEpisode?.let {
        AiringEpisode(
            id = 0,
            airingAt = it.airingAt ?: 0L,
            timeUntilAiring = it.timeUntilAiring ?: 0L,
            episode = it.episode ?: 0,
        )
    },
    // Site payload ships the full cast; the details screen shows 12.
    characters = CharacterConnection(
        characters.take(12).map {
            Character(
                id = it.anilistId ?: 0,
                name = CharacterName(full = it.name, native = it.nativeName),
                image = it.image?.let { url -> CharacterImage(large = url, medium = url) },
            )
        }
    ),
    relations = RelationConnection(
        relations.filter { (it.anilistId ?: 0) > 0 }.map { it.toAnime() }
    ),
    recommendations = RecommendationConnection(
        recommendations.filter { (it.anilistId ?: 0) > 0 }.map {
            RecommendationNode(mediaRecommendation = it.toAnime())
        }
    ),
)

/** A related/recommended card from the info payload -> shared Anime model. */
private fun AnikageRelationRef.toAnime(): Anime = Anime(
    id = anilistId ?: 0,
    slug = slug,
    title = AnimeTitle(romaji = title.romaji, english = title.english, native = title.native),
    coverImage = CoverImage(large = coverImage),
    bannerImage = bannerImage,
    format = format,
    status = status,
    episodes = episodes,
)

/** A schedule entry (Anikage schedule payload) -> shared AiringSchedule model. */
private fun com.anikage.app.core.data.api.AnikageScheduleEntry.toAiringSchedule(): AiringSchedule {
    val media = media
    val anime = Anime(
        id = media.anilistId ?: 0,
        slug = media.slug,
        title = AnimeTitle(
            romaji = media.title.romaji,
            english = media.title.english,
            native = media.title.native,
        ),
        coverImage = CoverImage(
            large = media.coverImage.large,
            extraLarge = media.coverImage.extraLarge,
            medium = media.coverImage.medium,
            color = media.coverImage.color,
        ),
        bannerImage = media.bannerImage,
        description = media.description,
        averageScore = media.averageScore,
        format = media.format,
        status = media.status,
        season = media.season,
        seasonYear = media.year,
        episodes = media.episodes,
        genres = media.genres,
        nextAiringEpisode = media.nextAiringEpisode?.let {
            AiringEpisode(
                id = 0,
                airingAt = it.airingAt ?: 0L,
                timeUntilAiring = it.timeUntilAiring ?: 0L,
                episode = it.episode ?: 0,
            )
        },
    )
    return AiringSchedule(
        // The Anikage payload has no per-entry id; synthesize a unique,
        // stable one from (anilistId, episode).
        id = (media.anilistId ?: 0) * 1000 + episode,
        episode = episode,
        airingAt = airingAt,
        timeUntilAiring = (airingAt - System.currentTimeMillis() / 1000).coerceAtLeast(0),
        media = anime,
    )
}

/** Parse the site's "Sep 29, 2023" date strings into a FuzzyDate. */
private fun parseWebDate(value: String?): FuzzyDate? {
    if (value.isNullOrBlank()) return null
    return try {
        val date = SimpleDateFormat("MMM d, yyyy", Locale.US).parse(value) ?: return null
        val cal = Calendar.getInstance()
        cal.time = date
        FuzzyDate(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
            day = cal.get(Calendar.DAY_OF_MONTH),
        )
    } catch (_: Exception) {
        null
    }
}

/**
 * Compute current season name based on month (AniList seasons).
 * WINTER = Dec–Feb, SPRING = Mar–May, SUMMER = Jun–Aug, FALL = Sep–Nov.
 */
fun currentSeason(): String {
    val month = Calendar.getInstance().get(Calendar.MONTH) + 1
    return when (month) {
        12, 1, 2 -> "WINTER"
        3, 4, 5 -> "SPRING"
        6, 7, 8 -> "SUMMER"
        else -> "FALL"
    }
}

fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)
