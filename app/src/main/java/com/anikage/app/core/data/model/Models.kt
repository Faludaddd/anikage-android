package com.anikage.app.core.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * All data models used by the app. These mirror the relevant subsets of the
 * AniList GraphQL schema — we only include the fields we actually use, to
 * keep the response payloads small.
 */

// ---------------------------------------------------------------------------
//  Title — AniList returns multiple titles per anime.
// ---------------------------------------------------------------------------

@Serializable
data class AnimeTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
) {
    /** Best display title: prefer English, fall back to Romaji, then Native. */
    fun preferred(): String = english?.takeIf { it.isNotBlank() }
        ?: romaji?.takeIf { it.isNotBlank() }
        ?: native?.takeIf { it.isNotBlank() }
        ?: "Unknown"
}

// ---------------------------------------------------------------------------
//  Cover image
// ---------------------------------------------------------------------------

@Serializable
data class CoverImage(
    val large: String? = null,
    val extraLarge: String? = null,
    val medium: String? = null,
    val color: String? = null,    // a CSS hex colour used by AniList for the gradient
) {
    fun best(): String? = extraLarge ?: large ?: medium
}

@Serializable
data class BannerImage(
    val value: String? = null
)

// ---------------------------------------------------------------------------
//  HomeFeed — the website homepage payload (mirrors GET /api/media/anime/home)
// ---------------------------------------------------------------------------

/**
 * 1:1 mirror of the Anikage homepage: spotlight = hero carousel slides
 * (TVDB fanart + clearLogo artwork), featured = "Editor's Pick" banner,
 * plus every rail in the exact order the site renders them.
 */
data class HomeFeed(
    val spotlight: List<Anime> = emptyList(),
    val featured: Anime? = null,
    val trending: List<Anime> = emptyList(),
    val seasonal: List<Anime> = emptyList(),
    val favorites: List<Anime> = emptyList(),
    val top10: List<Anime> = emptyList(),
    val popularMovies: List<Anime> = emptyList(),
    val upcoming: List<Anime> = emptyList(),
)

// ---------------------------------------------------------------------------
//  Anime — the core model used across the app
// ---------------------------------------------------------------------------
@Serializable
data class Anime(
    val id: Int,
    val idMal: Int? = null,
    /**
     * Anikage catalogue slug — the key every streaming endpoint needs
     * (episodes/servers/sources). The site's own payloads carry it on every
     * media item; keeping it here means the watch pipeline never has to
     * re-resolve it via fragile title search.
     */
    val slug: String? = null,
    val title: AnimeTitle = AnimeTitle(),
    val coverImage: CoverImage = CoverImage(),
    val bannerImage: String? = null,
    /** TVDB background artwork — used by the website hero (fanart). */
    val fanartUrl: String? = null,
    /** Official transparent title artwork used by Anikage's hero carousel. */
    val clearLogoUrl: String? = null,
    /** YouTube trailer id (site hero trailers). */
    val trailerId: String? = null,
    val description: String? = null,
    val averageScore: Int? = null,
    val meanScore: Int? = null,
    val popularity: Int? = null,
    val favourites: Int? = null,
    val format: String? = null,
    val status: String? = null,
    val episodes: Int? = null,
    val duration: Int? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val startDate: FuzzyDate? = null,
    val endDate: FuzzyDate? = null,
    val genres: List<String> = emptyList(),
    val studios: StudioConnection? = null,
    val nextAiringEpisode: AiringEpisode? = null,
    val trailer: Trailer? = null,
) {
    fun displayTitle(): String = title.preferred()
    fun coverUrl(): String? = coverImage.best()
}

@Serializable
data class FuzzyDate(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
) {
    fun isComplete(): Boolean = year != null && month != null && day != null
    fun formatShort(): String {
        if (year == null) return "TBA"
        return buildString {
            append(year)
            if (month != null) append("-").append(month.toString().padStart(2, '0'))
            if (day != null) append("-").append(day.toString().padStart(2, '0'))
        }
    }
}

@Serializable
data class Studio(
    val id: Int,
    val name: String,
    val isAnimationStudio: Boolean = false,
)

@Serializable
data class StudioConnection(
    val nodes: List<Studio> = emptyList(),
) {
    fun mainStudio(): Studio? = nodes.firstOrNull { it.isAnimationStudio }
        ?: nodes.firstOrNull()
}

@Serializable
data class AiringEpisode(
    val id: Int,
    val airingAt: Long,
    val timeUntilAiring: Long,
    val episode: Int,
)

@Serializable
data class Trailer(
    val id: String? = null,
    val site: String? = null,
    val thumbnail: String? = null,
)

// ---------------------------------------------------------------------------
//  Page response wrapper — GraphQL returns { data: { Page: { media: [...] } } }
// ---------------------------------------------------------------------------

@Serializable
data class PageMediaResponse(
    val data: PageMediaData? = null,
)

@Serializable
data class PageMediaData(
    val Page: PageMedia? = null,
)

