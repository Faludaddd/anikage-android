package com.anikage.app.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.model.AiringSchedule
import com.anikage.app.core.log.AppLogger
import com.anikage.app.core.log.LogCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ScheduleUiState(
    /** One entry per weekday, ordered from today. */
    val days: List<DaySchedule> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

/** A weekday column in the strip + its entries sorted by airing time. */
data class DaySchedule(
    val day: String,                 // "Sunday" … "Saturday"
    val shortDay: String,            // "Sun" … "Sat" (site: day.slice(0,3))
    val dateOfMonth: Int,            // date of this weekday in the current week
    val isToday: Boolean,
    val entries: List<AiringSchedule> = emptyList(),
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
                    // Dedupe by id — the same airing entry can appear on
                    // adjacent days; lazy-list keys must stay unique.
                    val distinct = items.distinctBy { it.id }
                    _state.value = ScheduleUiState(days = buildWeek(distinct), loading = false)
                    AppLogger.i(LogCategory.DATA, "Schedule loaded: ${distinct.size} entries across 7 days")
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

/**
 * Site behaviour (schedule node): exactly 7 day buttons — Sunday…Saturday —
 * each labelled with its weekday name and the DATE OF THAT WEEKDAY IN THE
 * CURRENT WEEK (`dayjs().day(idx).format('D')`). "Today" is a highlight on
 * the strip (border-accent) plus " · Today" in the section header — the day
 * name itself is never replaced, which is why a date number is ALWAYS
 * visible. Entries are grouped by their airing weekday and sorted by time.
 */
private fun buildWeek(items: List<AiringSchedule>): List<DaySchedule> {
    val weekdayNames = listOf(
        "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday",
    )
    val now = Calendar.getInstance()

    // Date of each weekday in the current (Sun-started) week.
    val weekDates = IntArray(7)
    val weekCal = (now.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
    }
    for (i in 0 until 7) {
        weekDates[i] = weekCal.get(Calendar.DAY_OF_MONTH)
        weekCal.add(Calendar.DAY_OF_MONTH, 1)
    }
    val todayIndex = (now.get(Calendar.DAY_OF_WEEK) + 6) % 7   // 0 = Sunday

    // Bucket entries by their airing weekday.
    val byWeekday = Array(7) { mutableListOf<AiringSchedule>() }
    val dayFmt = SimpleDateFormat("EEEE", Locale.US)
    for (entry in items) {
        val cal = Calendar.getInstance().apply { timeInMillis = entry.airingAt * 1000 }
        // Prefer the calendar's own weekday (robust across month edges).
        val idx = (cal.get(Calendar.DAY_OF_WEEK) + 6) % 7
        byWeekday[idx] += entry
    }

    // Build day entries; date numbers come from the real airing date when
    // the week wraps a month boundary, else from the current week's date.
    return (0 until 7).map { i ->
        val entries = byWeekday[i].sortedBy { it.airingAt }
        // If any entry lands on this weekday, use ITS date (handles the
        // month rollover — e.g. a week spanning Jul 28 → Aug 3); otherwise
        // the current week's date for that weekday. Every date is validated
        // so the strip can never render a blank/0 number.
        val dateFromEntry = entries.firstOrNull()?.let {
            Calendar.getInstance().apply { timeInMillis = it.airingAt * 1000 }
                .get(Calendar.DAY_OF_MONTH)
        }
        DaySchedule(
            day = weekdayNames[i],
            shortDay = weekdayNames[i].take(3),
            dateOfMonth = (dateFromEntry ?: weekDates[i]).coerceIn(1, 31),
            isToday = i == todayIndex,
            entries = entries,
        )
    }.sortedBy { day ->
        // Start the strip at today, like the site's default selection.
        (weekdayNames.indexOf(day.day) - todayIndex + 7) % 7
    }
}
