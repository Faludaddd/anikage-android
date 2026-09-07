package com.anikage.app.core.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Caption styling — the same knobs the site's Caption Styles panel exposes
 * (size / color / opacity / weight / shadow / backgrounds). Stored as one
 * JSON blob in prefs.
 */
@Serializable
data class CaptionStyles(
    /** Text size scale 0.5..2.0 (site: fontSize). */
    val fontSize: Float = 1.0f,
    /** Cue text color (site: textColor). */
    val textColor: Long = 0xFFFFFFFF,
    /** Cue text opacity (site: textOpacity). */
    val textOpacity: Float = 1.0f,
    /** Per-cue background color (site: textBg). */
    val textBg: Long = 0xFF000000,
    /** Per-cue background opacity (site: textBgOpacity). */
    val textBgOpacity: Float = 0.6f,
    /** Full display background color (site: displayBg). */
    val displayBg: Long = 0xFF000000,
    /** Full display background opacity (site: displayBgOpacity). */
    val displayBgOpacity: Float = 0.0f,
    /** 400 normal | 700 bold (site: fontWeight Bold/None). */
    val fontWeight: Int = 400,
    /** Text shadow on/off (site: textShadow). */
    val textShadow: Boolean = true,
    /** Text border/outline on/off (site: textBorder). */
    val textBorder: Boolean = false,
)

/**
 * Runtime, persisted app settings — the same keys the Anikage site stores
 * in localStorage, exposed as observable Compose state so every screen
 * (player, home, settings) reacts immediately when a toggle changes.
 *
 * The Settings screen writes here; the rest of the app reads here instead
 * of the static Config defaults.
 */
object SettingsState {

    private const val PREFS = "anikage_settings"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ── General ───────────────────────────────────────────────────────────
    /** Don't save watch history / list changes (site: incognito). */
    var incognito by mutableStateOf(false)

    /** Show 18+ anime (site: showAdultContent). */
    var showAdultContent by mutableStateOf(true)

    /** Comments on the watch page (site: commentsEnabled). */
    var commentsEnabled by mutableStateOf(true)

    /** Title language: english | romaji | native (site: english). */
    var titleLanguage by mutableStateOf("english")

    /** Autoplay the hero trailer on the home screen (site: autoplayHeroTrailer). */
    var autoplayHeroTrailer by mutableStateOf(false)

    // ── Player ────────────────────────────────────────────────────────────
    /** Autoplay on page load (site: autoplay). */
    var autoplay by mutableStateOf(true)

    /** Play the next episode automatically (site: autonext). */
    var autonext by mutableStateOf(true)

    /** Skip detected intros/outros (site: autoskip). */
    var autoskip by mutableStateOf(false)

    /** Always start muted (site: muted). */
    var muted by mutableStateOf(false)

    /** Skip filler episodes on auto-next (site: skipFillers). */
    var skipFillers by mutableStateOf(false)

    /** Ambient glow around the player (site: ambientMode). */
    var ambientMode by mutableStateOf(false)

    /** Thin progress bar while controls are hidden (site: miniProgressBar). */
    var miniProgressBar by mutableStateOf(true)

    /** Episode thumbnails in the episode list (site: episodeThumbnails). */
    var episodeThumbnails by mutableStateOf(true)

    /** Preferred stream quality (site: streamQuality: auto|low|standard|full). */
    var streamQuality by mutableStateOf("auto")

    /** Intro skip duration in seconds (site: introSkipDuration). */
    var introSkipDuration by mutableIntStateOf(85)

    /** Default volume 0..1 (site: volume). */
    var volume by mutableFloatStateOf(1.0f)

    /** Episode list sort order asc|desc (site: episodePreferences). */
    var episodeSortOrder by mutableStateOf("asc")

    /** Preferred sub/dub for new streams (site default sub). */
    var streamLang by mutableStateOf("sub")

    /** Verbose logging (diagnostics). */
    var verboseLogging by mutableStateOf(false)

    /** Last selected playback speed (site: playbackRate). */
    var playbackRate by mutableFloatStateOf(1.0f)

    /** Player caption styling (site: captionStyles). */
    var captionStyles by mutableStateOf(CaptionStyles())

