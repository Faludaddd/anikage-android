package com.anikage.app.core.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs for Anikage's own REST API (anikage.cc / auth.anikage.cc).
 *
 * Response shapes were captured from the live endpoints (see Config.kt for
 * the endpoint map). All models use default values + `ignoreUnknownKeys` so
 * additive server-side fields never break parsing.
 */

// ---------------------------------------------------------------------------
//  Browse — GET /api/media/anime/browse
// ---------------------------------------------------------------------------

@Serializable
data class AnikageBrowseResponse(
    val data: List<AnikageMedia> = emptyList(),
    val count: Int = 0,
    val total: Int = 0,
    val page: Int = 1,
    val hasNext: Boolean = false,
    val relaxedBy: List<String> = emptyList(),
    val matchQuality: String? = null,
)

@Serializable
data class AnikageMedia(
    val slug: String,
    val anilistId: Int? = null,
    val title: AnikageTitle = AnikageTitle(),
    val coverImage: AnikageCover = AnikageCover(),
    val coverColor: String? = null,
    val bannerImage: String? = null,
    val format: String? = null,
    val status: String? = null,
    val season: String? = null,
    val year: Int? = null,
    val totalEpisodes: Int? = null,
    val duration: Int? = null,
    val averageScore: Int? = null,
    val meanScore: Int? = null,
    val popularity: Int? = null,
    val favourites: Int? = null,
    val malScore: Double? = null,
    val genres: List<String> = emptyList(),
    val isAdult: Boolean = false,
    val type: String? = null,
    val description: String? = null,
    val nextAiringEpisode: AnikageAiringEpisode? = null,
    // Spotlight / hero-slide fields (GET /api/media/anime/home):
    /** TVDB background artwork — the website hero background image. */
    val fanart: String? = null,
    /** TVDB transparent title logo — the website hero logo image. */
    val clearLogo: String? = null,
    /** YouTube trailer id (site plays it muted in the hero). */
    val trailerId: String? = null,
    /** Spotlight rank (1..6). */
    val rank: Int? = null,
    /** Editor's-pick index for the featured card. */
    val index: Int? = null,
)

@Serializable
data class AnikageTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
    val userPreferred: String? = null,
) {
    fun preferred(): String =
        english?.takeIf { it.isNotBlank() }
            ?: userPreferred?.takeIf { it.isNotBlank() }
            ?: romaji?.takeIf { it.isNotBlank() }
            ?: native?.takeIf { it.isNotBlank() }
            ?: "Unknown"

    /** Romaji-first variant (the site shows romaji as the secondary title). */
    fun secondary(): String? =
        romaji?.takeIf { it.isNotBlank() && it != preferred() }
            ?: native?.takeIf { it.isNotBlank() && it != preferred() }
}

@Serializable
data class AnikageCover(
    val large: String? = null,
    val medium: String? = null,
    val extraLarge: String? = null,
) {
    fun best(): String? = extraLarge ?: large ?: medium
}

@Serializable
data class AnikageAiringEpisode(
    val episode: Int? = null,
    val airingAt: Long? = null,
    val timeUntilAiring: Long? = null,
)

// ---------------------------------------------------------------------------
//  Home — GET /api/media/anime/home
//
//  The exact payload the live website renders its homepage from:
//  { trending[15], seasonal[15], upcoming[15], top10[10], popularMovies[10],
//    favorites[15], spotlight[6], featured, cachedAt }
//
//  `spotlight` items are the hero slides — they carry TVDB `fanart`
//  (background artwork) + `clearLogo` (transparent title logo), which is
//  what makes the website's hero look the way it does.
// ---------------------------------------------------------------------------

@Serializable
data class AnikageHomeResponse(
    val trending: List<AnikageMedia> = emptyList(),
    val seasonal: List<AnikageMedia> = emptyList(),
    val upcoming: List<AnikageMedia> = emptyList(),
    val top10: List<AnikageMedia> = emptyList(),
    val popularMovies: List<AnikageMedia> = emptyList(),
    val favorites: List<AnikageMedia> = emptyList(),
    val spotlight: List<AnikageMedia> = emptyList(),
    val featured: AnikageMedia? = null,
    val cachedAt: Long? = null,
)

