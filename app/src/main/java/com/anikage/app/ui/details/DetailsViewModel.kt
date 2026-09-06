package com.anikage.app.ui.details

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
    /**
     * Anikage catalogue slug — passed to the watch screen so streaming
     * sources load without a title search.
     */
    val slug: String? = null,
    /**
     * Set when the page is being rendered from preview/list data while the
     * full info payload is still on its way (or failed): the hero, synopsis
     * and metadata are real data; characters/relations/recommendations are
     * pending or unavailable. Never set together with [error].
     */
    val degradedNotice: String? = null,
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

/**
 * Anime details flow:
 *
 *  1. The clicked list item (home rail / browse grid / search result /
 *     schedule entry) was seeded into [AnimePreviewStore] right before
 *     navigation — paint the page from it IMMEDIATELY. No black screen,
 *     no skeleton-only state for data we already hold.
 *  2. Enrich in the background with the site's own info payload
 *     (Anikage backend; see AnikageRepository.animeDetails) — full cast,
 *     relations, recommendations, studios, airing facts.
 *  3. If enrichment fails (both the Anikage and AniList paths), KEEP the
 *     preview-rendered page fully usable and show an honest notice —
 *     the watch pipeline still works via the slug, and the episode list
 *     still loads from the Anikage episodes API.
 *  4. A hard error page only when there is truly nothing to show (no
 *     preview, no cache, both sources failed) — with a retry action.
 */
class DetailsViewModel(
    private val repo: AnikageRepository,
    private val animeId: Int,
) : ViewModel() {
    private val _state = MutableStateFlow(DetailsUiState(loading = true))
    val state: StateFlow<DetailsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null, degradedNotice = null)
        viewModelScope.launch {
            // ── 1. Instant paint from the preview the click handed over. ──
            val preview = AnimePreviewStore.byId(animeId)
            if (preview != null && _state.value.details == null) {
                val provisional = preview.toProvisionalDetails()
                _state.value = _state.value.copy(
                    loading = false,
                    details = provisional,
                    slug = preview.slug,
                    episodes = synthesizeEpisodes(provisional),
                )
                // Real episode metadata needs no AniList at all — load it
                // right away from the catalogue slug.
                loadRealEpisodes(provisional)
            }

            // ── 2. Enrich with the full info payload. ────────────────────
            val result = repo.animeDetails(animeId)
            result.fold(
                onSuccess = { details ->
                    _state.value = _state.value.copy(
                        loading = false,
                        details = details,
                        degradedNotice = null,
                        slug = details.slug ?: _state.value.slug,
                        episodes = if (_state.value.realEpisodes) {
                            _state.value.episodes
                        } else {
                            synthesizeEpisodes(details)
                        },
                    )
                    loadRealEpisodes(details)
                },
                onFailure = { e ->
                    AppLogger.w(LogCategory.DATA, "Details enrichment failed (id=$animeId)", e)
                    if (_state.value.details != null) {
                        // ── 3. Degraded but usable — honest notice, no error page. ──
                        _state.value = _state.value.copy(
                            loading = false,
                            degradedNotice = friendlyNotice(e),
                        )
                    } else {
                        // ── 4. Nothing to show — error page with retry. ──
                        _state.value = _state.value.copy(
                            loading = false,
                            error = friendlyNotice(e),
                        )
                    }
                }
            )
        }
    }

    private suspend fun loadRealEpisodes(details: AnimeDetails) {
        val slug = details.slug ?: _state.value.slug
            ?: repo.resolveSlug(animeId, details.title.english, details.title.romaji)
            ?: return
        if (_state.value.slug != slug) {
            _state.value = _state.value.copy(slug = slug)
        }
        repo.anikageEpisodes(slug).onSuccess { eps ->
            if (eps.isNotEmpty()) {
                // Guard: duplicate episode numbers would collide as lazy-list
                // keys, so dedupe by number (API may return recaps/specials).
                val distinct = eps.distinctBy { it.number }
                _state.value = _state.value.copy(
                    episodes = distinct.map { ep ->
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
                    "Loaded ${distinct.size} real episodes for slug $slug (animeId=$animeId)",
                )
            }
        }
    }

    suspend fun markRecentlyViewed(episode: Int) {
        val details = _state.value.details ?: return
        val anime = com.anikage.app.core.data.model.Anime(
            id = details.id,
            slug = details.slug,
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

/** Honest, human-readable failure explanation (never a bare "HTTP 403"). */
private fun friendlyNotice(e: Throwable): String = when (e) {
    is ApiHttpException -> e.userMessage()
    else -> e.message ?: "Couldn't load the full anime info right now."
}

/**
 * Fallback when the Anikage episodes API is unavailable: synthesize an
 * "Episode N" list from the episode count.
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