    // ── lifecycle ─────────────────────────────────────────────────────────

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        incognito = p.getBoolean("incognito", false)
        showAdultContent = p.getBoolean("showAdultContent", true)
        commentsEnabled = p.getBoolean("commentsEnabled", true)
        titleLanguage = p.getString("titleLanguage", "english") ?: "english"
        autoplayHeroTrailer = p.getBoolean("autoplayHeroTrailer", false)
        autoplay = p.getBoolean("autoplay", true)
        autonext = p.getBoolean("autonext", true)
        autoskip = p.getBoolean("autoskip", false)
        muted = p.getBoolean("muted", false)
        skipFillers = p.getBoolean("skipFillers", false)
        ambientMode = p.getBoolean("ambientMode", false)
        miniProgressBar = p.getBoolean("miniProgressBar", true)
        episodeThumbnails = p.getBoolean("episodeThumbnails", true)
        streamQuality = p.getString("streamQuality", "auto") ?: "auto"
        introSkipDuration = p.getInt("introSkipDuration", 85)
        volume = p.getFloat("volume", 1.0f)
        episodeSortOrder = p.getString("episodeSortOrder", "asc") ?: "asc"
        streamLang = p.getString("streamLang", "sub") ?: "sub"
        verboseLogging = p.getBoolean("verboseLogging", false)
        playbackRate = p.getFloat("playbackRate", 1.0f)
        captionStyles = p.getString("captionStyles", null)
            ?.let { runCatching { json.decodeFromString<CaptionStyles>(it) }.getOrNull() }
            ?: CaptionStyles()
    }

    private fun save(context: Context, block: android.content.SharedPreferences.Editor.() -> Unit) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply(block).apply()
        }
    }

    fun setIncognito(context: Context, v: Boolean) { incognito = v; save(context) { putBoolean("incognito", v) } }
    fun setShowAdultContent(context: Context, v: Boolean) { showAdultContent = v; save(context) { putBoolean("showAdultContent", v) } }
    fun setCommentsEnabled(context: Context, v: Boolean) { commentsEnabled = v; save(context) { putBoolean("commentsEnabled", v) } }
    fun setTitleLanguage(context: Context, v: String) { titleLanguage = v; save(context) { putString("titleLanguage", v) } }
    fun setAutoplayHeroTrailer(context: Context, v: Boolean) { autoplayHeroTrailer = v; save(context) { putBoolean("autoplayHeroTrailer", v) } }
    fun setAutoplay(context: Context, v: Boolean) { autoplay = v; save(context) { putBoolean("autoplay", v) } }
    fun setAutonext(context: Context, v: Boolean) { autonext = v; save(context) { putBoolean("autonext", v) } }
    fun setAutoskip(context: Context, v: Boolean) { autoskip = v; save(context) { putBoolean("autoskip", v) } }
    fun setMuted(context: Context, v: Boolean) { muted = v; save(context) { putBoolean("muted", v) } }
    fun setSkipFillers(context: Context, v: Boolean) { skipFillers = v; save(context) { putBoolean("skipFillers", v) } }
    fun setAmbientMode(context: Context, v: Boolean) { ambientMode = v; save(context) { putBoolean("ambientMode", v) } }
    fun setMiniProgressBar(context: Context, v: Boolean) { miniProgressBar = v; save(context) { putBoolean("miniProgressBar", v) } }
    fun setEpisodeThumbnails(context: Context, v: Boolean) { episodeThumbnails = v; save(context) { putBoolean("episodeThumbnails", v) } }
    fun setStreamQuality(context: Context, v: String) { streamQuality = v; save(context) { putString("streamQuality", v) } }
    fun setIntroSkipDuration(context: Context, v: Int) { introSkipDuration = v; save(context) { putInt("introSkipDuration", v) } }
    fun setVolume(context: Context, v: Float) { volume = v; save(context) { putFloat("volume", v) } }
    fun setEpisodeSortOrder(context: Context, v: String) { episodeSortOrder = v; save(context) { putString("episodeSortOrder", v) } }
    fun setStreamLang(context: Context, v: String) { streamLang = v; save(context) { putString("streamLang", v) } }
    fun setPlaybackRate(context: Context, v: Float) { playbackRate = v; save(context) { putFloat("playbackRate", v) } }
    fun setCaptionStyles(context: Context, v: CaptionStyles) {
        captionStyles = v
        save(context) { putString("captionStyles", json.encodeToString(v)) }
    }
    fun setVerboseLogging(context: Context, v: Boolean) {
        verboseLogging = v
        save(context) { putBoolean("verboseLogging", v) }
        com.anikage.app.core.log.AppLogger.setVerbose(v, context)
    }
}
