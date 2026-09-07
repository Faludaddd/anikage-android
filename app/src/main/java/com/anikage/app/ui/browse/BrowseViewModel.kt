package com.anikage.app.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.model.Anime
import com.anikage.app.core.log.AppLogger
import com.anikage.app.core.log.LogCategory
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The site Browse page's filter state — every control the site exposes.
 * Null / empty means "Any" (the site's default).
 */
data class BrowseFilters(
    val query: String = "",
    val genres: Set<String> = emptySet(),
    val sort: String = "popularity",   // site default: Popularity
    val season: String? = null,
    val year: Int? = null,
    val formats: Set<String> = emptySet(),
    val statuses: Set<String> = emptySet(),
    val origin: String? = null,
) {
    val isDefault: Boolean
        get() = query.isBlank() && genres.isEmpty() && sort == "popularity" &&
            season == null && year == null && formats.isEmpty() &&
            statuses.isEmpty() && origin == null
}

data class BrowseUiState(
    val loading: Boolean = true,
    val items: List<Anime> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val hasNextPage: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val filters: BrowseFilters = BrowseFilters(),
)

/**
 * Browse screen data — the site's own /api/media/anime/browse endpoint with
 * the site's exact filter set, so the grid matches anikage.cc by construction.
 * AniList GraphQL is the fallback when the Anikage API is unreachable.
 */
@OptIn(FlowPreview::class)
class BrowseViewModel(
    private val repo: AnikageRepository,
    initialSort: String? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(
        BrowseUiState(filters = BrowseFilters(sort = initialSort ?: "popularity")),
    )
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    /** Search text debounced like the site's live search (300ms). */
    private val queryFlow = MutableStateFlow("")

    private var loadJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            queryFlow.debounce(300).distinctUntilChanged().collect { q ->
                // The DEBOUNCED fetch path for search text (site: live search).
                // setQuery only updates the flow; the applied filters update
                // here so typing never double-fires.
                applyFilters(_state.value.filters.copy(query = q))
            }
        }
        load()
    }

    fun load() = applyFilters(_state.value.filters)

    /**
     * Search text — display state updates instantly, the FETCH is debounced
     * (300ms) through [queryFlow]. Screens keep the typed text in a local
     * state so the input never waits for the debounce.
     */
    fun setQuery(query: String) {
        queryFlow.value = query
    }

    fun setFilters(filters: BrowseFilters) {
        // Non-query changes reload immediately WITH the current typed query
        // (the site applies filters + search text together).
        applyFilters(filters.copy(query = queryFlow.value))
    }

    fun resetFilters() {
        applyFilters(BrowseFilters(query = queryFlow.value))
    }

    fun loadMore() {
        val s = _state.value
        if (!s.hasNextPage || s.loadingMore || s.loading) return
        viewModelScope.launch {
            _state.value = s.copy(loadingMore = true)
            val result = fetchPage(s.page + 1, s.filters)
            result.fold(
                onSuccess = { (items, total, hasNext) ->
                    // Dedupe by id+title so lazy keys can never collide.
                    val merged = (s.items + items).distinctBy { it.id to it.displayTitle() }
                    _state.value = _state.value.copy(
                        loadingMore = false,
                        items = merged,
                        page = s.page + 1,
                        hasNextPage = hasNext,
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(loadingMore = false)
                },
            )
        }
    }

    /** Public so Music (the site's catalogue search) can drive it too. */
    fun applyFilters(filters: BrowseFilters) {
        loadJob?.cancel()
        _state.value = _state.value.copy(loading = true, error = null, filters = filters)
        loadJob = viewModelScope.launch {
            val result = fetchPage(1, filters)
            result.fold(
                onSuccess = { (items, total, hasNext) ->
                    _state.value = BrowseUiState(
                        loading = false,
                        items = items,
                        total = total,
                        page = 1,
                        hasNextPage = hasNext,
                        filters = filters,
                    )
                },
                onFailure = { e ->
                    AppLogger.w(LogCategory.DATA, "Browse load failed", e)
                    _state.value = _state.value.copy(
                        loading = false,
                        error = e.message ?: "Couldn't load the catalogue.",
                    )
                },
            )
        }
    }

    /** One page from the Anikage browse API (AniList fallback). */
    private suspend fun fetchPage(
        page: Int,
        filters: BrowseFilters,
    ): Result<Triple<List<Anime>, Int, Boolean>> =
        repo.browseCatalogue(
            query = filters.query.takeIf { it.isNotBlank() },
            sort = filters.sort,
            genres = filters.genres.takeIf { it.isNotEmpty() }?.joinToString(","),
            season = filters.season,
            year = filters.year,
            format = filters.formats.takeIf { it.isNotEmpty() }?.joinToString(","),
            status = filters.statuses.takeIf { it.isNotEmpty() }?.joinToString(","),
            country = filters.origin,
            page = page,
            limit = 30,
        )

    companion object {
        fun factory(repo: AnikageRepository, initialSort: String? = null) = viewModelFactory {
            initializer { BrowseViewModel(repo, initialSort) }
        }
    }
}
