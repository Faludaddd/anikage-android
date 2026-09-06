package com.anikage.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.model.Anime
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.WebTextStyles
import com.anikage.app.ui.components.ErrorOrEmptyState
import com.anikage.app.ui.components.LoadingGrid

/**
 * SEARCH — styled after the site's search experience: a big rounded input
 * (site palette: rounded-xl bg-surface-elevated p-4 text-base font-medium,
 * placeholder "Search Anime...") and results in the site's browse-card grid.
 */
@Composable
fun SearchScreen(
    onAnimeClick: (Anime) -> Unit,
    onBackClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val repo = remember { AnikageRepository.get(context) }
    val viewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory(repo))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val theme = LocalAnikageTheme.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface)
            .padding(top = 80.dp)
    ) {
        // ── Search input (site palette style) ─────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x0DFFFFFF))
                    .clickable(onClick = onBackClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = theme.fg,
                    modifier = Modifier.size(18.dp),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.surfaceElevated)
                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp)),
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = theme.fgMuted,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 14.dp)
                        .size(18.dp),
                )
                BasicTextField(
                    value = state.query,
                    onValueChange = viewModel::updateQuery,
                    singleLine = true,
                    textStyle = WebTextStyles.base.copy(
                        color = Color(0xE6FFFFFF),
                        fontWeight = FontWeight.Medium,
                    ),
                    cursorBrush = SolidColor(theme.action),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterStart)
                        .padding(start = 44.dp, end = 44.dp)
                        .height(52.dp),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (state.query.isEmpty()) {
                                Text(
                                    text = "Search Anime...",
                                    style = WebTextStyles.base,
                                    color = Color(0xFF71717A),
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            inner()
                        }
                    },
                )
                if (state.query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .size(24.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x14FFFFFF))
                            .clickable { viewModel.updateQuery("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = theme.fgMuted,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        // ── Results ────────────────────────────────────────────────────────
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
                // Site grid: minmax(105px, 1fr), gap-4.
                columns = GridCells.Adaptive(105.dp),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.items, key = { "${it.id}-${it.displayTitle()}" }) { anime ->
                    SearchCard(anime = anime, onClick = onAnimeClick)
                }
            }
        }
    }
}

/** Site browse-style card (same as the Browse grid). */
@Composable
private fun SearchCard(anime: Anime, onClick: (Anime) -> Unit) {
    val theme = LocalAnikageTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
            .clickable { onClick(anime) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x0DFFFFFF)),
        ) {
            anime.coverUrl()?.let { url ->
                coil.compose.AsyncImage(
                    model = url,
                    contentDescription = anime.displayTitle(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = anime.displayTitle(),
            style = WebTextStyles.xs,
            color = theme.fgMuted,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
        )
    }
}
