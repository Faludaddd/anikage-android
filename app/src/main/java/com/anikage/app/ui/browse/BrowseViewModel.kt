package com.anikage.app.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.currentSeason
import com.anikage.app.core.data.currentYear
import com.anikage.app.core.data.model.Anime
import com.anikage.app.core.data.model.PageInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BrowseUiState(
    val items: List<Anime> = emptyList(),
    val pageInfo: PageInfo = PageInfo(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val filters: BrowseFilters = BrowseFilters(),
)

data class BrowseFilters(
    val query: String = "",
    val season: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    val format: String? = null,
    val status: String? = null,
    val sort: String = "POPULARITY_DESC",
)

class BrowseViewModel(
    private val repo: AnikageRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BrowseUiState(loading = true))
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    init {
        loadFirstPage()
    }

    fun loadFirstPage(filters: BrowseFilters? = null) {
        val current = _state.value
        val newFilters = filters ?: current.filters
        _state.value = current.copy(
            loading = true, error = null,
            items = emptyList(),
            pageInfo = PageInfo(),
            filters = newFilters,
        )
        viewModelScope.launch {
            val result = if (newFilters.query.isNotBlank()) repo.search(
                query = newFilters.query,
                page = 1,
                perPage = 24,
            ) else repo.browse(
                page = 1, perPage = 24,
                season = newFilters.season, year = newFilters.year,
                genre = newFilters.genre, format = newFilters.format,
                status = newFilters.status, sort = newFilters.sort,
            )
            result.fold(
                onSuccess = { (items, info) ->
                    _state.value = _state.value.copy(
                        items = items, pageInfo = info,
                        loading = false, error = null,
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        loading = false, error = e.message ?: "Failed to load.",
                    )
                }
            )
        }
    }

    fun loadNextPage() {
        val current = _state.value
        if (!current.pageInfo.hasNextPage || current.loadingMore) return
        _state.value = current.copy(loadingMore = true)
        val nextPage = current.pageInfo.currentPage + 1
        viewModelScope.launch {
            val result = if (current.filters.query.isNotBlank()) repo.search(
                query = current.filters.query,
                page = nextPage,
                perPage = 24,
            ) else repo.browse(
                page = nextPage, perPage = 24,
                season = current.filters.season, year = current.filters.year,
                genre = current.filters.genre, format = current.filters.format,
                status = current.filters.status, sort = current.filters.sort,
            )
            result.fold(
                onSuccess = { (items, info) ->
                    _state.value = _state.value.copy(
                        items = current.items + items,
                        pageInfo = info,
                        loadingMore = false,
                    )
                },
                onFailure = { _ ->
                    _state.value = _state.value.copy(loadingMore = false)
                }
            )
        }
    }

    fun applyFilters(filters: BrowseFilters) {
        loadFirstPage(filters)
    }

    companion object {
        fun factory(repo: AnikageRepository) = viewModelFactory {
            initializer { BrowseViewModel(repo) }
        }
    }
}
