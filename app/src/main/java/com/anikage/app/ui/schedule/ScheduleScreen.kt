package com.anikage.app.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.anikage.app.Config
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.model.AiringSchedule
import com.anikage.app.core.data.model.Anime
import com.anikage.app.ui.components.ErrorOrEmptyState
import com.anikage.app.ui.components.LoadingSpinner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(onAnimeClick: (Anime) -> Unit) {
    val context = LocalContext.current
    val repo = remember { AnikageRepository.get(context) }
    val viewModel: ScheduleViewModel = viewModel(factory = ScheduleViewModel.factory(repo))
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("Schedule") },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
            ),
        )

        when {
            state.loading -> LoadingSpinner()
            state.error != null && state.byDay.isEmpty() -> ErrorOrEmptyState(
                title = "Couldn't load schedule",
                subtitle = state.error ?: "",
                onAction = viewModel::load,
            )
            state.byDay.isEmpty() -> ErrorOrEmptyState(
                title = "Nothing airing",
                subtitle = "No episodes airing this week.",
                onAction = viewModel::load,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp),
            ) {
                state.byDay.forEach { (day, schedules) ->
                    item {
                        Text(
                            text = day,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = Config.Spacing.screenHorizontal,
                                    end = Config.Spacing.screenHorizontal,
                                    top = Config.Spacing.xl,
                                    bottom = Config.Spacing.sm,
                                ),
                        )
                    }
                    items(schedules, key = { it.id }) { sched ->
                        ScheduleRow(schedule = sched, onClick = { onAnimeClick(sched.media) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(schedule: AiringSchedule, onClick: () -> Unit) {
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    val airingAt = schedule.airingAt * 1000
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Config.Spacing.screenHorizontal, vertical = Config.Spacing.sm)
            .clip(RoundedCornerShape(Config.Shape.card))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(Config.Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = schedule.media.coverUrl(),
                contentDescription = schedule.media.displayTitle(),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(60.dp, 90.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(Config.Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = schedule.media.displayTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Episode ${schedule.episode}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = timeFmt.format(Date(airingAt)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                val now = System.currentTimeMillis()
                val diffMs = airingAt - now
                val relative = if (diffMs > 0) {
                    val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
                    val days = TimeUnit.MILLISECONDS.toDays(diffMs)
                    if (days > 0) "in ${days}d ${hours % 24}h"
                    else "in ${hours}h"
                } else {
                    "aired"
                }
                Text(
                    text = relative,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
