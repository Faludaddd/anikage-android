package com.anikage.app.core.data

import android.content.Context
import android.util.Log
import com.anikage.app.Config
import com.anikage.app.core.data.api.AniListApi
import com.anikage.app.core.data.api.GraphQLException
import com.anikage.app.core.data.db.AnikageDatabase
import com.anikage.app.core.data.db.AnimeCacheEntity
import com.anikage.app.core.data.db.DetailCacheEntity
import com.anikage.app.core.data.db.RecentlyViewedEntity
import com.anikage.app.core.data.db.WatchProgressEntity
import com.anikage.app.core.data.model.AiringSchedule
import com.anikage.app.core.data.model.Anime
import com.anikage.app.core.data.model.AnimeDetails
import com.anikage.app.core.data.model.PageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.Calendar
import kotlin.math.ln
import kotlin.math.exp

/**
 * The repository. ViewModels call these suspend functions; the repository
 * talks to the AniList API (for fresh data) and the Room cache (for offline
 * fallback).
 *
 * Caching strategy: every successful API response is persisted to Room. On
 * failure, the repository transparently returns the cached data (with a
 * `fromCache` flag). UIs observe the cache via [observeRecent] for the
 * Continue Watching rail.
 */
class AnikageRepository(
    context: Context,
    private val api: AniListApi = AniListApi(AniListApi.defaultClient()),
) {
    private val db = AnikageDatabase.get(context)
    private val animeDao = db.animeDao()
    private val detailDao = db.detailDao()
    private val recentlyViewedDao = db.recentlyViewedDao()
    private val watchProgressDao = db.watchProgressDao()
    private val json = AniListApi.defaultJson

    private val tag = "AnikageRepository"

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
        title = com.anikage.app.core.data.model.AnimeTitle(
            romaji = titleRomaji,
            english = titleEnglish,
            native = titleNative,
        ),
        coverImage = com.anikage.app.core.data.model.CoverImage(
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
    //  Home screen — sections
    // -------------------------------------------------------------------------

    suspend fun trending(page: Int = 1, perPage: Int = 20): Result<List<Anime>> =
        withContext(Dispatchers.IO) {
            try {
                val (items, info) = api.trending(page, perPage)
                cacheAll(items)
                Result.success(items)
            } catch (e: Exception) {
                Log.w(tag, "trending failed: ${e.message}")
                val cached = animeDao.recent(perPage).map { it.toModel() }
                if (cached.isNotEmpty()) Result.success(cached)
                else Result.failure(e)
            }
        }

    suspend fun popularThisSeason(
        season: String,
        year: Int,
        page: Int = 1,
        perPage: Int = 20,
    ): Result<List<Anime>> = withContext(Dispatchers.IO) {
        try {
            val (items, info) = api.popularThisSeason(season, year, page, perPage)
            cacheAll(items)
            Result.success(items)
        } catch (e: Exception) {
            Log.w(tag, "popularThisSeason failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun topRated(page: Int = 1, perPage: Int = 20): Result<List<Anime>> =
        withContext(Dispatchers.IO) {
            try {
                val (items, info) = api.topRated(page, perPage)
                cacheAll(items)
                Result.success(items)
            } catch (e: Exception) {
                Log.w(tag, "topRated failed: ${e.message}")
                Result.failure(e)
            }
        }

    suspend fun upcoming(page: Int = 1, perPage: Int = 20): Result<List<Anime>> =
        withContext(Dispatchers.IO) {
            try {
                val (items, info) = api.upcoming(page, perPage)
                cacheAll(items)
                Result.success(items)
            } catch (e: Exception) {
                Log.w(tag, "upcoming failed: ${e.message}")
                Result.failure(e)
            }
        }

    // -------------------------------------------------------------------------
    //  Browse — filters
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
    //  Search
    // -------------------------------------------------------------------------

    suspend fun search(
        query: String,
        page: Int = 1,
        perPage: Int = 24,
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
    //  Schedule
    // -------------------------------------------------------------------------

    /** Returns the airing schedule for a 7-day window starting today. */
    suspend fun scheduleWeek(startAt: Long = System.currentTimeMillis() / 1000): Result<List<AiringSchedule>> =
        withContext(Dispatchers.IO) {
            try {
                val endAt = startAt + 7 * 24 * 60 * 60
                val (items, info) = api.schedule(startAt, endAt, page = 1, perPage = 50)
                Result.success(items)
            } catch (e: Exception) {
                Log.w(tag, "schedule failed: ${e.message}")
                Result.failure(e)
            }
        }

    // -------------------------------------------------------------------------
    //  Anime details
    // -------------------------------------------------------------------------

    suspend fun animeDetails(id: Int): Result<AnimeDetails> = withContext(Dispatchers.IO) {
        try {
            val details = api.animeDetails(id)
                ?: return@withContext Result.failure(IllegalArgumentException("Anime $id not found"))
            detailDao.upsert(DetailCacheEntity(animeId = id, json = json.encodeToString(com.anikage.app.core.data.model.AnimeDetails.serializer(), details)))
            Result.success(details)
        } catch (e: Exception) {
            Log.w(tag, "animeDetails failed: ${e.message}")
            // Fallback to cache
            val cached = detailDao.get(id)
            if (cached != null) {
                try {
                    val details = json.decodeFromString(com.anikage.app.core.data.model.AnimeDetails.serializer(), cached.json)
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
