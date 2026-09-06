package com.anikage.app.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anikage.app.Config
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.model.Anime
import com.anikage.app.ui.components.AnimeCard
import com.anikage.app.ui.components.ErrorOrEmptyState
import com.anikage.app.ui.components.LoadingGrid

private val SORTS = listOf(
    "POPULARITY_DESC" to "Popular",
    "TRENDING_DESC" to "Trending",
    "SCORE_DESC" to "Top rated",
    "FAVOURITES_DESC" to "Favourites",
    "START_DATE_DESC" to "Newest",
    "TITLE_ROMAJI_ASC" to "A–Z",
)

private val FORMATS = listOf(
    null to "All formats",
    "TV" to "TV",
    "MOVIE" to "Movie",
    "OVA" to "OVA",
    "ONA" to "ONA",
    "SPECIAL" to "Special",
    "MUSIC" to "Music",
)

private val STATUSES = listOf(
    null to "All statuses",
    "RELEASING" to "Releasing",
    "FINISHED" to "Finished",
    "NOT_YET_RELEASED" to "Upcoming",
    "CANCELLED" to "Cancelled",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(onAnimeClick: (Anime) -> Unit) {
    val context = LocalContext.current
    val repo = remember { AnikageRepository.get(context) }
    val viewModel: BrowseViewModel = viewModel(factory = BrowseViewModel.factory(repo))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    var showFilters by remember { mutableStateOf(false) }

    Column(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)) {

        TopAppBar(
            title = { Text("Browse") },
            actions = {
                IconButton(onClick = { showFilters = true }) {
                    Icon(Icons.Default.FilterList, contentDescription = "Filters")
                }
            },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                actionIconContentColor = MaterialTheme.colorScheme.onBackground,
            ),
        )

        TextField(
            value = state.filters.query,
            onValueChange = { viewModel.applyFilters(state.filters.copy(query = it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Config.Spacing.screenHorizontal),
            singleLine = true,
            placeholder = { Text("Search anime") },
            label = { Text("Search") },
        )

        // Sort chip row (always visible)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Config.Spacing.screenHorizontal, vertical = Config.Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Config.Spacing.sm),
        ) {
            SORTS.forEach { (value, label) ->
                FilterChip(
                    selected = state.filters.sort == value,
                    onClick = { viewModel.applyFilters(state.filters.copy(sort = value)) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }

        if (state.loading) {
            LoadingGrid()
            return@Column
        }

        if (state.items.isEmpty() && state.error != null) {
            ErrorOrEmptyState(
                title = "Couldn't load browse",
                subtitle = state.error ?: "Try again.",
                onAction = { viewModel.loadFirstPage() },
            )
            return@Column
        }

        if (state.items.isEmpty()) {
            ErrorOrEmptyState(
                title = "No anime match",
                subtitle = "Try different filters.",
                onAction = { showFilters = true },
            )
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(Config.Images.GRID_COLUMNS_PHONE),
            state = gridState,
            contentPadding = PaddingValues(
                horizontal = Config.Spacing.screenHorizontal,
                vertical = Config.Spacing.lg,
            ),
            horizontalArrangement = Arrangement.spacedBy(Config.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Config.Spacing.md),
        ) {
            items(state.items, key = { it.id }) { anime ->
                AnimeCard(
                    anime = anime,
                    onClick = onAnimeClick,
                )
            }
            if (state.loadingMore) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(Config.Images.GRID_COLUMNS_PHONE) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            } else if (state.pageInfo.hasNextPage) {
                // Trigger next-page when scrolled to bottom
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(Config.Images.GRID_COLUMNS_PHONE) }) {
                    LaTrigger(onTrigger = { viewModel.loadNextPage() })
                }
            }
        }
    }

    if (showFilters) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showFilters = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            FilterSheet(
                current = state.filters,
                onApply = {
                    viewModel.applyFilters(it)
                    showFilters = false
                },
            )
        }
    }
}

@Composable
private fun LaTrigger(onTrigger: () -> Unit) {
    // Simple trigger using LaunchedEffect on first composition.
    androidx.compose.runtime.LaunchedEffect(Unit) { onTrigger() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    current: BrowseFilters,
    onApply: (BrowseFilters) -> Unit,
) {
    var filters by remember { mutableStateOf(current) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Config.Spacing.xl, vertical = Config.Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Config.Spacing.lg),
    ) {
        Text("Format", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Config.Spacing.sm),
        ) {
            FORMATS.forEach { (value, label) ->
                FilterChip(
                    selected = filters.format == value,
                    onClick = { filters = filters.copy(format = value) },
                    label = { Text(label) },
                )
            }
        }

        Text("Status", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Config.Spacing.sm),
        ) {
            STATUSES.forEach { (value, label) ->
                FilterChip(
                    selected = filters.status == value,
                    onClick = { filters = filters.copy(status = value) },
                    label = { Text(label) },
                )
            }
        }

        androidx.compose.material3.Button(
            onClick = { onApply(filters) },
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text("Apply filters")
        }
    }
}
