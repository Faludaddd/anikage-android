package com.anikage.app.ui.player

import android.app.Activity
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.NextPlan
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.text.Cue
import com.anikage.app.core.settings.CaptionStyles
import com.anikage.app.core.settings.SettingsState
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.WebTextStyles
import kotlinx.coroutines.delay

/**
 * The Anikage custom player — every important playback function IN the
 * player (user directives #3, #7, #15, #16):
 *
 *  - TextureView surface (aspect-locked, identity-guarded attach/detach —
 *    the black-video fix)
 *  - Auto-hiding controls: seek slider with buffered track + scrub time,
 *    play/pause, prev/next, ±seek (CONFIGURABLE amount), speed pill,
 *    quality pill, audio menu, subtitle toggle + language menu, servers
 *    menu, list (bookmark) menu, sleep timer, PiP, orientation lock,
 *    screenshot, settings, fullscreen
 *  - Double-tap left/right to seek — ONE polished side ripple island with
 *    a replay/forward icon + the skipped seconds (directive #3: no random
 *    floating text; the amount uses the user's configured setting)
 *  - Vertical edge gestures: left = brightness, right = volume
 *  - Press & hold anywhere -> configurable hold speed (release restores)
 *  - Live captions rendered over the video + manageable from the CC menu
 *  - Skip Opening / Skip Ending buttons + autoskip (real episode metadata)
 *  - Filler chip + Skip Filler when the current episode is a filler
 *  - "Up next" card with a configurable auto-next countdown
 *  - Buffering + error overlays with retry / switch-server actions
 */
