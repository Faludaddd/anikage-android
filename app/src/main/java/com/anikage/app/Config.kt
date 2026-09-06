package com.anikage.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit

/**
 * ============================================================================
 *  ANIKAGE APP — CENTRAL CUSTOMIZATION CONFIG
 * ============================================================================
 *
 *  Single source of truth for everything you might want to tweak. Every
 *  colour, every URL, every brand string lives here. Nothing else in the
 *  project hard-codes these values.
 *
 *  To rebrand the app (e.g. rename "Anikage" → your own app name):
 *    1. Change APP_NAME and APP_SHORT_NAME
 *    2. Update the colours in [Theme]
 *    3. Update strings.xml <string name="app_name"> (for the launcher label)
 *    4. Replace the launcher icons (or re-run scripts/generate_assets.py)
 *    5. Change applicationId in app/build.gradle
 *    6. Rebuild: ./gradlew :app:assembleRelease
 *
 *  Everything else — every screen, every ViewModel, every API call — reads
 *  from this object and is automatically rebranded.
 * ============================================================================
 */
object Config {

    // -----------------------------------------------------------------------
    //  App identity
    // -----------------------------------------------------------------------

    /** Application display name — shown under launcher icon, in About, etc. */
    const val APP_NAME = "Anikage"

    /** Short name for compact UI (e.g. bottom nav tooltips). */
    const val APP_SHORT_NAME = "Anikage"

    /** Version label (shown in About). */
    const val APP_VERSION = "1.5.3"

    /** Version code (integer; bump for every release). */
    const val APP_VERSION_CODE = 9

    /** About / credits line. */
    val ABOUT_TEXT =
        "Anikage is a native Android client for browsing and watching " +
        "anime. Built with Jetpack Compose, Media3 ExoPlayer, and the " +
        "Anikage REST API (anikage.cc) with AniList metadata. " +
        "Not affiliated with AniList or any streaming provider."

    /** Optional link to your project (null = hide in About). */
    val PROJECT_URL: String? = null

    // -----------------------------------------------------------------------
    //  Data sources — what API backs the app
    // -----------------------------------------------------------------------

    /**
     * Base URL for the AniList GraphQL endpoint. AniList is a public, free,
     * rate-limited GraphQL API that provides all the metadata this app
     * displays (anime lists, search, schedule, anime details, characters,
     * recommendations, etc.).
     *
     * Rate limit: 90 requests per minute (per IP). The repository caches
     * aggressively and uses GraphQL's `Page` parameter to batch.
     */
    const val ANILIST_API_URL = "https://graphql.anilist.co"

    /** Image CDN base for AniList cover images (served from Cloudflare, no auth needed). */
    const val ANILIST_IMAGE_CDN = "https://s4.anilist.co"