@Serializable
data class PageMedia(
    val media: List<Anime> = emptyList(),
    val pageInfo: PageInfo? = null,
)

@Serializable
data class PageScheduleResponse(
    val data: PageScheduleData? = null,
)

@Serializable
data class PageScheduleData(
    val Page: PageSchedule? = null,
)

@Serializable
data class PageSchedule(
    val airingSchedules: List<AiringSchedule> = emptyList(),
    val pageInfo: PageInfo? = null,
)

@Serializable
data class AiringSchedule(
    val id: Int,
    val episode: Int,
    val airingAt: Long,
    val timeUntilAiring: Long,
    val media: Anime,
)

@Serializable
data class PageInfo(
    val total: Int = 0,
    val currentPage: Int = 0,
    val lastPage: Int = 0,
    val hasNextPage: Boolean = false,
    val perPage: Int = 0,
)

// ---------------------------------------------------------------------------
//  Anime details — extra nested fields for the details screen
// ---------------------------------------------------------------------------

@Serializable
data class AnimeDetails(
    val id: Int,
    /**
     * Anikage catalogue slug (the site's info/watch URL key). Present when
     * the payload came from the Anikage backend; null for plain AniList
     * responses (unknown keys are ignored / absent keys stay null).
     */
    val slug: String? = null,
    val title: AnimeTitle = AnimeTitle(),
    val coverImage: CoverImage = CoverImage(),
    val bannerImage: String? = null,
    /** TVDB background artwork — used by the website hero (fanart). */
    val fanartUrl: String? = null,
    /** Official transparent title artwork used by Anikage's hero carousel. */
    val clearLogoUrl: String? = null,
    /** YouTube trailer id (site hero trailers). */
    val trailerId: String? = null,
    val description: String? = null,
    val averageScore: Int? = null,
    val meanScore: Int? = null,
    val popularity: Int? = null,
    val favourites: Int? = null,
    val format: String? = null,
    val status: String? = null,
    val episodes: Int? = null,
    val duration: Int? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val startDate: FuzzyDate? = null,
    val endDate: FuzzyDate? = null,
    val genres: List<String> = emptyList(),
    val studios: StudioConnection? = null,
    val nextAiringEpisode: AiringEpisode? = null,
    val trailer: Trailer? = null,
    val characters: CharacterConnection? = null,
    val relations: RelationConnection? = null,
    val recommendations: RecommendationConnection? = null,
    val episodes_data: List<EpisodeInfo> = emptyList(),  // AniList doesn't expose episode data; we'll synthesize
) {
    fun displayTitle(): String = title.preferred()
    fun coverUrl(): String? = coverImage.best()
    fun mainStudio(): Studio? = studios?.mainStudio()
}

/**
 * Build a provisional details record from a list item the app already has
 * in memory (home rail / browse grid / search result / schedule entry).
 * Used to paint the details page instantly on click and to keep it usable
 * when the full-info fetch fails — real data, never placeholders.
 */
fun Anime.toProvisionalDetails(): AnimeDetails = AnimeDetails(
    id = id,
    slug = slug,
    title = title,
    coverImage = coverImage,
    bannerImage = bannerImage,
    fanartUrl = fanartUrl,
    clearLogoUrl = clearLogoUrl,
    trailerId = trailerId,
    description = description,
    averageScore = averageScore,
    meanScore = meanScore,
    popularity = popularity,
    favourites = favourites,
    format = format,
    status = status,
    episodes = episodes,
    duration = duration,
    season = season,
    seasonYear = seasonYear,
    genres = genres,
    nextAiringEpisode = nextAiringEpisode?.let {
        AiringEpisode(id = it.id, airingAt = it.airingAt, timeUntilAiring = it.timeUntilAiring, episode = it.episode)
    },
    trailer = trailerId?.let { Trailer(id = it, site = "youtube") },
)

@Serializable
data class Character(
    val id: Int,
    val name: CharacterName = CharacterName(),
    val image: CharacterImage? = null,
)

@Serializable
data class CharacterName(
    val full: String? = null,
    val native: String? = null,
) {
    fun preferred(): String = full?.takeIf { it.isNotBlank() } ?: native ?: "Unknown"
}

@Serializable
data class CharacterImage(
    val large: String? = null,
    val medium: String? = null,
)

@Serializable
data class CharacterConnection(
    val nodes: List<Character> = emptyList(),
)

@Serializable
data class RelationConnection(
    val nodes: List<Anime> = emptyList(),
)

@Serializable
data class RecommendationConnection(
    val nodes: List<RecommendationNode> = emptyList(),
)

@Serializable
data class RecommendationNode(
    val mediaRecommendation: Anime? = null,
)

@Serializable
data class EpisodeInfo(
    val number: Int,
    val title: String? = null,
    val thumbnail: String? = null,
    val description: String? = null,
    val airingAt: Long? = null,
)

// ---------------------------------------------------------------------------
//  GraphQL request wrapper
// ---------------------------------------------------------------------------

@Serializable
data class GraphQLRequest(
    val query: String,
    val variables: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
)
