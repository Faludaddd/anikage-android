package com.anikage.app.ui.player

import android.app.Application
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import com.anikage.app.Config
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.api.AnikageComment
import com.anikage.app.core.data.api.AnikageServer
import com.anikage.app.core.data.db.DownloadedEpisodeEntity
import com.anikage.app.core.data.model.AnimeDetails
import com.anikage.app.core.download.EpisodeDownloadEngine
import com.anikage.app.core.log.AppLogger
import com.anikage.app.core.log.LogCategory
import com.anikage.app.core.media.PlayerFactory
import com.anikage.app.core.settings.SettingsState
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One episode with real metadata (from the Anikage episodes API). */
data class EpisodeItem(
    val number: Int,
    val title: String = "Episode $number",
    val thumbnail: String? = null,
    val isFiller: Boolean = false,
    val isRecap: Boolean = false,
)

/** Per-episode watch progress for the episode list (watched state + bar). */
data class EpisodeProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val watched: Boolean get() = durationMs > 0 && positionMs >= durationMs * 95 / 100
    val inProgress: Boolean get() = positionMs > 10_000L && !watched
}

/** A subtitle track from the sources response (token -> proxy URL). */
data class SubtitleTrack(
    val label: String,
    val url: String,
    val language: String,
    val isDefault: Boolean,
)

/** A playback server + what it can serve (from the servers endpoint). */
data class StreamServer(
    val id: String,
    val name: String,
    val supportsSub: Boolean,
    val supportsDub: Boolean,
    val isDefault: Boolean,
)

/** An E-server (embed) chip — WebView players from the sources endpoint. */
data class EmbedServer(
    val key: String,
    val label: String,
    val url: String? = null,
)

/** One selectable video quality (HLS rendition). */
data class QualityOption(
    val label: String,
    val height: Int,
    val groupIndex: Int = -1,
    val trackIndex: Int = -1,
    val isSelected: Boolean = false,
)

/** One selectable audio / subtitle track line in the settings panel. */
data class TrackOption(
    val label: String,
    val groupIndex: Int = -1,
    val trackIndex: Int = -1,
    val isSelected: Boolean = false,
)

/** Download rendition choice for the in-app download dialog. */
data class DownloadQualityOption(
    val label: String,
    val height: Int,
)

/** "Up next" card state — autonext countdown at the end of an episode. */
data class NextUpState(
    val episode: Int,
    val title: String,
    val countdownSec: Int,
)

data class CommentsUiState(
    val loading: Boolean = false,
    val comments: List<AnikageComment> = emptyList(),
    val total: Int = 0,
)

data class WatchUiState(
    val loading: Boolean = true,
    val details: AnimeDetails? = null,
    val episode: Int = 1,
    val totalEpisodes: Int = 1,
    val episodes: List<EpisodeItem> = emptyList(),
    val streamUrl: String? = null,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val qualityOptions: List<String> = emptyList(),
    val streamLoading: Boolean = false,
    /** Source-resolution failure — shown with a retry + server hint. */
    val streamError: String? = null,
    /** In-player failure (ExoPlayer error) — shown on the player surface. */
    val playbackError: String? = null,
    val error: String? = null,
    val savedPositionMs: Long = 0L,
    val title: String = "",
    val slug: String? = null,
    val viewCount: Long? = null,
    val comments: CommentsUiState = CommentsUiState(),
    /** Site's server panel state. */
    val streamLang: String = Config.DEFAULT_STREAM_LANG,
    val streamServer: String = Config.DEFAULT_STREAM_PROVIDER,
    val servers: List<StreamServer> = emptyList(),
    /** E-server chips (site: E-Koto / E-Neko …) + embed mode. */
    val embedServers: List<EmbedServer> = emptyList(),
    val embedActive: EmbedServer? = null,
    val embedUrl: String? = null,
    val embedLoading: Boolean = false,
    val embedError: String? = null,
    // ── live player state (ticked by the position ticker / listener) ─────
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val playWhenReady: Boolean = false,
    val positionMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val durationMs: Long = 0L,
    val videoAspect: Float = 16f / 9f,
    val speed: Float = 1.0f,
    val volume: Float = 1.0f,
    /** Master volume 0..1 (site's volume slider; [volume] is the 1..2x boost). */
    val masterVolume: Float = 1.0f,
    val muted: Boolean = false,
    val captionsOn: Boolean = true,
    val cues: List<Cue> = emptyList(),
    /** Skip-timestamp ranges from the sources response. */
    val introStartMs: Long = -1L,
    val introEndMs: Long = -1L,
    val outroStartMs: Long = -1L,
    val outroEndMs: Long = -1L,
    // ── track menus (settings panel) ──────────────────────────────────────
    val qualities: List<QualityOption> = emptyList(),
    val audioTracks: List<TrackOption> = emptyList(),
    val textTracks: List<TrackOption> = emptyList(),
    /** True once the app's own view POST was sent for the current episode. */
    val viewCounted: Boolean = false,
    // ── episode progress + local list (episode list UI) ──────────────────
    val episodeProgress: Map<Int, EpisodeProgress> = emptyMap(),
    /** Local anime list status for this anime (null = not in list). */
    val listStatus: String? = null,
    // ── offline / downloaded playback ─────────────────────────────────────
    /** Non-null when the player is playing a downloaded file (offline mode). */
    val localFile: String? = null,
    /** Completed in-app download record for the CURRENT episode (if any). */
    val downloaded: DownloadedEpisodeEntity? = null,
    /** Download rendition choices resolved for the download dialog. */
    val downloadQualities: List<DownloadQualityOption> = emptyList(),
    val downloadQualitiesLoading: Boolean = false,
    val downloadQualitiesError: String? = null,
    /** Auto-next countdown card (site: "Playing next" overlay). */
    val nextUp: NextUpState? = null,
    /** Fillers skipped on the way to the current episode (toast info). */
    val skippedFillerNotice: String? = null,
) {
    /** Servers that can serve the current SUB/DUB selection. */
    val availableServers: List<StreamServer>
        get() = servers.filter {
            if (streamLang == "dub") it.supportsDub else it.supportsSub
        }

    val hasIntro: Boolean get() = introStartMs >= 0 && introEndMs > introStartMs
    val hasOutro: Boolean get() = outroStartMs >= 0 && outroEndMs > outroStartMs

    val currentEpisodeItem: EpisodeItem?
        get() = episodes.firstOrNull { it.number == episode }

    val isCurrentFiller: Boolean get() = currentEpisodeItem?.isFiller == true

    /** Playing a downloaded copy right now (offline mode). */
    val playingDownloaded: Boolean get() = localFile != null
}

/**
 * Watch screen data + ExoPlayer wiring — the app's full player engine.
 *
 * Streaming flow (mirrors anikage.cc exactly):
 *   1. details (Anikage info by slug first, AniList fallback, deduped) — OR
 *      carried-in Anikage slug from the originating screen.
 *   2. slug — used directly when known; resolved by title-search only as a
 *      fallback.
 *   3. episodes -> {slug}/episodes (titles/thumbs/filler).
 *   4. servers -> {slug}/episodes/{n}/servers (koto/kiwi/neko/zen + embeds).
 *   5. sources -> {slug}/episodes/{n}/sources?provider&lang -> HLS tokens,
 *      subtitles, intro/outro skip times, embed options.
 *   6. token -> {PROXY}/m3u8/{token}, played through [PlayerFactory]'s
 *      OkHttp transport (guaranteed Origin/Referer headers + LRU cache).
 *
 * VIDEO SURFACE (the black-video-with-audio fix): the TextureView composable
 * can attach BEFORE the player exists (player is built lazily when the first
 * stream resolves) — `player()` therefore attaches the stored view when the
 * player is created, and [attachSurface]/[detachSurface] are identity-guarded
 * so the fullscreen swap (dispose old view -> compose new view) can never
 * clear a NEWER attachment with the OLD view's release callback.
 */
