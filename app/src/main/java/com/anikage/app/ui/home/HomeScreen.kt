package com.anikage.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.anikage.app.Config
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.model.Anime
import com.anikage.app.ui.components.AnimeCard
import com.anikage.app.ui.components.ErrorOrEmptyState
import com.anikage.app.ui.components.HeroCarousel
import com.anikage.app.ui.components.LoadingGrid

@Composable
fun HomeScreen(
    onAnimeClick: (Anime) -> Unit,
    onSeeAllClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { AnikageRepository.get(context) }
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(repo))
    val state by viewModel.state.collectAsStateWithLifecycle()

    HomeContent(
        state = state,
        onAnimeClick = onAnimeClick,
        onSeeAllClick = onSeeAllClick,
        onRetry = viewModel::load,
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onAnimeClick: (Anime) -> Unit,
    onSeeAllClick: (String) -> Unit,
    onRetry: () -> Unit,
) {
    if (state.loading) {
        LoadingGrid(columns = Config.Images.GRID_COLUMNS_PHONE, rows = 4)
        return
    }

    if (state.error != null && state.trending.isEmpty() && state.popularSeason.isEmpty() &&
        state.topRated.isEmpty() && state.upcoming.isEmpty()) {
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
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 100.dp),
    ) {
        // Hero carousel — full-bleed, takes top trending item as the first slide
        if (state.trending.isNotEmpty()) {
            item {
                HeroCarousel(
                    items = state.trending.take(6),
                    onAnimeClick = onAnimeClick,
                    onWatchClick = { anime -> onAnimeClick(anime) },
                )
            }
        }

        if (state.popularSeason.isNotEmpty()) {
            item {
                SectionHeader(title = "Popular This Season")
                AnimeRow(items = state.popularSeason, onClick = onAnimeClick)
            }
        }

        if (state.trending.isNotEmpty()) {
            item {
                SectionHeader(title = "Trending Now")
                AnimeRow(items = state.trending, onClick = onAnimeClick)
            }
        }

        if (state.topRated.isNotEmpty()) {
            item {
                SectionHeader(title = "Top Rated")
                AnimeRow(items = state.topRated, onClick = onAnimeClick)
            }
        }

        if (state.upcoming.isNotEmpty()) {
            item {
                SectionHeader(title = "Upcoming")
                AnimeRow(items = state.upcoming, onClick = onAnimeClick)
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
        )
        // "View All →" link
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .padding(4.dp),
        ) {
            Text(
                text = "View All",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium,
            )
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
fun AnimeRow(
    items: List<Anime>,
    onClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(items) { anime ->
            AnimeCard(
                anime = anime,
                onClick = onClick,
                modifier = Modifier.width(130.dp),
            )
        }
    }
}
