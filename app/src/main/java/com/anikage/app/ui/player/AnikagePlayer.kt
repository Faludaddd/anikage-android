package com.anikage.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.NextPlan
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.text.Cue
import com.anikage.app.core.settings.CaptionStyles
import com.anikage.app.core.settings.SettingsState
import com.anikage.app.core.theme.WebTextStyles
import kotlinx.coroutines.delay

/**
 * The Anikage custom player — 1:1 with the site's media-chrome player:
 *  - TextureView surface (aspect-locked, screenshot-capable)
 *  - Auto-hiding controls: seek slider with buffered track + scrub time,
 *    play/pause, replay-10 / forward-10, prev/next episode, time display
 *    (tap toggles remaining), mute, captions, PiP, settings, fullscreen
 *  - Double-tap left/right to seek ±10s with animated side islands
 *  - Press & hold anywhere → 2x speed (release restores) — YouTube-style
 *  - Skip Opening / Skip Ending buttons + autoskip
 *  - Custom caption rendering with user caption styles
 *  - Buffering + error overlays with retry / fix-it / report actions
 *  - Hardware-keyboard shortcuts (site: anikage-watch-hotkeys)
 */
@Composable
fun AnikagePlayer(
    state: WatchUiState,
    viewModel: WatchViewModel,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    onOpenReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var showRemainingTime by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<PlayerFeedback?>(null) }
    var isScrubbing by remember { mutableStateOf(false) }
    // hold-to-speed (press & hold → 2x, release → restore)
    var holdActive by remember { mutableStateOf(false) }

    // ── controls auto-hide (site: 3s idle while playing) ──────────────
    LaunchedEffect(controlsVisible, state.isPlaying, isScrubbing, settingsOpen, state.isBuffering) {
        if (controlsVisible && state.isPlaying && !isScrubbing && !settingsOpen && !state.isBuffering) {
            delay(3000)
            controlsVisible = false
        }
    }
    // ── feedback auto-dismiss (seek islands only; the hold pill clears on release) ─
    LaunchedEffect(feedback) {
        if (feedback is PlayerFeedback.HoldSpeed) return@LaunchedEffect
        if (feedback != null) {
            delay(700)
            if (feedback !is PlayerFeedback.HoldSpeed) feedback = null
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { offset ->
                        val width = size.width.toFloat()
                        val x = offset.x
                        if (x < width * 0.35f) {
                            viewModel.seekBy(-10_000L)
                            feedback = PlayerFeedback.SeekBack(-10_000L)
                        } else if (x > width * 0.65f) {
                            viewModel.seekBy(10_000L)
                            feedback = PlayerFeedback.SeekForward(10_000L)
                        } else {
                            viewModel.togglePlayPause()
                        }
                    },
                    onLongPress = {
                        // Press & hold → 2x speed; release restores the speed.
                        holdActive = true
                        viewModel.beginHoldSpeed()
                        feedback = PlayerFeedback.HoldSpeed(2.0f)
                        controlsVisible = false
                    },
                    onPress = {
                        tryAwaitRelease()
                        if (holdActive) {
                            holdActive = false
                            viewModel.endHoldSpeed()
                            if (feedback is PlayerFeedback.HoldSpeed) feedback = null
                        }
                    },
                )
            },
    ) {
        // ── video surface (aspect-locked TextureView) ──────────────────────
        PlayerVideoSurface(
            state = state,
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
        )

        // ── buffering ──────────────────────────────────────────────────────
        PlayerBuffering(
            visible = state.isBuffering && !settingsOpen && state.embedActive == null,
            modifier = Modifier.align(Alignment.Center),
        )

        // ── captions (site: custom captions overlay, above controls) ──────
        if (state.captionsOn && state.cues.isNotEmpty() && state.streamUrl != null) {
            CaptionOverlay(
                cues = state.cues,
                styles = SettingsState.captionStyles,
                fullscreen = isFullscreen,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (controlsVisible) 92.dp else 28.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )
        }

        // ── skip intro / outro buttons (site: bottom-right, above controls)
        SkipButtons(
            state = state,
            visible = !settingsOpen,
            onSkipIntro = viewModel::skipIntro,
            onSkipOutro = viewModel::skipOutro,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp)
                .padding(bottom = if (controlsVisible) 92.dp else 28.dp),
        )

        // ── feedback islands (site: vjs-feedback-island; YouTube-style) ──
        feedback?.let { fb ->
            val anchor = when (fb) {
                is PlayerFeedback.SeekBack -> Alignment.CenterStart
                is PlayerFeedback.SeekForward -> Alignment.CenterEnd
                is PlayerFeedback.HoldSpeed -> Alignment.TopCenter
            }
            PlayerFeedbackIsland(
                feedback = fb,
                modifier = Modifier
                    .align(anchor)
                    .padding(horizontal = 56.dp)
                    .padding(top = if (fb is PlayerFeedback.HoldSpeed) 56.dp else 0.dp),
            )
        }

        // ── error overlay (retry + fix-it + report) ────────────────────────
        if (state.playbackError != null && !state.streamLoading && state.embedActive == null) {
            PlayerErrorOverlay(
                message = state.playbackError ?: "",
                onReload = { viewModel.reloadStream(refresh = true) },
                onReport = onOpenReport,
                modifier = Modifier.matchParentSize(),
            )
        }

        // ── controls ───────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = (controlsVisible || !state.isPlaying || isScrubbing) && !settingsOpen,
            enter = fadeIn(tween(160)) + slideInVertically(tween(160)) { it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 },
            modifier = Modifier.matchParentSize(),
        ) {
            PlayerControls(
                state = state,
                viewModel = viewModel,
                isFullscreen = isFullscreen,
                showRemainingTime = showRemainingTime,
                onToggleTimeMode = { showRemainingTime = !showRemainingTime },
                onToggleFullscreen = onToggleFullscreen,
                onBack = onBack,
                onOpenSettings = { settingsOpen = true },
                onScrubbingChange = { isScrubbing = it },
                onFeedback = { feedback = it },
            )
        }

        // ── mini progress bar while controls are hidden (site setting) ────
        if (!controlsVisible && !settingsOpen && SettingsState.miniProgressBar && state.streamUrl != null) {
            MiniProgressBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp),
            )
        }

        // ── settings panel (site: settings-root, slides over everything) ──
        if (settingsOpen) {
            PlayerSettingsPanel(
                state = state,
                viewModel = viewModel,
                onDismiss = { settingsOpen = false },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/**
 * Video surface: TextureView with the player attached (so screenshots work),
 * letterboxed to the stream's aspect ratio (site: contain).
 */
@Composable
private fun PlayerVideoSurface(
    state: WatchUiState,
    viewModel: WatchViewModel,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(if (state.videoAspect > 0f) state.videoAspect else 16f / 9f),
            factory = { ctx ->
                android.view.TextureView(ctx).also { view ->
                    viewModel.attachSurface(view)
                }
            },
            onRelease = { viewModel.detachSurface() },
        )
    }
}