    /**
     * Anikage's own REST API surface (extracted from the live site via the
     * Tampermonkey inspector):
     *
     *   Browse:    GET https://anikage.cc/api/media/anime/browse
     *                     ?sort={popularity|trending|score|favourites|...}
     *                     &page={n}&limit={n}&adult={bool}
     *                     &q={query}  (optional, for search)
     *                     &genres={comma-list}  (optional filter)
     *                     &type={anime}  (optional)
     *               → { data: [{ slug, anilistId, title, coverImage, coverColor,
     *                             format, status, season, year, totalEpisodes,
     *                             duration, averageScore, meanScore, popularity,
     *                             malScore, genres, isAdult, type, nextAiringEpisode }],
     *                   count, total, page, hasNext, relaxedBy, matchQuality }
     *
     *   Episodes:  GET https://anikage.cc/api/media/anime/{slug}/episodes
     *               → [ { id, slug, number, seasonNumber, episodeInSeason,
     *                     seasonName, title, titleRomaji, titleNative,
     *                     description, image (TheTVDB URL), airDate, runtime,
     *                     rating, isFiller, isRecap } ]
     *
     *   Servers:   GET https://anikage.cc/api/media/anime/{slug}/episodes/{ep}/servers
     *               → { servers: [{ id, providerId, default, subTypes: ["sub","dub"] }],
     *                   embeds: [{ id, key, label }] }
     *
     *   Sources:   GET https://anikage.cc/api/media/anime/{slug}/episodes/{ep}/sources
     *                     ?provider={koto|kiwi|neko|megg|dib|wave|zen}
     *                     &lang={sub|dub}
     *                     &server={providerId}
     *               → { slug, number, providerId, subType,
     *                   sources: [{ url (token), quality, isM3U8, embedUrl, type }],
     *                   subtitles: [{ file (token), label, kind, default, embedUrl }],
     *                   embeds: [{ url, type, server, status }],
     *                   intro: { start, end }, outro: { start, end },
     *                   headers, embedOptions, cached, stale }
     *
     *   Views:     GET https://auth.anikage.cc/api/views/anime/{slug}/episode/{ep}/views
     *               → { slug, epNum, viewCount }
     *
     *   Comments:  GET https://auth.anikage.cc/api/comments
     *                     ?animeId={anilistId}&episode={ep}&limit=20&sort=newest
     *               → { comments: [{ id, content, animeId, episode, isPinned,
     *                                isSpoiler, isEdited, likeCount, dislikeCount,
     *                                replyCount, createdAt, editedAt,
     *                                author: { id, username, displayName, avatar,
     *                                          avatarFrame, role }, myVote }],
     *                   nextCursor, hasMore, total }
     *
     *   Stream:    GET https://og.bakayaro.live/m3u8/{token}   (HLS playlist)
     *                     /stream/{token}                       (video thumbnail)
     *
     * All endpoints on anikage.cc / auth.anikage.cc / og.bakayaro.live are
     * fronted by Cloudflare's bot challenge. They work in the browser
     * because the user's session has Cloudflare cookies, but they will NOT
     * work from a native Android app without a proxy backend that holds
     * those cookies.
     *
     * To use this in your native app, set ANIKAGE_API_BASE_URL below to a
     * proxy backend you control that holds the Cloudflare cookies and
     * forwards requests to anikage.cc. The proxy should accept the same URL
     * paths and return the same JSON shapes.
     */

    /**
     * Base URL for Anikage's own REST API. Verified working directly from the
     * app (no Cloudflare block observed for these JSON endpoints with a
     * browser-like User-Agent). Leave null to fall back to AniList-only mode.
     */
    val ANIKAGE_API_BASE_URL: String? = "https://anikage.cc"

    /**
     * Base URL for Anikage's auth/comment API (comments, view counts).
     * Also verified working directly from the app. Leave null to disable
     * comments / view counts.
     */
    val ANIKAGE_AUTH_API_BASE_URL: String? = "https://auth.anikage.cc"

    /**
     * Anikage site origin — sent as Referer/Origin headers on stream requests.
     * og.bakayaro.live rejects stream tokens without these (403 "forbidden origin").
     */
    const val ANIKAGE_SITE_ORIGIN = "https://anikage.cc"

    /**
     * Base URL for Anikage's stream proxy (og.bakayaro.live). Stream tokens
     * returned by the sources endpoint are resolved as {base}/m3u8/{token}.
     */
    val ANIKAGE_STREAM_PROXY_BASE_URL: String? = "https://og.bakayaro.live"

    // Default providers observed in the captured snapshot
    const val DEFAULT_STREAM_PROVIDER = "koto"     // koto, kiwi, neko, megg, dib, wave, zen
    const val DEFAULT_STREAM_LANG = "sub"          // sub or dub

    /**
     * Optional direct stream-source endpoint (legacy / simple integration).
     * When set, the Watch screen will request a video URL for an episode:
     *
     *     GET {STREAM_SOURCE_URL}?id={animeId}&episode={episodeNumber}
     *     → 200 OK, application/json: { "streamUrl": "https://..." }
     *
     * If left null, the Watch screen uses the Anikage sources API (via
     * ANIKAGE_API_BASE_URL + og.bakayaro.live), or, failing that, the
     * sample HLS stream below.
     */
    val STREAM_SOURCE_URL: String? = null

    /** Sample HLS stream used by the Watch screen when no other source is configured. */
    const val SAMPLE_STREAM_URL =
        "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"

