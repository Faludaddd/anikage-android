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
    /** Present inside the cover object in schedule-media payloads. */
    val color: String? = null,
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

// ---------------------------------------------------------------------------
//  Music — GET /api/animethemes (proxy of the animethemes.moe API).
//
//  Search:  ?path=/search&include[anime]=animethemes.song,images
//            &q={query}&fields[search]=anime
//           -> { search: { anime: [ { name, slug, year, season, format,
//              synopsis, animethemes[ { slug, type, song } ], images[] } ] } }
//
//  Anime:   ?path=/anime/{slug}&include=animethemes.animethemeentries.videos,
//            animethemes.animethemeentries.videos.audio,animethemes.song,
//            images,resources&filter[site]=Anilist&fields[resource]=external_id
//           -> { anime: { ...same + entries with video/audio links } }
// ---------------------------------------------------------------------------

@Serializable
data class AnikageMusicSearchResponse(
    val search: AnikageMusicSearch = AnikageMusicSearch(),
)

@Serializable
data class AnikageMusicSearch(
    val anime: List<AnikageMusicAnime> = emptyList(),
)

@Serializable
data class AnikageMusicAnime(
    val id: Int? = null,
    val name: String? = null,
    val slug: String? = null,
    val year: Int? = null,
    val season: String? = null,
    @SerialName("media_format") val mediaFormat: String? = null,
    val synopsis: String? = null,
    val animethemes: List<AnikageMusicTheme> = emptyList(),
    val images: List<AnikageMusicImage> = emptyList(),
    val resources: List<AnikageMusicResource> = emptyList(),
) {
    fun coverUrl(): String? = images.firstOrNull { it.link != null }?.link
    fun anilistId(): Int? = resources.firstOrNull()?.externalId
}

@Serializable
data class AnikageMusicTheme(
    val id: Int? = null,
    val sequence: Int? = null,
    val slug: String? = null,          // "OP1", "ED1", "ED1-TV" …
    val type: String? = null,          // "OP" | "ED"
    val song: AnikageMusicSong? = null,
    val animethemeentries: List<AnikageMusicEntry> = emptyList(),
)

@Serializable
data class AnikageMusicSong(
    val id: Int? = null,
    val title: String? = null,
)

@Serializable
data class AnikageMusicImage(
    val id: Int? = null,
    val facet: String? = null,         // "Large Cover" …
    val path: String? = null,
    val link: String? = null,
)

@Serializable
data class AnikageMusicResource(
    @SerialName("external_id") val externalId: Int? = null,
    val site: String? = null,
    @SerialName("animeresource") val animeresource: AnikageMusicResourceLink? = null,
)

@Serializable
data class AnikageMusicResourceLink(
    @SerialName("as") val asField: String? = null,
)

@Serializable
data class AnikageMusicEntry(
    val id: Int? = null,
    val videos: List<AnikageMusicVideo> = emptyList(),
)

@Serializable
data class AnikageMusicVideo(
    val id: Int? = null,
    val link: String? = null,          // https://v.animethemes.moe/…webm
    val audio: AnikageMusicAudio? = null,
)

@Serializable
data class AnikageMusicAudio(
    val id: Int? = null,
    val link: String? = null,          // https://a.animethemes.moe/…ogg
)

/** Wrapper for the anime-detail music call: { anime: AnikageMusicAnime }. */
@Serializable
data class AnikageMusicAnimeResponse(
    val anime: AnikageMusicAnime = AnikageMusicAnime(),
)

// ---------------------------------------------------------------------------
//  Anime info — GET /api/media/anime/{slug}
//
//  The payload behind the site's /anime/info/{slug} page. This is the
//  website's OWN data source for anime details (served by the Anikage
//  backend, which mirrors AniList server-side) — using it means the app's
//  details pages work exactly like the site's, and keep working even when
//  the public AniList GraphQL API is unreachable (it was globally disabled
//  with HTTP 403 "temporarily disabled due to severe stability issues"
//  when this endpoint was introduced).
//
//  Shape (live-verified):
//    { "anime": { slug, anilistId, malId, title{…}, coverImage{…},
//      coverColor, bannerImage, fanart, clearLogo, trailerId, description,
//      averageScore, meanScore, popularity, favourites, format, status,
//      totalEpisodes, duration, season, year, genres[], startDate, endDate,
//      nextAiringEpisode{…}, studios[], characters[], relations[],
//      recommendations[], tags[], … }, "banned": false }
// ---------------------------------------------------------------------------

