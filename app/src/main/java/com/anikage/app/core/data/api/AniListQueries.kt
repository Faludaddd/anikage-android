package com.anikage.app.core.data.api

/**
 * All AniList GraphQL queries used by the app.
 *
 * AniList's schema is publicly documented at https://docs.anilist.io/.
 * We keep queries here as string constants; the repository passes them to the
 * GraphQL endpoint.
 *
 * Note on GraphQL: AniList's rate limit is 90 req/min per IP. We mitigate by
 * using the `Page` type to batch results (perPage=20-50) and by caching
 * responses in the local Room database.
 */
object AniListQueries {

    /** Common media fields used by card / list / search screens. */
    private const val MEDIA_FIELDS = """
        id
        idMal
        title { romaji english native }
        coverImage { large extraLarge medium color }
        bannerImage
        averageScore
        meanScore
        popularity
        favourites
        format
        status
        episodes
        duration
        season
        seasonYear
        startDate { year month day }
        endDate { year month day }
        genres
        studios(isMain: true) { nodes { id name isAnimationStudio } }
        nextAiringEpisode { id airingAt timeUntilAiring episode }
        trailer { id site thumbnail }
    """

    /** Extended fields for the Anime details screen. */
    private const val MEDIA_DETAIL_FIELDS = """
        $MEDIA_FIELDS
        description(asHtml: false)
        characters(sort: ROLE, perPage: 12) {
            nodes { id name { full native } image { large medium } }
        }
        relations {
            nodes { $MEDIA_FIELDS }
        }
        recommendations(sort: RATING_DESC, perPage: 12) {
            nodes { mediaRecommendation { $MEDIA_FIELDS } }
        }
    """

    // -------------------------------------------------------------------------
    //  Home screen
    // -------------------------------------------------------------------------

    const val TRENDING = """
        query Trending(${'$'}page: Int, ${'$'}perPage: Int) {
            Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                pageInfo { total currentPage lastPage hasNextPage perPage }
                media(type: ANIME, sort: TRENDING_DESC) { $MEDIA_FIELDS }
            }
        }
    """

    const val POPULAR_THIS_SEASON = """
        query PopularThisSeason(${'$'}page: Int, ${'$'}perPage: Int, ${'$'}season: MediaSeason, ${'$'}year: Int) {
            Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                pageInfo { total currentPage lastPage hasNextPage perPage }
                media(type: ANIME, season: ${'$'}season, seasonYear: ${'$'}year, sort: POPULARITY_DESC) { $MEDIA_FIELDS }
            }
        }
    """

    const val TOP_RATED = """
        query TopRated(${'$'}page: Int, ${'$'}perPage: Int) {
            Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                pageInfo { total currentPage lastPage hasNextPage perPage }
                media(type: ANIME, sort: SCORE_DESC, averageScore_greater: 50) { $MEDIA_FIELDS }
            }
        }
    """

    const val UPCOMING = """
        query Upcoming(${'$'}page: Int, ${'$'}perPage: Int) {
            Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                pageInfo { total currentPage lastPage hasNextPage perPage }
                media(type: ANIME, status: NOT_YET_RELEASED, sort: POPULARITY_DESC) { $MEDIA_FIELDS }
            }
        }
    """

    // -------------------------------------------------------------------------
    //  Browse — with filters
    // -------------------------------------------------------------------------

    const val BROWSE = """
        query Browse(${'$'}page: Int, ${'$'}perPage: Int, ${'$'}season: MediaSeason, ${'$'}year: Int, ${'$'}genre: String, ${'$'}format: MediaFormat, ${'$'}sort: [MediaSort], ${'$'}status: MediaStatus) {
            Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                pageInfo { total currentPage lastPage hasNextPage perPage }
                media(type: ANIME, season: ${'$'}season, seasonYear: ${'$'}year, genre: ${'$'}genre, format: ${'$'}format, status: ${'$'}status, sort: ${'$'}sort) { $MEDIA_FIELDS }
            }
        }
    """

    // -------------------------------------------------------------------------
    //  Search
    // -------------------------------------------------------------------------

    const val SEARCH = """
        query Search(${'$'}page: Int, ${'$'}perPage: Int, ${'$'}query: String) {
            Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                pageInfo { total currentPage lastPage hasNextPage perPage }
                media(search: ${'$'}query, type: ANIME, sort: SEARCH_MATCH) { $MEDIA_FIELDS }
            }
        }
    """

    // -------------------------------------------------------------------------
    //  Schedule
    // -------------------------------------------------------------------------

    const val SCHEDULE = """
        query Schedule(${'$'}page: Int, ${'$'}perPage: Int, ${'$'}from: Int, ${'$'}to: Int) {
            Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                pageInfo { total currentPage lastPage hasNextPage perPage }
                airingSchedules(airingAt_greater: ${'$'}from, airingAt_lesser: ${'$'}to, sort: TIME) {
                    id
                    episode
                    airingAt
                    timeUntilAiring
                    media { $MEDIA_FIELDS }
                }
            }
        }
    """

    // -------------------------------------------------------------------------
    //  Anime details
    // -------------------------------------------------------------------------

    const val ANIME_DETAILS = """
        query AnimeDetails(${'$'}id: Int) {
            Media(id: ${'$'}id, type: ANIME) { $MEDIA_DETAIL_FIELDS }
        }
    """
}
