package com.anikage.app.ui.details

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

data class DetailsUiState(
    val loading: Boolean = true,
    val details: AnimeDetails? = null,
    val error: String? = null,
    val episodes: List<EpisodeUi> = emptyList(),
    /** True when [episodes] came from the Anikage episodes API (real titles + thumbnails). */
    val realEpisodes: Boolean = false,
)

/** Episode info for the episode list. */
data class EpisodeUi(
    val number: Int,
    val title: String = "Episode $number",
    val thumbnail: String? = null,
    val airedAt: String? = null,
    val hasAired: Boolean = true,
    val isFiller: Boolean = false,
    val isRecap: Boolean = false,
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
                    // Enrich with the site's real episode metadata (titles,
                    // thumbnails, filler flags) when the slug resolves.
                    loadRealEpisodes(details)
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

    private suspend fun loadRealEpisodes(details: AnimeDetails) {
        val slug = repo.resolveSlug(animeId, details.title.english, details.title.romaji)
            ?: return
        repo.anikageEpisodes(slug).onSuccess { eps ->
            if (eps.isNotEmpty()) {
                _state.value = _state.value.copy(
                    episodes = eps.map { ep ->
                        EpisodeUi(
                            number = ep.number,
                            title = ep.title?.takeIf { it.isNotBlank() } ?: "Episode ${ep.number}",
                            thumbnail = ep.image,
                            airedAt = ep.airDate,
                            hasAired = true,
                            isFiller = ep.isFiller,
                            isRecap = ep.isRecap,
                        )
                    },
                    realEpisodes = true,
                )
                AppLogger.i(
                    LogCategory.DATA,
                    "Loaded ${eps.size} real episodes for slug $slug (animeId=$animeId)",
                )
            }
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
 * Fallback when the Anikage episodes API is unavailable: synthesize an
 * "Episode N" list from the AniList `episodes` count.
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
