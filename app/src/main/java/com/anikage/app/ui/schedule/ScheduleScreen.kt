package com.anikage.app.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.model.AiringSchedule
import com.anikage.app.core.data.model.Anime
import com.anikage.app.ui.components.ErrorOrEmptyState
import com.anikage.app.ui.components.LoadingSpinner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScheduleScreen(onAnimeClick: (Anime) -> Unit) {
    val context = LocalContext.current
    val repo = remember { AnikageRepository.get(context) }
    val viewModel: ScheduleViewModel = viewModel(factory = ScheduleViewModel.factory(repo))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val days = state.byDay.keys.toList()
    var selectedDay by remember { mutableStateOf<String?>(null) }
    val activeDay = selectedDay?.takeIf { it in state.byDay } ?: days.firstOrNull()

    Column(Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        when {
            state.loading -> LoadingSpinner()
            state.error != null && state.byDay.isEmpty() -> ErrorOrEmptyState("Couldn’t load schedule", state.error ?: "", onAction = viewModel::load)
            state.byDay.isEmpty() -> ErrorOrEmptyState("Nothing airing", "No episodes airing this week.", onAction = viewModel::load)
            else -> {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("WEEKLY SCHEDULE", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Text("Anime Schedule", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    Text("New episodes airing this week — pick a day below.", color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        days.forEach { day ->
                            val entries = state.byDay[day].orEmpty()
                            Surface(
                                color = if (day == activeDay) Color.White else Color.White.copy(alpha = .03f),
                                contentColor = if (day == activeDay) Color.Black else Color.White.copy(alpha = .7f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.clickable { selectedDay = day },
                            ) {
                                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(day.substringBefore(' '), fontWeight = FontWeight.SemiBold)
                                    Text(day.substringAfter(' ', ""), style = MaterialTheme.typography.labelSmall)
                                    Text("${entries.size} eps", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                val entries = state.byDay[activeDay].orEmpty()
                LazyColumn(contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${activeDay ?: "Schedule"} · Today", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("${entries.size} episodes", color = Color.White.copy(alpha = .55f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    items(entries, key = { it.id }) { schedule -> ScheduleRow(schedule) { onAnimeClick(schedule.media) } }
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(schedule: AiringSchedule, onClick: () -> Unit) {
    val airingAt = schedule.airingAt * 1000
    val aired = airingAt <= System.currentTimeMillis()
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(airingAt))
    Surface(
        color = Color.White.copy(alpha = .03f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .06f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(schedule.media.coverUrl(), schedule.media.displayTitle(), contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp, 84.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(schedule.media.displayTitle(), color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ep ${schedule.episode}", color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.labelSmall)
                    Icon(Icons.Default.Schedule, null, tint = Color.White.copy(alpha = .55f), modifier = Modifier.size(13.dp))
                    Text(time, color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.labelSmall)
                    Text("·", color = Color.White.copy(alpha = .4f))
                    Text(if (aired) "Aired" else "Soon", color = if (aired) Color(0xFF39D98A) else Color.White.copy(alpha = .55f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
