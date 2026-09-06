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
 *   /anime/watch/{id}    -> Native player (carries the Anikage slug when
 *                           known, so sources load without a title search)
 *   /music/info          -> Anime music player (site: /music/info?slug&type)
 *   /schedule/details    -> Airing details (schedule entry -> rich page)
 *   /settings            -> App settings (Account/General/Player/Themes/About)
 *   /notifications       -> Notification center (site /(account)/notifications)
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
    const val DIAGNOSTICS = "diagnostics"
    const val ABOUT = "about"

    const val DETAILS = "details/{id}"
    fun details(id: Int) = "details/$id"

    /** Watch — `slug` is optional (query param) so old links still work. */
    const val WATCH = "watch/{id}?ep={episode}&slug={slug}"
    fun watch(id: Int, episode: Int = 1, slug: String? = null): String =
        "watch/$id?ep=$episode" + (slug?.let { "&slug=$it" } ?: "")

    /** Music info player — site: /music/info?slug={slug}&type={OP1|ED1…}. */
    const val MUSIC_INFO = "music_info/{slug}?type={type}"
    fun musicInfo(slug: String, type: String) = "music_info/$slug?type=$type"

    /** Schedule entry -> rich airing details page (app-owned, site n/a). */
    const val SCHEDULE_DETAILS = "schedule_details/{id}?ep={episode}&airingAt={airingAt}"
    fun scheduleDetails(id: Int, episode: Int, airingAt: Long) =
        "schedule_details/$id?ep=$episode&airingAt=$airingAt"

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
        SETTINGS, NOTIFICATIONS,
    )
}
