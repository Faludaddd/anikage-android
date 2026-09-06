package com.anikage.app.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.model.AnimeDetails
import com.anikage.app.core.log.AppLogger
import com.anikage.app.core.log.LogCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScheduleDetailUiState(
    val loading: Boolean = true,
    val details: AnimeDetails? = null,
    /** Plain-text synopsis (site shows the AniList description). */
    val description: String? = null,
    /** Anikage slug — lets "Watch Episode" skip the title search. */
    val slug: String? = null,
    val error: String? = null,
)

/**
 * Backing data for the schedule details page: the AniList record for the
 * anime (synopsis, genres, next episode, artwork) + the Anikage slug for
 * the watch CTA.
 */
class ScheduleDetailViewModel(
    private val repo: AnikageRepository,
    private val animeId: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(ScheduleDetailUiState(loading = true))
    val state: StateFlow<ScheduleDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.animeDetails(animeId).fold(
                onSuccess = { details ->
                    _state.value = ScheduleDetailUiState(
                        loading = false,
                        details = details,
                        description = details.description?.let(::stripHtml),
                    )
                    // Resolve the slug in the background for the Watch CTA.
                    launch {
                        val slug = repo.resolveSlug(
                            animeId,
                            details.title.english,
                            details.title.romaji,
                        )
                        if (slug != null) {
                            _state.value = _state.value.copy(slug = slug)
                        }
                    }
                },
                onFailure = { e ->
                    AppLogger.w(LogCategory.DATA, "Schedule detail load failed (id=$animeId)", e)
                    _state.value = _state.value.copy(
                        loading = false,
                        error = e.message ?: "Failed to load anime details.",
                    )
                },
            )
        }
    }

    companion object {
        fun factory(repo: AnikageRepository, animeId: Int, episode: Int, airingAt: Long) =
            viewModelFactory {
                initializer { ScheduleDetailViewModel(repo, animeId) }
            }
    }
}

private fun stripHtml(html: String): String =
    html
        .replace("<br\\s*/?>".toRegex(), "\n")
        .replace("</p>|</li>".toRegex(), "\n")
        .replace(Regex("<[^>]*>"), "")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#039;", "'").replace("&nbsp;", " ")
        .replace("(Source: [^)]+\\)?\\s*$".toRegex(), "")
        .trim()
