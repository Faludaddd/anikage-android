package com.anikage.app.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import com.anikage.app.core.data.db.DownloadedEpisodeEntity
import com.anikage.app.core.download.EpisodeDownloadEngine
import com.anikage.app.core.settings.SettingsState
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.WebTextStyles

/**
 * DOWNLOADS — in-app manager for downloaded episodes.
 *
 * The engine's state flow is the single source of truth: COMPLETED rows
 * match DB records (with file path, quality, size, subtitle sidecar), and
 * active/paused/failed downloads show live progress with pause / resume /
 * retry / cancel. Completed rows play offline through the watch screen's
 * local-file mode.
 */
@Composable
fun DownloadsScreen(
    onBackClick: () -> Unit,
    onPlay: (DownloadedEpisodeEntity) -> Unit,
) {
    val theme = LocalAnikageTheme.current
    val context = LocalContext.current
    val downloads by EpisodeDownloadEngine.statesFlow.collectAsStateWithLifecycle()

    val completedBytes = downloads
        .filter { it.status == EpisodeDownloadEngine.Status.COMPLETED }
        .sumOf { it.bytesDone }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface)
            .statusBarsPadding(),
    ) {
        // ── Header (account-page style: back + title) ─────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x08FFFFFF))
                    .border(1.dp, Color(0x0FFFFFFF), CircleShape)
                    .clickable(onClick = onBackClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = theme.fg,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column {
                Text(
                    text = "Downloads",
                    style = WebTextStyles.lg,
                    color = theme.fg,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (downloads.isEmpty()) "No downloads yet"
                    else "${downloads.count { it.status == EpisodeDownloadEngine.Status.COMPLETED }} ready · ${formatBytes(completedBytes)} on this device",
                    style = WebTextStyles.sm,
                    color = theme.fgMuted,
                )
            }
        }

        if (downloads.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 96.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0x0DFFFFFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.FileDownload,
                        contentDescription = null,
                        tint = theme.fgMuted,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Text(
                    text = "Nothing downloaded yet",
                    style = WebTextStyles.titleSection,
                    color = theme.fg,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    text = "Open any episode's Download button to save it to this device and watch it offline.",
                    style = WebTextStyles.sm,
                    color = theme.fgMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth(0.8f),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 40.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(downloads, key = { it.key }) { dl ->
                    DownloadRow(
                        dl = dl,
                        onPlay = { onPlay(toEntity(dl)) },
                        onPause = { EpisodeDownloadEngine.pause(dl.key) },
                        onResume = { EpisodeDownloadEngine.resume(context, dl.key) },
                        onRetry = { EpisodeDownloadEngine.retry(context, dl.key) },
                        onCancel = { EpisodeDownloadEngine.cancel(context, dl.key) },
                    )
                }
                // ── Storage & management card (directive #5) ───────────────
                item(key = "storage-card") {
                    var freeText by remember { mutableStateOf("…") }
                    LaunchedEffect(Unit) {
                        freeText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val dir = EpisodeDownloadEngine.downloadsDir(context)
                            formatBytes(dir.usableSpace)
                        }
                    }
                    var confirmClear by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x08FFFFFF))
                            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Filled.Storage,
                                contentDescription = null,
                                tint = theme.fgMuted,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "Storage",
                                style = WebTextStyles.base,
                                color = theme.fg,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${formatBytes(completedBytes)} used · $freeText free",
                                style = WebTextStyles.xs,
                                color = theme.fgMuted,
                            )
                        }
                        if (SettingsState.downloadsWifiOnly) {
                            Text(
                                text = "Wi-Fi only downloads are ON — the queue waits for unmetered networks.",
                                style = WebTextStyles.xs,
                                color = Color(0xFF6EE7B7),
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = if (confirmClear) "Tap again to confirm" else "Delete all downloads",
                                style = WebTextStyles.xs,
                                color = Color(0xFFFCA5A5),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x14EF4444))
                                    .clickable {
                                        if (confirmClear) {
                                            downloads.forEach { EpisodeDownloadEngine.cancel(context, it.key) }
                                            confirmClear = false
                                        } else {
                                            confirmClear = true
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Engine state -> minimal entity for offline playback navigation. */
private fun toEntity(dl: EpisodeDownloadEngine.DownloadState): DownloadedEpisodeEntity =
    DownloadedEpisodeEntity(
        downloadKey = dl.key,
        animeId = dl.animeId,
        slug = dl.slug,
        episode = dl.episode,
        quality = dl.qualityLabel,
        height = dl.height,
        filePath = dl.filePath.orEmpty(),
        subtitlePath = dl.subtitlePath,
        titleRomaji = dl.titleRomaji,
        titleEnglish = dl.titleEnglish,
        episodeTitle = dl.episodeTitle,
        posterUrl = dl.posterUrl,
        sizeBytes = dl.bytesDone,
        provider = "",
        lang = "",
    )

@Composable
private fun DownloadRow(
    dl: EpisodeDownloadEngine.DownloadState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val theme = LocalAnikageTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x08FFFFFF))
            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(14.dp))
            .clickable(enabled = dl.status == EpisodeDownloadEngine.Status.COMPLETED, onClick = onPlay)
            .padding(10.dp),
    ) {
        // Poster thumb (episode thumb or cover fallback).
        Box(
            modifier = Modifier
                .width(112.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(theme.surfaceElevated),
        ) {
            dl.posterUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (dl.status == EpisodeDownloadEngine.Status.COMPLETED) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x40000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Play offline",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x59000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    when (dl.status) {
                        EpisodeDownloadEngine.Status.FAILED ->
                            Icon(Icons.Filled.Warning, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(24.dp))
                        else ->
                            androidx.compose.material3.CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp),
                            )
                    }
                }
            }
            // Quality badge.
            Text(
                text = dl.qualityLabel,
                style = WebTextStyles.xs2,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x8C000000))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = dl.titleEnglish ?: dl.titleRomaji ?: "Episode ${dl.episode}",
                style = WebTextStyles.sm,
                color = theme.fg,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Episode ${dl.episode}" + (dl.episodeTitle?.takeIf { !it.startsWith("Episode") }?.let { " · $it" } ?: ""),
                style = WebTextStyles.xs,
                color = theme.fgMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when (dl.status) {
                EpisodeDownloadEngine.Status.COMPLETED -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.DownloadDone, null, tint = Color(0xFF34D399), modifier = Modifier.size(13.dp))
                        Text(
                            text = "${formatBytes(dl.bytesDone)} · ready",
                            style = WebTextStyles.xs,
                            color = Color(0xFF34D399),
                        )
                    }
                }
                EpisodeDownloadEngine.Status.DOWNLOADING -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x14FFFFFF)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(dl.progress)
                                .fillMaxSize()
                                .background(theme.action),
                        )
                    }
                    Text(
                        text = "${(dl.progress * 100).toInt()}% · ${formatBytes(dl.bytesPerSec)}/s · ${dl.segmentsDone}/${dl.segmentsTotal}",
                        style = WebTextStyles.xs2,
                        color = theme.fgMuted,
                    )
                }
                EpisodeDownloadEngine.Status.PAUSED -> Text(
                    text = "Paused at ${formatBytes(dl.bytesDone)}",
                    style = WebTextStyles.xs,
                    color = Color(0xFFFBBF24),
                )
                EpisodeDownloadEngine.Status.FAILED -> Text(
                    text = dl.error ?: "Failed",
                    style = WebTextStyles.xs,
                    color = Color(0xFFFCA5A5),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                else -> Text(
                    text = "Preparing…",
                    style = WebTextStyles.xs,
                    color = theme.fgMuted,
                )
            }

            // Actions.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                when (dl.status) {
                    EpisodeDownloadEngine.Status.COMPLETED -> {
                        MiniAction("Play", theme.action, theme.actionFg, onPlay)
                        MiniAction("Delete", Color(0x14EF4444), Color(0xFFFCA5A5), onCancel)
                    }
                    EpisodeDownloadEngine.Status.DOWNLOADING -> {
                        MiniAction("Pause", Color(0x1FFFFFFF), Color.White, onPause)
                        MiniAction("Cancel", Color(0x14EF4444), Color(0xFFFCA5A5), onCancel)
                    }
                    EpisodeDownloadEngine.Status.PAUSED -> {
                        MiniAction("Resume", theme.action, theme.actionFg, onResume)
                        MiniAction("Cancel", Color(0x14EF4444), Color(0xFFFCA5A5), onCancel)
                    }
                    EpisodeDownloadEngine.Status.FAILED -> {
                        MiniAction("Retry", theme.action, theme.actionFg, onRetry)
                        MiniAction("Remove", Color(0x14EF4444), Color(0xFFFCA5A5), onCancel)
                    }
                    else -> MiniAction("Cancel", Color(0x14EF4444), Color(0xFFFCA5A5), onCancel)
                }
            }
        }
    }
}

@Composable
private fun MiniAction(label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Text(
        text = label,
        style = WebTextStyles.xs,
        color = fg,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1e9)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1e6)
    bytes >= 1_000 -> "%d KB".format(bytes / 1_000)
    else -> "$bytes B"
}
