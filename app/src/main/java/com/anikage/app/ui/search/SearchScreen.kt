package com.anikage.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anikage.app.Config
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.model.Anime
import com.anikage.app.ui.components.AnimeCard
import com.anikage.app.ui.components.ErrorOrEmptyState
import com.anikage.app.ui.components.LoadingGrid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onAnimeClick: (Anime) -> Unit) {
    val context = LocalContext.current
    val repo = remember { AnikageRepository(context) }
    val viewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory(repo))
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::updateQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search anime…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(Config.Shape.button),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )

        when {
            !state.hasSearched -> ErrorOrEmptyState(
                title = "Find your next anime",
                subtitle = "Search by title above. Try \"Frieren\", \"Chainsaw Man\", or \"Solo Leveling\".",
                icon = Icons.Default.Search,
                actionText = null,
                onAction = null,
            )
            state.loading -> LoadingGrid()
            state.error != null && state.items.isEmpty() -> ErrorOrEmptyState(
                title = "Search failed",
                subtitle = state.error ?: "",
                onAction = { viewModel.updateQuery(state.query) },
            )
            state.items.isEmpty() -> ErrorOrEmptyState(
                title = "No results",
                subtitle = "Try a different query.",
                icon = Icons.Default.Search,
                actionText = null,
                onAction = null,
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(Config.Images.GRID_COLUMNS_PHONE),
                contentPadding = PaddingValues(
                    horizontal = Config.Spacing.screenHorizontal,
                    vertical = Config.Spacing.lg,
                ),
                horizontalArrangement = Arrangement.spacedBy(Config.Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Config.Spacing.md),
            ) {
                items(state.items, key = { it.id }) { anime ->
                    AnimeCard(anime = anime, onClick = onAnimeClick)
                }
            }
        }
    }
}
