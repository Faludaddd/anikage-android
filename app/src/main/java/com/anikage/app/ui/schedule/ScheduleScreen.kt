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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
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
import androidx.compose.ui.platform.LocalConfiguration
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
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.WebTextStyles
import com.anikage.app.ui.components.ErrorOrEmptyState
import com.anikage.app.ui.components.SkeletonBlock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SCHEDULE — 1:1 port of anikage.cc/schedule (schedule node).
 *
 * Site structure:
 *   ├─ eyebrow "Weekly schedule" + title-hero "Anime Schedule" + subtitle
 *   ├─ day strip: 7 buttons (weekday.slice(0,3) + bold date + "N ep"),
 *   │    selected: bg-action text-action-fg shadow-lg;
 *   │    today:    border-action/40 bg-action/10;
 *   │    default:  border-fg/6 bg-fg/3 text-fg-muted
 *   ├─ day header: "Sunday · Today" + "N episodes"
 *   └─ rows grid: 1 col → sm:2 → xl:3 → 2xl:4
 *        row: 2:3 poster (w-14 sm:w-16) + title + "Ep N" + time +
 *        Aired(emerald)/Soon(action, pulsing) status
 * Clicking a row opens the rich schedule details page (airing countdown,
 * next-episode info, genres, watch CTA) — never a generic redirect.
 */
@Composable
fun ScheduleScreen(
    onEntryClick: (AiringSchedule) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { AnikageRepository.get(context) }
    val viewModel: ScheduleViewModel = viewModel(factory = ScheduleViewModel.factory(repo))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val theme = LocalAnikageTheme.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp

    // Day grid columns — site: 1 / sm:2 / xl:3 / 2xl:4.
    val columns = when {
        screenWidthDp >= 1280 -> 4
        screenWidthDp >= 840 -> 3
        screenWidthDp >= 600 -> 2
        else -> 1
    }

    var selectedDay by remember { mutableStateOf<String?>(null) }
    val activeDay = selectedDay?.takeIf { sel -> state.days.any { it.day == sel } }
        ?: state.days.firstOrNull()?.day

    Column(
        Modifier
            .fillMaxSize()
            .background(theme.surface)
            .padding(top = 80.dp)
    ) {
        when {
            state.loading -> ScheduleSkeleton(columns)
            state.error != null && state.days.isEmpty() ->
                ErrorOrEmptyState("Couldn't load schedule", state.error ?: "", onAction = viewModel::load)
            state.days.isEmpty() ->
                ErrorOrEmptyState("Nothing airing", "No episodes airing this week.", onAction = viewModel::load)
            else -> {
                // ── Page header (site: eyebrow + title-hero + subtitle) ──
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = theme.fgMuted,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "WEEKLY SCHEDULE",
                            style = WebTextStyles.xs2,
                            color = theme.fgMuted,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 2.sp,
                        )
                    }
                    Text(
                        text = "Anime Schedule",
                        style = WebTextStyles.titleHero,
                        color = theme.fg,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = "New episodes airing this week — pick a day below.",
                        style = WebTextStyles.sm,
                        color = theme.fgMuted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
                    )

                    // ── Day strip — 7 site-exact buttons with date numbers.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.days.forEach { day ->
                            val selected = day.day == activeDay
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(1.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        when {
                                            selected -> theme.action
                                            day.isToday -> theme.action.copy(alpha = 0.10f)
                                            else -> Color(0x08FFFFFF)
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        when {
                                            selected -> Color.Transparent
                                            day.isToday -> theme.action.copy(alpha = 0.40f)
                                            else -> Color(0x0FFFFFFF)
                                        },
                                        RoundedCornerShape(16.dp),
                                    )
                                    .clickable { selectedDay = day.day }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = day.shortDay.uppercase(),
                                    style = WebTextStyles.xs2,
                                    color = when {
                                        selected -> theme.actionFg.copy(alpha = 0.80f)
                                        else -> theme.fgMuted
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.sp,
                                )
                                // Date number — always present (the fix).
                                Text(
                                    text = day.dateOfMonth.toString(),
                                    style = WebTextStyles.lg,
                                    color = if (selected) theme.actionFg else theme.fg,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 20.sp,
                                )
                                Text(
                                    text = "${day.entries.size} ep",
                                    style = WebTextStyles.xs2,
                                    color = if (selected) theme.actionFg.copy(alpha = 0.70f) else theme.fgMuted,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }

                // ── Day header + entries grid ────────────────────────────
                val active = state.days.firstOrNull { it.day == activeDay }
                val entries = active?.entries.orEmpty()
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = (activeDay ?: "Schedule") + if (active?.isToday == true) " · Today" else "",
                            style = WebTextStyles.titleSection,
                            color = theme.fg,
                        )
                        Text(
                            text = "${entries.size} episode" + if (entries.size == 1) "" else "s",
                            style = WebTextStyles.xs,
                            color = theme.fgMuted,
                        )
                    }
                    if (entries.isEmpty()) {
                        // Site: circle icon + "No episodes on {day}".
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 64.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x0DFFFFFF)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = theme.fgMuted,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Text(
                                text = "No episodes on ${activeDay ?: ""}",
                                style = WebTextStyles.titleSection,
                                color = theme.fg,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            Text(
                                text = "Nothing scheduled for this day. Try another day from the strip above.",
                                style = WebTextStyles.sm,
                                color = theme.fgMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .fillMaxWidth(0.8f),
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            contentPadding = PaddingValues(
                                start = 16.dp, end = 16.dp, bottom = 120.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(entries, key = { it.id }) { schedule ->
                                ScheduleRow(schedule) { onEntryClick(schedule) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Site row — group flex gap-3 rounded-xl border-fg/6 bg-fg/3 p-2. */
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
        // Poster — site: aspect-2/3 w-14 (sm:w-16) rounded-lg.
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
                // Status — site: Aired (emerald, solid dot) / Soon (action,
                // pulsing dot).
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (aired) Color(0xFF34D399) else theme.action   // emerald-400 / action
                        )
                )
                Text(
                    text = if (aired) "Aired" else "Soon",
                    style = WebTextStyles.xs,
                    color = if (aired) Color(0xFF34D399) else theme.action,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Skeleton — site: 7 pulsing day pills (h-60px) + 8 pulsing rows
// ---------------------------------------------------------------------------

@Composable
private fun ScheduleSkeleton(columns: Int) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        SkeletonBlock(modifier = Modifier.width(160.dp).height(24.dp), corner = 8.dp)
        Spacer(Modifier.height(8.dp))
        SkeletonBlock(modifier = Modifier.width(220.dp).height(30.dp), corner = 10.dp)
        Spacer(Modifier.height(8.dp))
        SkeletonBlock(modifier = Modifier.width(260.dp).height(14.dp), corner = 7.dp)
        Spacer(Modifier.height(20.dp))
        // Day strip.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(7) {
                SkeletonBlock(
                    modifier = Modifier.width(58.dp).height(60.dp),
                    corner = 16.dp,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        // Entry rows.
        repeat(6) {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp),
                corner = 12.dp,
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}
