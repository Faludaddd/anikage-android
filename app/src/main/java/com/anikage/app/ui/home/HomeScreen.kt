package com.anikage.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.model.Anime
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.WebTextStyles
import com.anikage.app.ui.components.ErrorOrEmptyState
import com.anikage.app.ui.components.FeaturedBanner
import com.anikage.app.ui.components.HeroCarousel
import com.anikage.app.ui.components.SectionBadge
import com.anikage.app.ui.components.SectionHeader
import com.anikage.app.ui.components.SiteCarouselRow
import com.anikage.app.ui.components.SiteContainer
import com.anikage.app.ui.components.SkeletonBlock
import com.anikage.app.ui.components.Top10Row
import com.anikage.app.ui.components.siteCardWidth
import com.anikage.app.ui.components.siteSectionGap

/**
 * HOME — 1:1 port of the anikage.cc homepage.
 *
 * Site section order (from the live DOM):
 *   hero (spotlight, 72vh, full-bleed w-screen) → Featured Anime [Editor's
 *   Pick] → Trending Now [HOT] → Popular This Season [SEASONAL] →
 *   Most Favorite [TOP] → Top 10 Anime ⇄ Popular Movies → Coming Soon
 *   [UPCOMING]
 * Sections live in `main.container-custom flex-col gap-6 sm:gap-8 md:gap-12`
 * (site breakpoints), so headers and cards share the container's edges —
 * never the raw screen edge.
 */
@Composable
fun HomeScreen(
    onAnimeClick: (Anime) -> Unit,
    onWatchClick: (Anime) -> Unit,
    onSeeAllClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { AnikageRepository.get(context) }
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(repo))
    val state by viewModel.state.collectAsStateWithLifecycle()

    HomeContent(
        state = state,
        onAnimeClick = onAnimeClick,
        onWatchClick = onWatchClick,
        onSeeAllClick = onSeeAllClick,
        onRetry = viewModel::load,
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onAnimeClick: (Anime) -> Unit,
    onWatchClick: (Anime) -> Unit,
    onSeeAllClick: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val theme = LocalAnikageTheme.current
    val feed = state.feed
    val screenWidthDp = LocalConfiguration.current.screenWidthDp

    if (state.loading) {
        HomeSkeleton()
        return
    }

    if (feed.spotlight.isEmpty() && feed.trending.isEmpty() && feed.seasonal.isEmpty()) {
        ErrorOrEmptyState(
            title = "Couldn't load anime",
            subtitle = state.error ?: "Check your connection.",
            onAction = onRetry,
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface),
        // Site: main.container-custom + bottom nav clearance.
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        // 1 ── HERO (spotlight slides with TVDB fanart + clearLogo) — full-bleed.
        if (feed.spotlight.isNotEmpty()) {
            item(key = "hero") {
                HeroCarousel(
                    items = feed.spotlight,
                    onAnimeClick = onAnimeClick,
                    onWatchClick = onWatchClick,
                )
                Spacer(Modifier.height(siteSectionGap(screenWidthDp)))
            }
        }

        // 2 ── FEATURED / Editor's Pick.
        feed.featured?.let { featured ->
            item(key = "featured") {
                Section {
                    FeaturedBanner(
                        anime = featured,
                        onWatchClick = onWatchClick,
                        onClick = onAnimeClick,
                    )
                }
            }
        }

        // 3 ── Trending Now [HOT].
        if (feed.trending.isNotEmpty()) {
            item(key = "trending") {
                Section {
                    SectionHeader(
                        title = "Trending Now",
                        badge = SectionBadge.HOT,
                        onViewAll = { onSeeAllClick("trending") },
                    )
                    SiteCarouselRow(items = feed.trending, onClick = onAnimeClick)
                }
            }
        }

        // 4 ── Popular This Season [SEASONAL].
        if (feed.seasonal.isNotEmpty()) {
            item(key = "seasonal") {
                Section {
                    SectionHeader(
                        title = "Popular This Season",
                        badge = SectionBadge.SEASONAL,
                        onViewAll = { onSeeAllClick("seasonal") },
                    )
                    SiteCarouselRow(items = feed.seasonal, onClick = onAnimeClick)
                }
            }
        }

        // 5 ── Most Favorite [TOP].
        if (feed.favorites.isNotEmpty()) {
            item(key = "favorites") {
                Section {
                    SectionHeader(
                        title = "Most Favorite",
                        badge = SectionBadge.TOP,
                        onViewAll = { onSeeAllClick("favorite") },
                    )
                    SiteCarouselRow(items = feed.favorites, onClick = onAnimeClick)
                }
            }
        }

        // 6 ── Top 10 Anime ⇄ Popular Movies (toggle, site: header button).
        if (feed.top10.isNotEmpty() || feed.popularMovies.isNotEmpty()) {
            item(key = "top10") {
                var showMovies by rememberSaveable { mutableStateOf(false) }
                Section {
                    SectionHeader(
                        title = if (showMovies) "Popular Movies" else "Top 10 Anime",
                        trailing = {
                            MoviesToggle(
                                label = if (showMovies) "Top 10 Anime" else "Popular Movies",
                                onClick = { showMovies = !showMovies },
                            )
                        },
                    )
                    // Site: flex flex-col gap-2 (8px) sm:gap-2.5 (10px).
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val list = if (showMovies) feed.popularMovies else feed.top10
                        list.take(10).forEachIndexed { idx, anime ->
                            Top10Row(
                                rank = idx + 1,
                                anime = anime,
                                onClick = onAnimeClick,
                                isMovieRow = showMovies,
                            )
                        }
                    }
                }
            }
        }

        // 7 ── Coming Soon [UPCOMING].
        if (feed.upcoming.isNotEmpty()) {
            item(key = "upcoming") {
                Section {
                    SectionHeader(
                        title = "Coming Soon",
                        badge = SectionBadge.UPCOMING,
                        onViewAll = { onSeeAllClick("upcoming") },
                    )
                    SiteCarouselRow(items = feed.upcoming, onClick = onAnimeClick)
                }
            }
        }
    }
}