// ---------------------------------------------------------------------------
//  Episodes — GET /api/media/anime/{slug}/episodes
// ---------------------------------------------------------------------------

@Serializable
data class AnikageEpisode(
    val id: String? = null,
    val slug: String? = null,
    val number: Int,
    val seasonNumber: Int? = null,
    val episodeInSeason: Int? = null,
    val seasonName: String? = null,
    val title: String? = null,
    val titleRomaji: String? = null,
    val titleNative: String? = null,
    val description: String? = null,
    val image: String? = null,
    val airDate: String? = null,
    val runtime: Int? = null,
    val rating: Double? = null,
    val isFiller: Boolean = false,
    val isRecap: Boolean = false,
)

// ---------------------------------------------------------------------------
//  Servers — GET /api/media/anime/{slug}/episodes/{ep}/servers
// ---------------------------------------------------------------------------

@Serializable
data class AnikageServersResponse(
    val servers: List<AnikageServer> = emptyList(),
    val embeds: List<AnikageEmbedRef> = emptyList(),
)

@Serializable
data class AnikageServer(
    val id: String,
    val providerId: String,
    val default: Boolean = false,
    val subTypes: List<String> = emptyList(),
)

@Serializable
data class AnikageEmbedRef(
    val id: String? = null,
    val key: String? = null,
    val label: String? = null,
)

// ---------------------------------------------------------------------------
//  Sources — GET /api/media/anime/{slug}/episodes/{ep}/sources
// ---------------------------------------------------------------------------

@Serializable
data class AnikageSourcesResponse(
    val slug: String? = null,
    val number: Int? = null,
    val providerId: String? = null,
    val subType: String? = null,
    val sources: List<AnikageStreamSource> = emptyList(),
    val subtitles: List<AnikageSubtitle> = emptyList(),
    val embeds: List<AnikageStreamEmbed> = emptyList(),
    val intro: AnikageTimeRange? = null,
    val outro: AnikageTimeRange? = null,
    val headers: Map<String, String> = emptyMap(),
    val embedOptions: List<AnikageEmbedRef> = emptyList(),
    val cached: Boolean = false,
    val stale: Boolean = false,
)

@Serializable
data class AnikageStreamSource(
    /** Stream token — resolve via {STREAM_PROXY}/m3u8/{token} (isM3U8) or /stream/{token}. */
    val url: String? = null,
    val quality: String? = null,
    val isM3U8: Boolean = false,
    val embedUrl: String? = null,
    val type: String? = null,
)

@Serializable
data class AnikageSubtitle(
    /** Subtitle token — resolve via {STREAM_PROXY}/stream/{token}. */
    val file: String? = null,
    val label: String? = null,
    val kind: String? = null,
    val default: Boolean = false,
    val embedUrl: String? = null,
)

@Serializable
data class AnikageStreamEmbed(
    val url: String? = null,
    val type: String? = null,
    val server: String? = null,
    val status: String? = null,
)

@Serializable
data class AnikageTimeRange(
    val start: Double? = null,
    val end: Double? = null,
)

// ---------------------------------------------------------------------------
//  Views — GET auth.anikage.cc/api/views/anime/{slug}/episode/{ep}/views
// ---------------------------------------------------------------------------

@Serializable
data class AnikageViewsResponse(
    val slug: String? = null,
    val epNum: Int? = null,
    val viewCount: Long? = null,
)

// ---------------------------------------------------------------------------
//  Comments — GET auth.anikage.cc/api/comments
// ---------------------------------------------------------------------------

@Serializable
data class AnikageCommentsResponse(
    val comments: List<AnikageComment> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val total: Int = 0,
)

@Serializable
data class AnikageComment(
    val id: String,
    val content: String = "",
    val animeId: Long? = null,
    val episode: Int? = null,
    val isPinned: Boolean = false,
    val isSpoiler: Boolean = false,
    val isEdited: Boolean = false,
    val likeCount: Int = 0,
    val dislikeCount: Int = 0,
    val replyCount: Int = 0,
    val createdAt: String? = null,
    val editedAt: String? = null,
    val author: AnikageCommentAuthor? = null,
    val myVote: Int = 0,
)

@Serializable
data class AnikageCommentAuthor(
    val id: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val avatar: String? = null,
    val avatarFrame: String? = null,
    val role: String? = null,
)
