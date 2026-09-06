package com.anikage.app.core.nav

/**
 * All navigation routes used by the app. Centralised so we can change route
 * strings (e.g. "home" -> "feed") in one place if the app's IA changes.
 *
 * Routes (matching Anikage's actual routes extracted from the live site):
 *   /                    -> Discover / trending / sections
 *   /browse              -> Filterable grid (genres=, sort=, type=)
 *   /schedule            -> Airing schedule (this week)
 *   /search              -> Search input + results
 *   /music               -> OST info (Anikage-specific)
 *   /torrents            -> Anime info (Anikage-specific)
 *   /anime/info/{id}     -> Anime details
 *   /anime/watch/{id}    -> Native player
 *   /settings            -> App settings (Account/General/Player/Themes/About)
 *   /notifications       -> Notification center (site /(account)/notifications)
 *   /profile             -> Profile / account area (site /(account)/profile)
 *   /diagnostics         -> In-app session logs (app-only; the site has no equivalent)
 *   /about               -> About / credits
 */
object Routes {
    const val HOME = "home"
    const val BROWSE = "browse"
    const val SCHEDULE = "schedule"
    const val SEARCH = "search"
    const val MUSIC = "music"
    const val TORRENTS = "torrents"
    const val SETTINGS = "settings"
    const val NOTIFICATIONS = "notifications"
    const val PROFILE = "profile"
    const val DIAGNOSTICS = "diagnostics"
    const val ABOUT = "about"

    const val DETAILS = "details/{id}"
    fun details(id: Int) = "details/$id"

    const val WATCH = "watch/{id}/{episode}"
    fun watch(id: Int, episode: Int = 1) = "watch/$id/$episode"

    /** Bottom nav — exact site set + order: Home, Browse, Music, Schedule, Torrents. */
    val bottomNav: List<String> = listOf(
        HOME, BROWSE, MUSIC, SCHEDULE, TORRENTS,
    )

    /** Routes that show the top app bar (logo + search + bell + profile). */
    val topBarScreens: List<String> = listOf(
        HOME, BROWSE, SCHEDULE, SEARCH, MUSIC, TORRENTS, DETAILS, WATCH,
    )

    /** Account pages use their own back headers (site: /(account) layout). */
    val accountScreens: List<String> = listOf(
        SETTINGS, NOTIFICATIONS, PROFILE,
    )
}
