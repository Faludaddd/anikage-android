package com.anikage.app.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.model.Anime
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.WebTextStyles
import com.anikage.app.ui.components.ErrorOrEmptyState
import com.anikage.app.ui.components.LoadingGrid

/**
 * BROWSE — 1:1 port of anikage.cc/browse (mobile).
 *
 * Site chrome (no M3 app bar — the floating top nav handles it):
 *   ├─ toolbar: search input (rounded-xl border-white/8 bg-white/5, search
 *   │  icon start, text-sm font-semibold) + filter toggle + reset buttons
 *   │  (bg-white/5 rounded-xl, size-5 icons)
 *   ├─ grid: repeat(auto-fill, minmax(105px, 1fr)) gap-4 (3 cols on phones)
 *   └─ card: aspect-2/3 rounded-xl cover + centered title below
 *      (px-1.5 pt-1.5 text-xs font-medium text-fg-muted), no pill
 * Filters open in a bottom sheet with the site's radio-group sections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(onAnimeClick: (Anime) -> Unit) {
    val context = LocalContext.current
    val repo = remember { AnikageRepository.get(context) }
    val viewModel: BrowseViewModel = viewModel(factory = BrowseViewModel.factory(repo))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    var showFilters by remember { mutableStateOf(false) }
    val theme = LocalAnikageTheme.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface)
            // Site: container pt-20 (clears the floating top nav).
            .padding(top = 80.dp),
    ) {
        // ── Toolbar (site: search + filter + reset) ─────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Search input — site: rounded-xl border-white/8 bg-white/5 ps-10.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(41.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x0DFFFFFF))
                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(12.dp)),
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = theme.fgMuted,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                        .size(16.dp),
                )
                androidx.compose.foundation.text.BasicTextField(
                    value = state.filters.query,
                    onValueChange = { viewModel.applyFilters(state.filters.copy(query = it)) },
                    singleLine = true,
                    textStyle = WebTextStyles.sm.copy(
                        color = Color(0xE6FFFFFF),
                        fontWeight = FontWeight.SemiBold,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(theme.action),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterStart)
                        .padding(start = 40.dp, end = 12.dp)
                        .height(41.dp),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (state.filters.query.isEmpty()) {
                                Text(
                                    text = "Search",
                                    style = WebTextStyles.sm,
                                    color = Color(0xFF71717A),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            inner()
                        }
                    },
                )
            }

            // Filter toggle — site: bg-white/5 rounded-xl px-3 py-2.5.
            Box(
                modifier = Modifier
                    .height(41.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x0DFFFFFF))
                    .clickable { showFilters = true }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = "Toggle filters",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }

            // Reset — site: same style, disabled when no filters.
            val hasFilters = state.filters.season != null || state.filters.year != null ||
                state.filters.genre != null || state.filters.format != null ||
                state.filters.status != null || state.filters.sort != "POPULARITY_DESC"
            Box(
                modifier = Modifier
                    .height(41.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x0DFFFFFF))
                    .clickable(enabled = hasFilters) {
                        viewModel.applyFilters(
                            state.filters.copy(
                                season = null, year = null, genre = null,
                                format = null, status = null, sort = "POPULARITY_DESC",
                            )
                        )
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Reset filters",
                    tint = if (hasFilters) Color.White else Color(0x4DFFFFFF),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // ── Grid / states ───────────────────────────────────────────────────
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
            // Site: minmax(105px, 1fr) phone / 135px sm / 155px md, gap-4.
            columns = GridCells.Adaptive(if (isTablet) 135.dp else 105.dp),
            state = gridState,
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(state.items, key = { "${it.id}-${it.displayTitle()}" }) { anime ->
                BrowseCard(anime = anime, onClick = onAnimeClick)
            }

            if (state.loadingMore) {
                item(span = { GridItemSpan(3) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = theme.action, strokeWidth = 2.dp)
                    }
                }
            } else if (state.pageInfo.hasNextPage) {
                item(span = { GridItemSpan(3) }) {
                    // Site: "Load more" — centered pill button, my-5.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Load More",
                            style = WebTextStyles.sm,
                            color = theme.fgMuted,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0x0DFFFFFF))
                                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(50))
                                .clickable { viewModel.loadNextPage() }
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                        )
                    }
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
            containerColor = theme.surfaceCard,
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

/** Site browse card — aspect-2/3 rounded-xl cover, centered title below. */
@Composable
private fun BrowseCard(
    anime: Anime,
    onClick: (Anime) -> Unit,
) {
    val theme = LocalAnikageTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)   // site: mb-6
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
                AsyncImage(
                    model = url,
                    contentDescription = anime.displayTitle(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Site: line-clamp-2 px-1.5 pt-1.5 text-center text-xs font-medium fg-muted.
        Text(
            text = anime.displayTitle(),
            style = WebTextStyles.xs,
            color = theme.fgMuted,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun LaTrigger(onTrigger: () -> Unit) {
    LaunchedEffect(Unit) { onTrigger() }
}

/**
 * Filter sheet — site sections (radio groups): Sort, Season, Format, Status,
 * with the site's radio dots (size-4 round, checked = fg) + text-sm zinc-500
 * labels, inside settings-style cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    current: BrowseFilters,
    onApply: (BrowseFilters) -> Unit,
) {
    var filters by remember { mutableStateOf(current) }
    val theme = LocalAnikageTheme.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Filters",
            style = WebTextStyles.titleSection,
            color = theme.fg,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        FilterGroup(title = "Sort") {
            SortOptions(
                selected = filters.sort,
                onSelect = { filters = filters.copy(sort = it) },
            )
        }
        FilterGroup(title = "Season") {
            listOf(null to "Any", "WINTER" to "Winter", "SPRING" to "Spring", "SUMMER" to "Summer", "FALL" to "Fall").forEach { (value, label) ->
                RadioRow(
                    label = label,
                    selected = filters.season == value,
                    onClick = { filters = filters.copy(season = value) },
                )
            }
        }
        FilterGroup(title = "Format") {
            listOf(null to "Any", "TV" to "TV", "TV_SHORT" to "TV Short", "MOVIE" to "Movie", "SPECIAL" to "Special", "OVA" to "OVA", "ONA" to "ONA").forEach { (value, label) ->
                RadioRow(
                    label = label,
                    selected = filters.format == value,
                    onClick = { filters = filters.copy(format = value) },
                )
            }
        }
        FilterGroup(title = "Status") {
            listOf(null to "Any", "RELEASING" to "Releasing", "FINISHED" to "Finished", "NOT_YET_RELEASED" to "Not Yet Released", "CANCELLED" to "Cancelled").forEach { (value, label) ->
                RadioRow(
                    label = label,
                    selected = filters.status == value,
                    onClick = { filters = filters.copy(status = value) },
                )
            }
        }

        // Apply — site primary button style (white/action).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(theme.action)
                .clickable { onApply(filters) }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Apply filters",
                style = WebTextStyles.sm,
                color = theme.actionFg,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    val theme = LocalAnikageTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x08FFFFFF))
            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = WebTextStyles.xs2,
            color = Color(0x66FFFFFF),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        )
        content()
    }
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalAnikageTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
    ) {
        // Site radio: size-4 round border, checked = filled fg dot.
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color(0x14FFFFFF))
                .border(
                    1.dp,
                    if (selected) theme.fg else Color(0x33FFFFFF),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(theme.fg)
                )
            }
        }
        Text(
            text = label,
            style = WebTextStyles.sm,
            color = if (selected) theme.fg else Color(0xFF71717A),
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SortOptions(selected: String, onSelect: (String) -> Unit) {
    listOf(
        "POPULARITY_DESC" to "Popularity",
        "TRENDING_DESC" to "Trending",
        "SCORE_DESC" to "Top rated",
        "FAVOURITES_DESC" to "Favourites",
        "START_DATE_DESC" to "Newest",
        "TITLE_ROMAJI_ASC" to "A–Z",
    ).forEach { (value, label) ->
        RadioRow(
            label = label,
            selected = selected == value,
            onClick = { onSelect(value) },
        )
    }
}
