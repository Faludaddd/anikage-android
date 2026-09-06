package com.anikage.app.ui.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.api.AnikageMusicAnime
import com.anikage.app.core.log.AppLogger
import com.anikage.app.core.log.LogCategory
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class MusicUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<AnikageMusicAnime> = emptyList(),
    /** True once at least one search finished — gates the empty state. */
    val searched: Boolean = false,
    val error: String? = null,
)

/**
 * Music Explorer state — the site's music page debounces the query 500ms
 * and calls `/api/animethemes?path=/search…`; same flow here.
 */
@OptIn(FlowPreview::class)
class MusicViewModel(
    private val repo: AnikageRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MusicUiState())
    val state: StateFlow<MusicUiState> = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")
    private var searchJob: Job? = null

    init {
        // Site: debounce 500ms -> search.
        viewModelScope.launch {
            queryFlow
                .debounce(500)
                .distinctUntilChanged()
                .collect { q -> if (q.isNotBlank()) doSearch(q) }
        }
    }

    fun onQueryChange(q: String) {
        _state.value = _state.value.copy(query = q)
        queryFlow.value = q
        if (q.isBlank()) {
            searchJob?.cancel()
            _state.value = _state.value.copy(loading = false, results = emptyList(), searched = false)
        }
    }

    fun retry() {
        val q = _state.value.query
        if (q.isNotBlank()) doSearch(q)
    }

    private fun doSearch(q: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            repo.musicSearch(q).fold(
                onSuccess = { results ->
                    _state.value = _state.value.copy(
                        loading = false,
                        results = results,
                        searched = true,
                    )
                },
                onFailure = { e ->
                    AppLogger.w(LogCategory.NETWORK, "Music search error", e)
                    _state.value = _state.value.copy(
                        loading = false,
                        searched = true,
                        error = e.message ?: "Search failed. Check your connection and try again.",
                    )
                },
            )
        }
    }

    companion object {
        fun factory(repo: AnikageRepository) = viewModelFactory {
            initializer { MusicViewModel(repo) }
        }
    }
}
