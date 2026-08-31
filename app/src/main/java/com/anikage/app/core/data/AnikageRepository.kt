package com.anikage.app.core.data

import android.content.Context
import android.util.Log
import com.anikage.app.Config
import com.anikage.app.core.data.api.AniListApi
import com.anikage.app.core.data.api.AnikageApi
import com.anikage.app.core.data.api.AnikageBrowseResponse
import com.anikage.app.core.data.api.AnikageComment
import com.anikage.app.core.data.api.AnikageEpisode
import com.anikage.app.core.data.api.AnikageSourcesResponse
import com.anikage.app.core.data.db.AnikageDatabase
import com.anikage.app.core.data.db.AnimeCacheEntity
import com.anikage.app.core.data.db.DetailCacheEntity
import com.anikage.app.core.data.db.RecentlyViewedEntity
import com.anikage.app.core.data.db.WatchProgressEntity
import com.anikage.app.core.data.model.AiringEpisode
import com.anikage.app.core.data.model.AiringSchedule
import com.anikage.app.core.data.model.Anime
import com.anikage.app.core.data.model.AnimeDetails
import com.anikage.app.core.data.model.AnimeTitle
import com.anikage.app.core.data.model.CoverImage
import com.anikage.app.core.data.model.PageInfo
import com.anikage.app.core.log.AppLogger
import com.anikage.app.core.log.LogCategory
import java.util.Calendar
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
        animeDao.upsertAll(items.map { it.toEntity() })
    }

    // -------------------------------------------------------------------------
    //  Home screen — sections (TTL 60s, single-flight)
    // -------------------------------------------------------------------------

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
        startAt: Long = System.currentTimeMillis() / 1000,
        forceRefresh: Boolean = false,
    ): Result<List<AiringSchedule>> =
        singleFlight("anikage:schedule:$startAt", TTL_LONG, forceRefresh) { fetchScheduleWeek(startAt) }

    private suspend fun fetchScheduleWeek(startAt: Long): Result<List<AiringSchedule>> =
        withContext(Dispatchers.IO) {
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
    // -------------------------------------------------------------------------

    suspend fun animeDetails(id: Int, forceRefresh: Boolean = false): Result<AnimeDetails> =
        singleFlight("anikage:details:$id", TTL_LONG, forceRefresh) { fetchAnimeDetails(id) }

    private suspend fun fetchAnimeDetails(id: Int): Result<AnimeDetails> = withContext(Dispatchers.IO) {
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
            // Fallback to cache
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
    suspend fun anikageSources(slug: String, episode: Int): Result<AnikageSourcesResponse> =
        singleFlight("anikage:sources:$slug:$episode", TTL_STREAM) {
            val api = anikage
                ?: return@singleFlight Result.failure(IllegalStateException("Anikage API disabled"))
            try {
                val response = api.sources(slug, episode)
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

    /** Full HLS URL for the best source, or null when the provider chain fails. */
    suspend fun anikageStreamUrl(slug: String, episode: Int): String? {
        val api = anikage ?: return null
        val result = anikageSources(slug, episode)
        val response = result.getOrNull() ?: return null
        val best = response.sources.firstOrNull { it.isM3U8 } ?: response.sources.firstOrNull()
        val token = best?.url ?: return null
        return api.resolveStreamUrl(token)
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
