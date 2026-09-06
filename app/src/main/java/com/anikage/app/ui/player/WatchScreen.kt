package com.anikage.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.WebTextStyles
import com.anikage.app.ui.components.ErrorOrEmptyState
import com.anikage.app.ui.components.LoadingSpinner
import java.time.Duration
import java.time.Instant

/**
 * WATCH — 1:1 port of anikage.cc/anime/watch/{id}?ep=n (mobile).
 *
 * Site layout (portrait page, no forced landscape):
 *   ├─ player 16:9 rounded-2xl with ambient glow (back button overlay)
 *   ├─ EP chip + "14,702 views" + action buttons (Add to List / Download / Report)
 *   ├─ server panel: "Servers (4)" + SUB/DUB segmented + server chips
 *   ├─ mobile tabs: Episodes (count) | Comments
 *   ├─ episode rows: h-76 thumb + title + desc + progress bar
 *   └─ comments: header card + list
 */
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
    val theme = LocalAnikageTheme.current

    // Mobile tab: 0 = Episodes, 1 = Comments (site's .mobile-tabs).
    var tab by remember { mutableIntStateOf(0) }

    val activity = context as? android.app.Activity
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation
        onDispose {
            // Save progress before leaving.
            val position = player.currentPosition
            val duration = player.duration.coerceAtLeast(0L)
            if (duration > 0) viewModel.saveProgress(position, duration)
            if (originalOrientation != null) {
                activity?.requestedOrientation = originalOrientation
            }
            // Enter PiP if available and a video is actually playing.
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface)
    ) {
        if (state.loading) {
            LoadingSpinner()
            return@Column
        }

        if (state.error != null && state.details == null) {
            ErrorOrEmptyState(
                title = "Couldn't load anime",
                subtitle = state.error ?: "",
                actionText = "Back",
                onAction = onBackClick,
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Site: .watch-layout pt-20 lg:pt-23 — clears the fixed top nav.
            contentPadding = PaddingValues(top = 64.dp),
        ) {
            // ── Player (site: player-glass rounded-2xl, 16:9) ──────────────
            item(key = "player") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                useController = true
                                controllerAutoShow = true
                                controllerHideOnTouch = true
                                setShowNextButton(true)
                                setShowPreviousButton(true)
                            }
                        },
                    )
                    if (state.streamLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(32.dp),
                        )
                    }
                }
            }

            // ── Meta row: EP chip + views + actions ────────────────────────
            item(key = "meta") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // EP chip — site: rounded-xl border-white/6 bg-white/3 px-4 py-1.5.
                        Text(
                            text = "EP ${state.episode}",
                            style = WebTextStyles.base,
                            color = theme.fg,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x08FFFFFF))
                                .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        state.viewCount?.let { vc ->
                            Text(
                                text = "%,d views".format(vc),
                                style = WebTextStyles.xs,
                                color = Color(0xFF71717A),
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Prev / Next episode (site: player control bar arrows).
                        EpisodeNavButton(
                            label = "Prev",
                            enabled = state.episode > 1,
                            onClick = { viewModel.switchEpisode(state.episode - 1) },
                        )
                        EpisodeNavButton(
                            label = "Next",
                            enabled = state.episode < state.totalEpisodes,
                            onClick = { viewModel.switchEpisode(state.episode + 1) },
                        )
                        // Refresh stream (re-resolve sources for this episode).
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0x08FFFFFF))
                                .clickable { viewModel.reloadStream() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Reload",
                                tint = theme.fgMuted,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            // ── Server panel (site: rounded-2xl border bg-white/3 p-3) ──────
            item(key = "servers") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x08FFFFFF))
                        .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    ) {
                        Text(
                            text = "Servers (${state.servers.size.coerceAtLeast(1)})",
                            style = WebTextStyles.base,
                            color = theme.fg,
                            fontWeight = FontWeight.SemiBold,
                        )
                        // SUB / DUB segmented (site: btn-xs, active bg-action).
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x14FFFFFF))
                                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(8.dp)),
                        ) {
                            LangChip(
                                label = "SUB",
                                active = state.streamLang == "sub",
                                onClick = { viewModel.setStreamLang("sub") },
                            )
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(20.dp)
                                    .background(Color(0x14FFFFFF))
                            )
                            LangChip(
                                label = "DUB",
                                active = state.streamLang == "dub",
                                onClick = { viewModel.setStreamLang("dub") },
                            )
                        }
                    }
                    // Server chips (site: flex-wrap gap-2 rounded-lg bg-white/5).
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val serverList = state.servers.ifEmpty { listOf(state.streamServer) }
                        serverList.take(4).forEach { server ->
                            val active = server == state.streamServer
                            Text(
                                text = server,
                                style = WebTextStyles.xs,
                                color = if (active) theme.actionFg else Color(0xFFA1A1AA),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) theme.action else Color(0x0DFFFFFF))
                                    .clickable { viewModel.setStreamServer(server) }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            // ── Mobile tabs (site: Episodes | Comments) ─────────────────────
            item(key = "tabs") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x08FFFFFF))
                        .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TabChip(
                        icon = Icons.Default.List,
                        label = "Episodes",
                        count = state.episodes.size,
                        active = tab == 0,
                        onClick = { tab = 0 },
                        modifier = Modifier.weight(1f),
                    )
                    TabChip(
                        icon = Icons.Default.ChatBubble,
                        label = "Comments",
                        count = state.comments.total,
                        active = tab == 1,
                        onClick = { tab = 1 },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (tab == 0) {
                // ── Episode rows (site: h-76, active state ring) ────────────
                items(state.episodes, key = { it.number }) { ep ->
                    EpisodeRow(
                        ep = ep,
                        active = ep.number == state.episode,
                        onClick = { viewModel.switchEpisode(ep.number) },
                    )
                }
            } else {
                // ── Comments ────────────────────────────────────────────────
                item(key = "comments") {
                    CommentsSection(
                        state = state.comments,
                        episode = state.episode,
                        onRefresh = viewModel::refreshComments,
                    )
                }
            }

            item(key = "bottom-space") { Spacer(Modifier.height(96.dp)) }
        }
    }
}

