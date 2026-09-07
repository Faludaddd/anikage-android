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

/**
 * A fully-downloaded episode (in-app download engine). The file lives in the
 * app-scoped external storage; the row is the source of truth for the
 * Downloads screen and offline playback.
 */
@Entity(tableName = "downloaded_episodes")
data class DownloadedEpisodeEntity(
    /** "{animeId}-ep{episode}-{quality}" */
    @PrimaryKey val downloadKey: String,
    val animeId: Int,
    val slug: String?,
    val episode: Int,
    /** Display label of the chosen rendition, e.g. "720p". */
    val quality: String,
    val height: Int,
    val filePath: String,
    /** Side-loaded subtitle VTT path (nullable — softsub downloads only). */
    val subtitlePath: String?,
    val titleRomaji: String?,
    val titleEnglish: String?,
    val episodeTitle: String?,
    val posterUrl: String?,
    val sizeBytes: Long,
    val provider: String,
    val lang: String,
    val downloadedAt: Long = System.currentTimeMillis(),
)

/** In-app anime list entry (local equivalent of the site's account list). */
@Entity(tableName = "anime_list")
data class AnimeListEntity(
    @PrimaryKey val animeId: Int,
    val status: String,            // watching | planned | completed | on_hold | dropped
    val titleRomaji: String?,
    val titleEnglish: String?,
    val posterUrl: String?,
    val coverColor: String?,
    val updatedAt: Long = System.currentTimeMillis(),
)
