package com.anikage.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.model.Anime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val items: List<Anime> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false,
)

class SearchViewModel(
    private val repo: AnikageRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    fun updateQuery(q: String) {
        _state.value = _state.value.copy(query = q)
        searchJob?.cancel()
        if (q.isBlank()) {
            _state.value = SearchUiState(query = q)
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)  // debounce 300ms
            _state.value = _state.value.copy(loading = true, error = null, hasSearched = true)
            val result = repo.search(q)
            result.fold(
                onSuccess = { (items, _) ->
                    // Dedupe (id+title) — lazy-list keys must be unique.
                    val distinct = items.distinctBy { it.id to it.displayTitle() }
                    _state.value = _state.value.copy(items = distinct, loading = false)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = e.message ?: "Search failed.",
                    )
                }
            )
        }
    }

    companion object {
        fun factory(repo: AnikageRepository) = viewModelFactory {
            initializer { SearchViewModel(repo) }
        }
    }
}