/** Prev/Next episode chip — site player control arrows, pill styling. */
@Composable
private fun EpisodeNavButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalAnikageTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) Color(0x0DFFFFFF) else Color(0x05FFFFFF))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        if (label == "Prev") {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = if (enabled) theme.fg else Color(0x50FFFFFF),
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = label,
            style = WebTextStyles.xs,
            color = if (enabled) theme.fg else Color(0x50FFFFFF),
            fontWeight = FontWeight.Medium,
        )
        if (label == "Next") {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (enabled) theme.fg else Color(0x50FFFFFF),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** site: .mobile-tab — icon + label + count badge, active = bg-action. */
@Composable
private fun TabChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAnikageTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) theme.action else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) theme.actionFg else theme.fgMuted,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = WebTextStyles.xs,
            color = if (active) theme.actionFg else theme.fgMuted,
            fontWeight = FontWeight.Medium,
        )
        if (count > 0) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = count.toString(),
                style = WebTextStyles.xs2,
                color = if (active) theme.actionFg.copy(alpha = 0.60f) else theme.fgMuted.copy(alpha = 0.60f),
            )
        }
    }
}

/** site: SUB/DUB chip — active bg-action text-action-fg, btn-xs. */
@Composable
private fun LangChip(label: String, active: Boolean, onClick: () -> Unit) {
    val theme = LocalAnikageTheme.current
    Text(
        text = label,
        style = WebTextStyles.xs,
        color = if (active) theme.actionFg else Color(0xFF71717A),
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) theme.action else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** site episode row — h-76 thumb + play overlay + title + desc. */
@Composable
private fun EpisodeRow(
    ep: EpisodeItem,
    active: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalAnikageTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Color(0x14FFFFFF) else Color.Transparent)
            .border(
                1.dp,
                if (active) Color(0x26FFFFFF) else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .height(76.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(theme.surfaceElevated),
        ) {
            ep.thumbnail?.let { thumb ->
                AsyncImage(
                    model = thumb,
                    contentDescription = "Episode ${ep.number}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Active episode: play overlay + EP badge.
            if (active) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Text(
                text = "EP ${ep.number}",
                style = WebTextStyles.xs2,
                color = theme.fg,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(theme.surface.copy(alpha = 0.85f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = if (ep.title.startsWith("Episode")) "${ep.number}." else ep.title,
                style = WebTextStyles.sm,
                color = if (active) theme.fg.copy(alpha = 0.90f) else theme.fg,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (ep.isFiller) {
                Text(
                    text = "Filler",
                    style = WebTextStyles.xs,
                    color = Color(0xFFFB923C),
                )
            } else if (ep.isRecap) {
                Text(
                    text = "Recap",
                    style = WebTextStyles.xs,
                    color = theme.fgMuted,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Comments — site: header card ("N Comments" + EP pill) + list
// ---------------------------------------------------------------------------

private val AvatarBase = "https://auth.anikage.cc"

@Composable
private fun CommentsSection(
    state: CommentsUiState,
    episode: Int,
    onRefresh: () -> Unit,
) {
    val theme = LocalAnikageTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Header card (site: rounded-xl border-white/8 bg-white/[0.02]).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x05FFFFFF))
                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                .padding(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x0AFFFFFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.ChatBubble,
                        contentDescription = null,
                        tint = Color(0xFFD4D4D8),
                        modifier = Modifier.size(14.dp),
                    )
                }
                Column {
                    Text(
                        text = "${state.total} Comments",
                        style = WebTextStyles.base,
                        color = theme.fg,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Talk about this episode without spoiling others.",
                        style = WebTextStyles.sm,
                        color = Color(0xFF71717A),
                    )
                }
            }
            Text(
                text = "EP $episode",
                style = WebTextStyles.xs,
                color = Color(0xFFD4D4D8),
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x08FFFFFF))
                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = theme.action, strokeWidth = 2.dp)
            }
        } else if (state.comments.isEmpty()) {
            Text(
                text = "No comments yet — be the first to share your thoughts.",
                style = WebTextStyles.sm,
                color = theme.fgMuted,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            state.comments.forEach { comment ->
                CommentRow(comment)
            }
        }
    }
}

@Composable
private fun CommentRow(comment: AnikageComment) {
    val theme = LocalAnikageTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AsyncImage(
            model = comment.author?.avatar?.let { "$AvatarBase$it" },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(theme.surfaceElevated),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = comment.author?.displayName ?: comment.author?.username ?: "Anonymous",
                    style = WebTextStyles.xs,
                    color = theme.fg,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = relativeTime(comment.createdAt),
                    style = WebTextStyles.xs,
                    color = theme.fgMuted,
                )
            }
            Text(
                text = comment.content,
                style = WebTextStyles.sm,
                color = Color(0xFFD4D4D8),
                lineHeight = 19.5.sp,
            )
        }
    }
}

private fun relativeTime(iso: String?): String {
    if (iso == null) return ""
    return try {
        val then = Instant.parse(iso)
        val dur = Duration.between(then, Instant.now())
        val minutes = dur.toMinutes()
        when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 60 * 24 -> "${minutes / 60}h ago"
            else -> "${minutes / (60 * 24)}d ago"
        }
    } catch (_: Exception) {
        ""
    }
}