@Composable
fun AnikagePlayer(
    state: WatchUiState,
    viewModel: WatchViewModel,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var showRemainingTime by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<PlayerFeedback?>(null) }
    var isScrubbing by remember { mutableStateOf(false) }
    // hold-to-speed (press & hold -> configured rate, release -> restore)
    var holdActive by remember { mutableStateOf(false) }
    var quickMenu by remember { mutableStateOf<QuickMenu?>(null) }
    // Orientation lock (directive #16): locks to the current orientation.
    var orientationLocked by remember { mutableStateOf(false) }
    // Gesture levels (vertical edge swipes).
    var gestureBrightness by remember { mutableFloatStateOf(-1f) }
    var gestureVolume by remember { mutableFloatStateOf(-1f) }

    val context = LocalContext.current
    val activity = context as? Activity
    val window = activity?.window

    val seekAmount = SettingsState.seekAmountSec.coerceIn(5, 60)
    val gesturesOn = SettingsState.gestureControls
    val doubleTapOn = SettingsState.doubleTapSeek && gesturesOn
    val holdOn = SettingsState.holdToSpeedEnabled && gesturesOn

    // ── controls auto-hide (site: 3s idle while playing) ──────────────
    LaunchedEffect(controlsVisible, state.isPlaying, isScrubbing, settingsOpen, state.isBuffering, quickMenu) {
        if (controlsVisible && state.isPlaying && !isScrubbing && !settingsOpen && quickMenu == null && !state.isBuffering) {
            delay(3000)
            controlsVisible = false
        }
    }

    // ── feedback auto-dismiss ────────────────────────────────────────────
    LaunchedEffect(feedback) {
        if (feedback is PlayerFeedback.HoldSpeed) return@LaunchedEffect
        if (feedback != null) {
            delay(700)
            if (feedback !is PlayerFeedback.HoldSpeed) feedback = null
        }
    }

    // ── gesture level indicators auto-clear ──────────────────────────────
    LaunchedEffect(gestureBrightness) {
        if (gestureBrightness >= 0f) { delay(900); gestureBrightness = -1f }
    }
    LaunchedEffect(gestureVolume) {
        if (gestureVolume >= 0f) { delay(900); gestureVolume = -1f }
    }

    BoxWithConstraints(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(doubleTapOn, holdOn, seekAmount) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { offset ->
                        if (doubleTapOn) {
                            val width = size.width.toFloat()
                            if (offset.x < width * 0.35f) {
                                viewModel.seekBy(-seekAmount * 1000L)
                                feedback = PlayerFeedback.SeekBack(seekAmount)
                            } else if (offset.x > width * 0.65f) {
                                viewModel.seekBy(seekAmount * 1000L)
                                feedback = PlayerFeedback.SeekForward(seekAmount)
                            } else {
                                viewModel.togglePlayPause()
                            }
                        } else {
                            viewModel.togglePlayPause()
                        }
                    },
                    onLongPress = {
                        if (holdOn) {
                            holdActive = true
                            viewModel.beginHoldSpeed()
                            feedback = PlayerFeedback.HoldSpeed(SettingsState.holdSpeedRate)
                            controlsVisible = false
                        }
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
            }
            .pointerInput(gesturesOn, state.masterVolume) {
                // Vertical EDGE swipes: left edge = brightness, right edge =
                // volume (the gesture convention directive #16 asks for).
                if (!gesturesOn) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val edgePx = 90f
                    val isLeftEdge = down.position.x < edgePx
                    val isRightEdge = down.position.x > size.width - edgePx
                    if (!isLeftEdge && !isRightEdge) return@awaitEachGesture
                    val isBrightness = isLeftEdge // both edges? left wins
                    var totalDy = 0f
                    val startLevel = when {
                        isBrightness -> window?.attributes?.screenBrightness
                            ?.takeIf { it >= 0f } ?: 0.5f
                        else -> state.masterVolume
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        totalDy += change.positionChange().y
                        val range = size.height.toFloat()
                        val delta = -(totalDy / range) * 0.9f
                        val level = (startLevel + delta).coerceIn(0.02f, 1f)
                        if (isBrightness) {
                            gestureBrightness = level
                            window?.let { w ->
                                val params = w.attributes
                                params.screenBrightness = level
                                w.attributes = params
                            }
                        } else {
                            gestureVolume = level
                            viewModel.setVolume(level)
                        }
                        if (!change.pressed) break
                    }
                }
            },
    ) {
        val playerHeight = maxHeight

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
            visible = state.isBuffering && quickMenu == null,
            modifier = Modifier.align(Alignment.Center),
        )

        // ── captions (site: custom captions overlay, above controls) ──────
        if (state.captionsOn && state.cues.isNotEmpty() && state.streamUrl != null) {
            val captionLift = playerHeight * (SettingsState.captionStyles.position * 0.45f)
            val captionMin = if (controlsVisible) 92.dp else 28.dp
            CaptionOverlay(
                cues = state.cues,
                styles = SettingsState.captionStyles,
                fullscreen = isFullscreen,
                playerHeight = playerHeight,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (captionLift > captionMin) captionLift else captionMin)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )
        }

        // ── LIVE captions (directive #15): speech-recognition text, rendered
        //    above the video with the user's caption styling. ───────────────
        if (state.liveCaptionsActive && !state.liveCaptionText.isNullOrBlank() && quickMenu == null) {
            LiveCaptionOverlay(
                text = state.liveCaptionText ?: "",
                styles = SettingsState.captionStyles,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (controlsVisible) 104.dp else 64.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )
        }

        // ── filler chip (top end) — clear filler indicator ────────────────
        if (state.isCurrentFiller && state.streamUrl != null && quickMenu == null) {
            FillerChip(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 16.dp, top = 8.dp),
            )
        }

        // ── offline badge when playing a downloaded copy ───────────────────
        if (state.playingDownloaded && quickMenu == null) {
            OfflineChip(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = if (state.isCurrentFiller) 96.dp else 16.dp, top = 8.dp),
            )
        }

        // ── skip intro / outro buttons (site: bottom-right, above controls) ─
        SkipButtons(
            state = state,
            visible = quickMenu == null,
            onSkipIntro = viewModel::skipIntro,
            onSkipOutro = viewModel::skipOutro,
            onSkipFiller = viewModel::skipCurrentFiller,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp)
                .padding(bottom = if (controlsVisible) 92.dp else 28.dp),
        )

        // ── double-tap seek feedback: ONE polished ripple island (no text) ─
        feedback?.let { fb ->
            when (fb) {
                is PlayerFeedback.SeekBack -> SeekFeedbackIsland(
                    seconds = fb.seconds,
                    forward = false,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 40.dp),
                )
                is PlayerFeedback.SeekForward -> SeekFeedbackIsland(
                    seconds = fb.seconds,
                    forward = true,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 40.dp),
                )
                is PlayerFeedback.HoldSpeed -> PlayerFeedbackIsland(
                    feedback = fb,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 56.dp),
                )
            }
        }

        // ── gesture level indicator (brightness / volume) ─────────────────
        if (gestureBrightness >= 0f) {
            GestureLevelIndicator(
                icon = Icons.Default.Brightness6,
                level = gestureBrightness,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (gestureVolume >= 0f) {
            GestureLevelIndicator(
                icon = if (gestureVolume <= 0.02f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                level = gestureVolume,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // ── up-next countdown card (auto-next with countdown) ────────────
        state.nextUp?.let { nextUp ->
            NextUpCard(
                nextUp = nextUp,
                animeTitle = state.title,
                posterUrl = state.currentEpisodeItem?.thumbnail ?: state.details?.coverImage?.best(),
                onPlayNow = viewModel::playNextNow,
                onCancel = viewModel::cancelNextUp,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // ── error overlay (retry + switch server — report removed) ───────
        if (state.playbackError != null && !state.streamLoading && state.nextUp == null) {
            PlayerErrorOverlay(
                message = state.playbackError ?: "",
                canSwitchServer = state.availableServers.size > 1,
                onReload = { viewModel.reloadStream(refresh = true) },
                onSwitchServer = {
                    val others = state.availableServers.filter {
                        !it.name.equals(state.streamServer, ignoreCase = true)
                    }
                    others.firstOrNull()?.let { viewModel.setStreamServer(it.name) }
                },
                modifier = Modifier.matchParentSize(),
            )
        }

        // ── controls ───────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = (controlsVisible || !state.isPlaying || isScrubbing) && quickMenu == null,
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
                onOpenQuickMenu = { quickMenu = it },
                onScrubbingChange = { isScrubbing = it },
                onFeedback = { feedback = it },
                orientationLocked = orientationLocked,
                onToggleOrientationLock = {
                    orientationLocked = !orientationLocked
                    val a = activity
                    if (a != null) {
                        if (orientationLocked) {
                            val rotation = a.windowManager.defaultDisplay.rotation
                            a.requestedOrientation = when (rotation) {
                                android.view.Surface.ROTATION_90, android.view.Surface.ROTATION_270 ->
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                            }
                        } else {
                            a.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                    }
                },
                onShare = { shareEpisode(context, state) },
            )
        }

        // ── quick menus (speed / quality / audio / subtitles / servers /
        //    list / sleep timer) ───────────────────────────────────────────
        quickMenu?.let { menu ->
            PlayerQuickMenu(
                menu = menu,
                state = state,
                viewModel = viewModel,
                onDismiss = { quickMenu = null },
                modifier = Modifier.matchParentSize(),
            )
        }

        // ── mini progress bar while controls are hidden (site setting) ────
        if (!controlsVisible && quickMenu == null && !settingsOpen && SettingsState.miniProgressBar && state.streamUrl != null) {
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

/** Which quick menu is open in the player. */
enum class QuickMenu { SPEED, QUALITY, AUDIO, SUBTITLES, SERVERS, LIST, SLEEP }

/**
 * Video surface: TextureView with the player attached (so screenshots work),
 * letterboxed to the stream's aspect ratio (site: contain).
 *
 * onRelease passes the view to [WatchViewModel.detachSurface], which is
 * identity-guarded — releasing the OLD surface after the NEW fullscreen
 * surface attached can never clear the new one.
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
            onRelease = { view -> viewModel.detachSurface(view) },
        )
    }
}

/** Thin action-colored progress bar shown while the controls are hidden. */
@Composable
private fun MiniProgressBar(positionMs: Long, durationMs: Long, modifier: Modifier = Modifier) {
    val theme = LocalAnikageTheme.current
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
    onOpenQuickMenu: (QuickMenu?) -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    onFeedback: (PlayerFeedback?) -> Unit,
    orientationLocked: Boolean,
    onToggleOrientationLock: () -> Unit,
    onShare: () -> Unit,
) {
    val context = LocalContext.current
    val theme = LocalAnikageTheme.current
    val seekAmount = SettingsState.seekAmountSec.coerceIn(5, 60)

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
        // ── top row: back + title + subscribe + share ───────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isFullscreen) {
                PlayerIconButton(onClick = onBack, contentDescription = "Exit fullscreen") {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                }
            }
            Text(
                text = "${state.title} — EP ${state.episode}",
                style = WebTextStyles.sm,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            if (state.playingDownloaded) {
                Text(
                    text = "Downloaded",
                    style = WebTextStyles.xs,
                    color = Color(0xFF34D399),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x3334D399))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            // Subscribe (directive #12) — also on the details page.
            PlayerIconButton(
                onClick = viewModel::toggleSubscription,
                contentDescription = if (state.subscribed) "Unsubscribe" else "Subscribe",
            ) {
                Icon(
                    if (state.subscribed) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                    null,
                    tint = if (state.subscribed) theme.action else Color.White,
                    modifier = Modifier.size(21.dp),
                )
            }
            if (isFullscreen) {
                PlayerIconButton(onClick = onShare, contentDescription = "Share") {
                    Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        // ── center play/pause (site: media-play-button) ──────────────────
        if (!state.isPlaying && !state.isBuffering && state.streamUrl != null && state.nextUp == null) {
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
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // transport (site order: prev, -N, play, +N, next)
                PlayerIconButton(
                    onClick = viewModel::previousEpisode,
                    enabled = state.episode > 1,
                    contentDescription = "Previous episode",
                ) {
                    Icon(Icons.Filled.SkipPrevious, null, tint = Color.White)
                }
                SeekIconButton(
                    amountSec = seekAmount,
                    forward = false,
                    onClick = {
                        viewModel.seekBy(-seekAmount * 1000L)
                        onFeedback(PlayerFeedback.SeekBack(seekAmount))
                    },
                )
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
                SeekIconButton(
                    amountSec = seekAmount,
                    forward = true,
                    onClick = {
                        viewModel.seekBy(seekAmount * 1000L)
                        onFeedback(PlayerFeedback.SeekForward(seekAmount))
                    },
                )
                PlayerIconButton(
                    onClick = viewModel::nextEpisode,
                    enabled = state.episode < state.totalEpisodes,
                    contentDescription = "Next episode",
                ) {
                    Icon(Icons.Filled.SkipNext, null, tint = Color.White)
                }

                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))

                // right cluster — every key function lives here:
                // speed, subtitles, quality, servers, audio, list, mute,
                // sleep, orientation lock, PiP, screenshot, settings, full.
                SpeedPill(
                    speed = state.speed,
                    onClick = { onOpenQuickMenu(QuickMenu.SPEED) },
                )
                if (state.textTracks.isNotEmpty() || viewModel.liveCaptionsSupported()) {
                    PlayerIconButton(
                        onClick = {
                            if (state.textTracks.size > 1 || viewModel.liveCaptionsSupported()) {
                                onOpenQuickMenu(QuickMenu.SUBTITLES)
                            } else viewModel.toggleCaptions()
                        },
                        contentDescription = "Subtitles",
                    ) {
                        Icon(
                            if (state.captionsOn || state.liveCaptionsActive) Icons.Filled.Subtitles else Icons.Filled.SubtitlesOff,
                            null,
                            tint = if (state.captionsOn || state.liveCaptionsActive) theme.action else Color.White,
                        )
                    }
                }
                QualityPill(
                    label = state.qualities.firstOrNull { it.isSelected }?.label ?: "Auto",
                    onClick = { onOpenQuickMenu(QuickMenu.QUALITY) },
                )
                // Servers (directive #7): switch without leaving the video.
                if (state.availableServers.size > 1) {
                    PlayerIconButton(
                        onClick = { onOpenQuickMenu(QuickMenu.SERVERS) },
                        contentDescription = "Servers",
                    ) {
                        Icon(Icons.Filled.Dns, null, tint = Color.White, modifier = Modifier.size(21.dp))
                    }
                }
                if (state.audioTracks.size > 1) {
                    PlayerIconButton(
                        onClick = { onOpenQuickMenu(QuickMenu.AUDIO) },
                        contentDescription = "Audio track",
                    ) {
                        Icon(Icons.Filled.VolumeUp, null, tint = Color.White)
                    }
                }
                PlayerIconButton(
                    onClick = { onOpenQuickMenu(QuickMenu.LIST) },
                    contentDescription = "Add to list",
                ) {
                    Icon(
                        if (state.listStatus != null) Icons.Filled.BookmarkAdded else Icons.Filled.BookmarkBorder,
                        null,
                        tint = if (state.listStatus != null) theme.action else Color.White,
                        modifier = Modifier.size(21.dp),
                    )
                }
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
                // Sleep timer (directive #16).
                PlayerIconButton(
                    onClick = { onOpenQuickMenu(QuickMenu.SLEEP) },
                    contentDescription = "Sleep timer",
                ) {
                    Icon(
                        Icons.Filled.Bedtime, null,
                        tint = if (state.sleepTimerSec > 0) theme.action else Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (isFullscreen) {
                    // Orientation lock (directive #16).
                    PlayerIconButton(
                        onClick = onToggleOrientationLock,
                        contentDescription = if (orientationLocked) "Unlock orientation" else "Lock orientation",
                    ) {
                        Icon(
                            Icons.Filled.ScreenLockPortrait, null,
                            tint = if (orientationLocked) theme.action else Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    PlayerIconButton(
                        onClick = { takeScreenshot(context, viewModel, state) },
                        contentDescription = "Screenshot",
                    ) {
                        Icon(Icons.Filled.CameraAlt, null, tint = Color.White)
                    }
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
            if (state.sleepTimerSec > 0) {
                Text(
                    text = "Sleep timer: ${formatPlayerTime(state.sleepTimerSec * 1000L)} left",
                    style = WebTextStyles.xs,
                    color = Color(0xCCFFFFFF),
                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
                )
            }
        }
    }
}

/**
 * Seek button with the CONFIGURABLE amount — uses the exact material icon
 * for 5/10/30s, and a generic replay/forward icon with an inline "Ns" label
 * for custom amounts.
 */
@Composable
private fun SeekIconButton(
    amountSec: Int,
    forward: Boolean,
    onClick: () -> Unit,
) {
    val icon: ImageVector = when {
        forward -> when (amountSec) {
            5 -> Icons.Filled.Forward5
            10 -> Icons.Filled.Forward10
            30 -> Icons.Filled.Forward30
            else -> Icons.Filled.Forward
        }
        else -> when (amountSec) {
            5 -> Icons.Filled.Replay5
            10 -> Icons.Filled.Replay10
            30 -> Icons.Filled.Replay30
            else -> Icons.Filled.Replay
        }
    }
    if (amountSec !in listOf(5, 10, 30)) {
        // custom amount: icon + small overlay label
        PlayerIconButton(onClick = onClick, contentDescription = "${if (forward) "Forward" else "Back"} $amountSec seconds") {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
                Text(
                    text = amountSec.toString(),
                    style = WebTextStyles.xs2,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .graphicsLayer { translationY = -8.dp.toPx() }
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White)
                        .padding(horizontal = 3.dp, vertical = 0.dp),
                )
            }
        }
    } else {
        PlayerIconButton(onClick = onClick, contentDescription = "${if (forward) "Forward" else "Back"} $amountSec seconds") {
            Icon(icon, null, tint = Color.White)
        }
    }
}

/** Speed pill — shows the live speed, opens the quick menu. */
@Composable
private fun SpeedPill(speed: Float, onClick: () -> Unit) {
    val label = if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${"%.2f".format(speed).trimEnd('0').trimEnd('.')}x"
    Text(
        text = label,
        style = WebTextStyles.xs,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x1A000000))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
    )
}

/** Quality pill — shows the active rendition, opens the quick menu. */
@Composable
private fun QualityPill(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = WebTextStyles.xs,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x1A000000))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
    )
}

/** Share the episode (site: share button -> share sheet). */
private fun shareEpisode(context: android.content.Context, state: WatchUiState) {
    val url = "https://anikage.cc/anime/watch/${state.slug ?: state.details?.id ?: ""}?ep=${state.episode}"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TITLE, state.title)
        putExtra(
            Intent.EXTRA_TEXT,
            "Watch ${state.title} — Episode ${state.episode} on Anikage: $url",
        )
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Share")) }
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
//  Feedback (site: vjs-feedback-island) — directive #3 rebuild
// ---------------------------------------------------------------------------

sealed interface PlayerFeedback {
    /** Double-tap / button seek feedback; [seconds] uses the configured amount. */
    data class SeekForward(val seconds: Int) : PlayerFeedback
    data class SeekBack(val seconds: Int) : PlayerFeedback
    data class HoldSpeed(val speed: Float) : PlayerFeedback
}

/**
 * THE double-tap seek feedback (directive #3): one polished side island —
 * a dark translucent circle with a replay/forward icon and the skip amount
 * as a small chip BELOW it. Spring pop-in, quick fade-out, nothing else.
 * Uses the user's CONFIGURED seek amount; no random floating text.
 */
@Composable
private fun SeekFeedbackIsland(
    seconds: Int,
    forward: Boolean,
    modifier: Modifier = Modifier,
) {
    val pop = remember { androidx.compose.animation.core.Animatable(0.55f) }
    LaunchedEffect(seconds, forward) {
        pop.snapTo(0.55f)
        pop.animateTo(
            1f,
            androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
            ),
        )
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
            alpha = ((pop.value - 0.55f) / 0.45f).coerceIn(0f, 1f)
        },
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0x99000000))
                .border(1.dp, Color(0x40FFFFFF), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // Double chevron for direction + the configured seconds badge.
            Row(horizontalArrangement = Arrangement.Center) {
                if (!forward) {
                    Icon(Icons.Filled.ChevronLeft, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Icon(Icons.Filled.ChevronLeft, null, tint = Color(0xB3FFFFFF), modifier = Modifier.size(22.dp))
                } else {
                    Icon(Icons.Filled.ChevronRight, null, tint = Color(0xB3FFFFFF), modifier = Modifier.size(22.dp))
                    Icon(Icons.Filled.ChevronRight, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            Text(
                text = "${seconds}s",
                style = WebTextStyles.xs2,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x33FFFFFF))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

/** Hold-speed pill (top center) — "2x speed" while the surface is held. */
@Composable
private fun PlayerFeedbackIsland(feedback: PlayerFeedback, modifier: Modifier = Modifier) {
    val label = when (feedback) {
        is PlayerFeedback.SeekForward -> "+${feedback.seconds}s"
        is PlayerFeedback.SeekBack -> "-${feedback.seconds}s"
        is PlayerFeedback.HoldSpeed -> "${feedback.speed}x speed"
    }
    Row(
        modifier = modifier
            .graphicsLayer { alpha = 1f }
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xB3000000))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (feedback) {
            is PlayerFeedback.SeekBack -> Icon(
                Icons.Filled.ChevronLeft, contentDescription = null, tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            is PlayerFeedback.SeekForward -> Icon(
                Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            is PlayerFeedback.HoldSpeed -> Icon(
                Icons.Filled.Speed, contentDescription = null, tint = Color.White,
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

/** Brightness / volume gesture indicator (vertical swipe on an edge). */
@Composable
private fun GestureLevelIndicator(
    icon: ImageVector,
    level: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xB3000000))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x33FFFFFF)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(level.coerceIn(0f, 1f))
                    .fillMaxSize()
                    .background(Color.White),
            )
        }
    }
}

// ---------------------------------------------------------------------------
//  Quick menus — speed / quality / audio / subtitles / servers / list / sleep
// ---------------------------------------------------------------------------

/** RECORD_AUDIO granted check (needed before starting live captions). */
private fun android.content.Context.hasMicPermission(): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(
        this, android.Manifest.permission.RECORD_AUDIO,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

@Composable
private fun PlayerQuickMenu(
    menu: QuickMenu,
    state: WatchUiState,
    viewModel: WatchViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAnikageTheme.current
    val context = LocalContext.current
    val title = when (menu) {
        QuickMenu.SPEED -> "Playback speed"
        QuickMenu.QUALITY -> "Quality"
        QuickMenu.AUDIO -> "Audio"
        QuickMenu.SUBTITLES -> "Subtitles / CC"
        QuickMenu.SERVERS -> "Servers"
        QuickMenu.LIST -> "My list"
        QuickMenu.SLEEP -> "Sleep timer"
    }
    Box(
        modifier = modifier
            .background(Color(0x88000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xD90D0D0D))
                .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close menu",
                    tint = Color(0xA6FFFFFF),
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismiss)
                        .padding(4.dp),
                )
                Text(
                    text = title,
                    style = WebTextStyles.sm.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            when (menu) {
                QuickMenu.SPEED -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { s ->
                            QuickChip(
                                label = if (s == s.toInt().toFloat()) "${s.toInt()}x" else "${s}x",
                                selected = kotlin.math.abs(state.speed - s) < 0.01f,
                                onClick = { viewModel.setSpeed(s) },
                            )
                        }
                    }
                    QuickMenuHint("Applies instantly and is remembered for the next episode.")
                }
                QuickMenu.QUALITY -> {
                    if (state.qualities.isEmpty()) {
                        QuickMenuHint("Renditions appear once the stream starts.")
                    } else {
                        state.qualities.forEach { q ->
                            QuickMenuRow(
                                label = q.label,
                                selected = q.isSelected,
                                onClick = { viewModel.selectQuality(q) },
                            )
                        }
                        QuickMenuHint("Your pick is saved and reused on the next episode.")
                    }
                }
                QuickMenu.AUDIO -> {
                    state.audioTracks.forEach { t ->
                        QuickMenuRow(
                            label = t.label,
                            selected = t.isSelected,
                            onClick = { viewModel.selectAudioTrack(t) },
                        )
                    }
                    if (state.audioTracks.isEmpty()) QuickMenuHint("This stream has a single audio track.")
                }
                QuickMenu.SUBTITLES -> {
                    // Microphone permission gate for live captions (directive
                    // #15): the runtime request happens right here, and the
                    // captions start the moment it's granted.
                    val micPermission = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission(),
                    ) { granted ->
                        if (granted) viewModel.startLiveCaptions()
                        else viewModel.reportLiveCaptionPermissionDenied()
                    }
                    if (state.textTracks.isNotEmpty()) {
                        QuickMenuRow(
                            label = "Off",
                            selected = !state.captionsOn,
                            onClick = { viewModel.selectTextTrack(null) },
                        )
                        state.textTracks.forEach { t ->
                            QuickMenuRow(
                                label = t.label,
                                selected = t.isSelected,
                                onClick = { viewModel.selectTextTrack(t) },
                            )
                        }
                    } else if (!viewModel.liveCaptionsSupported()) {
                        QuickMenuHint("No subtitle tracks in this stream.")
                    }
                    // ── Live Captions (directive #15): a REAL on-device
                    //    speech-recognition caption track for the audio.
                    if (viewModel.liveCaptionsSupported()) {
                        QuickMenuSectionLabel("LIVE CAPTIONS")
                        QuickMenuRow(
                            label = if (state.liveCaptionsActive) "Live captions — on" else "Live captions (on-device)",
                            selected = state.liveCaptionsActive,
                            onClick = {
                                if (state.liveCaptionsActive) {
                                    viewModel.stopLiveCaptions()
                                } else if (context.hasMicPermission()) {
                                    viewModel.startLiveCaptions()
                                } else {
                                    micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
                            },
                        )
                        if (state.liveCaptionsError != null) {
                            QuickMenuHint(state.liveCaptionsError ?: "")
                        } else {
                            QuickMenuHint(
                                "Recognizes the playing audio on this device and captions it live. " +
                                    "Works best with speaker playback; uses your caption style.",
                            )
                        }
                    }
                    if (state.textTracks.isNotEmpty()) {
                        QuickMenuHint("Style them in Settings -> Captions.")
                    }
                }
                QuickMenu.SERVERS -> {
                    state.availableServers.forEach { server ->
                        val active = server.name.equals(state.streamServer, ignoreCase = true) ||
                            server.id.equals(state.streamServer, ignoreCase = true)
                        QuickMenuRow(
                            label = server.name,
                            selected = active,
                            trailing = {
                                // Observed health dot: green = streaming ok,
                                // red = failed this session, dim = unchecked.
                                val dot = when (server.healthy) {
                                    true -> Color(0xFF34D399)
                                    false -> Color(0xFFF87171)
                                    null -> Color(0x59FFFFFF)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(dot),
                                )
                            },
                            onClick = {
                                if (!active) {
                                    viewModel.setStreamServer(server.name)
                                    onDismiss()
                                }
                            },
                        )
                    }
                    QuickMenuHint("Failing servers are skipped automatically.")
                }
                QuickMenu.LIST -> {
                    listOf(
                        "watching" to "Watching",
                        "planned" to "Plan to Watch",
                        "completed" to "Completed",
                        "on_hold" to "On Hold",
                        "dropped" to "Dropped",
                    ).forEach { (key, label) ->
                        QuickMenuRow(
                            label = label,
                            selected = state.listStatus == key,
                            onClick = {
                                viewModel.setListStatus(if (state.listStatus == key) null else key)
                            },
                        )
                    }
                    QuickMenuRow(
                        label = "Remove from list",
                        selected = false,
                        destructive = true,
                        onClick = { viewModel.setListStatus(null) },
                    )
                    QuickMenuHint("Saved on this device${if (SettingsState.incognito) " (incognito blocks changes)" else ""}.")
                }
                QuickMenu.SLEEP -> {
                    if (state.sleepTimerSec > 0) {
                        QuickMenuRow(
                            label = "Off (currently ${formatPlayerTime(state.sleepTimerSec * 1000L)})",
                            selected = false,
                            onClick = { viewModel.setSleepTimer(0) },
                        )
                    }
                    listOf(5, 10, 15, 20, 30, 45, 60, 90).forEach { minutes ->
                        QuickMenuRow(
                            label = if (minutes >= 60) "${minutes / 60} hour${if (minutes >= 120) "s" else ""}" else "$minutes minutes",
                            selected = false,
                            onClick = { viewModel.setSleepTimer(minutes) },
                        )
                    }
                    QuickMenuHint("Playback pauses when the timer runs out.")
                }
            }
        }
    }
}

@Composable
private fun QuickMenuSectionLabel(text: String) {
    Text(
        text = text,
        style = WebTextStyles.xs2,
        color = Color(0x70FFFFFF),
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun QuickMenuRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    destructive: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val theme = LocalAnikageTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0x14FFFFFF) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            style = WebTextStyles.sm,
            color = when {
                destructive -> Color(0xFFFCA5A5)
                selected -> theme.action
                else -> Color(0xFFD4D4D8)
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            trailing()
        } else if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = theme.action,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun QuickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val theme = LocalAnikageTheme.current
    Text(
        text = label,
        style = WebTextStyles.sm,
        color = if (selected) theme.actionFg else Color(0xFFD4D4D8),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) theme.action else Color(0x14FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun QuickMenuHint(text: String) {
    Text(
        text = text,
        style = WebTextStyles.xs,
        color = Color(0x70FFFFFF),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

// ---------------------------------------------------------------------------
//  Up-next card (autonext countdown)
// ---------------------------------------------------------------------------

@Composable
private fun NextUpCard(
    nextUp: NextUpState,
    animeTitle: String,
    posterUrl: String?,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAnikageTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xE60D0D0D))
            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        posterUrl?.let {
            coil.compose.AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .size(width = 96.dp, height = 54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x14FFFFFF)),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.width(190.dp),
        ) {
            Text(
                text = "Up next",
                style = WebTextStyles.xs,
                color = theme.action,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Text(
                text = nextUp.title,
                style = WebTextStyles.sm,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Playing in ${nextUp.countdownSec}s",
                style = WebTextStyles.xs,
                color = Color(0xFFA1A1AA),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = "Play now",
                    style = WebTextStyles.xs,
                    color = theme.actionFg,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.action)
                        .clickable(onClick = onPlayNow)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Text(
                    text = "Cancel",
                    style = WebTextStyles.xs,
                    color = Color(0xFFD4D4D8),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x1FFFFFFF))
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Skip intro / outro / filler (site: Skip Opening / Skip Ending buttons)
// ---------------------------------------------------------------------------

@Composable
private fun SkipButtons(
    state: WatchUiState,
    visible: Boolean,
    onSkipIntro: () -> Unit,
    onSkipOutro: () -> Unit,
    onSkipFiller: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inIntro = state.hasIntro && state.positionMs >= state.introStartMs && state.positionMs < state.introEndMs
    val inOutro = state.hasOutro && state.positionMs >= state.outroStartMs && state.positionMs < state.outroEndMs
    val nearIntroStart = state.hasIntro &&
        state.positionMs < state.introStartMs && state.introStartMs - state.positionMs < 30_000L
    if (!visible || state.streamUrl == null || (!inIntro && !inOutro && !nearIntroStart && !state.isCurrentFiller)) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        if (state.isCurrentFiller) {
            SiteSkipChip(
                label = "Skip Filler",
                icon = Icons.Filled.Tune,
                onClick = onSkipFiller,
                accent = Color(0xFFFB923C),
            )
        }
        if (inIntro || nearIntroStart) {
            SiteSkipChip(label = "Skip Opening", icon = Icons.Filled.NextPlan, onClick = onSkipIntro)
        }
        if (inOutro) {
            SiteSkipChip(label = "Skip Ending", icon = Icons.Filled.NextPlan, onClick = onSkipOutro)
        }
    }
}

@Composable
private fun SiteSkipChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    accent: Color = Color.White,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xE6000000))
            .border(1.dp, Color(0x4DFFFFFF), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        Text(label, style = WebTextStyles.sm, color = accent, fontWeight = FontWeight.SemiBold)
    }
}