@Serializable
data class AnikageInfoResponse(
    val anime: AnikageInfoAnime? = null,
    val banned: Boolean = false,
)

@Serializable
data class AnikageInfoAnime(
    val slug: String? = null,
    val anilistId: Int? = null,
    val malId: Int? = null,
    val title: AnikageTitle = AnikageTitle(),
    val coverImage: AnikageCover = AnikageCover(),
    val coverColor: String? = null,
    val bannerImage: String? = null,
    /** TVDB background artwork (site hero). */
    val fanart: String? = null,
    /** Transparent title logo (site hero). */
    val clearLogo: String? = null,
    val trailerId: String? = null,
    val description: String? = null,
    val averageScore: Int? = null,
    val meanScore: Int? = null,
    val popularity: Int? = null,
    val favourites: Int? = null,
    val format: String? = null,
    val status: String? = null,
    val totalEpisodes: Int? = null,
    val duration: Int? = null,
    val season: String? = null,
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    /** Format "Sep 29, 2023" — parsed leniently on mapping. */
    val startDate: String? = null,
    val endDate: String? = null,
    val nextAiringEpisode: AnikageAiringEpisode? = null,
    val studios: List<AnikageStudio> = emptyList(),
    val characters: List<AnikageCharacter> = emptyList(),
    val relations: List<AnikageRelationRef> = emptyList(),
    val recommendations: List<AnikageRelationRef> = emptyList(),
    val countryOfOrigin: String? = null,
    val source: String? = null,
    val isAdult: Boolean = false,
    val type: String? = null,
)

@Serializable
data class AnikageStudio(
    val anilistId: Int? = null,
    val name: String? = null,
    val siteUrl: String? = null,
    val isAnimationStudio: Boolean = false,
)

@Serializable
data class AnikageCharacter(
    val anilistId: Int? = null,
    val name: String? = null,
    val nativeName: String? = null,
    val role: String? = null,
    val image: String? = null,
    val age: String? = null,
    val description: String? = null,
)

/** A related / recommended anime card in the info payload. */
@Serializable
data class AnikageRelationRef(
    val slug: String? = null,
    val anilistId: Int? = null,
    val title: AnikageTitle = AnikageTitle(),
    /** Plain URL string in this payload (not an object like AnikageCover). */
    val coverImage: String? = null,
    val bannerImage: String? = null,
    val format: String? = null,
    val status: String? = null,
    val episodes: Int? = null,
    val type: String? = null,
    val relationType: String? = null,
)

// ---------------------------------------------------------------------------
//  Schedule — GET /api/media/anime/schedule
//
//  The payload behind the site's /schedule page, served by the Anikage
//  backend (same data the site renders, no public-AniList dependency).
//  Shape (live-verified):
//    [ { "id": "schedule", "weekStart": …, "weekEnd": … },
//      { "day": "Saturday", "schedule": [ { "episode": 24, "airingAt": …,
//        "media": { anilistId, slug, title{…}, coverImage{…}, description,
//                   episodes, … } } ] }, … 7 day elements … ]
// ---------------------------------------------------------------------------

@Serializable
data class AnikageScheduleElement(
    /** "schedule" for the header element; null for day elements. */
    val id: String? = null,
    val weekStart: Long? = null,
    val weekEnd: Long? = null,
    /** Weekday name ("Saturday"…) for day elements; null for the header. */
    val day: String? = null,
    val schedule: List<AnikageScheduleEntry> = emptyList(),
)

@Serializable
data class AnikageScheduleEntry(
    val episode: Int = 0,
    val airingAt: Long = 0,
    val media: AnikageScheduleMedia = AnikageScheduleMedia(),
)

@Serializable
data class AnikageScheduleMedia(
    val slug: String? = null,
    val anilistId: Int? = null,
    val title: AnikageTitle = AnikageTitle(),
    val coverImage: AnikageCover = AnikageCover(),
    val description: String? = null,
    val episodes: Int? = null,
    val format: String? = null,
    val status: String? = null,
    val season: String? = null,
    val year: Int? = null,
    val averageScore: Int? = null,
    val genres: List<String> = emptyList(),
    val bannerImage: String? = null,
    val nextAiringEpisode: AnikageAiringEpisode? = null,
    val isAdult: Boolean = false,
    val type: String? = null,
)
