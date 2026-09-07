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
    const val APP_VERSION = "2.0.0"

    /** Version code (integer; bump for every release). */
    const val APP_VERSION_CODE = 20

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
    //  Theme — EXACT replication of the Anikage website theme system.
    //
    //  The site ships 11 selectable themes (default + 10 accent themes) as
    //  CSS variable sets on <html data-theme="...">. Every token below was
    //  extracted verbatim from the live site's compiled CSS
    //  (_app/immutable/assets/0.*.css, [data-theme=...] blocks) on 2026-09-06.
    //
    //  The DEFAULT theme (data-theme="default", class "dark") is a neutral
    //  dark surface set with a WHITE action colour — white primary buttons
    //  with near-black text — exactly like a first-visit anikage.cc.
    // -----------------------------------------------------------------------

    /**
     * One Anikage web theme — a 1:1 port of a `[data-theme=...]` CSS block.
     */
    data class WebTheme(
        val key: String,
        val label: String,
        val surface: Color,
        val surfaceCard: Color,
        val surfaceCardHover: Color,
        val surfaceElevated: Color,
        val surfaceInput: Color,
        val fg: Color,
        val fgMuted: Color,
        val action: Color,
        val actionFg: Color,
        val accent: Color,
        val accentDeep: Color,
        val accentInfo: Color,
        val danger: Color,
        val ringFocus: Color,
        val aurora1: Color,
        val aurora2: Color,
        val aurora3: Color,
    )

    object Theme {

        /** Neutral dark default — what anikage.cc serves on first visit. */
        val DEFAULT = WebTheme(
            key = "default", label = "Default",
            surface = Color(0xFF0A0A0A),
            surfaceCard = Color(0xFF151515),
            surfaceCardHover = Color(0x14FFFFFF),
            surfaceElevated = Color(0xFF1A1A1A),
            surfaceInput = Color(0xFF27272A),
            fg = Color(0xFFFFFFFF),
            fgMuted = Color(0xB3FFFFFF),
            action = Color(0xFFFFFFFF),
            actionFg = Color(0xFF0A0A0A),
            accent = Color(0xFFFFFFFF),
            accentDeep = Color(0xFF2A2A2A),
            accentInfo = Color(0xFFBFBFBF),
            danger = Color(0xFF812435),
            ringFocus = Color(0xFFFFFFFF),
            aurora1 = Color(0xFFA3A3A3),
            aurora2 = Color(0xFF525252),
            aurora3 = Color(0xFF737373),
        )

        val MIDNIGHT = WebTheme(
            key = "midnight", label = "Midnight",
            surface = Color(0xFF060912),
            surfaceCard = Color(0xFF0F1424),
            surfaceCardHover = Color(0x1478A0FF),
            surfaceElevated = Color(0xFF161D35),
            surfaceInput = Color(0xFF1A2138),
            fg = Color(0xFFFFFFFF),
            fgMuted = Color(0xB3FFFFFF),
            action = Color(0xFF6E90FF),
            actionFg = Color(0xFF060912),
            accent = Color(0xFF6E90FF),
            accentDeep = Color(0xFF1E3A8A),
            accentInfo = Color(0xFF3C83F6),
            danger = Color(0xFF7F1D2E),
            ringFocus = Color(0xFF60A5FA),
            aurora1 = Color(0xFF6E90FF),
            aurora2 = Color(0xFF4A6FE1),
            aurora3 = Color(0xFF93B3FF),
        )

        val CRIMSON = WebTheme(
            key = "crimson", label = "Crimson",
            surface = Color(0xFF0C0707),
            surfaceCard = Color(0xFF1A0F10),
            surfaceCardHover = Color(0x14FF786E),
            surfaceElevated = Color(0xFF221416),
            surfaceInput = Color(0xFF2A1416),
            fg = Color(0xFFFFFFFF),
            fgMuted = Color(0xB3FFFFFF),
            action = Color(0xFFE64158),
            actionFg = Color(0xFFFFFFFF),
            accent = Color(0xFFE64158),
            accentDeep = Color(0xFF9B1C2E),
            accentInfo = Color(0xFFF5683D),
            danger = Color(0xFF4D0F17),
            ringFocus = Color(0xFFFF7A85),
            aurora1 = Color(0xFFE64158),
            aurora2 = Color(0xFFFF6B7D),
            aurora3 = Color(0xFFC0293F),
        )

        val EMERALD = WebTheme(
            key = "emerald", label = "Emerald",
            surface = Color(0xFF06100C),
            surfaceCard = Color(0xFF0D1C17),
            surfaceCardHover = Color(0x146EE6B4),
            surfaceElevated = Color(0xFF11251C),
            surfaceInput = Color(0xFF142D24),
            fg = Color(0xFFFFFFFF),
            fgMuted = Color(0xB3FFFFFF),
            action = Color(0xFF2DBF8F),
            actionFg = Color(0xFF04130D),
            accent = Color(0xFF2DBF8F),
            accentDeep = Color(0xFF126B48),
            accentInfo = Color(0xFF17CF91),
            danger = Color(0xFF7F1D2E),
            ringFocus = Color(0xFF34D399),
            aurora1 = Color(0xFF2DBF8F),
            aurora2 = Color(0xFF5FDCAF),
            aurora3 = Color(0xFF197555),
        )

        val AMOLED = WebTheme(
            key = "amoled", label = "Amoled",
            surface = Color(0xFF000000),
            surfaceCard = Color(0xFF0A0A0A),
            surfaceCardHover = Color(0x0FFFFFFF),
            surfaceElevated = Color(0xFF141414),
            surfaceInput = Color(0xFF1A1A1A),
            fg = Color(0xFFFFFFFF),
            fgMuted = Color(0xB3FFFFFF),
            action = Color(0xFFFFFFFF),
            actionFg = Color(0xFF000000),
            accent = Color(0xFFFFFFFF),
            accentDeep = Color(0xFF2A2A2A),
            accentInfo = Color(0xFFCCCCCC),
            danger = Color(0xFF5A1622),
            ringFocus = Color(0xFFFFFFFF),
            aurora1 = Color(0xFF707070),
            aurora2 = Color(0xFF4A4A4A),
            aurora3 = Color(0xFF9A9A9A),
        )

        val SUNSET = WebTheme(
            key = "sunset", label = "Sunset",
            surface = Color(0xFF0E0805),
            surfaceCard = Color(0xFF1C110A),
            surfaceCardHover = Color(0x14FFAA6E),
            surfaceElevated = Color(0xFF251610),
            surfaceInput = Color(0xFF2C1A10),
            fg = Color(0xFFFFFFFF),
            fgMuted = Color(0xB3FFFFFF),
            action = Color(0xFFE07238),
            actionFg = Color(0xFFFFFFFF),
            accent = Color(0xFFE07238),
            accentDeep = Color(0xFFB54A14),
            accentInfo = Color(0xFFF68D31),
            danger = Color(0xFF7F1D1D),
            ringFocus = Color(0xFFFBBF24),
            aurora1 = Color(0xFFE07238),
            aurora2 = Color(0xFFF5985C),
            aurora3 = Color(0xFFB54A14),
        )

        val ROSE = WebTheme(
            key = "rose", label = "Rose",
            surface = Color(0xFF0D0810),
            surfaceCard = Color(0xFF1A0F1D),
            surfaceCardHover = Color(0x14FF8CC8),
            surfaceElevated = Color(0xFF221428),
            surfaceInput = Color(0xFF2A1530),
            fg = Color(0xFFFFFFFF),
            fgMuted = Color(0xB3FFFFFF),
            action = Color(0xFFE0489A),
            actionFg = Color(0xFFFFFFFF),
            accent = Color(0xFFE0489A),
            accentDeep = Color(0xFF9D174D),
            accentInfo = Color(0xFFED5EA6),
            danger = Color(0xFF7F1D2E),
            ringFocus = Color(0xFFF472B6),
            aurora1 = Color(0xFFE0489A),
            aurora2 = Color(0xFFF273B8),
            aurora3 = Color(0xFFBE2D7C),
        )

        val GALAXY = WebTheme(
            key = "galaxy", label = "Galaxy",
            surface = Color(0xFF0C0414),
            surfaceCard = Color(0xFF150A24),
            surfaceCardHover = Color(0x1AA855F7),
            surfaceElevated = Color(0xFF1C1528),
            surfaceInput = Color(0xFF251E38),
            fg = Color(0xFFFFFFFF),
            fgMuted = Color(0xB3FFFFFF),
            action = Color(0xFFA855F7),
            actionFg = Color(0xFFFFFFFF),
            accent = Color(0xFFA855F7),
            accentDeep = Color(0xFF581C87),
            accentInfo = Color(0xFFA65EED),
            danger = Color(0xFF7F1D3A),
            ringFocus = Color(0xFFC084FC),
            aurora1 = Color(0xFFA855F7),
            aurora2 = Color(0xFF6366F1),
            aurora3 = Color(0xFF3B82F6),
        )

        val OCEAN = WebTheme(
            key = "ocean", label = "Ocean",
            surface = Color(0xFF040E1A),
            surfaceCard = Color(0xFF0A1929),
            surfaceCardHover = Color(0x1400C8FF),
            surfaceElevated = Color(0xFF0F2236),
            surfaceInput = Color(0xFF142D45),
            fg = Color(0xFFFFFFFF),
            fgMuted = Color(0xB3FFFFFF),
            action = Color(0xFF06B6D4),
            actionFg = Color(0xFFFFFFFF),
            accent = Color(0xFF06B6D4),
            accentDeep = Color(0xFF0E4D6E),
            accentInfo = Color(0xFF0DC4F2),
            danger = Color(0xFF7F1D2E),
            ringFocus = Color(0xFF22D3EE),
            aurora1 = Color(0xFF06B6D4),
            aurora2 = Color(0xFF0EA5E9),
            aurora3 = Color(0xFF6366F1),
        )

        val SAKURA = WebTheme(
            key = "sakura", label = "Sakura",
            surface = Color(0xFF100810),
            surfaceCard = Color(0xFF1C0F1C),
            surfaceCardHover = Color(0x14FB92B4),
            surfaceElevated = Color(0xFF261428),
            surfaceInput = Color(0xFF321A34),
            fg = Color(0xFFFFFFFF),
            fgMuted = Color(0xB3FFFFFF),
            action = Color(0xFFF472B6),
            actionFg = Color(0xFFFFFFFF),
            accent = Color(0xFFF472B6),
            accentDeep = Color(0xFF831843),
            accentInfo = Color(0xFFEF5D8D),
            danger = Color(0xFF7F1D2E),
            ringFocus = Color(0xFFF9A8D4),
            aurora1 = Color(0xFFF472B6),
            aurora2 = Color(0xFFC084FC),
            aurora3 = Color(0xFF818CF8),
        )

        val AMBER = WebTheme(
            key = "amber", label = "Amber",
            surface = Color(0xFF0C0A06),
            surfaceCard = Color(0xFF161208),
            surfaceCardHover = Color(0x14F59E0B),
            surfaceElevated = Color(0xFF1A1508),
            surfaceInput = Color(0xFF2A2310),
            fg = Color(0xFFE5E5E5),
            fgMuted = Color(0xFFA3A3A3),
            action = Color(0xFFF59E0B),
            actionFg = Color(0xFF000000),
            accent = Color(0xFFF59E0B),
            accentDeep = Color(0xFF92400E),
            accentInfo = Color(0xFFF59F0A),
            danger = Color(0xFF991B1B),
            ringFocus = Color(0xFFF59E0B),
            aurora1 = Color(0xFFF59E0B),
            aurora2 = Color(0xFFFBBF24),
            aurora3 = Color(0xFFB45309),
        )

        /** All site themes in picker order (default first). */
        val ALL = listOf(
            DEFAULT, MIDNIGHT, CRIMSON, EMERALD, AMOLED,
            SUNSET, ROSE, GALAXY, OCEAN, SAKURA, AMBER,
        )

        fun byKey(key: String): WebTheme = ALL.find { it.key == key } ?: DEFAULT

        // ---- Convenience aliases mapped onto the ACTIVE theme -------------
        // (These read the runtime-selected theme so legacy call sites keep
        // working; new code should prefer LocalAnikageTheme.)
        var active: WebTheme = DEFAULT

        val primary: Color get() = active.action
        val secondary: Color get() = active.accentInfo
        val tertiary: Color get() = active.aurora2
        val background: Color get() = active.surface
        val surface: Color get() = active.surfaceCard
        val surfaceVariant: Color get() = active.surfaceElevated
        val surfaceElevated: Color get() = active.surfaceElevated
        val onBackground: Color get() = active.fg
        val onSurface: Color get() = active.fg
        val onSurfaceVariant: Color get() = active.fgMuted
        val outline: Color get() = active.fgMuted.copy(alpha = 0.25f)
        val error: Color get() = active.danger
        val onError: Color get() = Color(0xFFFFFFFF)

        // Fixed semantic colours shared by every website theme.
        val success = Color(0xFF22C55E)      // green-500 (site "Aired"/releasing dots)
        val warning = Color(0xFFF59E0B)      // amber-500
        val info = Color(0xFF5D61E5)
        val star = Color(0xFFFACC15)         // yellow-400 (score chips)
        val releasing = Color(0xFF4ADE80)    // green-400 dot
        val releasingDim = Color(0xFF22C55E) // green-500 border
        val finished = Color(0xFFFB7185)     // rose-400 dot
        val finishedDim = Color(0xFFF43F5E)  // rose-500 border

        /** Glassy overlay (used for hero banner gradients). */
        val scrim = Color(0xCC000000)        // 80% black
        val scrimLight = Color(0x66000000)   // 40% black

        /**
         * Whether the app starts in dark mode by default.
         * (The website is dark-only.)
         */
        const val DEFAULT_DARK_MODE = true

        /** Persisted theme key ("default", "midnight", ...). */
        const val PREFS_KEY = "anikage_theme"
    }

    // -----------------------------------------------------------------------
    //  Typography — 1:1 port of the website's fluid clamp() scale.
    //
    //  The site uses the system font stack (Roboto on Android), NOT a custom
    //  webfont, with fluid sizes clamped by viewport width. [fluidSp]
    //  reproduces the clamp formula against the device screen width.
    // -----------------------------------------------------------------------

    object Typography {
        /** Default font family — system (Roboto on Android), like the site. */
        const val USE_RUBIK = false

        /**
         * Port of CSS clamp(min, preferred, max): scales `preferred` with the
         * screen width between the two bounds. Screen width is in dp and the
         * site's vw maps 1:1 to dp on Android.
         */
        fun fluidSp(screenWidthDp: Int, min: Float, preferred: Float, max: Float): Float {
            val scaled = preferred * screenWidthDp / 100f
            return scaled.coerceIn(min, max)
        }

        // Text sizes (in sp) — base values at phone width; Type.kt applies the
        // fluid variant at runtime. Values mirror the site's clamp tokens.
        val displayLarge = 32.sp
        val displayMedium = 28.sp
        val displaySmall = 24.sp
        val headlineLarge = 22.sp
        val headlineMedium = 20.sp
        val headlineSmall = 18.sp
        val titleLarge = 17.sp   // text-xl (title-section) at phone width
        val titleMedium = 15.sp  // text-lg
        val titleSmall = 14.sp   // text-base
        val bodyLarge = 14.sp    // text-sm
        val bodyMedium = 13.sp
        val bodySmall = 11.sp    // text-xs
        val labelLarge = 14.sp
        val labelMedium = 12.sp
        val labelSmall = 10.sp   // text-2xs
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
        const val ENABLE_TORRENTS_SCREEN = true

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
         * Client-side cap on AniList GraphQL requests per rolling minute.
         * AniList documents 90/min (30/min while degraded); staying well
         * under it means the app can never trip the limit, even when a
         * user browses aggressively.
         */
        const val ANILIST_MAX_PER_MINUTE = 25

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
