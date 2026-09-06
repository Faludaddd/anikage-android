package com.anikage.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.Factory
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.model.Anime
import com.anikage.app.core.data.model.HomeFeed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val feed: HomeFeed = HomeFeed(),
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * Home screen data — a single GET /api/media/anime/home call (the exact
 * payload the website renders from), so section order, contents and hero
 * artwork match anikage.cc by construction.
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
            val result = repo.homeFeed(forceRefresh)
            _state.value = HomeUiState(
                feed = result.getOrDefault(HomeFeed()),
                loading = false,
                error = result.exceptionOrNull()?.let { "Couldn't load the homepage. Pull to retry." },
            )
        }
    }

    companion object {
        fun factory(repo: AnikageRepository) = viewModelFactory {
            initializer { HomeViewModel(repo) }
        }
    }
}
