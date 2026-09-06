package com.anikage.app.ui.music

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.api.AnikageMusicTheme
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.WebTextStyles
import com.anikage.app.ui.components.ErrorOrEmptyState
import com.anikage.app.ui.components.SkeletonBlock

/**
 * MUSIC INFO — 1:1 port of anikage.cc/music/info?slug&type (node 17).
 *
 * Site layout:
 *   container-custom pt-26 pb-20, xl: flex-row gap-8
 *   ├─ w-full flex-1: player card (rounded-2xl border-fg/10 bg-surface-card/50
 *   │   shadow-2xl, aspect-video) + song title (title-section) + type badge
 *   │   (uppercase tracking-widest) + anime name (italic) + play pill
 *   └─ theme list: rows (number square + song + "Type: X"), active row
 *       highlighted (accent border + bg)
 */
@Composable
fun MusicInfoScreen(
    slug: String,
    type: String,
    onBackClick: () -> Unit,
    onOpenAnime: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val repo = remember { AnikageRepository.get(context) }
    val viewModel: MusicInfoViewModel = viewModel(
        factory = MusicInfoViewModel.factory(app, repo, slug, type)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player = remember { viewModel.player() }
    val theme = LocalAnikageTheme.current
    val isWide = LocalConfiguration.current.screenWidthDp >= 840

    // Progress tick — site updates the progress bar continuously.
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.tick()
            kotlinx.coroutines.delay(250)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface)
            .statusBarsPadding(),
    ) {
        // Back header.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = theme.fg,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBackClick)
                    .padding(8.dp),
            )
            Text(
                text = "Music",
                style = WebTextStyles.base,
                color = theme.fg,
                fontWeight = FontWeight.SemiBold,
            )
        }

        when {
            state.loading -> MusicInfoSkeleton()
            state.error != null && state.anime == null -> ErrorOrEmptyState(
                title = "Couldn't load music",
                subtitle = state.error ?: "",
                actionText = "Back",
                onAction = onBackClick,
            )
            else -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    // ── Player column (site: w-full flex-1) ────────────────
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Player card — site: rounded-2xl border shadow-2xl.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x870A0A0A))
                                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f),
                            ) {
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            this.player = player
                                            useController = true
                                            controllerAutoShow = true
                                            controllerHideOnTouch = true
                                        }
                                    },
                                )
                            }
                        }

                        // Title block — site: mb-5 pt-5.
                        Column(modifier = Modifier.padding(top = 20.dp, bottom = 16.dp)) {
                            Text(
                                text = state.activeTheme?.song?.title ?: state.activeTheme?.slug ?: "Untitled",
                                style = WebTextStyles.titleSection,
                                color = theme.fg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 6.dp),
                            ) {
                                // Type badge — site: rounded-md border bg-fg/5 uppercase.
                                Text(
                                    text = (state.activeTheme?.slug ?: state.type).uppercase(),
                                    style = WebTextStyles.xs,
                                    color = theme.fgMuted,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0x0DFFFFFF))
                                        .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                                Text(
                                    text = state.anime?.name ?: "",
                                    style = WebTextStyles.sm,
                                    color = theme.fgMuted,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        // Transport row — play/pause + seek + time.
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                // Play pill — site: rounded-full bg-action circle.
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(theme.action)
                                        .clickable { viewModel.togglePlay() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                                        tint = theme.actionFg,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Text(
                                    text = state.activeTheme?.song?.title ?: "",
                                    style = WebTextStyles.sm,
                                    color = theme.fg,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                // AniList link — site: h-10 w-10 rounded-full button.
                                state.anime?.anilistId()?.let { anilistId ->
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color(0x0DFFFFFF))
                                            .border(1.dp, Color(0x14FFFFFF), CircleShape)
                                            .clickable {
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(
                                                            Intent.ACTION_VIEW,
                                                            Uri.parse("https://anilist.co/anime/$anilistId"),
                                                        ),
                                                    )
                                                }
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "AL",
                                            style = WebTextStyles.xs2,
                                            color = Color(0xFF3DBBEE),
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                            // Seek bar + time (site: progress track + timestamps).
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = formatTime(state.positionMs),
                                    style = WebTextStyles.xs2,
                                    color = theme.fgMuted,
                                    fontWeight = FontWeight.Bold,
                                )
                                Slider(
                                    value = if (state.durationMs > 0) {
                                        state.positionMs.toFloat() / state.durationMs
                                    } else 0f,
                                    onValueChange = { fraction ->
                                        viewModel.seekTo((fraction * state.durationMs).toLong())
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = theme.fg,
                                        activeTrackColor = theme.fg.copy(alpha = 0.8f),
                                        inactiveTrackColor = Color(0x1AFFFFFF),
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = formatTime(state.durationMs),
                                    style = WebTextStyles.xs2,
                                    color = theme.fgMuted,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        // Mobile: theme list inline below the player.
                        if (!isWide) {
                            Spacer(Modifier.height(20.dp))
                            ThemeList(state = state, viewModel = viewModel)
                            Spacer(Modifier.height(80.dp))
                        }
                    }

                    // ── Theme list side column (site: xl only) ─────────────
                    if (isWide) {
                        Column(
                            modifier = Modifier
                                .width(340.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            ThemeList(state = state, viewModel = viewModel)
                            Spacer(Modifier.height(48.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Site theme list: rows with number square + title + "Type: X". */
@Composable
private fun ThemeList(
    state: MusicInfoUiState,
    viewModel: MusicInfoViewModel,
) {
    val theme = LocalAnikageTheme.current
    val anime = state.anime ?: return
    val tracks = anime.animethemes

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        tracks.forEachIndexed { index, t ->
            val active = t.slug == state.type
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) Color(0x1AFFFFFF) else Color(0x08FFFFFF))
                    .border(
                        1.dp,
                        if (active) theme.action.copy(alpha = 0.35f) else Color(0x0FFFFFFF),
                        RoundedCornerShape(12.dp),
                    )
                    .clickable { viewModel.selectTheme(t) }
                    .padding(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x14FFFFFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = String.format("%02d", index + 1),
                        style = WebTextStyles.xs2,
                        color = if (active) Color.White else theme.fgMuted,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = t.song?.title ?: "Untitled",
                    style = WebTextStyles.base,
                    color = if (active) Color.White else theme.fg,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Type: ${t.slug ?: ""}".uppercase(),
                    style = WebTextStyles.xs2,
                    color = if (active) theme.action.copy(alpha = 0.7f) else theme.fgMuted,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                )
            }
        }
        if (tracks.isEmpty()) {
            Text(
                text = "No themes available.",
                style = WebTextStyles.sm,
                color = theme.fgMuted,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun MusicInfoSkeleton() {
    val theme = LocalAnikageTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface)
            .padding(horizontal = 16.dp),
    ) {
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            corner = 16.dp,
        )
        Spacer(Modifier.height(20.dp))
        SkeletonBlock(modifier = Modifier.width(200.dp).height(20.dp), corner = 8.dp)
        Spacer(Modifier.height(10.dp))
        SkeletonBlock(modifier = Modifier.width(140.dp).height(14.dp), corner = 7.dp)
        Spacer(Modifier.height(20.dp))
        repeat(5) {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                corner = 12.dp,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