    // -----------------------------------------------------------------------
    //  Theme — Material 3 colour scheme
    //
    //  These are the EXACT colours extracted from the live Anikage website:
    //  - body background-color: rgb(6, 9, 18) = #060912 (their "midnight" theme)
    //  - body color: white
    //  - body font: Rubik, sans-serif 14.59px
    //  - h2 font-weight: 560 (Rubik supports the full 300-900 range)
    //  - Lucide icons with stroke-width 2
    //  Edit these and the entire UI rebrands.
    // -----------------------------------------------------------------------

    object Theme {
        // Brand — accent colour used for chips, primary buttons, progress bars.
        val primary = Color(0xFFE64158)        // Anikage accent (red-pink)
        val secondary = Color(0xFFA855F7)      // Secondary (purple)
        val tertiary = Color(0xFFF472B6)      // Tertiary (pink)

        // Surfaces — the actual midnight theme extracted from Anikage.
        // (background = rgb(6,9,18) from the live site's body computed style)
        val background = Color(0xFF060912)    // Page background — "midnight" theme
        val surface = Color(0xFF0E1422)       // Cards, dialogs (slightly elevated)
        val surfaceVariant = Color(0xFF161D2E) // Subtle raised surface
        val surfaceElevated = Color(0xFF1F2841) // Hover/selected state

        // Text
        val onBackground = Color(0xFFFFFFFF)
        val onSurface = Color(0xFFFFFFFF)
        val onSurfaceVariant = Color(0xFFB3B3B3)
        val outline = Color(0xFF404040)

        // Error
        val error = Color(0xFFEF4444)
        val onError = Color(0xFFFFFFFF)

        // Misc
        val success = Color(0xFF22C55E)
        val warning = Color(0xFFF59E0B)
        val info = Color(0xFF5D61E5)
        val star = Color(0xFFFACC15)          // yellow-400 (Anikage uses for score chips)

        /** Glassy overlay (used for hero banners gradients). */
        val scrim = Color(0xCC000000)         // 80% black
        val scrimLight = Color(0x66000000)    // 40% black

        /**
         * Whether the app starts in dark mode by default.
         * Dark = true is recommended because the design uses dark surfaces
         * as the dominant colour.
         */
        const val DEFAULT_DARK_MODE = true
    }

    // -----------------------------------------------------------------------
    //  Typography
    // -----------------------------------------------------------------------

    object Typography {
        /** Default font family. Use [FontFamily.Default] in code; the manifest
         *  doesn't force a specific family. Anikage uses Rubik on the web. */
        const val USE_RUBIK = true   // Will use the bundled Rubik font in res/font/

        // Text sizes (in sp) — used by ui/theme/Type.kt
        val displayLarge = 32.sp
        val displayMedium = 28.sp
        val displaySmall = 24.sp
        val headlineLarge = 22.sp
        val headlineMedium = 20.sp
        val headlineSmall = 18.sp
        val titleLarge = 18.sp
        val titleMedium = 16.sp
        val titleSmall = 14.sp
        val bodyLarge = 16.sp
        val bodyMedium = 14.sp
        val bodySmall = 12.sp
        val labelLarge = 14.sp
        val labelMedium = 12.sp
        val labelSmall = 11.sp
    }

    // -----------------------------------------------------------------------
    //  Shape — corner radii
    // -----------------------------------------------------------------------

    object Shape {
        val card: Dp = 16.dp
        val button: Dp = 24.dp
        val chip: Dp = 18.dp
        val image: Dp = 12.dp
        val sheet: Dp = 24.dp
        val navigation: Dp = 0.dp   // bottom navigation bar
    }

    // -----------------------------------------------------------------------
    //  Layout — spacing & paddings
    // -----------------------------------------------------------------------

    object Spacing {
        val xs: Dp = 4.dp
        val sm: Dp = 8.dp
        val md: Dp = 12.dp
        val lg: Dp = 16.dp
        val xl: Dp = 24.dp
        val xxl: Dp = 32.dp

        /** Horizontal padding around screen content. */
        val screenHorizontal: Dp = 16.dp

        /** Spacing between items in a vertical list. */
        val listVertical: Dp = 8.dp

        /** Spacing between items in a horizontal carousel. */
        val carouselGap: Dp = 12.dp
    }

    // -----------------------------------------------------------------------
    //  Image loading
    // -----------------------------------------------------------------------

