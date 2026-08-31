package com.anikage.app.core.nav

/**
 * All navigation routes used by the app. Centralised so we can change route
 * strings (e.g. "home" -> "feed") in one place if the app's IA changes.
 *
 * Routes (matching Anikage's actual routes extracted from the live site):
 *   /                 -> Discover / trending / sections
 *   /browse           -> Filterable grid (genres=, sort=, type=)
 *   /schedule         -> Airing schedule (this week)
 *   /search           -> Search input + results
 *   /music            -> OST info (Anikage-specific)
 *   /torrents         -> Anime info (Anikage-specific; disabled by default)
 *   /anime/info/{id}  -> Anime details (Anikage uses short IDs; we use AniList IDs)
 *   /anime/watch/{id} -> Native player (Anikage uses short IDs; we use AniList IDs)
 *   /settings         -> App settings (autoplays, stream quality, captions, etc.)
 *   /about            -> About / credits
 */
object Routes {
    const val HOME = "home"
    const val BROWSE = "browse"
    const val SCHEDULE = "schedule"
    const val SEARCH = "search"
    const val MUSIC = "music"
    const val TORRENTS = "torrents"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    const val DETAILS = "details/{id}"
    fun details(id: Int) = "details/$id"

    const val WATCH = "watch/{id}/{episode}"
    fun watch(id: Int, episode: Int = 1) = "watch/$id/$episode"

    /** Top-level routes shown in the bottom navigation. Matches Anikage's mobile nav. */
    val bottomNav: List<String> = buildList {
        add(HOME)
        add(BROWSE)
        add(SCHEDULE)
        if (com.anikage.app.Config.Features.ENABLE_MUSIC_SCREEN) add(MUSIC)
        if (com.anikage.app.Config.Features.ENABLE_TORRENTS_SCREEN) add(TORRENTS)
        add(SEARCH)
    }

    /** Routes that show the top app bar (logo + search + bell + settings). */
    val topBarScreens: List<String> = listOf(
        HOME, BROWSE, SCHEDULE, SEARCH, MUSIC, TORRENTS,
    )
}