@OptIn(androidx.media3.common.util.UnstableApi::class)
class WatchViewModel(
    private val app: Application,
    private val repo: AnikageRepository,
    private val animeId: Int,
    /** 0 = auto-resume from watch progress (the site's /watch/{slug} behaviour). */
    private val initialEpisode: Int,
    initialSlug: String? = null,
    /** When set, playback uses this downloaded file instead of the network. */
    private val initialLocalFile: String? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(
        WatchUiState(
            loading = true,
            episode = if (initialEpisode > 0) initialEpisode else 1,
            slug = initialSlug?.takeIf { it.isNotBlank() },
            streamLang = SettingsState.streamLang,
            masterVolume = SettingsState.volume,
        ),
    )
    val state: StateFlow<WatchUiState> = _state.asStateFlow()

    private var player: ExoPlayer? = null

    // ── single-active-playback-session machinery (race-condition fix) ────
    private var episodeJob: Job? = null
    private val episodeGen = java.util.concurrent.atomic.AtomicInteger(0)

    /** Providers that failed for the CURRENT episode+lang (auto-fallback memory). */
    private val failedProviders = LinkedHashSet<String>()

    /** True while an automatic fallback drives the reload (prevents loops). */
    private var autoFallingBack = false

    /** TextureView the surface composable registered (for screenshots). */
    @Volatile
    var textureView: TextureView? = null
        private set

    private var tickerJob: Job? = null
    private val viewSent = AtomicBoolean(false)

    /** Guards the "no video track" check to run once per prepared item. */
    private var checkedVideoTrackForItem = false

    /**
     * The player — built ONCE via [PlayerFactory] (OkHttp transport with the
     * Origin/Referer headers og.bakayaro.live requires, LRU cache, tuned
     * buffers). All settings (speed/volume/autonext) apply immediately.
     *
     * THE BLACK-VIDEO FIX: whenever the player is created here, the already
     * registered TextureView (the UI usually composes BEFORE the first
     * stream resolves) is attached — audio without video was exactly the
     * missed-attach case.
     */
    fun player(): ExoPlayer = player ?: buildPlayer().also { p ->
        player = p
        p.repeatMode = Player.REPEAT_MODE_OFF
        p.playWhenReady = SettingsState.autoplay
        applyVolumeTo(p)
        p.setPlaybackSpeed(SettingsState.playbackRate)
        p.addListener(playerListener)
        textureView?.let { view ->
            p.setVideoTextureView(view)
            AppLogger.d(LogCategory.PLAYER, "Video surface attached to a fresh player instance")
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updateState {
                copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    durationMs = player?.duration?.takeIf { it > 0 } ?: durationMs,
                )
            }
            if (playbackState == Player.STATE_READY) {
                updateState { copy(durationMs = player?.duration ?: durationMs) }
                startTicker()
                checkVideoTrackActuallySelected()
            }
            if (playbackState == Player.STATE_ENDED) {
                onEpisodeEnded()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateState { copy(isPlaying = isPlaying) }
            if (isPlaying) startTicker() else tickerJob?.cancel()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            updateState { copy(playWhenReady = playWhenReady) }
        }

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                updateState { copy(videoAspect = videoSize.width.toFloat() / videoSize.height) }
            }
        }

        override fun onCues(cueGroup: CueGroup) {
            if (_state.value.cues !== cueGroup.cues) {
                updateState { copy(cues = cueGroup.cues) }
            }
        }

        override fun onCues(cues: MutableList<Cue>) {
            if (_state.value.cues != cues) updateState { copy(cues = cues) }
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            refreshTrackMenus(tracks)
        }

        override fun onPlayerError(error: PlaybackException) {
            val gen = episodeGen.get()
            val friendly = friendlyPlaybackError(error)
            AppLogger.e(
                LogCategory.PLAYER,
                "Playback failed (${error.errorCodeName}) on ${_state.value.streamServer}/ep${_state.value.episode} " +
                    "[attempt ${failedProviders.size + 1}]\n" +
                    "  Media3 error: ${error.errorCode} — ${friendly}\n" +
                    "  cause: ${error.cause?.javaClass?.simpleName ?: "-"}: ${error.cause?.message?.take(160) ?: "-"}",
                error,
            )
            cancelNextUp()
            maybeAutoFallback(error, gen, friendly)
        }
    }

    /**
     * "Audio plays but the screen stays black" guard: after READY, if the
     * stream selects NO video track (a broken/video-less rendition, an
     * unsupported codec silently dropped, an audio-only manifest), that's a
     * source problem — burn the provider and fall back to the next one
     * automatically instead of leaving the user on a black player.
     */
    private fun checkVideoTrackActuallySelected() {
        if (checkedVideoTrackForItem) return
        checkedVideoTrackForItem = true
        val p = player ?: return
        if (_state.value.embedActive != null || _state.value.localFile != null) return
        val hasSelectedVideo = p.currentTracks.groups.any { g ->
            g.type == C.TRACK_TYPE_VIDEO && (0 until g.length).any { g.isTrackSelected(it) }
        }
        if (!hasSelectedVideo) {
            val gen = episodeGen.get()
            AppLogger.w(
                LogCategory.PLAYER,
                "No video track selected after READY on ${_state.value.streamServer} " +
                    "(audio-only or unsupported codec) — auto-falling back to the next server",
            )
            val friendly = "This server's stream has no playable video track (audio-only or an " +
                "unsupported codec). Switching servers…"
            burnProviderAndFallback("no-video-track", gen, friendly)
        } else {
            val video = p.currentTracks.groups
                .filter { it.type == C.TRACK_TYPE_VIDEO }
                .flatMap { g -> (0 until g.length).map { i -> g.getTrackFormat(i) to g.isTrackSelected(i) } }
                .filter { it.second }
                .map { it.first }
                .firstOrNull()
            AppLogger.i(
                LogCategory.PLAYER,
                "Video track live: ${video?.width}x${video?.height} ${video?.sampleMimeType ?: "?"}" +
                    " — surface ${if (textureView != null) "attached" else "PENDING"}",
            )
        }
    }

    /** Episode ended — autonext (with configurable countdown) or just idle. */
    private fun onEpisodeEnded() {
        if (!SettingsState.autonext) return
        val next = nextPlayableEpisode(
            _state.value.episode + 1,
            forceSkipFiller = SettingsState.autoSkipFiller,
        ) ?: return
        val countdown = SettingsState.autonextCountdownSec.coerceIn(0, 30)
        if (countdown <= 0) {
            switchEpisode(next)
        } else {
            val nextItem = _state.value.episodes.firstOrNull { it.number == next }
            updateState {
                copy(
                    nextUp = NextUpState(
                        episode = next,
                        title = nextItem?.title?.takeIf { t -> t.isNotBlank() } ?: "Episode $next",
                        countdownSec = countdown,
                    ),
                )
            }
            startTicker()
        }
    }

    fun cancelNextUp() {
        if (_state.value.nextUp != null) updateState { copy(nextUp = null) }
    }

    fun playNextNow() {
        val n = _state.value.nextUp?.episode ?: return
        cancelNextUp()
        switchEpisode(n)
    }

    /**
     * Intelligent provider fallback — bounded, per-episode.
     *
     * Triggered when playback fails with a SOURCE-level error (parsing /
     * container / manifest / HTTP 4xx-5xx from the stream host), when a
     * provider resolves no usable source, or when the stream turns out to
     * have no playable video track.
     */
    /** Returns true when a fallback reload was started (caller should bail). */
    private fun maybeAutoFallback(error: PlaybackException?, gen: Int, friendly: String?): Boolean {
        if (_state.value.embedActive != null) return false
        if (_state.value.localFile != null) return false
        if (episodeGen.get() != gen) return false // stale error from a previous load

        val http = error?.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
        val isSourceError = when {
            error == null -> true // source resolution failure path
            http != null && (http.responseCode == 403 || http.responseCode in 500..599) -> true
            http != null && http.responseCode == 429 -> false // rate limit: retry same source, don't burn providers
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> false
            error.errorCodeName.contains("PARSING") ||
                error.errorCodeName.contains("SOURCE") ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> true
            else -> false
        }
        if (!isSourceError) {
            updateState { copy(playbackError = friendly ?: friendlyPlaybackError(error!!)) }
            return false
        }
        return burnProviderAndFallback(_state.value.streamServer, gen, friendly)
    }

    /** Burn the current provider (once) and auto-switch to the next capable one. */
    private fun burnProviderAndFallback(current: String, gen: Int, friendly: String?): Boolean {
        if (episodeGen.get() != gen) return false
        failedProviders += current.lowercase()
        val next = nextCapableProvider()
        if (next != null) {
            autoFallingBack = true
            val tried = failedProviders.joinToString(" → ")
            AppLogger.i(
                LogCategory.PLAYER,
                "FALLBACK #${failedProviders.size}: $current failed -> trying $next (tried: $tried)",
            )
            updateState { copy(playbackError = null, streamError = null, streamUrl = null) }
            _state.value = _state.value.copy(streamServer = next)
            loadEpisode(_state.value.episode)
            return true
        } else {
            autoFallingBack = false
            val tried = failedProviders.joinToString(", ")
            val verdict = "All servers failed for this episode ($tried). " +
                (friendly ?: "Try switching language or reloading the stream.")
            AppLogger.w(LogCategory.PLAYER, "FALLBACK exhausted (ep ${_state.value.episode}): $verdict")
            updateState { copy(playbackError = verdict) }
        }
        return false
    }

    /** Next capable provider not yet failed for this episode+lang. */
    private fun nextCapableProvider(): String? {
        val lang = _state.value.streamLang
        return _state.value.servers
            .filter { if (lang == "dub") it.supportsDub else it.supportsSub }
            .map { it.name }
            .firstOrNull { it.lowercase() !in failedProviders }
    }

    private fun buildPlayer(): ExoPlayer = PlayerFactory.build(app)

    private fun applyVolumeTo(p: Player) {
        p.volume = if (SettingsState.muted || _state.value.muted) 0f else SettingsState.volume * _state.value.volume
    }

    // -----------------------------------------------------------------------
    //  Player surface registration (called by the UI)
    // -----------------------------------------------------------------------

    /**
     * Attach the Compose-rendered TextureView. The player may not exist yet
     * (first load: the surface composes while sources are still resolving) —
     * in that case [player()] attaches the stored view when it builds the
     * player, so both orders are covered.
     */
    fun attachSurface(view: TextureView) {
        textureView = view
        player?.setVideoTextureView(view)
    }

    /**
     * Identity-guarded detach: only clears the player's surface when the
     * view being released is STILL the registered one. This is what makes
     * the inline <-> fullscreen swap safe — if Compose releases the OLD
     * view after the NEW view already attached, the new attachment survives.
     */
    fun detachSurface(view: TextureView) {
        if (textureView === view) {
            textureView = null
            player?.clearVideoSurface()
        }
    }

    // -----------------------------------------------------------------------
    //  Transport controls — every control in the custom player UI routes here
    // -----------------------------------------------------------------------

    fun togglePlayPause() {
        val p = player() ?: return
        if (p.isPlaying) p.pause() else p.play()
        cancelNextUp()
    }

    fun play() {
        cancelNextUp()
        player()?.play()
    }

    fun pause() = player()?.pause()

    fun seekTo(positionMs: Long) {
        val p = player() ?: return
        val bounded = positionMs.coerceIn(0L, if (p.duration > 0) p.duration else Long.MAX_VALUE)
        p.seekTo(bounded)
        updateState { copy(positionMs = bounded) }
    }

    /** Seek by the CONFIGURABLE amount (Settings -> Player -> Seek amount). */
    fun seekByConfigured(deltaSign: Int) {
        seekBy(deltaSign.toLong() * SettingsState.seekAmountSec * 1000L)
    }

    fun seekBy(deltaMs: Long) {
        val p = player() ?: return
        seekTo(p.currentPosition + deltaMs)
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 2.0f)
        player()?.setPlaybackSpeed(clamped)
        SettingsState.setPlaybackRate(app, clamped)
        updateState { copy(speed = clamped) }
    }

    // ── hold-to-speed (press & hold the video surface) ─────────────────────

    /** Temporarily jump to the configured hold speed while the surface is held. */
    fun beginHoldSpeed() {
        val p = player() ?: return
        if (holdOriginalSpeed == null) {
            holdOriginalSpeed = p.playbackParameters.speed.takeIf { it > 0f } ?: 1f
        }
        p.setPlaybackSpeed(SettingsState.holdSpeedRate)
        updateState { copy(speed = SettingsState.holdSpeedRate) }
    }

    /** Restore the speed from before the hold (never persists the hold rate). */
    fun endHoldSpeed() {
        val restore = holdOriginalSpeed ?: return
        holdOriginalSpeed = null
        player()?.setPlaybackSpeed(restore)
        updateState { copy(speed = restore) }
    }

    private var holdOriginalSpeed: Float? = null

    /** Audio boost 1x..2x (the site's Web-Audio gain equivalent). */
    fun setBoost(boost: Float) {
        val clamped = boost.coerceIn(1.0f, 2.0f)
        updateState { copy(volume = clamped, muted = false) }
        player()?.let { p ->
            p.volume = clamped * SettingsState.volume *
                if (SettingsState.muted) 0f else 1f
        }
    }

    /** Master volume 0..1 (site: player volume slider, persisted). */
    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        val boost = _state.value.volume.coerceAtLeast(1.0f)
        updateState { copy(masterVolume = clamped, muted = clamped <= 0f) }
        val p = player ?: return
        p.volume = if (clamped <= 0f) 0f else clamped * boost
        SettingsState.setVolume(app, clamped)
    }

    fun setMuted(muted: Boolean) {
        updateState { copy(muted = muted) }
        applyVolumeTo(player())
    }

    fun toggleMuted() = setMuted(!_state.value.muted)

    /** Select a specific HLS rendition; null = Auto. */
    fun selectQuality(option: QualityOption?) {
        val p = player() ?: return
        val params = p.trackSelectionParameters.buildUpon()
        if (option == null || option.trackIndex < 0) {
            params.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        } else {
            val group = p.currentTracks.groups.getOrNull(option.groupIndex) ?: return
            params.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            params.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
        }
        p.trackSelectionParameters = params.build()
        refreshTrackMenus(p.currentTracks)
    }

    /** Select an audio track line from the Audio menu. */
    fun selectAudioTrack(option: TrackOption) {
        val p = player() ?: return
        val group = p.currentTracks.groups.getOrNull(option.groupIndex) ?: return
        val params = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
        params.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
        p.trackSelectionParameters = params.build()
        refreshTrackMenus(p.currentTracks)
    }

    /** Select a subtitle language (site: Subtitles / CC menu). null = Off. */
    fun selectTextTrack(option: TrackOption?) {
        val p = player() ?: return
        val params = p.trackSelectionParameters.buildUpon()
        if (option == null || option.trackIndex < 0) {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            val group = p.currentTracks.groups.getOrNull(option.groupIndex) ?: return
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            params.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
        }
        p.trackSelectionParameters = params.build()
        val on = option != null && option.trackIndex >= 0
        updateState { copy(captionsOn = on, cues = if (on) cues else emptyList()) }
        refreshTrackMenus(p.currentTracks)
    }

    /** Cycle captions Off <-> preferred/default track (site: CC button). */
    fun toggleCaptions() {
        val current = _state.value.textTracks.firstOrNull { it.isSelected }
        if (current != null) {
            selectTextTrack(null)
        } else {
            val preferred = preferredSubtitleTrack()
            selectTextTrack(preferred)
        }
    }

    /** User's default subtitle language (Settings), else English, else first. */
    fun preferredSubtitleTrack(): TrackOption? {
        val tracks = _state.value.textTracks
        if (tracks.isEmpty()) return null
        val preferredLabel = SettingsState.defaultSubtitleLang
        return tracks.firstOrNull { it.label.contains(preferredLabel, ignoreCase = true) }
            ?: tracks.firstOrNull { it.label.contains("english", ignoreCase = true) }
            ?: tracks.firstOrNull()
    }

    /**
     * Skip past the opening/ending. Uses the episode's REAL skip timestamps
     * from the sources API when available; only falls back to the
     * configurable intro-skip duration when the episode has no metadata
     * (never a random fixed jump).
     */
    fun skipIntro() {
        val s = _state.value
        if (s.hasIntro) {
            seekTo(s.introEndMs)
        } else if (SettingsState.introSkipDuration > 0) {
            seekTo((SettingsState.introSkipDuration * 1000L).coerceAtMost(s.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE))
        }
    }

    fun skipOutro() {
        val s = _state.value
        if (s.hasOutro) {
            seekTo(s.outroEndMs)
        } else if (s.durationMs > 0) {
            seekTo(s.durationMs)
        }
    }

    fun nextEpisode() {
        val next = nextPlayableEpisode(_state.value.episode + 1)
        if (next != null) switchEpisode(next)
    }

    fun previousEpisode() {
        val prev = _state.value.episode - 1
        if (prev >= 1) switchEpisode(prev)
    }

    /**
     * [skipFillers] setting: autonext/next skips filler episodes.
     * [forceSkipFiller] (auto paths + autoSkipFiller setting) skips fillers
     * even when the regular skip-fillers toggle is off.
     */
    private fun nextPlayableEpisode(from: Int, forceSkipFiller: Boolean = false): Int? {
        val eps = _state.value.episodes
        val total = _state.value.totalEpisodes
        if (from > total) return null
        if (eps.isEmpty()) return from
        val skip = SettingsState.skipFillers || forceSkipFiller
        var n = from
        while (n <= total) {
            val item = eps.firstOrNull { it.number == n }
            if (item == null || !item.isFiller || !skip) return n
            n++
        }
        return null
    }

    /** Jump straight past a filler episode the user just landed on. */
    fun skipCurrentFiller() {
        val current = _state.value.episode
        val next = nextPlayableEpisode(current + 1) ?: return
        if (next > current) {
            updateState { copy(skippedFillerNotice = null) }
            switchEpisode(next)
        }
    }

    /**
     * Screenshot — TextureView bitmap (site: canvas capture). Returns the
     * bitmap or null when no frame is available.
     */
    fun captureScreenshot(): android.graphics.Bitmap? = runCatching {
        textureView?.bitmap
    }.getOrNull()

    /** Snapshot of live playback state — used for the leave-PiP decision. */
    fun isPlayingForPip(): Boolean = player?.isPlaying == true

    /** Enter PiP (button; also auto on leave when playing). */
    fun enterPictureInPicture() {
        val activity = app as? android.app.Activity
        if (activity == null || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        runCatching {
            activity.enterPictureInPictureMode(
                android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(android.util.Rational(16, 9))
                    .build(),
            )
        }
    }

    // -----------------------------------------------------------------------
    //  Data pipeline
    // -----------------------------------------------------------------------

    private var pipelineJob: Job? = null

    init {
        loadAnime()
    }

    /** Full load: details (deduped) -> slug -> resume-episode -> stream. */
    private fun loadAnime() {
        pipelineJob?.cancel()
        pipelineJob = viewModelScope.launch {
            AppLogger.d(LogCategory.PLAYER, "Loading anime details (id=$animeId)")
            val result = repo.animeDetails(animeId)
            result.fold(
                onSuccess = { details ->
                    val metaCount = details.episodes ?: details.nextAiringEpisode?.episode?.minus(1) ?: 1
                    var ep = if (initialEpisode > 0) initialEpisode else resumeEpisode()
                    ep = ep.coerceAtLeast(1)
                    if (initialEpisode <= 0 && ep > 1) {
                        AppLogger.i(LogCategory.PLAYER, "Continue watching: resuming at episode $ep")
                    }
                    _state.value = _state.value.copy(
                        loading = false,
                        details = details,
                        totalEpisodes = metaCount.coerceAtLeast(1),
                        episode = ep,
                        title = details.displayTitle(),
                    )
                    var slug = _state.value.slug ?: details.slug
                    if (slug == null) {
                        slug = repo.resolveSlug(
                            animeId,
                            details.title.english,
                            details.title.romaji,
                        )
                    } else {
                        AppLogger.d(LogCategory.PLAYER, "Using known slug '$slug' (no search needed)")
                    }
                    if (slug != null) {
                        if (_state.value.slug != slug) {
                            _state.value = _state.value.copy(slug = slug)
                        }
                        launch {
                            repo.anikageEpisodes(slug).onSuccess { eps ->
                                if (eps.isNotEmpty()) {
                                    val distinct = eps.distinctBy { it.number }
                                    _state.value = _state.value.copy(
                                        episodes = distinct.map {
                                            EpisodeItem(
                                                number = it.number,
                                                title = it.title?.takeIf { t -> t.isNotBlank() } ?: "Episode ${it.number}",
                                                thumbnail = it.image,
                                                isFiller = it.isFiller,
                                                isRecap = it.isRecap,
                                            )
                                        },
                                        totalEpisodes = distinct.size,
                                    )
                                    AppLogger.i(
                                        LogCategory.PLAYER,
                                        "Loaded '${details.displayTitle()}' — ${distinct.size} episodes listed " +
                                            "(metadata says ${details.episodes ?: "?"})",
                                    )
                                    // Auto-skip filler on arrival: when the
                                    // session auto-resumed INTO a filler and
                                    // the setting is on, jump to the next
                                    // canon episode (explicit picks are kept).
                                    if (SettingsState.autoSkipFiller && initialEpisode <= 0 &&
                                        _state.value.episode == ep
                                    ) {
                                        val landed = distinct.firstOrNull { it.number == ep }
                                        if (landed?.isFiller == true) {
                                            val next = distinct
                                                .firstOrNull { it.number > ep && !it.isFiller }
                                                ?.number
                                            if (next != null && next <= distinct.size) {
                                                AppLogger.i(
                                                    LogCategory.PLAYER,
                                                    "Auto-skip filler: episode $ep is filler -> jumping to $next",
                                                )
                                                updateState { copy(skippedFillerNotice = "Skipped filler episode $ep") }
                                                switchEpisode(next)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Per-episode watch progress for the episode list UI.
                    launch { loadEpisodeProgress() }
                    // Local anime list status.
                    launch {
                        val status = repo.getListStatus(animeId)
                        if (_state.value.listStatus != status) {
                            _state.value = _state.value.copy(listStatus = status)
                        }
                    }
                    if (initialLocalFile != null && File(initialLocalFile).exists()) {
                        playLocalFile(initialLocalFile, ep)
                    } else {
                        loadEpisode(ep)
                    }
                },
                onFailure = { e ->
                    // Offline + Room cache may still serve a downloaded copy.
                    if (initialLocalFile != null && File(initialLocalFile).exists()) {
                        _state.value = _state.value.copy(loading = false)
                        playLocalFile(initialLocalFile, _state.value.episode)
                    } else {
                        AppLogger.e(LogCategory.PLAYER, "Failed to load anime (id=$animeId)", e)
                        _state.value = _state.value.copy(
                            loading = false,
                            error = (e as? com.anikage.app.core.data.api.ApiHttpException)?.userMessage()
                                ?: e.message ?: "Failed to load anime.",
                        )
                    }
                },
            )
        }
    }

    /** Per-episode progress for the whole anime (episode list UI). */
    private suspend fun loadEpisodeProgress() {
        if (SettingsState.incognito || !SettingsState.rememberPosition) return
        runCatching {
            val all = repo.loadProgressForAnime(animeId)
            _state.value = _state.value.copy(
                episodeProgress = all.associate { it.episode to EpisodeProgress(it.positionMs, it.durationMs) },
            )
        }
    }

    /**
     * Auto-resume: the episode with the newest saved progress. A finished
     * episode (>= 95%) continues into the NEXT one — the site's behaviour.
     */
    private suspend fun resumeEpisode(): Int {
        if (SettingsState.incognito || !SettingsState.rememberPosition) return 1
        return runCatching {
            val all = repo.loadProgressForAnime(animeId)
            val latest = all.maxByOrNull { it.episode } ?: return 1
            if (latest.durationMs > 0 && latest.positionMs >= latest.durationMs * 95 / 100) {
                latest.episode + 1
            } else {
                latest.episode
            }
        }.getOrDefault(1)
    }

    /** Load ONLY the per-episode bits: stream sources, view count, comments. */
    private fun loadEpisode(episode: Int, refresh: Boolean = false) {
        val gen = episodeGen.incrementAndGet()
        episodeJob?.cancel()
        checkedVideoTrackForItem = false
        cancelNextUp()
        episodeJob = viewModelScope.launch {
            if (!isActive) return@launch
            _state.value = _state.value.copy(
                streamLoading = true,
                streamError = null,
                playbackError = null,
                viewCounted = false,
                cues = emptyList(),
                localFile = null,
            )
            viewSent.set(false)
            if (!autoFallingBack) failedProviders.clear()
            autoFallingBack = false
            val attempt = failedProviders.size + 1
            val slug = _state.value.slug
            val lang = _state.value.streamLang
            val server = _state.value.streamServer
            val activeEmbed = _state.value.embedActive
            var streamUrl: String? = null
            var subtitles: List<SubtitleTrack> = emptyList()
            var failure: String? = null

            if (activeEmbed != null) {
                // E-server mode: resolve the embed URL for this episode.
                _state.value = _state.value.copy(embedLoading = true, embedError = null)
                if (slug == null) {
                    _state.value = _state.value.copy(embedLoading = false, embedError = "No Anikage slug for this anime.")
                } else {
                    repo.anikageEmbedSources(slug, episode, activeEmbed.key, lang)
                        .onSuccess { response ->
                            val url = response.embedOptions.firstOrNull { it.key == activeEmbed.key && it.url != null }?.url
                                ?: response.embeds.firstOrNull { it.status == "ok" }?.url
                            if (url != null) {
                                _state.value = _state.value.copy(embedUrl = url, embedLoading = false)
                                AppLogger.i(LogCategory.PLAYER, "Embed ready: $url")
                            } else {
                                _state.value = _state.value.copy(
                                    embedLoading = false,
                                    embedError = "This embed server has no source for episode $episode.",
                                )
                            }
                        }
                        .onFailure { e ->
                            _state.value = _state.value.copy(
                                embedLoading = false,
                                embedError = "Embed failed: ${e.message ?: "network error"}. Try another E-server.",
                            )
                        }
                }
            } else if (slug != null) {
                repo.anikageSources(slug, episode, server.lowercase(), lang, refresh)
                    .onSuccess { response ->
                        if (episodeGen.get() != gen) return@onSuccess // stale
                        // ── source selection: prefer softsub m3u8, then any m3u8,
                        // then anything left (site: softsub is the default pick).
                        val best = response.sources
                            .filter { it.isM3U8 && (lang == "dub" || it.type != "hardsub") }
                            .firstOrNull()
                            ?: response.sources.firstOrNull { it.isM3U8 }
                            ?: response.sources.firstOrNull()
                        streamUrl = best?.url?.let { token ->
                            if (token.startsWith("http")) token
                            else "${Config.ANIKAGE_STREAM_PROXY_BASE_URL ?: "https://og.bakayaro.live"}/m3u8/$token"
                        }
                        subtitles = response.subtitles.map { sub ->
                            SubtitleTrack(
                                label = sub.label ?: "",
                                url = sub.file?.let { t ->
                                    if (t.startsWith("http")) t
                                    else "${Config.ANIKAGE_STREAM_PROXY_BASE_URL ?: "https://og.bakayaro.live"}/stream/$t"
                                } ?: "",
                                language = sub.label ?: "",
                                isDefault = sub.default,
                            )
                        }.filter { it.url.isNotBlank() }
                        // Skip timestamps (site: Skip Opening / Skip Ending).
                        val intro = response.intro
                        val outro = response.outro
                        _state.value = _state.value.copy(
                            introStartMs = (intro?.start ?: -1.0).toLong(),
                            introEndMs = (intro?.end ?: -1.0).toLong(),
                            outroStartMs = (outro?.start ?: -1.0).toLong(),
                            outroEndMs = (outro?.end ?: -1.0).toLong(),
                        )
                        if (streamUrl == null) {
                            val langLabel = if (lang == "dub") "dub" else "sub"
                            failure = "No $langLabel source on $server for this episode. " +
                                "Try the other language or another server."
                            AppLogger.w(LogCategory.PLAYER, "No stream sources for $slug ep $episode ($server/$lang)")
                        } else {
                            val urlKind = if (best!!.isM3U8) "HLS(m3u8)" else (best.type ?: "file")
                            val host = runCatching { java.net.URI(streamUrl).host }.getOrDefault("?")
                            AppLogger.i(
                                LogCategory.PLAYER,
                                "PLAYBACK ATTEMPT #$attempt\n" +
                                    "  anime id=$animeId slug=$slug ep=$episode provider=$server lang=$lang\n" +
                                    "  sources=${response.sources.size} (m3u8×${response.sources.count { it.isM3U8 }}) " +
                                    "subtitles=${subtitles.size} selected=${best.quality ?: "-"}/${best.type ?: "-"}\n" +
                                    "  url=$host [$urlKind] serverCache=${response.cached}",
                            )
                        }
                    }
                    .onFailure { e ->
                        if (episodeGen.get() != gen) return@onFailure // stale
                        AppLogger.w(LogCategory.PLAYER, "Sources failed for $slug ep $episode ($server/$lang)", e)
                        failure = "$server couldn't provide this episode (${e.message ?: "network error"}). " +
                            "Trying the next server…"
                    }
            } else {
                AppLogger.w(LogCategory.PLAYER, "No Anikage slug — stream unavailable")
                failure = "This anime isn't in the Anikage catalogue, so no stream is available."
            }

            if (episodeGen.get() != gen) return@launch // user moved on — drop stale result

            val saved = if (!SettingsState.incognito && SettingsState.rememberPosition) {
                repo.loadProgress(animeId, episode)?.positionMs ?: 0L
            } else 0L

            _state.value = _state.value.copy(
                streamUrl = if (activeEmbed != null) null else streamUrl,
                subtitles = subtitles,
                savedPositionMs = saved,
                streamLoading = false,
                streamError = failure,
            )

            // ── automatic provider fallback when resolution yielded nothing ──
            if (streamUrl == null && activeEmbed == null && slug != null && failure != null &&
                !failure!!.contains("isn't in the Anikage catalogue") &&
                maybeAutoFallback(null, gen, failure)
            ) {
                return@launch // fallback reload owns the episode now
            } else if (streamUrl != null && activeEmbed == null) {
                preparePlayer(episode, gen)
            }

            if (slug != null) {
                launch { repo.anikageViews(slug, episode)?.let { vc ->
                    if (episodeGen.get() == gen) {
                        _state.value = _state.value.copy(viewCount = vc)
                    }
                } }
                launch {
                    repo.anikageServersResponse(slug, episode).getOrNull()?.let { raw ->
                        if (episodeGen.get() == gen) {
                            buildServerPanels(raw.servers, raw.embeds, _state.value.streamServer, lang)
                        }
                    }
                }
            }
            loadComments(episode)
            refreshDownloadedForEpisode(episode)

            if (!SettingsState.incognito) {
                repo.markRecentlyViewed(
                    com.anikage.app.core.data.model.Anime(
                        id = animeId,
                        slug = _state.value.slug,
                        title = _state.value.details?.title ?: com.anikage.app.core.data.model.AnimeTitle(),
                        coverImage = _state.value.details?.coverImage
                            ?: com.anikage.app.core.data.model.CoverImage(),
                    ),
                    episode,
                )
            }
        }
    }

    private fun buildServerPanels(
        raw: List<AnikageServer>,
        embeds: List<com.anikage.app.core.data.api.AnikageEmbedRef>,
        server: String,
        lang: String,
    ) {
        if (raw.isEmpty() && embeds.isEmpty()) return
        val models = raw.map { s ->
            StreamServer(
                id = s.providerId,
                name = displayServerName(s.providerId),
                supportsSub = s.subTypes.isEmpty() || s.subTypes.contains("sub"),
                supportsDub = s.subTypes.contains("dub"),
                isDefault = s.default,
            )
        }.distinctBy { it.id }
        val embedModels = embeds.mapNotNull { e ->
            val key = e.key ?: e.id ?: return@mapNotNull null
            EmbedServer(key = key, label = e.label ?: displayServerName(key))
        }.distinctBy { it.key }
        val currentValid = models.any {
            it.id.equals(server, ignoreCase = true) &&
                (if (lang == "dub") it.supportsDub else it.supportsSub)
        }
        val fallback = models.firstOrNull {
            if (lang == "dub") it.supportsDub else it.supportsSub
        }
        _state.value = _state.value.copy(
            servers = models,
            embedServers = embedModels,
            streamServer = if (currentValid) server else (fallback?.name ?: server),
        )
    }

    /** Provider id → display name (site shows Koto / Kiwi / Neko / Zen …). */
    private fun displayServerName(providerId: String): String =
        providerId.replaceFirstChar { it.uppercase() }

    /**
     * Set the media item (with the EXPLICIT content type + subtitle tracks)
     * and start it.
     *
     * MIME TYPE IS THE ROOT-CAUSE FIX for ERROR_CODE_PARSING_CONTAINER_
     * UNSUPPORTED: the stream proxy URL (og.bakayaro.live/m3u8/{token}) has
     * NO file extension, so without an explicit MIME type ExoPlayer's
     * DefaultMediaSourceFactory infers CONTENT_TYPE_OTHER and builds a
     * ProgressiveMediaSource (extractor sniffing) — which cannot parse an
     * HLS manifest and fails with a container-parsing "Source error".
     * Declaring APPLICATION_M3U8 routes the item to the HLS media source.
     */
    private fun preparePlayer(episode: Int, gen: Int) {
        val url = _state.value.streamUrl ?: return
        if (episodeGen.get() != gen) return // stale — a newer load owns the player
        val player = player()

        // Belt & suspenders: the surface MUST be attached for this item —
        // covers every attach/player ordering (the black-video fix).
        textureView?.let { view ->
            player.setVideoTextureView(view)
        }

        val mime = when {
            url.startsWith("file:") || url.startsWith("/") -> localFileMime(url)
            url.contains(".mpd") -> MimeTypes.APPLICATION_MPD
            url.contains(".m3u8") || url.contains("/m3u8/") -> MimeTypes.APPLICATION_M3U8
            url.contains(".mp4") -> MimeTypes.VIDEO_MP4
            url.contains(".webm") -> MimeTypes.VIDEO_WEBM
            url.contains(".mkv") -> MimeTypes.VIDEO_MATROSKA
            url.endsWith(".ts") -> MimeTypes.VIDEO_MP2T
            else -> MimeTypes.APPLICATION_M3U8 // Anikage tokens are HLS by default
        }
        AppLogger.d(
            LogCategory.PLAYER,
            "Preparing ExoPlayer: mime=$mime pos=${_state.value.savedPositionMs}ms " +
                "surface=${if (textureView != null) "attached" else "pending-attach"}",
        )

        val builder = MediaItem.Builder()
            .setUri(url)
            .setMimeType(mime)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("${_state.value.title} — Episode $episode")
                    .build(),
            )
        // Side-load subtitle tracks (site's softsub VTT files via the proxy).
        // TEXT_VTT is explicit because the proxy mislabels subtitle responses
        // (content-type: image/jpeg) — inference would fail.
        if (_state.value.subtitles.isNotEmpty()) {
            builder.setSubtitleConfigurations(
                _state.value.subtitles.map { sub ->
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage(sub.language)
                        .setLabel(sub.label)
                        .setSelectionFlags(
                            if (sub.isDefault) C.SELECTION_FLAG_DEFAULT else 0,
                        )
                        .build()
                },
            )
        }
        player.setMediaItem(builder.build(), _state.value.savedPositionMs)

        // Quality cap (site: streamQuality setting) via track selection.
        runCatching {
            val quality = SettingsState.streamQuality
            val params = player.trackSelectionParameters.buildUpon()
            when (quality) {
                "low" -> params.setMaxVideoSize(480, 854)
                "standard" -> params.setMaxVideoSize(720, 1280)
                "full" -> params.setMaxVideoSize(1080, 1920)
                else -> params /* auto: no cap */
            }
            player.trackSelectionParameters = params.build()
        }
        if (_state.value.savedPositionMs > 0L) {
            AppLogger.d(LogCategory.PLAYER, "Resuming from saved position ${_state.value.savedPositionMs / 1000}s")
        }
        player.setPlaybackSpeed(SettingsState.playbackRate)
        applyVolumeTo(player)
        updateState { copy(speed = SettingsState.playbackRate, positionMs = savedPositionMs) }
        player.prepare()
        checkedVideoTrackForItem = false
        refreshTrackMenus(player.currentTracks)
        if (SettingsState.autoplay) player.play()
    }

    private fun localFileMime(path: String): String = when {
        path.endsWith(".ts") -> MimeTypes.VIDEO_MP2T
        path.endsWith(".mp4") || path.endsWith(".m4v") -> MimeTypes.VIDEO_MP4
        path.endsWith(".webm") -> MimeTypes.VIDEO_WEBM
        path.endsWith(".mkv") -> MimeTypes.VIDEO_MATROSKA
        path.endsWith(".mp3") -> MimeTypes.AUDIO_MPEG
        else -> MimeTypes.VIDEO_MP2T
    }

    /** Build the Quality / Audio / Subtitles menus from the current tracks. */
    private fun refreshTrackMenus(tracks: androidx.media3.common.Tracks) {
        val qualities = mutableListOf<QualityOption>()
        val audio = mutableListOf<TrackOption>()
        val text = mutableListOf<TrackOption>()
        val videoOverride = player?.trackSelectionParameters?.overrides
            ?.values?.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }
        val videoGroupIndex = tracks.groups.indexOfFirst {
            it.type == C.TRACK_TYPE_VIDEO && it.length > 0
        }
        if (videoGroupIndex >= 0) {
            val vg = tracks.groups[videoGroupIndex]
            for (ti in 0 until vg.length) {
                val format = vg.getTrackFormat(ti)
                if (format.height > 0 && vg.isTrackSupported(ti)) {
                    qualities += QualityOption(
                        label = "${format.height}p",
                        height = format.height,
                        groupIndex = videoGroupIndex,
                        trackIndex = ti,
                        isSelected = videoOverride?.trackIndices?.contains(ti) == true,
                    )
                }
            }
        }
        // Auto (adaptive) is active whenever no explicit video override exists.
        if (qualities.isNotEmpty()) {
            qualities += QualityOption("Auto", 0, -1, -1, isSelected = videoOverride == null)
        }
        tracks.groups.forEachIndexed { gi, group ->
            when (group.type) {
                C.TRACK_TYPE_AUDIO -> {
                    for (ti in 0 until group.length) {
                        if (!group.isTrackSupported(ti)) continue
                        val format = group.getTrackFormat(ti)
                        audio += TrackOption(
                            label = format.label ?: format.language ?: format.id ?: "Audio ${audio.size + 1}",
                            groupIndex = gi,
                            trackIndex = ti,
                            isSelected = group.isTrackSelected(ti),
                        )
                    }
                }
                C.TRACK_TYPE_TEXT -> {
                    for (ti in 0 until group.length) {
                        if (!group.isTrackSupported(ti)) continue
                        val format = group.getTrackFormat(ti)
                        text += TrackOption(
                            label = format.label ?: format.language ?: "Subtitle ${text.size + 1}",
                            groupIndex = gi,
                            trackIndex = ti,
                            isSelected = group.isTrackSelected(ti),
                        )
                    }
                }
            }
        }
        qualities.sortByDescending { it.height }
        updateState {
            copy(
                qualities = qualities,
                audioTracks = if (audio.size > 1) audio else emptyList(),
                textTracks = text,
                captionsOn = text.any { it.isSelected },
            )
        }
    }

    // -----------------------------------------------------------------------
    //  Position ticker — progress, autoskip, view counting, next-up countdown
    // -----------------------------------------------------------------------

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            var lastSave = 0L
            while (isActive) {
                val p = player ?: break
                if (p.isPlaying || _state.value.nextUp != null) {
                    val pos = p.currentPosition
                    val dur = if (p.duration > 0) p.duration else 0L
                    updateState {
                        copy(
                            positionMs = pos,
                            bufferedMs = p.bufferedPosition,
                            durationMs = if (dur > 0) dur else durationMs,
                        )
                    }
                    // Live episode-list progress for the current episode.
                    if (dur > 0 && pos > 0) {
                        updateState {
                            copy(
                                episodeProgress = episodeProgress +
                                    (episode to EpisodeProgress(pos, dur)),
                            )
                        }
                    }
                    // Autoskip intro/outro (site: autoskip setting) — REAL
                    // timestamps from the sources response only.
                    if (SettingsState.autoskip && p.isPlaying) {
                        val s = _state.value
                        if (s.hasIntro && pos >= s.introStartMs && pos < s.introEndMs) {
                            AppLogger.d(LogCategory.PLAYER, "Autoskip intro -> ${s.introEndMs / 1000}s")
                            seekTo(s.introEndMs)
                        } else if (s.hasOutro && pos >= s.outroStartMs && pos < s.outroEndMs) {
                            AppLogger.d(LogCategory.PLAYER, "Autoskip outro -> ${s.outroEndMs / 1000}s")
                            seekTo(s.outroEndMs)
                        }
                    }
                    // Periodic progress save (site: watch progress auto-sync).
                    if (!SettingsState.incognito && SettingsState.rememberPosition &&
                        dur > 0 && pos - lastSave > 5_000L
                    ) {
                        lastSave = pos
                        repo.saveProgress(animeId, _state.value.episode, pos, dur)
                    }
                    // View counting once >15s watched (site: POST /view).
                    if (!viewSent.get() && pos > 15_000L) {
                        viewSent.set(true)
                        val slug = _state.value.slug
                        if (slug != null && !SettingsState.incognito) {
                            launch {
                                if (repo.anikageRecordView(slug, _state.value.episode, animeId)) {
                                    updateState { copy(viewCounted = true, viewCount = (viewCount ?: 0L) + 1L) }
                                }
                            }
                        } else {
                            updateState { copy(viewCounted = true) }
                        }
                    }
                    // Auto-next countdown.
                    val n = _state.value.nextUp
                    if (n != null) {
                        if (n.countdownSec <= 1) {
                            playNextNow()
                        } else {
                            updateState { copy(nextUp = n.copy(countdownSec = n.countdownSec - 1)) }
                        }
                        delay(1000)
                        continue
                    }
                }
                delay(250)
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Episode / server / lang switching
    // -----------------------------------------------------------------------

    /**
     * Switch episode WITHOUT refetching anime details. Cancels any in-flight
     * episode load first — only ONE active playback session per player.
     */
    fun switchEpisode(episode: Int) {
        val ep = episode.coerceIn(1, _state.value.totalEpisodes)
        if (ep == _state.value.episode) {
            // Tapping the current episode row with a failed stream = retry.
            if (_state.value.streamError != null || _state.value.playbackError != null) {
                reloadStream(refresh = true)
            }
            return
        }
        viewModelScope.launch { saveProgressNow() }
        episodeJob?.cancel()
        _state.value = _state.value.copy(
            episode = ep,
            savedPositionMs = 0L,
            viewCount = null,
            positionMs = 0L,
            bufferedMs = 0L,
            embedUrl = null,
            embedError = null,
            localFile = null,
            nextUp = null,
        )
        viewSent.set(false)
        loadEpisode(ep)
    }

    /** Site's SUB/DUB toggle — reloads the current episode's stream. */
    fun setStreamLang(lang: String) {
        if (_state.value.streamLang == lang) return
        SettingsState.setStreamLang(app, lang)
        episodeJob?.cancel()
        failedProviders.clear()
        _state.value = _state.value.copy(
            streamLang = lang,
            streamUrl = null,
            playbackError = null,
            embedUrl = null,
            embedError = null,
            localFile = null,
        )
        loadEpisode(_state.value.episode)
    }

    /** Site's server-chip switch — reloads the current episode's stream. */
    fun setStreamServer(server: String) {
        if (_state.value.streamServer == server && !autoFallingBack) return
        episodeJob?.cancel()
        failedProviders.clear() // user's explicit pick gets a fresh chance
        _state.value = _state.value.copy(
            streamServer = server,
            streamUrl = null,
            playbackError = null,
            embedUrl = null,
            embedError = null,
            localFile = null,
        )
        loadEpisode(_state.value.episode)
    }

    /** Site's E-server chip — switches to the WebView embed player. */
    fun setEmbedServer(embed: EmbedServer?) {
        if (embed == null) {
            episodeJob?.cancel()
            failedProviders.clear()
            _state.value = _state.value.copy(
                embedActive = null,
                embedUrl = null,
                embedError = null,
                embedLoading = false,
                streamUrl = null,
                playbackError = null,
                localFile = null,
            )
            loadEpisode(_state.value.episode)
            return
        }
        if (_state.value.embedActive?.key == embed.key) return
        saveProgressNow()
        pause()
        episodeJob?.cancel()
        failedProviders.clear()
        _state.value = _state.value.copy(
            embedActive = embed,
            embedUrl = null,
            embedError = null,
            streamUrl = null,
            streamError = null,
            playbackError = null,
            localFile = null,
        )
        loadEpisode(_state.value.episode)
    }

    /**
     * Re-resolve the stream for the current episode — the site's refresh
     * button (fresh token).
     */
    fun reloadStream(refresh: Boolean = false) {
        episodeJob?.cancel()
        failedProviders.clear()
        _state.value = _state.value.copy(
            streamUrl = null,
            playbackError = null,
            streamError = null,
            embedUrl = null,
            embedError = null,
            cues = emptyList(),
            localFile = null,
        )
        loadEpisode(_state.value.episode, refresh = refresh)
    }

    /** Reload comments for the current episode (pull-to-refresh in the panel). */
    fun refreshComments() {
        loadComments(_state.value.episode)
    }

    private fun loadComments(episode: Int) {
        if (!SettingsState.commentsEnabled) return
        val gen = episodeGen.get()
        viewModelScope.launch {
            _state.value = _state.value.copy(
                comments = _state.value.comments.copy(loading = true),
            )
            val result = repo.anikageComments(animeId, episode)
            if (episodeGen.get() != gen) return@launch // stale
            result.fold(
                onSuccess = { comments ->
                    _state.value = _state.value.copy(
                        comments = CommentsUiState(
                            loading = false,
                            comments = comments,
                            total = comments.size,
                        ),
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        comments = _state.value.comments.copy(loading = false),
                    )
                },
            )
        }
    }

    // -----------------------------------------------------------------------
    //  In-app downloads (EpisodeDownloadEngine bridge)
    // -----------------------------------------------------------------------

    /** Resolve the downloadable renditions for the current episode (dialog). */
    fun resolveDownloadQualities() {
        val slug = _state.value.slug ?: run {
            updateState { copy(downloadQualitiesError = "No Anikage slug for this anime.") }
            return
        }
        updateState { copy(downloadQualitiesLoading = true, downloadQualitiesError = null, downloadQualities = emptyList()) }
        viewModelScope.launch {
            val result = EpisodeDownloadEngine.resolveVariantList(
                app, slug, _state.value.episode, _state.value.streamServer.lowercase(), _state.value.streamLang,
            )
            if (result.isSuccess) {
                val list = result.getOrDefault(emptyList())
                val options = list.map { DownloadQualityOption(it.label, it.height) }
                val capped = when (SettingsState.streamQuality) {
                    "low" -> options.filter { it.height <= 480 || it.height == 0 }
                    "standard" -> options.filter { it.height <= 720 || it.height == 0 }
                    "full" -> options.filter { it.height <= 1080 || it.height == 0 }
                    else -> options
                }
                updateState {
                    copy(
                        downloadQualities = (capped.ifEmpty { options }).ifEmpty { listOf(DownloadQualityOption("Auto", 0)) },
                        downloadQualitiesLoading = false,
                    )
                }
            } else {
                updateState {
                    copy(
                        downloadQualitiesLoading = false,
                        downloadQualitiesError = result.exceptionOrNull()?.message ?: "Couldn't resolve download qualities.",
                    )
                }
            }
        }
    }

    /** Start an in-app download of the current episode at [height] (0 = auto). */
    fun startDownload(height: Int) {
        val slug = _state.value.slug ?: return
        val d = _state.value.details
        EpisodeDownloadEngine.enqueue(
            app,
            EpisodeDownloadEngine.DownloadRequest(
                animeId = animeId,
                slug = slug,
                episode = _state.value.episode,
                provider = _state.value.streamServer,
                lang = _state.value.streamLang,
                height = height,
                titleRomaji = d?.title?.romaji,
                titleEnglish = d?.title?.english,
                episodeTitle = _state.value.currentEpisodeItem?.title,
                posterUrl = d?.coverImage?.best(),
            ),
        )
    }

    /** Load the completed download row for an episode into state. */
    fun refreshDownloadedForEpisode(episode: Int) {
        viewModelScope.launch {
            runCatching {
                val row = repo.downloadedEpisode(animeId, episode)
                if (_state.value.episode == episode) {
                    _state.value = _state.value.copy(downloaded = row)
                }
            }
        }
    }

    /** Switch the player to the downloaded copy of the current episode. */
    fun playDownloadedCopy() {
        val row = _state.value.downloaded ?: return
        if (!File(row.filePath).exists()) {
            updateState { copy(downloaded = null) }
            refreshDownloadedForEpisode(row.episode)
            return
        }
        viewModelScope.launch {
            saveProgressNow()
            pause()
            episodeJob?.cancel()
            val gen = episodeGen.incrementAndGet()
            _state.value = _state.value.copy(
                episode = row.episode,
                localFile = row.filePath,
                streamUrl = File(row.filePath).toURI().toString(),
                streamError = null,
                playbackError = null,
                nextUp = null,
                savedPositionMs = 0L,
            )
            prepareDownloadedPlayer(gen, row)
        }
    }

    /** Leave offline mode — go back to the live stream for this episode. */
    fun playStreamVersion() {
        _state.value = _state.value.copy(localFile = null)
        loadEpisode(_state.value.episode)
    }

    /** Initial route entry: play a downloaded file for this anime/episode. */
    private fun playLocalFile(path: String, episode: Int) {
        viewModelScope.launch {
            runCatching {
                val row = repo.downloadedEpisode(animeId, episode)
                val gen = episodeGen.incrementAndGet()
                checkedVideoTrackForItem = true
                val file = File(path)
                _state.value = _state.value.copy(
                    loading = false,
                    episode = episode,
                    localFile = path,
                    streamUrl = file.toURI().toString(),
                    streamError = null,
                    streamLoading = false,
                    downloaded = row,
                )
                prepareDownloadedPlayer(gen, row, path)
                loadComments(episode)
                loadEpisodeProgress()
            }
        }
    }

    /** Prepare the player for a downloaded (local) file + its sidecar VTT. */
    private suspend fun prepareDownloadedPlayer(gen: Int, row: DownloadedEpisodeEntity?, explicitPath: String? = null) {
        val path = explicitPath ?: row?.filePath ?: return
        val player = player()
        textureView?.let { player.setVideoTextureView(it) }
        val file = File(path)
        val builder = MediaItem.Builder()
            .setUri(file.toURI().toString())
            .setMimeType(localFileMime(path))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("${_state.value.title} — Episode ${_state.value.episode}")
                    .build(),
            )
        val vtt = row?.subtitlePath?.let { File(it) }?.takeIf { it.exists() }
        if (vtt != null) {
            builder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.fromFile(vtt))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLabel("English")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build(),
                ),
            )
        }
        val saved = if (!SettingsState.incognito && SettingsState.rememberPosition) {
            repo.loadProgress(animeId, _state.value.episode)?.positionMs ?: 0L
        } else 0L
        player.setMediaItem(builder.build(), saved)
        player.setPlaybackSpeed(SettingsState.playbackRate)
        applyVolumeTo(player)
        updateState { copy(speed = SettingsState.playbackRate) }
        player.prepare()
        if (SettingsState.autoplay) player.play()
        AppLogger.i(LogCategory.PLAYER, "Playing downloaded copy: ${file.name} (${file.length() / 1_000_000}MB)")
    }

    // -----------------------------------------------------------------------
    //  Local anime list
    // -----------------------------------------------------------------------

    /** Set / clear this anime's local list status (null removes it). */
    fun setListStatus(status: String?) {
        if (SettingsState.incognito) return
        val d = _state.value.details
        viewModelScope.launch {
            repo.setListStatus(
                animeId, status,
                titleRomaji = d?.title?.romaji,
                titleEnglish = d?.title?.english,
                posterUrl = d?.coverImage?.best(),
                coverColor = d?.coverImage?.color,
            )
            updateState { copy(listStatus = status) }
        }
    }

    // -----------------------------------------------------------------------
    //  Progress persistence
    // -----------------------------------------------------------------------

    /** Save current position so we can resume later. */
    fun saveProgress(positionMs: Long, durationMs: Long) {
        viewModelScope.launch {
            repo.saveProgress(animeId, _state.value.episode, positionMs, durationMs)
        }
    }

    fun saveProgressNow() {
        val p = player ?: return
        if (SettingsState.incognito || !SettingsState.rememberPosition) return
        val position = p.currentPosition
        val duration = p.duration.coerceAtLeast(0L)
        if (duration > 0 && position > 0) {
            viewModelScope.launch {
                repo.saveProgress(animeId, _state.value.episode, position, duration)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        episodeJob?.cancel()
        pipelineJob?.cancel()
        tickerJob?.cancel()
        // Last-resort fire-and-forget save (the screen's onDispose + the 5s
        // ticker already cover the normal paths; viewModelScope is cancelled
        // before onCleared, so a GlobalScope write is the only reliable way).
        val p = player
        if (p != null && !SettingsState.incognito && SettingsState.rememberPosition) {
            val pos = p.currentPosition
            val dur = p.duration.coerceAtLeast(0L)
            if (dur > 0 && pos > 0) {
                @Suppress("DEPRECATION")
                kotlinx.coroutines.GlobalScope.launch {
                    repo.saveProgress(animeId, _state.value.episode, pos, dur)
                }
            }
        }
        p?.removeListener(playerListener)
        p?.release()
        player = null
    }

    private inline fun updateState(block: WatchUiState.() -> WatchUiState) {
        _state.value = _state.value.block()
    }

    companion object {
        fun factory(
            app: Application,
            repo: AnikageRepository,
            animeId: Int,
            episode: Int,
            slug: String? = null,
            localFile: String? = null,
        ) = viewModelFactory {
            initializer { WatchViewModel(app, repo, animeId, episode, slug, localFile) }
        }
    }
}

/** Map ExoPlayer errors to honest, actionable messages (site taxonomy). */
fun friendlyPlaybackError(error: PlaybackException): String {
    val code = error.errorCode
    val cause = error.cause
    val http = (cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)
    return when {
        http != null && http.responseCode == 403 ->
            "The stream host blocked this request (403). Switching servers or reloading usually clears it."
        http != null && http.responseCode == 429 ->
            "The stream host is rate limiting (429). Wait a few seconds, then retry."
        http != null && http.responseCode in 500..599 ->
            "The stream host had a server error (${http.responseCode}). Try another server."
        code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Network problem while streaming. Check your connection and retry."
        code == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ||
            code == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
            code == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ||
            code == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
            "This stream's format couldn't be parsed. Another server will usually work."
        else -> "Playback failed (${error.errorCodeName}). Try another server or reload the stream."
    }
}
