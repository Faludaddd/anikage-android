package com.anikage.app.ui.player

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.anikage.app.Config
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.model.AnimeDetails
import com.anikage.app.core.log.AppLogger
import com.anikage.app.core.log.LogCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WatchUiState(
    val loading: Boolean = true,
    val details: AnimeDetails? = null,
    val episode: Int = 1,
    val totalEpisodes: Int = 1,
    val streamUrl: String? = null,
    val error: String? = null,
    val savedPositionMs: Long = 0L,
    val title: String = "",
)

class WatchViewModel(
    private val app: Application,
    private val repo: AnikageRepository,
    private val animeId: Int,
    initialEpisode: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(WatchUiState(loading = true, episode = initialEpisode))
    val state: StateFlow<WatchUiState> = _state.asStateFlow()

    private var player: ExoPlayer? = null

    fun player(): ExoPlayer = player ?: ExoPlayer.Builder(app)
        .build().also {
            player = it
            it.repeatMode = Player.REPEAT_MODE_OFF
            it.playWhenReady = Config.Player.AUTO_PLAY
        }

    init {
        loadAnime()
    }

    private fun loadAnime() {
        viewModelScope.launch {
            AppLogger.d(LogCategory.PLAYER, "Loading anime details (id=$animeId)")
            val result = repo.animeDetails(animeId)
            result.fold(
                onSuccess = { details ->
                    AppLogger.i(
                        LogCategory.PLAYER,
                        "Loaded '${details.displayTitle()}' (${details.episodes ?: '?'} eps)",
                    )
                    val total = details.episodes ?: (details.nextAiringEpisode?.episode?.minus(1)) ?: 1
                    val ep = _state.value.episode.coerceIn(1, total.coerceAtLeast(1))
                    _state.value = _state.value.copy(
                        loading = false,
                        details = details,
                        totalEpisodes = total.coerceAtLeast(1),
                        episode = ep,
                        title = details.displayTitle(),
                    )
                    loadStream(ep)
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

    private fun loadStream(episode: Int) {
        viewModelScope.launch {
            AppLogger.d(LogCategory.PLAYER, "Loading stream for episode $episode")
            val streamUrl = resolveStreamUrl(animeId, episode)
            // Load saved position if any
            val saved = if (Config.Player.RESUME_FROM_POSITION)
                repo.loadProgress(animeId, episode)?.positionMs ?: 0L
            else 0L
            _state.value = _state.value.copy(
                streamUrl = streamUrl,
                savedPositionMs = saved,
            )
            // Set the media item on the player
            val player = player()
            val media = MediaItem.Builder()
                .setUri(streamUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("${_state.value.title} — Episode $episode")
                        .build()
                )
                .build()
            player.setMediaItem(media)
            if (saved > 0L) {
                player.seekTo(saved)
                AppLogger.d(LogCategory.PLAYER, "Resuming from saved position ${saved / 1000}s")
            }
            player.prepare()
            if (Config.Player.AUTO_PLAY) player.play()
            // Mark as recently viewed
            repo.markRecentlyViewed(
                com.anikage.app.core.data.model.Anime(
                    id = animeId,
                    title = _state.value.details?.title ?: com.anikage.app.core.data.model.AnimeTitle(),
                    coverImage = _state.value.details?.coverImage ?: com.anikage.app.core.data.model.CoverImage(),
                ),
                episode,
            )
        }
    }

    /** Switch to a different episode. */
    fun switchEpisode(episode: Int) {
        val ep = episode.coerceIn(1, _state.value.totalEpisodes)
        _state.value = _state.value.copy(episode = ep)
        loadStream(ep)
    }

    /** Save current position so we can resume later. */
    fun saveProgress(positionMs: Long, durationMs: Long) {
        viewModelScope.launch {
            repo.saveProgress(animeId, _state.value.episode, positionMs, durationMs)
        }
    }

    /**
     * Resolve the stream URL for an episode.
     *
     * If [Config.STREAM_SOURCE_URL] is set, the app calls that endpoint:
     *     GET {STREAM_SOURCE_URL}?id={animeId}&episode={episode}
     * and expects: { "streamUrl": "https://..." }
     *
     * Otherwise it uses [Config.SAMPLE_STREAM_URL] so the player UI works
     * out-of-the-box.
     */
    private suspend fun resolveStreamUrl(animeId: Int, episode: Int): String {
        val source = Config.STREAM_SOURCE_URL ?: run {
            AppLogger.w(
                LogCategory.PLAYER,
                "STREAM_SOURCE_URL not set — using the sample stream (see Config.kt)",
            )
            return Config.SAMPLE_STREAM_URL
        }
        return try {
            val client = okhttp3.OkHttpClient()
            val req = okhttp3.Request.Builder()
                .url("$source?id=$animeId&episode=$episode")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    AppLogger.w(
                        LogCategory.PLAYER,
                        "Stream source HTTP ${resp.code} — falling back to the sample stream",
                    )
                    return Config.SAMPLE_STREAM_URL
                }
                val body = resp.body?.string() ?: return Config.SAMPLE_STREAM_URL
                val parsed = kotlinx.serialization.json.Json.parseToJsonElement(body) as kotlinx.serialization.json.JsonObject
                parsed["streamUrl"]?.toString()?.trim('"')?.also {
                    AppLogger.i(LogCategory.PLAYER, "Resolved stream URL for episode $episode")
                } ?: Config.SAMPLE_STREAM_URL
            }
        } catch (e: Exception) {
            AppLogger.e(LogCategory.PLAYER, "Stream source request failed", e)
            Config.SAMPLE_STREAM_URL
        }
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
