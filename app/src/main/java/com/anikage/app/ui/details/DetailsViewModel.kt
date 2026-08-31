package com.anikage.app.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.model.AnimeDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailsUiState(
    val loading: Boolean = true,
    val details: AnimeDetails? = null,
    val error: String? = null,
    val episodes: List<EpisodeUi> = emptyList(),
)

/** Episode info for the episode list (synthesized from `episodes` count). */
data class EpisodeUi(
    val number: Int,
    val title: String = "Episode $number",
    val thumbnail: String? = null,
    val airedAt: String? = null,
    val hasAired: Boolean = true,
)

class DetailsViewModel(
    private val repo: AnikageRepository,
    private val animeId: Int,
) : ViewModel() {
    private val _state = MutableStateFlow(DetailsUiState(loading = true))
    val state: StateFlow<DetailsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val result = repo.animeDetails(animeId)
            result.fold(
                onSuccess = { details ->
                    _state.value = DetailsUiState(
                        loading = false,
                        details = details,
                        episodes = synthesizeEpisodes(details),
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = e.message ?: "Failed to load.",
                    )
                }
            )
        }
    }

    suspend fun markRecentlyViewed(episode: Int) {
        val details = _state.value.details ?: return
        val anime = com.anikage.app.core.data.model.Anime(
            id = details.id,
            title = details.title,
            coverImage = details.coverImage,
        )
        repo.markRecentlyViewed(anime, episode)
    }

    companion object {
        fun factory(repo: AnikageRepository, animeId: Int) = viewModelFactory {
            initializer { DetailsViewModel(repo, animeId) }
        }
    }
}

/**
 * AniList does not provide per-episode metadata (titles / thumbnails) via
 * the public GraphQL API — that data lives in paid / partner integrations.
 * We synthesize a simple "Episode N" list from the `episodes` count so the
 * episode list UI works. Real episode data would come from a separate
 * stream-source endpoint (see Config.STREAM_SOURCE_URL).
 */
private fun synthesizeEpisodes(details: AnimeDetails): List<EpisodeUi> {
    val count = details.episodes
    return when {
        count != null && count > 0 -> (1..count).map { EpisodeUi(number = it, hasAired = hasAired(details, it)) }
        details.status == "RELEASING" -> (1..((details.nextAiringEpisode?.episode ?: 1) - 1).coerceAtLeast(0))
            .map { EpisodeUi(number = it, hasAired = true) }
        else -> emptyList()
    }
}

private fun hasAired(details: AnimeDetails, episode: Int): Boolean {
    val next = details.nextAiringEpisode?.episode ?: return true
    return episode < next
}
