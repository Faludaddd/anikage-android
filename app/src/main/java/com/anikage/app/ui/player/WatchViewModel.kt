package com.anikage.app.ui.player

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.anikage.app.Config
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.api.AnikageComment
import com.anikage.app.core.data.api.AnikageServer
import com.anikage.app.core.data.model.AnimeDetails
import com.anikage.app.core.log.AppLogger
import com.anikage.app.core.log.LogCategory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One episode with real metadata (from the Anikage episodes API). */
data class EpisodeItem(
    val number: Int,
    val title: String = "Episode $number",
    val thumbnail: String? = null,
    val isFiller: Boolean = false,
    val isRecap: Boolean = false,
)

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
) {
    /** Servers that can serve the current SUB/DUB selection. */
    val availableServers: List<StreamServer>
        get() = servers.filter {
            if (streamLang == "dub") it.supportsDub else it.supportsSub
        }
}

/**
 * Watch screen data + ExoPlayer wiring.
 *
 * Streaming flow (mirrors anikage.cc exactly):
 *   1. details (AniList id, deduped) — OR carried-in Anikage slug from the
 *      originating screen (home/browse/schedule payloads already have it).
 *   2. slug — used directly when known; only resolved by title-search as a
 *      fallback (this was the reliability bug: searches can miss).
 *   3. episodes -> {slug}/episodes (titles/thumbs/filler).
 *   4. servers -> {slug}/episodes/{n}/servers (koto/kiwi/neko/zen + subTypes).
 *   5. sources -> {slug}/episodes/{n}/sources?provider&lang -> HLS tokens.
 *   6. token -> {PROXY}/m3u8/{token}, played with Referer/Origin headers.
 *
 * Every failure is surfaced (streamError / playbackError) so the user can
 * retry or switch servers — never a silent black player.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class WatchViewModel(
    private val app: Application,
    private val repo: AnikageRepository,
    private val animeId: Int,
    initialEpisode: Int,
    initialSlug: String? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(
        WatchUiState(loading = true, episode = initialEpisode, slug = initialSlug?.takeIf { it.isNotBlank() }),
    )
    val state: StateFlow<WatchUiState> = _state.asStateFlow()

    private var player: ExoPlayer? = null

    /**
     * ExoPlayer with a DataSource factory that attaches the headers the
     * Anikage stream host (og.bakayaro.live) demands: Referer + Origin of
     * anikage.cc and a browser-like User-Agent. Without these the HLS
     * playlist returns 403 "forbidden origin".
     */
    fun player(): ExoPlayer = player ?: buildPlayer().also {
        player = it
        it.repeatMode = Player.REPEAT_MODE_OFF
        // Settings-gated playback behaviour (site: autoplay/autonext/volume).
        it.playWhenReady = com.anikage.app.core.settings.SettingsState.autoplay
        it.volume = com.anikage.app.core.settings.SettingsState.volume *
            if (com.anikage.app.core.settings.SettingsState.muted) 0f else 1f
        // Autonext — site: auto-play the next episode when this one ends.
        // In-player failures surface as playbackError (retry / switch server).
        it.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED &&
                    com.anikage.app.core.settings.SettingsState.autonext
                ) {
                    val next = _state.value.episode + 1
                    if (next <= _state.value.totalEpisodes) {
                        AppLogger.i(LogCategory.PLAYER, "Autonext -> episode $next")
                        switchEpisode(next)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                AppLogger.e(LogCategory.PLAYER, "Playback error (${error.errorCodeName})", error)
                _state.value = _state.value.copy(
                    playbackError = "Playback failed (${error.errorCodeName}). " +
                        "Try another server or reload the stream.",
                )
            }
        })
    }

    private fun buildPlayer(): ExoPlayer {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(Config.Network.USER_AGENT)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "${Config.ANIKAGE_SITE_ORIGIN}/",
                    "Origin" to Config.ANIKAGE_SITE_ORIGIN,
                )
            )
            .setConnectTimeoutMs(Config.Network.CONNECT_TIMEOUT * 1000)
            .setReadTimeoutMs(Config.Network.READ_TIMEOUT * 1000)
            .setAllowCrossProtocolRedirects(true)
        return ExoPlayer.Builder(app)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
    }

    private var pipelineJob: Job? = null

    init {
        loadAnime()
    }

    /** Full load: details (deduped) -> slug -> episodes -> stream + comments. */
    private fun loadAnime() {
        pipelineJob?.cancel()
        pipelineJob = viewModelScope.launch {
            AppLogger.d(LogCategory.PLAYER, "Loading anime details (id=$animeId)")
            val result = repo.animeDetails(animeId)
            result.fold(
                onSuccess = { details ->
                    AppLogger.i(
                        LogCategory.PLAYER,
                        "Loaded '${details.displayTitle()}' (${details.episodes ?: '?'} eps)",
                    )
                    val total = details.episodes
                        ?: (details.nextAiringEpisode?.episode?.minus(1))
                        ?: 1
                    val ep = _state.value.episode.coerceIn(1, total.coerceAtLeast(1))
                    _state.value = _state.value.copy(
                        loading = false,
                        details = details,
                        totalEpisodes = total.coerceAtLeast(1),
                        episode = ep,
                        title = details.displayTitle(),
                    )
                    // Slug: prefer the one carried in from the originating
                    // screen, then the one embedded in the details payload
                    // (Anikage info path); resolve by title search only as
                    // the last resort.
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
                                    // Dedupe by number — lazy-list keys must be unique.
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
                                        totalEpisodes = maxOf(total, distinct.size),
                                    )
                                }
                            }
                        }
                    }
                    loadEpisode(ep)
                },
                onFailure = { e ->
                    AppLogger.e(LogCategory.PLAYER, "Failed to load anime (id=$animeId)", e)
                    _state.value = _state.value.copy(
                        loading = false,
                        error = (e as? com.anikage.app.core.data.api.ApiHttpException)?.userMessage()
                            ?: e.message ?: "Failed to load anime.",
                    )
                }
            )
        }
    }

    /** Load ONLY the per-episode bits: stream sources, view count, comments. */
    private fun loadEpisode(episode: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                streamLoading = true,
                streamError = null,
                playbackError = null,
            )
            val slug = _state.value.slug
            val lang = _state.value.streamLang
            val server = _state.value.streamServer
            var streamUrl: String? = null
            var subtitles: List<SubtitleTrack> = emptyList()
            var failure: String? = null

            if (slug != null) {
                repo.anikageSources(slug, episode, server.lowercase(), lang)
                    .onSuccess { response ->
                        val best = response.sources.firstOrNull { it.isM3U8 } ?: response.sources.firstOrNull()
                        streamUrl = best?.url?.let { token ->
                            // Token -> https://og.bakayaro.live/m3u8/{token} (the
                            // exact URL the site's player builds; the data source
                            // attaches the Origin header the proxy requires).
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
                        if (streamUrl == null) {
                            val langLabel = if (lang == "dub") "dub" else "sub"
                            failure = "No $langLabel source on $server for this episode. " +
                                "Try the other language or another server."
                            AppLogger.w(LogCategory.PLAYER, "No stream sources for $slug ep $episode ($server/$lang)")
                        } else {
                            AppLogger.i(
                                LogCategory.PLAYER,
                                "Stream ready for episode $episode on $server/$lang (${subtitles.size} subtitle track(s))",
                            )
                        }
                    }
                    .onFailure { e ->
                        AppLogger.w(LogCategory.PLAYER, "Sources failed for $slug ep $episode ($server/$lang)", e)
                        failure = "$server couldn't provide this episode (${e.message ?: "network error"}). " +
                            "Try another server below."
                    }
            } else {
                AppLogger.w(LogCategory.PLAYER, "No Anikage slug — stream unavailable")
                failure = "This anime isn't in the Anikage catalogue, so no stream is available."
            }

            val saved = repo.loadProgress(animeId, episode)?.positionMs ?: 0L

            _state.value = _state.value.copy(
                streamUrl = streamUrl,
                subtitles = subtitles,
                savedPositionMs = saved,
                streamLoading = false,
                streamError = failure,
            )
            if (streamUrl != null) preparePlayer(episode)

            if (slug != null) {
                launch { repo.anikageViews(slug, episode)?.let { vc ->
                    _state.value = _state.value.copy(viewCount = vc)
                } }
                launch {
                    repo.anikageServers(slug, episode).getOrNull()?.let { servers ->
                        if (servers.isNotEmpty()) {
                            val models = servers.map { s ->
                                StreamServer(
                                    id = s.providerId,
                                    name = displayServerName(s.providerId),
                                    supportsSub = s.subTypes.isEmpty() || s.subTypes.contains("sub"),
                                    supportsDub = s.subTypes.contains("dub"),
                                    isDefault = s.default,
                                )
                            }.distinctBy { it.id }
                            val currentValid = models.any {
                                it.id.equals(server, ignoreCase = true) &&
                                    (if (lang == "dub") it.supportsDub else it.supportsSub)
                            }
                            val fallback = models.firstOrNull {
                                if (lang == "dub") it.supportsDub else it.supportsSub
                            }
                            _state.value = _state.value.copy(
                                servers = models,
                                streamServer = if (currentValid) server else (fallback?.name ?: server),
                            )
                        }
                    }
                }
            }
            loadComments(episode)

            if (!com.anikage.app.core.settings.SettingsState.incognito) {
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

    /** Provider id → display name (site shows Koto / Kiwi / Neko / Zen …). */
    private fun displayServerName(providerId: String): String =
        providerId.replaceFirstChar { it.uppercase() }

    /** Set the media item (with subtitle tracks + quality caps) and start it. */
    private fun preparePlayer(episode: Int) {
        val url = _state.value.streamUrl ?: return
        val player = player()

        val builder = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("${_state.value.title} — Episode $episode")
                    .build()
            )
        // Side-load subtitle tracks (site's softsub VTT files via the proxy).
        if (_state.value.subtitles.isNotEmpty()) {
            builder.setSubtitleConfigurations(
                _state.value.subtitles.map { sub ->
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url))
                        .setMimeType(androidx.media3.common.MimeTypes.TEXT_VTT)
                        .setLanguage(sub.language)
                        .setLabel(sub.label)
                        .setSelectionFlags(
                            if (sub.isDefault) androidx.media3.common.C.SELECTION_FLAG_DEFAULT else 0
                        )
                        .build()
                }
            )
        }
        player.setMediaItem(builder.build())

        // Quality cap (site: streamQuality setting) via track selection.
        runCatching {
            val quality = com.anikage.app.core.settings.SettingsState.streamQuality
            val params = player.trackSelectionParameters.buildUpon()
            when (quality) {
                "low" -> params.setMaxVideoSize(480, 854)
                "standard" -> params.setMaxVideoSize(720, 1280)
                "full" -> params.setMaxVideoSize(1080, 1920)
                else -> params/* auto: no cap */
            }
            player.trackSelectionParameters = params.build()
        }

        if (_state.value.savedPositionMs > 0L) {
            player.seekTo(_state.value.savedPositionMs)
            AppLogger.d(LogCategory.PLAYER, "Resuming from saved position ${_state.value.savedPositionMs / 1000}s")
        }
        player.prepare()
        if (com.anikage.app.core.settings.SettingsState.autoplay) player.play()
    }

    /**
     * Switch episode WITHOUT refetching anime details. Only the stream
     * sources and comments for the new episode are requested.
     */
    fun switchEpisode(episode: Int) {
        val ep = episode.coerceIn(1, _state.value.totalEpisodes)
        if (ep == _state.value.episode) return
        viewModelScope.launch { saveProgressNow() }
        _state.value = _state.value.copy(episode = ep, savedPositionMs = 0L, viewCount = null)
        loadEpisode(ep)
    }

    /** Site's SUB/DUB toggle — reloads the current episode's stream. */
    fun setStreamLang(lang: String) {
        if (_state.value.streamLang == lang) return
        _state.value = _state.value.copy(streamLang = lang, streamUrl = null, playbackError = null)
        loadEpisode(_state.value.episode)
    }

    /** Site's server-chip switch — reloads the current episode's stream. */
    fun setStreamServer(server: String) {
        if (_state.value.streamServer == server) return
        _state.value = _state.value.copy(streamServer = server, streamUrl = null, playbackError = null)
        loadEpisode(_state.value.episode)
    }

    /** Re-resolve the stream for the current episode (site's refresh button). */
    fun reloadStream() {
        _state.value = _state.value.copy(streamUrl = null, playbackError = null, streamError = null)
        loadEpisode(_state.value.episode)
    }

    /** Reload comments for the current episode (pull-to-refresh in the panel). */
    fun refreshComments() {
        loadComments(_state.value.episode)
    }

    private fun loadComments(episode: Int) {
        if (!com.anikage.app.core.settings.SettingsState.commentsEnabled) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                comments = _state.value.comments.copy(loading = true),
            )
            val result = repo.anikageComments(animeId, episode)
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
                }
            )
        }
    }

    /** Save current position so we can resume later. */
    fun saveProgress(positionMs: Long, durationMs: Long) {
        viewModelScope.launch {
            repo.saveProgress(animeId, _state.value.episode, positionMs, durationMs)
        }
    }

    private suspend fun saveProgressNow() {
        val p = player ?: return
        val position = p.currentPosition
        val duration = p.duration.coerceAtLeast(0L)
        if (duration > 0) repo.saveProgress(animeId, _state.value.episode, position, duration)
    }

    override fun onCleared() {
        super.onCleared()
        player?.release()
        player = null
    }

    companion object {
        fun factory(
            app: Application,
            repo: AnikageRepository,
            animeId: Int,
            episode: Int,
            slug: String? = null,
        ) = viewModelFactory {
            initializer { WatchViewModel(app, repo, animeId, episode, slug) }
        }
    }
}
