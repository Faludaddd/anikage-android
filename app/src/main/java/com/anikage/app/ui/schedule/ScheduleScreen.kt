package com.anikage.app.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.model.AiringSchedule
import com.anikage.app.core.data.model.Anime
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.WebTextStyles
import com.anikage.app.ui.components.ErrorOrEmptyState
import com.anikage.app.ui.components.LoadingSpinner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SCHEDULE — 1:1 port of anikage.cc/schedule (mobile).
 *
 * Site: "WEEKLY SCHEDULE" eyebrow → title-hero "Anime Schedule" → subtitle →
 * day strip (active: rounded-2xl bg-action, day/date/ep-count stack) →
 * "Sunday · Today" header + episode rows (rounded-xl border-fg/6 bg-fg/3 p-2,
 * poster w-14, `Ep 23 • 12:30 AM • Aired` with emerald dot).
 */
@Composable
fun ScheduleScreen(onAnimeClick: (Anime) -> Unit) {
    val context = LocalContext.current
    val repo = remember { AnikageRepository.get(context) }
    val viewModel: ScheduleViewModel = viewModel(factory = ScheduleViewModel.factory(repo))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val theme = LocalAnikageTheme.current
    val days = state.byDay.keys.toList()
    var selectedDay by remember { mutableStateOf<String?>(null) }
    val activeDay = selectedDay?.takeIf { it in state.byDay } ?: days.firstOrNull()

    Column(
        Modifier
            .fillMaxSize()
            .background(theme.surface)
            .padding(top = 80.dp)
    ) {
        when {
            state.loading -> LoadingSpinner()
            state.error != null && state.byDay.isEmpty() ->
                ErrorOrEmptyState("Couldn't load schedule", state.error ?: "", onAction = viewModel::load)
            state.byDay.isEmpty() ->
                ErrorOrEmptyState("Nothing airing", "No episodes airing this week.", onAction = viewModel::load)
            else -> {
                // ── Page header (site: eyebrow + title-hero + subtitle) ──────
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(
                        text = "WEEKLY SCHEDULE",
                        style = WebTextStyles.xs2,
                        color = theme.fgMuted,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp,
                    )
                    Text(
                        text = "Anime Schedule",
                        style = WebTextStyles.titleHero,
                        color = theme.fg,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = "New episodes airing this week",
                        style = WebTextStyles.sm,
                        color = theme.fgMuted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
                    )

                    // ── Day strip (site: no-scrollbar horizontal buttons) ────
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        days.forEach { day ->
                            val entries = state.byDay[day].orEmpty()
                            val selected = day == activeDay
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (selected) theme.action else Color.Transparent
                                    )
                                    .clickable { selectedDay = day }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = day.substringBefore(' ').uppercase(),
                                    style = WebTextStyles.xs2,
                                    color = if (selected) theme.actionFg.copy(alpha = 0.80f) else theme.fgMuted,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.sp,
                                )
                                Text(
                                    text = day.substringAfter(' ', ""),
                                    style = WebTextStyles.base.copy(fontSize = 17.sp),
                                    color = if (selected) theme.actionFg else theme.fg,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "${entries.size} ep",
                                    style = WebTextStyles.xs2,
                                    color = if (selected) theme.actionFg.copy(alpha = 0.70f) else theme.fgMuted,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }

                // ── Day header + rows ────────────────────────────────────────
                val entries = state.byDay[activeDay].orEmpty()
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = activeDay ?: "Schedule",
                                style = WebTextStyles.titleSection,
                                color = theme.fg,
                            )
                            Text(
                                text = "${entries.size} episodes",
                                style = WebTextStyles.xs,
                                color = theme.fgMuted,
                            )
                        }
                    }
                    items(entries, key = { it.id }) { schedule ->
                        ScheduleRow(schedule) { onAnimeClick(schedule.media) }
                    }
                }
            }
        }
    }
}

/** Site row — rounded-xl border-fg/6 bg-fg/3 p-2, poster w-14 2:3 + meta. */
@Composable
private fun ScheduleRow(schedule: AiringSchedule, onClick: () -> Unit) {
    val theme = LocalAnikageTheme.current
    val airingAt = schedule.airingAt * 1000
    val aired = airingAt <= System.currentTimeMillis()
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(airingAt))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x08FFFFFF))
            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        // Poster — site: aspect-2/3 w-14 (56px) rounded-lg.
        Box(
            modifier = Modifier
                .width(56.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(theme.surfaceCard)
        ) {
            schedule.media.coverUrl()?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = schedule.media.displayTitle(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = schedule.media.displayTitle(),
                style = WebTextStyles.base,
                color = theme.fg,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Ep ${schedule.episode}",
                    style = WebTextStyles.xs,
                    color = theme.fg.copy(alpha = 0.70f),
                    fontWeight = FontWeight.Medium,
                )
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = theme.fgMuted,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = time,
                    style = WebTextStyles.xs,
                    color = theme.fgMuted,
                )
                if (aired) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF34D399))   // emerald-400
                    )
                    Text(
                        text = "Aired",
                        style = WebTextStyles.xs,
                        color = Color(0xFF34D399),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
