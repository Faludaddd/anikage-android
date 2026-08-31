package com.anikage.app.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.model.AiringSchedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ScheduleUiState(
    val byDay: Map<String, List<AiringSchedule>> = emptyMap(),
    val loading: Boolean = true,
    val error: String? = null,
)

class ScheduleViewModel(
    private val repo: AnikageRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ScheduleUiState(loading = true))
    val state: StateFlow<ScheduleUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val result = repo.scheduleWeek()
            result.fold(
                onSuccess = { items ->
                    val grouped = groupByDay(items)
                    _state.value = ScheduleUiState(byDay = grouped, loading = false)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = e.message ?: "Failed to load schedule.",
                    )
                }
            )
        }
    }

    companion object {
        fun factory(repo: AnikageRepository) = viewModelFactory {
            initializer { ScheduleViewModel(repo) }
        }
    }
}

/** Group schedules by day label (e.g. "Today", "Tomorrow", "Mon 12 Aug"). */
private fun groupByDay(items: List<AiringSchedule>): Map<String, List<AiringSchedule>> {
    val now = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val todayMidnight = now.timeInMillis / 1000
    val tomorrowMidnight = todayMidnight + 24 * 60 * 60
    val dayAfter = tomorrowMidnight + 24 * 60 * 60

    val fmtDay = SimpleDateFormat("EEE d MMM", Locale.getDefault())
    return items.groupBy {
        when {
            it.airingAt < tomorrowMidnight -> "Today"
            it.airingAt < dayAfter -> "Tomorrow"
            else -> fmtDay.format(Date(it.airingAt * 1000))
        }
    }
}
