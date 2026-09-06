package com.anikage.app.ui.music

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.api.AnikageMusicAnime
import com.anikage.app.core.data.api.AnikageMusicTheme
import com.anikage.app.core.log.AppLogger
import com.anikage.app.core.log.LogCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MusicInfoUiState(
    val loading: Boolean = true,
    val anime: AnikageMusicAnime? = null,
    /** The theme slug to feature (e.g. "OP1"). */
    val type: String = "",
    val activeTheme: AnikageMusicTheme? = null,
    /** Playable video/audio pair for the active theme. */
    val videoUrl: String? = null,
    val audioUrl: String? = null,
    val error: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

/**
 * Music info player — the site's /music/info page: one anime, every theme,
 * the selected one playing (video primary, audio fallback), with functional
 * play/pause + seek + track switching.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MusicInfoViewModel(
    private val app: Application,
    private val repo: AnikageRepository,
    private val slug: String,
    initialType: String,
) : ViewModel() {

    private val _state = MutableStateFlow(MusicInfoUiState(type = initialType))
    val state: StateFlow<MusicInfoUiState> = _state.asStateFlow()

    private var player: ExoPlayer? = null

    /** The site's ThemePlayer plays the theme video; audio mirrors it. */
    fun player(): ExoPlayer = player ?: ExoPlayer.Builder(app).build().also {
        player = it
        it.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                    _state.value = _state.value.copy(
                        durationMs = it.duration.coerceAtLeast(0L),
                        positionMs = it.currentPosition.coerceAtLeast(0L),
                    )
                }
            }
        })
    }

    init {
        viewModelScope.launch {
            repo.musicAnime(slug).fold(
                onSuccess = { anime ->
                    val themes = anime.animethemes
                    val initial = themes.firstOrNull { it.slug == _state.value.type }
                        ?: themes.firstOrNull()
                    _state.value = _state.value.copy(
                        loading = false,
                        anime = anime,
                        activeTheme = initial,
                        type = initial?.slug ?: _state.value.type,
                    )
                    playTheme(initial)
                },
                onFailure = { e ->
                    AppLogger.w(LogCategory.NETWORK, "Music info failed for $slug", e)
                    _state.value = _state.value.copy(
                        loading = false,
                        error = e.message ?: "Couldn't load this anime's music.",
                    )
                },
            )
        }
    }

    /** Switch the playing theme (site: click a row in the theme list). */
    fun selectTheme(theme: AnikageMusicTheme) {
        if (theme.slug == _state.value.type) {
            togglePlay()
            return
        }
        _state.value = _state.value.copy(type = theme.slug ?: "", activeTheme = theme)
        playTheme(theme)
    }

    private fun playTheme(theme: AnikageMusicTheme?) {
        val entry = theme?.animethemeentries?.firstOrNull()
        val video = entry?.videos?.firstOrNull()
        val videoUrl = video?.link
        val audioUrl = video?.audio?.link
        _state.value = _state.value.copy(videoUrl = videoUrl, audioUrl = audioUrl)

        val url = videoUrl ?: audioUrl ?: run {
            _state.value = _state.value.copy(error = "No playable file for this theme.")
            return
        }
        val p = player()
        val builder = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(theme?.song?.title ?: theme?.slug ?: "Theme")
                    .setArtist(_state.value.anime?.name)
                    .build()
            )
        if (url == audioUrl) {
            builder.setMimeType(androidx.media3.common.MimeTypes.AUDIO_OGG)
        }
        p.setMediaItem(builder.build())
        p.prepare()
        p.play()
    }

    fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    /** Periodic position tick driven by the UI (site: 200ms progress updates). */
    fun tick() {
        val p = player ?: return
        if (p.isPlaying) {
            _state.value = _state.value.copy(
                positionMs = p.currentPosition.coerceAtLeast(0L),
                durationMs = p.duration.coerceAtLeast(0L),
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        player?.release()
        player = null
    }

    companion object {
        fun factory(app: Application, repo: AnikageRepository, slug: String, type: String) =
            viewModelFactory {
                initializer { MusicInfoViewModel(app, repo, slug, type) }
            }
    }
}
