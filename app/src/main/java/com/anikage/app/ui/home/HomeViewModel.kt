package com.anikage.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.Factory
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.currentSeason
import com.anikage.app.core.data.currentYear
import com.anikage.app.core.data.model.Anime
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

class HomeViewModel(
    private val repo: AnikageRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState(loading = true))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val trending = repo.trending(perPage = 20)
            val popularSeason = repo.popularThisSeason(currentSeason(), currentYear(), perPage = 20)
            val topRated = repo.topRated(perPage = 20)
            val upcoming = repo.upcoming(perPage = 20)
            _state.value = HomeUiState(
                trending = trending.getOrDefault(emptyList()),
                popularSeason = popularSeason.getOrDefault(emptyList()),
                topRated = topRated.getOrDefault(emptyList()),
                upcoming = upcoming.getOrDefault(emptyList()),
                loading = false,
                error = if (trending.isFailure && popularSeason.isFailure && topRated.isFailure && upcoming.isFailure)
                    "No data available. Check your internet connection."
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
