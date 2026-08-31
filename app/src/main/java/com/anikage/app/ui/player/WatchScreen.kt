package com.anikage.app.ui.player

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.util.Rational
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import com.anikage.app.Config
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.ui.components.ErrorOrEmptyState
import com.anikage.app.ui.components.LoadingSpinner
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun WatchScreen(
    animeId: Int,
    initialEpisode: Int,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
    val repo = remember { AnikageRepository(context) }
    val viewModel: WatchViewModel = viewModel(
        factory = WatchViewModel.factory(app, repo, animeId, initialEpisode)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player = remember { viewModel.player() }

    // Lock orientation to landscape while playing (optional, configurable)
    val activity = context as? android.app.Activity
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation
        if (Config.Player.FORCE_LANDSCAPE_FULLSCREEN) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            // Save progress before leaving
            val position = player.currentPosition
            val duration = player.duration.coerceAtLeast(0L)
            if (duration > 0) viewModel.saveProgress(position, duration)
            if (Config.Player.FORCE_LANDSCAPE_FULLSCREEN) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            // Enter PiP if available and a video is actually playing
            if (Config.Player.AUTO_PIP_ON_LEAVE &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                player.isPlaying) {
                try {
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                    activity?.enterPictureInPictureMode(params)
                } catch (_: Exception) { /* ignore */ }
            }
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)) {

        if (state.loading) {
            LoadingSpinner()
            return@Box
        }

        if (state.error != null && state.details == null) {
            ErrorOrEmptyState(
                title = "Couldn't load anime",
                subtitle = state.error ?: "",
                actionText = "Back",
                onAction = onBackClick,
            )
            return@Box
        }

        // Player surface — fills the screen
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setFullscreenButtonClickListener { /* delegate to system */ }
                    // Auto-hide the controls after a few seconds
                    controllerAutoShow = true
                    controllerHideOnTouch = true
                    setShowNextButton(true)
                    setShowPreviousButton(true)
                }
            },
        )

        // Top-left back button overlay (because the PlayerView's own back arrow
        // is awkward to wire to Compose navigation)
        androidx.compose.material3.IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        ) {
            androidx.compose.material3.Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }

        // Episode switcher bar (overlay, bottom) — visible while paused
        if (state.totalEpisodes > 1) {
            EpisodeSwitcherBar(
                episode = state.episode,
                total = state.totalEpisodes,
                onPrev = { viewModel.switchEpisode(state.episode - 1) },
                onNext = { viewModel.switchEpisode(state.episode + 1) },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(bottom = 88.dp),   // sit above PlayerControlView
            )
        }

        // If no stream URL is available, show a placeholder message
        if (state.streamUrl == null) {
            Box(modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center) {
                Text(
                    text = "No streaming source configured. See Config.STREAM_SOURCE_URL.",
                    color = Color.White,
                    modifier = Modifier.padding(32.dp),
                )
            }
        }
    }
}

@Composable
private fun EpisodeSwitcherBar(
    episode: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(Color.Black.copy(alpha = 0.5f), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        androidx.compose.material3.IconButton(onClick = onPrev, enabled = episode > 1) {
            androidx.compose.material3.Icon(Icons.Default.SkipPrevious, contentDescription = "Previous episode", tint = Color.White)
        }
        Text(
            text = "Episode $episode / $total",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
        androidx.compose.material3.IconButton(onClick = onNext, enabled = episode < total) {
            androidx.compose.material3.Icon(Icons.Default.SkipNext, contentDescription = "Next episode", tint = Color.White)
        }
    }
}
