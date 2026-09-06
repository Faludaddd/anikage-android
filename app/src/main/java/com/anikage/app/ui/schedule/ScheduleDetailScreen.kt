package com.anikage.app.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.model.AnimeDetails
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.WebTextStyles
import com.anikage.app.ui.components.ErrorOrEmptyState
import com.anikage.app.ui.components.SkeletonBlock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * SCHEDULE DETAILS — a dedicated, information-rich page for a schedule entry
 * (the site redirects rows to the anime page; the app instead shows this
 * airing-focused page with a live countdown, then links into watch/details).
 *
 *   ├─ poster + banner hero (gradient scrim, title + status pill)
 *   ├─ countdown card: live "1d 4h 12m 30s" (or "Aired · 2h ago")
 *   ├─ airing facts: episode, release date, time + timezone, next episode
 *   ├─ genres chips, status/format/season, description (expandable)
 *   └─ actions: Watch Now (white action) / View Anime (ghost)
 */
@Composable
fun ScheduleDetailScreen(
    animeId: Int,
    episode: Int,
    airingAt: Long,               // epoch seconds
    onBackClick: () -> Unit,
    onWatchClick: (Int, Int, String?) -> Unit,
    onViewAnime: (Int) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { AnikageRepository.get(context) }
    val viewModel: ScheduleDetailViewModel = viewModel(
        factory = ScheduleDetailViewModel.factory(repo, animeId, episode, airingAt)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val theme = LocalAnikageTheme.current

    // Live countdown — ticks every second.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
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
                text = "Airing Details",
                style = WebTextStyles.base,
                color = theme.fg,
                fontWeight = FontWeight.SemiBold,
            )
        }

        when {
            state.loading -> ScheduleDetailSkeleton()
            state.error != null && state.details == null -> ErrorOrEmptyState(
                title = "Couldn't load details",
                subtitle = state.error ?: "",
                actionText = "Back",
                onAction = onBackClick,
            )
            else -> {
                val details = state.details ?: return@Column
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 48.dp),
                ) {
                    // Honest degraded-mode notice (page rendered from the
                    // schedule entry's own data; enrichment unavailable).
                    state.degradedNotice?.let { notice ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x1AF59E0B))
                                .border(1.dp, Color(0x33F59E0B), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = notice,
                                style = WebTextStyles.xs,
                                color = Color(0xFFFDE68A),
                            )
                        }
                    }
                    // ── Hero: banner + poster + title ──────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp),
                    ) {
                        val banner = details.bannerImage ?: details.coverImage?.best()
                        if (banner != null) {
                            AsyncImage(
                                model = banner,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0x66000000), theme.surface),
                                    )
                                ),
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(96.dp)
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(theme.surfaceCard)
                                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp)),
                            ) {
                                details.coverImage?.best()?.let { cover ->
                                    AsyncImage(
                                        model = cover,
                                        contentDescription = details.displayTitle(),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            Column(modifier = Modifier.padding(bottom = 2.dp)) {
                                Text(
                                    text = details.displayTitle(),
                                    style = WebTextStyles.titleSection,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                details.season?.let { s ->
                                    Text(
                                        text = listOfNotNull(
                                            s, details.seasonYear?.toString(),
                                        ).joinToString(" · "),
                                        style = WebTextStyles.sm,
                                        color = Color(0xFFD4D4D8),
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                    }

                    // ── Countdown card ────────────────────────────────────
                    val airsMs = airingAt * 1000
                    val diffMs = airsMs - nowMs
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (diffMs > 0) Color(0x1AFFFFFF) else Color(0x1422C55E))
                            .border(
                                1.dp,
                                if (diffMs > 0) Color(0x14FFFFFF) else Color(0x3322C55E),
                                RoundedCornerShape(16.dp),
                            )
                            .padding(16.dp),
                    ) {
                        Icon(
                            if (diffMs > 0) Icons.Default.Schedule else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (diffMs > 0) Color.White else Color(0xFF34D399),
                            modifier = Modifier.size(22.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (diffMs > 0) {
                                    "Episode $episode airs in"
                                } else {
                                    "Episode $episode has aired"
                                },
                                style = WebTextStyles.xs,
                                color = theme.fgMuted,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = if (diffMs > 0) {
                                    formatCountdown(diffMs)
                                } else {
                                    formatAgo(-diffMs)
                                },
                                style = WebTextStyles.titleSection,
                                color = if (diffMs > 0) Color.White else Color(0xFF34D399),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    // ── Actions ───────────────────────────────────────────
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        // Watch Now — site: bg-action text-action-fg.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.action)
                                .clickable { onWatchClick(animeId, episode, state.slug) }
                                .padding(vertical = 12.dp)
                                .padding(horizontal = 16.dp),
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = theme.actionFg,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = "Watch Episode $episode",
                                style = WebTextStyles.sm,
                                color = theme.actionFg,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        // View Anime — ghost.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x0DFFFFFF))
                                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                                .clickable { onViewAnime(animeId) }
                                .padding(vertical = 12.dp)
                                .padding(horizontal = 16.dp),
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = theme.fg,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "View Anime",
                                style = WebTextStyles.sm,
                                color = theme.fg,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    // ── Airing facts grid ─────────────────────────────────
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x08FFFFFF))
                            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Airing Information",
                            style = WebTextStyles.titleSection,
                            color = theme.fg,
                        )
                        val dateFmt = SimpleDateFormat("EEEE, MMM d yyyy", Locale.getDefault())
                        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
                        FactRow(Icons.Default.CalendarMonth, "Release date", dateFmt.format(Date(airsMs)))
                        FactRow(Icons.Default.Schedule, "Release time", timeFmt.format(Date(airsMs)))
                        FactRow(Icons.Default.Public, "Timezone", zoneLabel())
                        FactRow(Icons.Default.PlayArrow, "Episode", "Episode $episode of ${details.episodes ?: "?"}")
                        details.nextAiringEpisode?.let { next ->
                            FactRow(
                                Icons.Default.Schedule,
                                "Next episode",
                                "Ep ${next.episode} · " + formatCountdown(next.airingAt * 1000 - nowMs),
                            )
                        }
                        details.status?.let { FactRow(Icons.Default.Info, "Status", statusLabel(it)) }
                        details.duration?.let { FactRow(Icons.Default.Info, "Duration", "${it} min") }
                    }

                    // ── Genres ────────────────────────────────────────────
                    if (details.genres.isNotEmpty()) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(
                                text = "Genres",
                                style = WebTextStyles.titleSection,
                                color = theme.fg,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                            ) {
                                details.genres.forEach { g ->
                                    Text(
                                        text = g,
                                        style = WebTextStyles.xs,
                                        color = theme.fgMuted,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0x0DFFFFFF))
                                            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                    )
                                }
                            }
                        }
                    }

                    // ── Description ───────────────────────────────────────
                    state.description?.takeIf { it.isNotBlank() }?.let { desc ->
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "About",
                                style = WebTextStyles.titleSection,
                                color = theme.fg,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            var expanded by remember { mutableStateOf(false) }
                            Text(
                                text = desc,
                                style = WebTextStyles.sm,
                                color = Color(0xFFD4D4D8),
                                lineHeight = 21.sp,
                                maxLines = if (expanded) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (expanded) "Show less" else "Show more",
                                style = WebTextStyles.xs,
                                color = theme.action,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .clickable { expanded = !expanded },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Label/value fact row inside the airing info card. */
@Composable
private fun FactRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    val theme = LocalAnikageTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = theme.fgMuted,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = WebTextStyles.sm,
            color = theme.fgMuted,
            modifier = Modifier.width(110.dp),
        )
        Text(
            text = value,
            style = WebTextStyles.sm,
            color = theme.fg,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun formatCountdown(ms: Long): String {
    if (ms <= 0) return "now"
    val s = ms / 1000
    val d = s / 86400
    val h = (s % 86400) / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return when {
        d > 0 -> "%dd %dh %dm".format(d, h, m)
        h > 0 -> "%dh %dm %ds".format(h, m, sec)
        m > 0 -> "%dm %ds".format(m, sec)
        else -> "%ds".format(sec)
    }
}

private fun formatAgo(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    return when {
        h > 0 -> "%dh %dm ago".format(h, m)
        m > 0 -> "%dm ago".format(m)
        else -> "just aired"
    }
}

/** Timezone label like the site's schedule (e.g. "EST (UTC-5)"). */
private fun zoneLabel(): String {
    val tz = TimeZone.getDefault()
    val short = tz.id
    val offset = tz.rawOffset / 3600000
    val sign = if (offset >= 0) "+" else "-"
    val abs = kotlin.math.abs(offset)
    return "$short (UTC$sign$abs)"
}

private fun statusLabel(status: String): String = when (status.uppercase()) {
    "RELEASING" -> "Airing now"
    "FINISHED" -> "Finished"
    "NOT_YET_RELEASED" -> "Not yet released"
    "CANCELLED" -> "Cancelled"
    else -> status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

@Composable
private fun ScheduleDetailSkeleton() {
    val theme = LocalAnikageTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface),
    ) {
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
            corner = 0.dp,
        )
        SkeletonBlock(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .height(90.dp),
            corner = 16.dp,
        )
        SkeletonBlock(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(44.dp),
            corner = 12.dp,
        )
        SkeletonBlock(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .height(200.dp),
            corner = 16.dp,
        )
    }
}
