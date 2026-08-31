package com.anikage.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.Factory
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.model.Anime
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val trending: List<Anime> = emptyList(),
    val popularSeason: List<Anime> = emptyList(),
    val topRated: List<Anime> = emptyList(),
    val upcoming: List<Anime> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * Home screen data.
 *
 * All four section fetches launch CONCURRENTLY (the previous version awaited
 * them one-by-one, adding each request's latency to the total). The
 * repository's single-flight layer guarantees each section is fetched exactly
 * once no matter how many widgets consume it, so duplicated network calls at
 * startup are impossible by construction.
 */
class HomeViewModel(
    private val repo: AnikageRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState(loading = true))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var loadJob: kotlinx.coroutines.Job? = null

    init {
        load()
    }

    fun load(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        _state.value = _state.value.copy(loading = true, error = null)
        loadJob = viewModelScope.launch {
            val trending = async { repo.trending(forceRefresh) }
            val popularSeason = async { repo.popularThisSeason(forceRefresh) }
            val topRated = async { repo.topRated(forceRefresh) }
            val upcoming = async { repo.upcoming(forceRefresh) }
            val trendingResult = trending.await()
            val popularResult = popularSeason.await()
            val topRatedResult = topRated.await()
            val upcomingResult = upcoming.await()
            _state.value = HomeUiState(
                trending = trendingResult.getOrDefault(emptyList()),
                popularSeason = popularResult.getOrDefault(emptyList()),
                topRated = topRatedResult.getOrDefault(emptyList()),
                upcoming = upcomingResult.getOrDefault(emptyList()),
                loading = false,
                error = if (
                    trendingResult.isFailure && popularResult.isFailure &&
                    topRatedResult.isFailure && upcomingResult.isFailure
                ) "No data available. Check your internet connection."
                else null,
            )
        }
    }

    companion object {
        fun factory(repo: AnikageRepository) = viewModelFactory {
            initializer { HomeViewModel(repo) }
        }
    }
}