/** Thin action-colored progress bar shown while the controls are hidden. */
@Composable
private fun MiniProgressBar(positionMs: Long, durationMs: Long, modifier: Modifier = Modifier) {
    val theme = com.anikage.app.core.theme.LocalAnikageTheme.current
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Box(
        modifier = modifier
            .background(Color(0x33FFFFFF)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxSize()
                .background(theme.action),
        )
    }
}

/** Site time format — m:ss / h:mm:ss, negative for remaining time. */
internal fun formatPlayerTime(ms: Long): String {
    val negative = ms < 0
    val totalSeconds = kotlin.math.abs(ms) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val body = if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    return if (negative) "-$body" else body
}

// ---------------------------------------------------------------------------
//  Controls layer
// ---------------------------------------------------------------------------

@Composable
private fun PlayerControls(
    state: WatchUiState,
    viewModel: WatchViewModel,
    isFullscreen: Boolean,
    showRemainingTime: Boolean,
    onToggleTimeMode: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    onFeedback: (PlayerFeedback?) -> Unit,
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0x66000000),
                    0.22f to Color.Transparent,
                    0.74f to Color.Transparent,
                    1f to Color(0xCC000000),
                ),
            ),
    ) {
        // ── top row: fullscreen back + title ─────────────────────────────
        if (isFullscreen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerIconButton(onClick = onBack, contentDescription = "Exit fullscreen") {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                }
                Text(
                    text = "${state.title} — EP ${state.episode}",
                    style = WebTextStyles.sm,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        // ── center play/pause (site: media-play-button) ──────────────────
        if (!state.isPlaying && !state.isBuffering && state.streamUrl != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(if (isFullscreen) 72.dp else 56.dp)
                    .clip(CircleShape)
                    .background(Color(0x33000000))
                    .clickable(onClick = viewModel::togglePlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(if (isFullscreen) 44.dp else 36.dp),
                )
            }
        }

        // ── bottom: seek slider + times + transport row ──────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = if (isFullscreen) 24.dp else 8.dp)
                .padding(bottom = if (isFullscreen) 18.dp else 2.dp),
        ) {
            PlayerSeekRow(
                state = state,
                viewModel = viewModel,
                showRemainingTime = showRemainingTime,
                onToggleTimeMode = onToggleTimeMode,
                onScrubbingChange = onScrubbingChange,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // transport (site order: prev, -10, play, +10, next)
                PlayerIconButton(
                    onClick = viewModel::previousEpisode,
                    enabled = state.episode > 1,
                    contentDescription = "Previous episode",
                ) {
                    Icon(Icons.Filled.SkipPrevious, null, tint = Color.White)
                }
                PlayerIconButton(
                    onClick = {
                        viewModel.seekBy(-10_000L)
                        onFeedback(PlayerFeedback.SeekBack(-10_000L))
                    },
                    contentDescription = "Back 10 seconds",
                ) {
                    Icon(Icons.Filled.Replay10, null, tint = Color.White)
                }
                PlayerIconButton(
                    onClick = viewModel::togglePlayPause,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
                PlayerIconButton(
                    onClick = {
                        viewModel.seekBy(10_000L)
                        onFeedback(PlayerFeedback.SeekForward(10_000L))
                    },
                    contentDescription = "Forward 10 seconds",
                ) {
                    Icon(Icons.Filled.Forward10, null, tint = Color.White)
                }
                PlayerIconButton(
                    onClick = viewModel::nextEpisode,
                    enabled = state.episode < state.totalEpisodes,
                    contentDescription = "Next episode",
                ) {
                    Icon(Icons.Filled.SkipNext, null, tint = Color.White)
                }

                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))

                // right cluster: mute, captions, screenshot, PiP, settings, fullscreen
                PlayerIconButton(
                    onClick = viewModel::toggleMuted,
                    contentDescription = if (state.muted) "Unmute" else "Mute",
                ) {
                    Icon(
                        if (state.muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        null,
                        tint = Color.White,
                    )
                }
                if (state.textTracks.isNotEmpty()) {
                    PlayerIconButton(
                        onClick = viewModel::toggleCaptions,
                        contentDescription = "Toggle subtitles",
                    ) {
                        Icon(
                            if (state.captionsOn) Icons.Filled.Subtitles else Icons.Filled.SubtitlesOff,
                            null,
                            tint = Color.White,
                        )
                    }
                }
                PlayerIconButton(
                    onClick = { takeScreenshot(context, viewModel, state) },
                    contentDescription = "Screenshot",
                ) {
                    Icon(Icons.Filled.CameraAlt, null, tint = Color.White)
                }
                PlayerIconButton(
                    onClick = viewModel::enterPictureInPicture,
                    contentDescription = "Picture in picture",
                ) {
                    Icon(Icons.Filled.PictureInPictureAlt, null, tint = Color.White)
                }
                PlayerIconButton(onClick = onOpenSettings, contentDescription = "Settings") {
                    Icon(Icons.Filled.Settings, null, tint = Color.White)
                }
                PlayerIconButton(
                    onClick = onToggleFullscreen,
                    contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                ) {
                    Icon(
                        if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        null,
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

/** Save a screenshot of the current frame (site: camera button -> PNG). */
private fun takeScreenshot(
    context: android.content.Context,
    viewModel: WatchViewModel,
    state: WatchUiState,
) {
    val bitmap = viewModel.captureScreenshot()
    if (bitmap == null) {
        android.widget.Toast.makeText(context, "Screenshot unavailable for this stream.", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val name = state.title.replace(Regex("\\s+"), "_") + "_EP${state.episode}"
    runCatching {
        val resolver = context.contentResolver
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Anikage")
            }
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            val anikage = java.io.File(dir, "Anikage").apply { mkdirs() }
            java.io.File(anikage, "$name.png").outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        android.widget.Toast.makeText(context, "Screenshot saved to Pictures/Anikage", android.widget.Toast.LENGTH_SHORT).show()
    }.onFailure {
        android.widget.Toast.makeText(context, "Couldn't save the screenshot.", android.widget.Toast.LENGTH_SHORT).show()
    }
}

/** Seek bar + time display (site: media-time-slider + time display). */
@Composable
private fun PlayerSeekRow(
    state: WatchUiState,
    viewModel: WatchViewModel,
    showRemainingTime: Boolean,
    onToggleTimeMode: () -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
) {
    var scrubPosition by remember { mutableLongStateOf(-1L) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatPlayerTime(if (scrubPosition >= 0) scrubPosition else state.positionMs),
            style = WebTextStyles.xs.copy(fontFeatureSettings = "tnum"),
            color = Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onToggleTimeMode)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 2.dp),
        ) {
            SeekSlider(
                positionMs = state.positionMs,
                bufferedMs = state.bufferedMs,
                durationMs = state.durationMs,
                onScrubStart = { onScrubbingChange(true) },
                onScrub = { scrubPosition = it },
                onScrubEnd = { pos ->
                    viewModel.seekTo(pos)
                    scrubPosition = -1L
                    onScrubbingChange(false)
                },
            )
            // scrub time bubble follows the drag (site: tooltip on drag)
            if (scrubPosition >= 0) {
                Text(
                    text = formatPlayerTime(scrubPosition),
                    style = WebTextStyles.xs,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xE6000000))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Text(
            text = formatPlayerTime(
                if (showRemainingTime && state.durationMs > 0) -(state.durationMs - state.positionMs) else state.durationMs,
            ),
            style = WebTextStyles.xs.copy(fontFeatureSettings = "tnum"),
            color = Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onToggleTimeMode)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

// ---------------------------------------------------------------------------
//  Feedback (site: vjs-feedback-island)
// ---------------------------------------------------------------------------

sealed interface PlayerFeedback {
    data class SeekForward(val amountMs: Long) : PlayerFeedback
    data class SeekBack(val amountMs: Long) : PlayerFeedback
    data class HoldSpeed(val speed: Float) : PlayerFeedback
}

@Composable
private fun PlayerFeedbackIsland(feedback: PlayerFeedback, modifier: Modifier = Modifier) {
    val label = when (feedback) {
        is PlayerFeedback.SeekForward -> "+${feedback.amountMs / 1000}s"
        is PlayerFeedback.SeekBack -> "-${kotlin.math.abs(feedback.amountMs) / 1000}s"
        is PlayerFeedback.HoldSpeed -> "${feedback.speed}x speed"
    }
    // Pop-in: scale 0.6 -> 1 with a spring, fade in (fresh mount each time).
    val pop = remember { androidx.compose.animation.core.Animatable(0.6f) }
    LaunchedEffect(feedback) {
        pop.snapTo(0.6f)
        pop.animateTo(1f, androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
        ))
    }
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                alpha = ((pop.value - 0.6f) / 0.4f).coerceIn(0f, 1f)
            }
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xB3000000))
            .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            when (feedback) {
                is PlayerFeedback.SeekBack -> Icon(
                    Icons.Filled.ChevronLeft,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                is PlayerFeedback.SeekForward -> Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                is PlayerFeedback.HoldSpeed -> Icon(
                    Icons.Filled.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = label,
                style = WebTextStyles.base.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
        }
}

// ---------------------------------------------------------------------------
//  Skip intro / outro (site: Skip Opening / Skip Ending buttons)
// ---------------------------------------------------------------------------

@Composable
private fun SkipButtons(
    state: WatchUiState,
    visible: Boolean,
    onSkipIntro: () -> Unit,
    onSkipOutro: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inIntro = state.hasIntro && state.positionMs >= state.introStartMs && state.positionMs < state.introEndMs
    val inOutro = state.hasOutro && state.positionMs >= state.outroStartMs && state.positionMs < state.outroEndMs
    if (!visible || state.streamUrl == null || (!inIntro && !inOutro)) return
    Column(
        modifier = modifier,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        if (inIntro) {
            SiteSkipChip(label = "Skip Opening", onClick = onSkipIntro)
        }
        if (inOutro) {
            SiteSkipChip(label = "Skip Ending", onClick = onSkipOutro)
        }
    }
}

@Composable
private fun SiteSkipChip(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xE6000000))
            .border(1.dp, Color(0x4DFFFFFF), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.NextPlan, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(label, style = WebTextStyles.sm, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------------------
//  Captions (site: custom captions renderer with user styles)
// ---------------------------------------------------------------------------

@Composable
private fun CaptionOverlay(
    cues: List<Cue>,
    styles: CaptionStyles,
    fullscreen: Boolean,
    modifier: Modifier = Modifier,
) {
    val text = cues.mapNotNull { it.text?.toString() }.joinToString("\n")
    if (text.isBlank()) return
    val textColor = Color(styles.textColor).copy(alpha = styles.textOpacity)
    val cueBg = Color(styles.textBg).let {
        if (styles.textBgOpacity > 0f) it.copy(alpha = styles.textBgOpacity) else Color.Transparent
    }
    val displayBg = Color(styles.displayBg).let {
        if (styles.displayBgOpacity > 0f) it.copy(alpha = styles.displayBgOpacity) else Color.Transparent
    }
    val fontSize = (if (fullscreen) 18.sp else 15.sp) * styles.fontSize
    val fontWeight = if (styles.fontWeight >= 700) FontWeight.Bold else FontWeight.Normal
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(displayBg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = WebTextStyles.base.copy(
                fontSize = fontSize,
                fontWeight = fontWeight,
                textAlign = TextAlign.Center,
                lineHeight = fontSize * 1.25f,
            ),
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(2.dp))
                .background(cueBg)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

// ---------------------------------------------------------------------------
//  Buffering + error
// ---------------------------------------------------------------------------

@Composable
private fun PlayerBuffering(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(150)), exit = fadeOut(tween(200)), modifier = modifier) {
        androidx.compose.material3.CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(38.dp),
        )
    }
}

/** In-player failure — retry (fresh token), report, never silent (site 1:1). */
@Composable
fun PlayerErrorOverlay(
    message: String,
    onReload: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color(0xB3000000))
            .padding(20.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(10.dp))
        Text(
            text = message,
            style = WebTextStyles.sm,
            color = Color(0xFFD4D4D8),
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .clickable(onClick = onReload)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                Text("Fix it", style = WebTextStyles.xs, color = Color.Black, fontWeight = FontWeight.SemiBold)
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0x4DFFFFFF), RoundedCornerShape(10.dp))
                    .clickable(onClick = onReport)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text("Report", style = WebTextStyles.xs, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Shared bits
// ---------------------------------------------------------------------------

@Composable
internal fun PlayerIconButton(
    onClick: () -> Unit,
    contentDescription: String?,
    enabled: Boolean = true,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.alpha(if (enabled) 1f else 0.3f)) {
            icon()
        }
    }
}