/**
 * One site section: container-custom gap (24/32/48dp responsive) between
 * sections, content aligned to the container edges — headers line up with
 * the cards beneath them, exactly like the site.
 */
@Composable
private fun Section(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val gap = siteSectionGap(LocalConfiguration.current.screenWidthDp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = gap),
    ) {
        SiteContainer {
            content()
        }
    }
}

/** site: header toggle button — rounded-lg border-white/10 bg-white/3, text-sm. */
@Composable
private fun MoviesToggle(label: String, onClick: () -> Unit) {
    val theme = LocalAnikageTheme.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x08FFFFFF))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = WebTextStyles.sm,
            color = theme.fgMuted,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Home loading state — the site pulses placeholders (animate-pulse
 * bg-surface-card) while the feed hydrates; this mirrors the page shape:
 * hero block, section header bars, card rows.
 */
@Composable
private fun HomeSkeleton() {
    val theme = LocalAnikageTheme.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val cardWidth = siteCardWidth(screenWidthDp)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item(key = "skel-hero") {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f),
                corner = 0.dp,
            )
            Spacer(Modifier.height(24.dp))
        }
        item(key = "skel-featured") {
            Section {
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(212.dp),
                    corner = 28.dp,
                )
            }
        }
        repeat(2) { section ->
            item(key = "skel-row-$section") {
                Section {
                    SkeletonBlock(
                        modifier = Modifier.width(180.dp).height(18.dp),
                        corner = 9.dp,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(4) {
                            SkeletonBlock(
                                modifier = Modifier
                                    .width(cardWidth)
                                    .aspectRatio(2f / 3f),
                            )
                        }
                    }
                }
            }
        }
    }
}
