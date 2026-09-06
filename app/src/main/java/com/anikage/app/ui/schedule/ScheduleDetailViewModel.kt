package com.anikage.app.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.AnimePreviewStore
import com.anikage.app.core.data.api.ApiHttpException
import com.anikage.app.core.data.model.AnimeDetails
import com.anikage.app.core.data.model.toProvisionalDetails
import com.anikage.app.core.log.AppLogger
import com.anikage.app.core.log.LogCategory
import com.anikage.app.core.util.HtmlText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScheduleDetailUiState(
    val loading: Boolean = true,
    val details: AnimeDetails? = null,
    /** Plain-text synopsis (site shows the description). */
    val description: String? = null,
    /** Anikage slug — lets "Watch Episode" skip the title search. */
    val slug: String? = null,
    /** Honest notice when the page renders from preview data only. */
    val degradedNotice: String? = null,
    val error: String? = null,
)

/**
 * Backing data for the schedule details page. The clicked schedule entry
 * was seeded into [AnimePreviewStore] (title, poster, description, slug —
 * the site's schedule payload carries all of it), so the page paints
 * instantly and stays fully functional even when enrichment fails.
 */
class ScheduleDetailViewModel(
    private val repo: AnikageRepository,
    private val animeId: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(ScheduleDetailUiState(loading = true))
    val state: StateFlow<ScheduleDetailUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null, degradedNotice = null)
        viewModelScope.launch {
            // ── 1. Instant paint from the schedule entry preview. ──
            val preview = AnimePreviewStore.byId(animeId)
            if (preview != null && _state.value.details == null) {
                val provisional = preview.toProvisionalDetails()
                _state.value = _state.value.copy(
                    loading = false,
                    details = provisional,
                    description = provisional.description?.let(HtmlText::clean),
                    slug = preview.slug,
                )
            }

            // ── 2. Enrich with the full info payload (Anikage primary). ──
            repo.animeDetails(animeId).fold(
                onSuccess = { details: AnimeDetails ->
                    _state.value = _state.value.copy(
                        loading = false,
                        details = details,
                        description = details.description?.let(HtmlText::clean),
                        slug = details.slug ?: _state.value.slug,
                        degradedNotice = null,
                    )
                },
                onFailure = { e ->
                    AppLogger.w(LogCategory.DATA, "Schedule detail load failed (id=$animeId)", e)
                    if (_state.value.details != null) {
                        // Degraded but usable — honest notice.
                        _state.value = _state.value.copy(
                            loading = false,
                            degradedNotice = friendlyNotice(e),
                        )
                    } else {
                        _state.value = _state.value.copy(
                            loading = false,
                            error = friendlyNotice(e),
                        )
                    }
                },
            )

            // Resolve the slug in the background for the Watch CTA.
            val currentSlug = _state.value.slug
            if (currentSlug == null) {
                launch {
                    val title = _state.value.details?.title
                    val slug = repo.resolveSlug(
                        animeId,
                        title?.english,
                        title?.romaji,
                    )
                    if (slug != null) {
                        _state.value = _state.value.copy(slug = slug)
                    }
                }
            }
        }
    }

    companion object {
        fun factory(repo: AnikageRepository, animeId: Int, episode: Int, airingAt: Long) =
            viewModelFactory {
                initializer { ScheduleDetailViewModel(repo, animeId) }
            }
    }
}

/** Honest, human-readable failure explanation (never a bare "HTTP 403"). */
private fun friendlyNotice(e: Throwable): String = when (e) {
    is ApiHttpException -> e.userMessage()
    else -> e.message ?: "Couldn't load the full anime info right now."
}
