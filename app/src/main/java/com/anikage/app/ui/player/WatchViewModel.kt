package com.anikage.app.ui.player

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.anikage.app.Config
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.api.AnikageComment
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
    val streamLoading: Boolean = false,
    val streamError: String? = null,
    val error: String? = null,
    val savedPositionMs: Long = 0L,
    val title: String = "",
    val slug: String? = null,
    val viewCount: Long? = null,
    val comments: CommentsUiState = CommentsUiState(),
)

/**
 * Watch screen data + ExoPlayer wiring.
 *
 * Fix history (v1.5.0 regressions this class addresses):
 *  - The anime details are fetched exactly ONCE per id (the repository's
 *    single-flight dedups across the Details screen and this screen).
 *  - Switching episodes does NOT refetch details — only the new episode's
 *    sources + comments are loaded.
 *  - Real streams: Anikage sources -> HLS token -> og.bakayaro.live, played
 *    with the Referer/Origin headers the stream host requires.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class WatchViewModel(
    private val app: Application,
    private val repo: AnikageRepository,
    private val animeId: Int,
    initialEpisode: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(WatchUiState(loading = true, episode = initialEpisode))
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
        it.playWhenReady = Config.Player.AUTO_PLAY
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
                    // Slug + real episode metadata (non-fatal if unavailable).
                    val slug = repo.resolveSlug(
                        animeId,
                        details.title.english,
                        details.title.romaji,
                    )
                    if (slug != null) {
                        _state.value = _state.value.copy(slug = slug)
                        repo.anikageEpisodes(slug).onSuccess { eps ->
                            if (eps.isNotEmpty()) {
                                _state.value = _state.value.copy(
                                    episodes = eps.map {
                                        EpisodeItem(
                                            number = it.number,
                                            title = it.title?.takeIf { t -> t.isNotBlank() } ?: "Episode ${it.number}",
                                            thumbnail = it.image,
                                            isFiller = it.isFiller,
                                            isRecap = it.isRecap,
                                        )
                                    },
                                    totalEpisodes = maxOf(total, eps.size),
                                )
                            }
                        }
                    }
                    loadEpisode(ep)
                },
                onFailure = { e ->
                    AppLogger.e(LogCategory.PLAYER, "Failed to load anime (id=$animeId)", e)
                    _state.value = _state.value.copy(
                        loading = false,
                        error = e.message ?: "Failed to load anime.",
                    )
                }
            )
        }
    }

    /** Load ONLY the per-episode bits: stream sources, view count, comments. */
    private fun loadEpisode(episode: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(streamLoading = true, streamError = null)
            val slug = _state.value.slug
            val streamUrl: String? = if (slug != null) {
                repo.anikageStreamUrl(slug, episode)
            } else {
                AppLogger.w(
                    LogCategory.PLAYER,
                    "No Anikage slug — using the sample stream (see Config.kt)",
                )
                Config.SAMPLE_STREAM_URL
            }
            if (streamUrl == null) {
                AppLogger.w(LogCategory.PLAYER, "No stream source resolved — using the sample stream")
            } else {
                AppLogger.i(LogCategory.PLAYER, "Stream ready for episode $episode")
            }

            val saved = if (Config.Player.RESUME_FROM_POSITION)
                repo.loadProgress(animeId, episode)?.positionMs ?: 0L
            else 0L

            _state.value = _state.value.copy(
                streamUrl = streamUrl ?: Config.SAMPLE_STREAM_URL,
                savedPositionMs = saved,
                streamLoading = false,
                streamError = if (streamUrl == null) "Stream unavailable — playing the sample stream." else null,
            )
            preparePlayer(episode)

            if (slug != null) {
                launch { repo.anikageViews(slug, episode)?.let { vc ->
                    _state.value = _state.value.copy(viewCount = vc)
                } }
            }
            loadComments(episode)

            if (!Config.Player.INCOGNITO) {
                repo.markRecentlyViewed(
                    com.anikage.app.core.data.model.Anime(
                        id = animeId,
                        title = _state.value.details?.title ?: com.anikage.app.core.data.model.AnimeTitle(),
                        coverImage = _state.value.details?.coverImage
                            ?: com.anikage.app.core.data.model.CoverImage(),
                    ),
                    episode,
                )
            }
        }
    }

    /** Set the media item on the (already-created) player and start it. */
    private fun preparePlayer(episode: Int) {
        val url = _state.value.streamUrl ?: return
        val player = player()
        val media = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("${_state.value.title} — Episode $episode")
                    .build()
            )
            .build()
        player.setMediaItem(media)
        if (_state.value.savedPositionMs > 0L) {
            player.seekTo(_state.value.savedPositionMs)
            AppLogger.d(LogCategory.PLAYER, "Resuming from saved position ${_state.value.savedPositionMs / 1000}s")
        }
        player.prepare()
        if (Config.Player.AUTO_PLAY) player.play()
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

    /** Reload comments for the current episode (pull-to-refresh in the panel). */
    fun refreshComments() {
        loadComments(_state.value.episode)
    }

    private fun loadComments(episode: Int) {
        if (!Config.Player.COMMENTS_ENABLED) return
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
        fun factory(app: Application, repo: AnikageRepository, animeId: Int, episode: Int) = viewModelFactory {
            initializer { WatchViewModel(app, repo, animeId, episode) }
        }
    }
}