    object Images {
        /** Composable grid columns for phones (portrait). */
        const val GRID_COLUMNS_PHONE = 3
        /** Composable grid columns for tablets (portrait). */
        const val GRID_COLUMNS_TABLET = 5
        /** Composable grid columns for tablets (landscape). */
        const val GRID_COLUMNS_TABLET_LAND = 7

        /** Card aspect ratio (width / height). 2:3 is the standard poster shape. */
        const val POSTER_ASPECT = 2f / 3f

        /** Hero banner aspect ratio (width / height). */
        const val BANNER_ASPECT = 16f / 9f

        /** Max image cache size in MB. */
        const val CACHE_MB = 96
    }

    // -----------------------------------------------------------------------
    //  Player
    // -----------------------------------------------------------------------

    object Player {
        /** Auto-play when the player screen opens. (Anikage default: true) */
        const val AUTO_PLAY = true

        /** Default playback speed. 1.0 = normal. (Anikage default: 1) */
        const val DEFAULT_SPEED = 1.0f

        /** Resume from saved position when re-opening an episode. */
        const val RESUME_FROM_POSITION = true

        /** Skip-forward / skip-backward amount in seconds. */
        const val SEEK_STEP_SECONDS = 10

        /** Auto-enter PiP when the user leaves the app while a video is playing. */
        const val AUTO_PIP_ON_LEAVE = true

        /** Force landscape when entering fullscreen on a phone. */
        const val FORCE_LANDSCAPE_FULLSCREEN = true

        /** Show episode list as a sidebar on tablets / landscape. */
        const val EPISODE_SIDEBAR_LANDSCAPE = true

        /** Auto-skip intro/outro — Anikage's "autoskip" setting. */
        const val AUTO_SKIP = true

        /** Auto-play the next episode when current finishes — Anikage's "autonext". */
        const val AUTO_NEXT = true

        /** Skip filler episodes — Anikage's "skipFillers". */
        const val SKIP_FILLERS = true

        /** Ambient lighting mode (player glow that matches the video) — Anikage's "ambientMode". */
        const val AMBIENT_MODE = true

        /** Mini progress bar (persistent thin progress bar at the top) — Anikage's "miniProgressBar". */
        const val MINI_PROGRESS_BAR = true

        /** Default volume (0.0 to 1.0). Anikage default: 1 (max). */
        const val DEFAULT_VOLUME = 1.0f

        /** Default muted state. */
        const val DEFAULT_MUTED = false

        /** Auto-play the hero trailer on the home screen — Anikage's "autoplayHeroTrailer". */
        const val AUTOPLAY_HERO_TRAILER = true

        /** Show adult content (18+ anime). Anikage default: true. */
        const val SHOW_ADULT_CONTENT = true

        /** Incognito mode (don't save to watch history). */
        const val INCOGNITO = false

        /** Intro skip duration in seconds (Anikage default: 85s). */
        const val INTRO_SKIP_DURATION = 85

        /** Comments enabled (overlay comments on the player). */
        const val COMMENTS_ENABLED = true

        /** Preferred stream type — "sub" or "dub" (Anikage default: "sub"). */
        const val STREAM_TYPE = "sub"

        /** Preferred stream quality (Anikage default: 1080p / 1920x1080). */
        const val STREAM_QUALITY_ID = "1080"
        const val STREAM_QUALITY_WIDTH = 1920
        const val STREAM_QUALITY_HEIGHT = 1080

        /** Anime title language preference — "english", "romaji", or "native" (Anikage default: english). */
        const val ANIME_TITLE_LANGUAGE = "english"
    }

    /**
     * Caption / subtitle styles — mirrors Anikage's "captionStyles" localStorage key.
     * Used by the player for VTT caption rendering.
     */
    object CaptionStyles {
        const val fontFamily = "trebuchet"
        const val fontWeight = 700
        const val textBorder = 100          // percent
        const val fontSize = 100             // percent (relative to default)
        const val textColor = "#ffffff"
        const val textOpacity = 100          // percent
        const val textShadow = "none"
        const val textBg = "#000000"
        const val textBgOpacity = 0          // percent
        const val displayBg = "#000000"
        const val displayBgOpacity = 0       // percent
    }