/** Clear filler indicator chip (top-right of the player). */
@Composable
private fun FillerChip(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC000000))
            .border(1.dp, Color(0x59FB923C), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Color(0xFFFB923C)),
        )
        Text(
            text = "FILLER",
            style = WebTextStyles.xs2,
            color = Color(0xFFFDBA74),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

/** Offline (downloaded) playback badge. */
@Composable
private fun OfflineChip(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC000000))
            .border(1.dp, Color(0x5934D399), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Color(0xFF34D399)),
        )
        Text(
            text = "OFFLINE",
            style = WebTextStyles.xs2,
            color = Color(0xFF6EE7B7),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

// ---------------------------------------------------------------------------
//  Captions (site: custom captions renderer with user styles + position)
// ---------------------------------------------------------------------------

@Composable
private fun CaptionOverlay(
    cues: List<Cue>,
    styles: CaptionStyles,
    fullscreen: Boolean,
    playerHeight: Dp,
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

/**
 * LIVE captions line (directive #15): the current recognition phrase with
 * the user's caption styling, marked with a small "CC LIVE" tag so it's
 * clearly the live track (not a subtitle file).
 */
@Composable
private fun LiveCaptionOverlay(
    text: String,
    styles: CaptionStyles,
    modifier: Modifier = Modifier,
) {
    val textColor = Color(styles.textColor).copy(alpha = styles.textOpacity)
    val cueBg = Color(styles.textBg).let {
        if (styles.textBgOpacity > 0f) it.copy(alpha = styles.textBgOpacity) else Color(0xAA000000)
    }
    val fontSize = 15.sp * styles.fontSize
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0x66000000))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Icon(
                Icons.Default.Subtitles, null,
                tint = Color(0xFFA1A1AA),
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = "LIVE",
                style = WebTextStyles.xs2,
                color = Color(0xFFA1A1AA),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
        Text(
            text = text,
            style = WebTextStyles.base.copy(
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = fontSize * 1.25f,
            ),
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(cueBg)
                .padding(horizontal = 10.dp, vertical = 4.dp),
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

/**
 * In-player failure — retry (fresh token) + switch server. Never silent;
 * the useless Report action is gone, replaced by a real fix action.
 */
@Composable
fun PlayerErrorOverlay(
    message: String,
    canSwitchServer: Boolean,
    onReload: () -> Unit,
    onSwitchServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color(0xB3000000))
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .clickable(onClick = onReload)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                Text("Fix it", style = WebTextStyles.xs, color = Color.Black, fontWeight = FontWeight.SemiBold)
            }
            if (canSwitchServer) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0x4DFFFFFF), RoundedCornerShape(10.dp))
                        .clickable(onClick = onSwitchServer)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text("Switch server", style = WebTextStyles.xs, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
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

