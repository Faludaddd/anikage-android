package com.anikage.app.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached anime metadata. Stored as a denormalized row — enough info to
 * render a card offline (cover image URL, title, score, etc.).
 *
 * The full AnimeDetails response is cached separately as raw JSON in
 * [DetailCacheEntity].
 */
@Entity(tableName = "anime_cache")
data class AnimeCacheEntity(
    @PrimaryKey val id: Int,
    val titleRomaji: String?,
    val titleEnglish: String?,
    val titleNative: String?,
    val coverLarge: String?,
    val coverExtraLarge: String?,
    val coverMedium: String?,
    val coverColor: String?,
    val bannerImage: String?,
    val averageScore: Int?,
    val popularity: Int?,
    val favourites: Int?,
    val format: String?,
    val status: String?,
    val episodes: Int?,
    val duration: Int?,
    val season: String?,
    val seasonYear: Int?,
    val genres: String?,          // comma-joined
    val cachedAt: Long = System.currentTimeMillis(),
)

/** Full JSON blob of an AnimeDetails response — used to render the details
 *  screen offline. */
@Entity(tableName = "detail_cache")
data class DetailCacheEntity(
    @PrimaryKey val animeId: Int,
    val json: String,
    val cachedAt: Long = System.currentTimeMillis(),
)

/** Recently-viewed anime (for the Continue Watching rail on Home). */
@Entity(tableName = "recently_viewed")
data class RecentlyViewedEntity(
    @PrimaryKey val animeId: Int,
    val lastViewedAt: Long,
    val titleRomaji: String?,
    val titleEnglish: String?,
    val coverUrl: String?,
    val lastEpisode: Int = 1,
)

/** Watch-position progress per episode. */
@Entity(tableName = "watch_progress")
data class WatchProgressEntity(
    @PrimaryKey val episodeKey: String,    // "{animeId}-{episode}"
    val animeId: Int,
    val episode: Int,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis(),
)