    /** Episode list preferences — mirrors Anikage's "episodePreferences" localStorage. */
    object EpisodePreferences {
        /** viewType: 1 = list, 2 = grid (Anikage default: 2). */
        const val VIEW_TYPE = 2
        /** sortOrder: "asc" or "desc" (Anikage default: asc). */
        const val SORT_ORDER = "asc"
    }

    /** Watch-history storage (mirrors Anikage's "watch_history" localStorage key). */
    object WatchHistory {
        const val ENABLED = true
        const val MAX_ENTRIES = 200
    }

    /** Sync integrations — mirrors Anikage's "syncDefaults" object. */
    object Sync {
        const val SYNC_TO_LOCAL_DB = true
        const val SYNC_TO_ANILIST = true
        const val SYNC_TO_MAL = true
    }

    // -----------------------------------------------------------------------
    //  Navigation
    // -----------------------------------------------------------------------

    object Nav {
        /** Default screen when the app launches. */
        const val DEFAULT_SCREEN = "home"

        /**
         * Available top-level screens in the order they appear in the
         * bottom mobile nav. Matches Anikage's actual mobile nav:
         * Home / Browse / Schedule / Music / Torrents.
         */
        val TOP_LEVEL_SCREENS = listOf(
            "home", "browse", "schedule", "music", "torrents",
        )

        /**
         * Top-bar layout. Anikage's actual top bar (extracted from the
         * live site DOM) is:
         *   [Logo]                            [Search] [Bell]
         * Fixed to top, backdrop-blur, rounded, with surface/70 background.
         */
        const val SHOW_TOP_BAR = true
        const val TOP_BAR_SHOWS_SEARCH = true
        const val TOP_BAR_SHOWS_NOTIFICATIONS = true
    }

    // -----------------------------------------------------------------------
    //  Features — feature flags
    // -----------------------------------------------------------------------

    object Features {
        /** Music tab — Anikage has this. Shows anime OST / opening theme info. */
        const val ENABLE_MUSIC_SCREEN = true

        /** Torrents tab — Anikage has this (links to Nyaa.si etc.). Disabled by default for legal reasons. */
        const val ENABLE_TORRENTS_SCREEN = false

        /** Continue Watching rail on Home — uses locally-stored watch history. */
        const val ENABLE_CONTINUE_WATCHING = true

        /** Recommendations section on Details screen. */
        const val ENABLE_RECOMMENDATIONS = true

        /** Characters section on Details screen. */
        const val ENABLE_CHARACTERS = true

        /** Pull-to-refresh on lists. */
        const val PULL_TO_REFRESH = true

        /** Caching. */
        const val ENABLE_OFFLINE_CACHE = true

        /** Genre chips on Home (Anikage has them at the top of the home screen). */
        const val ENABLE_GENRE_CHIPS_ON_HOME = true

        /** Hero carousel with progress dots + nav arrows (matches Anikage's home layout). */
        const val ENABLE_HERO_CAROUSEL = true

        /** Comments overlay on Watch screen. */
        const val ENABLE_COMMENTS = true

        /** Google Analytics (Anikage uses gtag — disabled here for privacy). */
        const val ENABLE_ANALYTICS = false

        /** Pop-up ads (Anikage uses llvpn — disabled here for obvious reasons). */
        const val ENABLE_POPUP_ADS = false
    }

    // -----------------------------------------------------------------------
    //  Network
    // -----------------------------------------------------------------------

    object Network {
        /** HTTP connect timeout (seconds). */
        const val CONNECT_TIMEOUT = 15
        /** HTTP read timeout (seconds). */
        const val READ_TIMEOUT = 30
        /** HTTP write timeout (seconds). */
        const val WRITE_TIMEOUT = 30

        /**
         * Browser-like User-Agent for outgoing API requests. The Anikage API
         * sits behind Cloudflare; a plain OkHttp UA risks a bot challenge,
         * while a Chrome-mobile UA passes cleanly (verified against the live
         * endpoints).
         */
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16; SM-X216B) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/138.0.0.0 Mobile Safari/537.36"

        /** OkHttp disk cache size in MB. */
        const val DISK_CACHE_MB = 64
    }
}
