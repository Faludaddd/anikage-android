package com.anikage.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.anikage.app.Config
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.api.AnikageComment
import com.anikage.app.ui.components.ErrorOrEmptyState
import com.anikage.app.ui.components.LoadingSpinner
import java.time.Duration
import java.time.Instant

/**
 * Watch screen: fullscreen ExoPlayer + episode switcher + a comments panel
 * (mirrors the site's comment section under the player).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun WatchScreen(
    animeId: Int,
    initialEpisode: Int,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val repo = remember { AnikageRepository.get(context) }
    val viewModel: WatchViewModel = viewModel(
        factory = WatchViewModel.factory(app, repo, animeId, initialEpisode)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player = remember { viewModel.player() }

    var showComments by remember { mutableStateOf(false) }

    val activity = context as? android.app.Activity
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation
        if (Config.Player.FORCE_LANDSCAPE_FULLSCREEN) {
            activity?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            // Save progress before leaving
            val position = player.currentPosition
            val duration = player.duration.coerceAtLeast(0L)
            if (duration > 0) viewModel.saveProgress(position, duration)
            if (Config.Player.FORCE_LANDSCAPE_FULLSCREEN) {
                activity?.requestedOrientation =
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            // Enter PiP if available and a video is actually playing
            if (Config.Player.AUTO_PIP_ON_LEAVE &&
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                player.isPlaying
            ) {
                try {
                    val params = android.app.PictureInPictureParams.Builder()
                        .setAspectRatio(android.util.Rational(16, 9))
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
                    controllerAutoShow = true
                    controllerHideOnTouch = true
                    setShowNextButton(true)
                    setShowPreviousButton(true)
                }
            },
        )

        // Top-left back + top-right comments toggle
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            if (Config.Player.COMMENTS_ENABLED) {
                IconButton(
                    onClick = { showComments = !showComments },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                ) {
                    Icon(Icons.Filled.ChatBubble, contentDescription = "Comments", tint = Color.White)
                }
            }
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
                    .padding(bottom = 88.dp),
            )
        }

        // Stream loading / fallback notice
        if (state.streamLoading) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Loading stream…",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        // Comments panel (right-side sheet, like the site's comment section)
        AnimatedVisibility(
            visible = showComments,
            enter = slideInHorizontally(tween(220)) { it / 2 } + fadeIn(tween(220)),
            exit = slideOutHorizontally(tween(180)) { it / 2 } + fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(0.9f)
                .padding(vertical = 56.dp, horizontal = 12.dp),
        ) {
            CommentsPanel(
                state = state.comments,
                episode = state.episode,
                viewCount = state.viewCount,
                onRefresh = viewModel::refreshComments,
                onClose = { showComments = false },
            )
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
            .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrev, enabled = episode > 1) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous episode", tint = Color.White)
        }
        Text(
            text = "Episode $episode / $total",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
        IconButton(onClick = onNext, enabled = episode < total) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next episode", tint = Color.White)
        }
    }
}

// ---------------------------------------------------------------------------
//  Comments panel — mirrors the site's episode comment section
// ---------------------------------------------------------------------------

private val AvatarBase = "https://auth.anikage.cc"

@Composable
private fun CommentsPanel(
    state: CommentsUiState,
    episode: Int,
    viewCount: Long?,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        color = Color(0xE6060912),
        contentColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .width(320.dp)
            .fillMaxHeight(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildString {
                        append("Comments — Ep $episode")
                        viewCount?.let { append("  ·  $it views") }
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh comments", tint = Color.White, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close comments", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            // List
            if (state.loading && state.comments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            } else if (state.comments.isEmpty()) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No comments yet on this episode.",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    state = rememberLazyListState(),
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 4.dp,
                    ),
                ) {
                    items(state.comments, key = { it.id }) { comment ->
                        CommentRow(comment)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: AnikageComment) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Avatar
        val avatarUrl = comment.author?.avatar?.let { path ->
            if (path.startsWith("http")) path else "$AvatarBase$path"
        }
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(0xFF161D2E), CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(0xFFA855F7).copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (comment.author?.displayName ?: "?").take(1).uppercase(),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.author?.displayName ?: comment.author?.username ?: "Anon",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (comment.author?.role == "admin" || comment.author?.role == "mod")
                        Color(0xFFE64158) else Color.White,
                )
                if (comment.isPinned) {
                    Spacer(Modifier.width(6.dp))
                    Text("PIN", color = Color(0xFFFACC15), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = relativeTime(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "▲ ${comment.likeCount}  ▼ ${comment.dislikeCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                )
                if (comment.replyCount > 0) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "${comment.replyCount} replies",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
                if (comment.isSpoiler) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "SPOILER",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFACC15),
                        fontSize = 9.sp,
                    )
                }
            }
        }
    }
}

/** "2h ago" style timestamps from ISO-8601 strings. */
private fun relativeTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val instant = Instant.parse(iso)
        val duration = Duration.between(instant, Instant.now())
        val minutes = duration.toMinutes()
        when {
            minutes < 1 -> "now"
            minutes < 60 -> "${minutes}m"
            minutes < 60 * 24 -> "${minutes / 60}h"
            minutes < 60 * 24 * 30 -> "${minutes / (60 * 24)}d"
            minutes < 60 * 24 * 365 -> "${minutes / (60 * 24 * 30)}mo"
            else -> "${minutes / (60 * 24 * 365)}y"
        }
    } catch (_: Exception) {
        iso.take(10)
    }
}
