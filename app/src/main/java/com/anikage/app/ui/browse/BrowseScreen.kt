package com.anikage.app.ui.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
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

/**
 * BROWSE — 1:1 port of anikage.cc/browse.
 *
 * Site DOM (extracted from the live page):
 *   container-custom min-h-[100dvh] pt-20 lg:pt-23
 *   ├─ MOBILE (<lg): flex w-full gap-3
 *   │    [search input flex-1 rounded-xl bg-white/5 px-4 py-2.5 ps-10
 *   │     text-sm font-semibold placeholder "Search"]
 *   │    [toggle-filters btn bg-white/5 px-3 py-2.5 (Tune icon)]
 *   │    [reset btn bg-white/5 px-3 py-2.5 (trash, disabled:opacity-50)]
 *   │    filter panel (expanded): 2-col grid of selects —
 *   │      Genres | Sort by · Season | Year · Status | Format · Origin
 *   ├─ DESKTOP (lg+): flex-row gap-4
 *   │    [Search flex-1 (label "Search" title-subsec)] [Genres w-180]
 *   │    [Sort by w-180 (default "Popularity")] [Year w-150] [Reset self-end]
 *   │    mt-5 flex-row gap-6:
 *   │      [sidebar hidden lg:flex min-w-200 gap-4: accordions
 *   │        Season(open) / Format / Status / Origin — radio rows]
 *   │      [results grid minmax(105px,1fr) gap-4 sm:135 md:155]
 *
 * Data comes from the site's own /api/media/anime/browse so the results
 * match anikage.cc by construction (AniList GraphQL is only a fallback).
 */
@Composable
fun BrowseScreen(
    onAnimeClick: (Anime) -> Unit,
    initialSort: String? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { AnikageRepository.get(context) }
    val viewModel: BrowseViewModel = viewModel(factory = BrowseViewModel.factory(repo, initialSort))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val theme = LocalAnikageTheme.current

    val configuration = LocalConfiguration.current
    val isDesktop = configuration.screenWidthDp >= 840     // site lg breakpoint
    var filtersOpen by remember { mutableStateOf(false) }

    // Infinite scroll: fetch the next page when the end is near.
    val gridState = rememberLazyGridState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.hasNextPage && !state.loadingMore && !state.loading &&
                info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface)
            .statusBarsPadding(),
    ) {
        // ── Top clearance: site pt-20 (content starts under the floating nav).
        Spacer(Modifier.height(if (isDesktop) 68.dp else 56.dp))

        // Live search text (site's search field) — local state so typing is
        // instant; the ViewModel debounces the actual fetch.
        var searchQuery by rememberSaveable { mutableStateOf("") }
        val onQueryChange: (String) -> Unit = { searchQuery = it; viewModel.setQuery(it) }

        if (isDesktop) {
            DesktopFilterRow(
                filters = state.filters,
                query = searchQuery,
                onQuery = onQueryChange,
                onChange = viewModel::setFilters,
                onReset = viewModel::resetFilters,
            )
        } else {
            MobileFilterBar(
                query = searchQuery,
                onQuery = onQueryChange,
                filtersOpen = filtersOpen,
                onToggle = { filtersOpen = !filtersOpen },
                canReset = !state.filters.isDefault,
                onReset = viewModel::resetFilters,
            )
            AnimatedVisibility(
                visible = filtersOpen,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                MobileFilterPanel(
                    filters = state.filters,
                    onChange = viewModel::setFilters,
                )
            }
        }

        // ── Results ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = if (isDesktop) 20.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),   // site gap-6
        ) {
            if (isDesktop) {
                DesktopFilterSidebar(
                    filters = state.filters,
                    onChange = viewModel::setFilters,
                    modifier = Modifier
                        .width(200.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }

            // Grid (site: minmax(105px,1fr) gap-4; sm:135 md:155).
            val minCard = when {
                configuration.screenWidthDp >= 840 -> 155.dp
                configuration.screenWidthDp >= 600 -> 135.dp
                else -> 105.dp
            }
            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.loading -> {
                        // Site: pulsing card grid (animate-pulse bg-fg/5),
                        // never a bare spinner on black.
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minCard),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 16.dp, end = 16.dp, bottom = 110.dp,
                            ),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(18) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    com.anikage.app.ui.components.SkeletonBlock(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(2f / 3f),
                                        corner = 12.dp,
                                    )
                                    com.anikage.app.ui.components.SkeletonBlock(
                                        modifier = Modifier
                                            .fillMaxWidth(0.75f)
                                            .padding(top = 8.dp)
                                            .height(12.dp),
                                        corner = 6.dp,
                                    )
                                }
                            }
                        }
                    }
                    state.error != null && state.items.isEmpty() -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 64.dp),
                        ) {
                            Text(
                                text = "Couldn't load the catalogue",
                                style = WebTextStyles.base,
                                color = theme.fg,
                            )
                            Text(
                                text = state.error ?: "",
                                style = WebTextStyles.sm,
                                color = theme.fgMuted,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "Retry",
                                style = WebTextStyles.sm,
                                color = theme.action,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x08FFFFFF))
                                    .clickable { viewModel.load() }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    state.items.isEmpty() -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 64.dp),
                        ) {
                            Text(
                                text = "No results",
                                style = WebTextStyles.base,
                                color = theme.fg,
                            )
                            Text(
                                text = "Try different filters or clear them.",
                                style = WebTextStyles.sm,
                                color = theme.fgMuted,
                            )
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(minCard),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 16.dp, end = 16.dp, bottom = 110.dp,
                            ),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                state.items,
                                key = { "${it.id}-${it.displayTitle()}" },
                            ) { anime ->
                                BrowseCard(anime = anime, onClick = { onAnimeClick(anime) })
                            }
                            if (state.loadingMore) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            color = theme.fgMuted,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Site card — aspect-2/3 rounded-xl shadow, title below (line-clamp-2).
// ---------------------------------------------------------------------------

@Composable
private fun BrowseCard(anime: Anime, onClick: () -> Unit) {
    val theme = LocalAnikageTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .shadow(4.dp, RoundedCornerShape(12.dp), spotColor = Color(0x4D000000))
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
        Spacer(Modifier.height(8.dp))
        Text(
            text = anime.displayTitle(),
            style = WebTextStyles.xs,
            color = theme.fg,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------
//  Mobile: search bar + filter toggle + reset  (site: lg:hidden row)
// ---------------------------------------------------------------------------

@Composable
private fun MobileFilterBar(
    query: String,
    onQuery: (String) -> Unit,
    filtersOpen: Boolean,
    onToggle: () -> Unit,
    canReset: Boolean,
    onReset: () -> Unit,
) {
    val theme = LocalAnikageTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        // Search input — site: rounded-xl border-white/8 bg-white/5 ps-10.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x0DFFFFFF))
                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = theme.fgMuted,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Search", style = WebTextStyles.sm, color = Color(0xFF71717A), fontWeight = FontWeight.SemiBold)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = WebTextStyles.sm.copy(color = theme.fg, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        // Toggle filters — site: bg-white/5 px-3 py-2.5 (Tune icon).
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x0DFFFFFF))
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(
                Icons.Default.Tune,
                contentDescription = "Toggle filters",
                tint = if (filtersOpen) theme.fg else theme.fgMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        // Reset — site: trash icon, disabled:opacity-50.
        Icon(
            Icons.Default.Delete,
            contentDescription = "Reset filters",
            tint = if (canReset) Color.White else Color(0x50FFFFFF),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x0DFFFFFF))
                .clickable(enabled = canReset, onClick = onReset)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .size(20.dp),
        )
    }
}

/**
 * Mobile filter panel — site's expanded state: 2-column grid of selects.
 * Order: Genres | Sort by · Season | Year · Status | Format · Origin.
 */
@Composable
private fun MobileFilterPanel(
    filters: BrowseFilters,
    onChange: (BrowseFilters) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterSelect(
                label = "Genres",
                selected = filters.genres.sorted().joinToString(", ") { genreLabel(it) },
                options = Genres.map { it to genreLabel(it) },
                multi = true,
                checked = { filters.genres.contains(it) },
                onPick = { value, on ->
                    val next = if (on) filters.genres + value else filters.genres - value
                    onChange(filters.copy(genres = next))
                },
                onClear = { onChange(filters.copy(genres = emptySet())) },
                modifier = Modifier.weight(1f),
            )
            FilterSelect(
                label = "Sort by",
                selected = sortLabel(filters.sort),
                options = SortOptions.map { it.first to it.second },
                multi = false,
                checked = { filters.sort == it },
                onPick = { value, _ -> onChange(filters.copy(sort = value)) },
                onClear = { onChange(filters.copy(sort = "popularity")) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterSelect(
                label = "Season",
                selected = filters.season?.let { seasonLabel(it) } ?: "",
                options = Seasons.map { it to seasonLabel(it) },
                multi = false,
                checked = { filters.season == it },
                onPick = { value, _ -> onChange(filters.copy(season = value)) },
                onClear = { onChange(filters.copy(season = null)) },
                modifier = Modifier.weight(1f),
            )
            FilterSelect(
                label = "Year",
                selected = filters.year?.toString() ?: "",
                options = Years.map { it.toString() to it.toString() },
                multi = false,
                checked = { filters.year?.toString() == it },
                onPick = { value, _ -> onChange(filters.copy(year = value.toIntOrNull())) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterSelect(
                label = "Status",
                selected = filters.statuses.sorted().joinToString(", ") { statusLabel(it) },
                options = Statuses.map { it to statusLabel(it) },
                multi = true,
                checked = { filters.statuses.contains(it) },
                onPick = { value, on ->
                    val next = if (on) filters.statuses + value else filters.statuses - value
                    onChange(filters.copy(statuses = next))
                },
                onClear = { onChange(filters.copy(statuses = emptySet())) },
                modifier = Modifier.weight(1f),
            )
            FilterSelect(
                label = "Format",
                selected = filters.formats.sorted().joinToString(", ") { formatLabel(it) },
                options = Formats.map { it to formatLabel(it) },
                multi = true,
                checked = { filters.formats.contains(it) },
                onPick = { value, on ->
                    val next = if (on) filters.formats + value else filters.formats - value
                    onChange(filters.copy(formats = next))
                },
                onClear = { onChange(filters.copy(formats = emptySet())) },
                modifier = Modifier.weight(1f),
            )
        }
        FilterSelect(
            label = "Origin",
            selected = filters.origin?.let { originLabel(it) } ?: "",
            options = Origins.map { it to originLabel(it) },
            multi = false,
            checked = { filters.origin == it },
            onPick = { value, _ -> onChange(filters.copy(origin = value)) },
            onClear = { onChange(filters.copy(origin = null)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------------------
//  Desktop: top filter row + left accordion sidebar
// ---------------------------------------------------------------------------

@Composable
private fun DesktopFilterRow(
    filters: BrowseFilters,
    query: String,
    onQuery: (String) -> Unit,
    onChange: (BrowseFilters) -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        // Search — site: label "Search" (title-subsec) + input flex-1.
        Column(modifier = Modifier.weight(1f)) {
            Text("Search", style = WebTextStyles.sm, color = LocalAnikageTheme.current.fg, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
            SiteSearchInput(query, onQuery)
        }
        Column(modifier = Modifier.width(180.dp)) {
            Text("Genres", style = WebTextStyles.sm, color = LocalAnikageTheme.current.fg, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
            FilterSelect(
                label = "",
                selected = filters.genres.sorted().joinToString(", ") { genreLabel(it) },
                options = Genres.map { it to genreLabel(it) },
                multi = true,
                checked = { filters.genres.contains(it) },
                onPick = { value, on ->
                    val next = if (on) filters.genres + value else filters.genres - value
                    onChange(filters.copy(genres = next))
                },
                onClear = { onChange(filters.copy(genres = emptySet())) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Column(modifier = Modifier.width(180.dp)) {
            Text("Sort by", style = WebTextStyles.sm, color = LocalAnikageTheme.current.fg, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
            FilterSelect(
                label = "",
                selected = sortLabel(filters.sort),
                options = SortOptions.map { it.first to it.second },
                multi = false,
                checked = { filters.sort == it },
                onPick = { value, _ -> onChange(filters.copy(sort = value)) },
                onClear = { onChange(filters.copy(sort = "popularity")) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Column(modifier = Modifier.width(150.dp)) {
            Text("Year", style = WebTextStyles.sm, color = LocalAnikageTheme.current.fg, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
            FilterSelect(
                label = "",
                selected = filters.year?.toString() ?: "",
                options = Years.map { it.toString() to it.toString() },
                multi = false,
                checked = { filters.year?.toString() == it },
                onPick = { value, _ -> onChange(filters.copy(year = value.toIntOrNull())) },
                onClear = { onChange(filters.copy(year = null)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Reset — site: self-end trash button.
        Icon(
            Icons.Default.Delete,
            contentDescription = "Reset filters",
            tint = if (!filters.isDefault) Color.White else Color(0x50FFFFFF),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x0DFFFFFF))
                .clickable(enabled = !filters.isDefault, onClick = onReset)
                .padding(horizontal = 14.dp, vertical = 11.dp)
                .size(20.dp),
        )
    }
}

/** Desktop sidebar — site: hidden lg:flex min-w-[200px] flex-col gap-4 accordions. */
@Composable
private fun DesktopFilterSidebar(
    filters: BrowseFilters,
    onChange: (BrowseFilters) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(start = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RadioAccordion(
            title = "Season",
            initiallyOpen = true,          // site: Season accordion open by default
            options = Seasons.map { it to seasonLabel(it) },
            selected = filters.season,
            onSelect = { onChange(filters.copy(season = it)) },
        )
        RadioAccordion(
            title = "Format",
            initiallyOpen = false,
            options = Formats.map { it to formatLabel(it) },
            selected = null,               // multi-select on the site
            multiOptions = formatsChecked(filters),
            onMultiToggle = { value, on ->
                val next = if (on) filters.formats + value else filters.formats - value
                onChange(filters.copy(formats = next))
            },
            selectedCount = filters.formats.size,
            onSelect = { onChange(filters.copy(formats = emptySet())) },
        )
        RadioAccordion(
            title = "Status",
            initiallyOpen = false,
            options = Statuses.map { it to statusLabel(it) },
            selected = null,
            multiOptions = statusesChecked(filters),
            onMultiToggle = { value, on ->
                val next = if (on) filters.statuses + value else filters.statuses - value
                onChange(filters.copy(statuses = next))
            },
            selectedCount = filters.statuses.size,
            onSelect = { onChange(filters.copy(statuses = emptySet())) },
        )
        RadioAccordion(
            title = "Origin",
            initiallyOpen = false,
            options = Origins.map { it to originLabel(it) },
            selected = filters.origin,
            onSelect = { onChange(filters.copy(origin = it)) },
        )
    }
}

private fun formatsChecked(filters: BrowseFilters): Map<String, Boolean> =
    Formats.associateWith { filters.formats.contains(it) }

private fun statusesChecked(filters: BrowseFilters): Map<String, Boolean> =
    Statuses.associateWith { filters.statuses.contains(it) }

/** Site accordion item: rounded-xl border-white/6 bg-white/3 px-4 py-2. */
@Composable
private fun RadioAccordion(
    title: String,
    initiallyOpen: Boolean,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
    multiOptions: Map<String, Boolean> = emptyMap(),
    onMultiToggle: (String, Boolean) -> Unit = { _, _ -> },
    selectedCount: Int = 0,
) {
    var open by remember { mutableStateOf(initiallyOpen) }
    val theme = LocalAnikageTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x08FFFFFF))
            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(12.dp)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text = title + if (selectedCount > 0) " ($selectedCount)" else "",
                style = WebTextStyles.lg,
                fontSize = 17.sp,
                color = theme.fg,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                tint = theme.fg,
                modifier = Modifier.size(20.dp),
            )
        }
        AnimatedVisibility(visible = open, enter = expandVertically(), exit = shrinkVertically()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // "Any" row (site's radio groups include a clear/Any option).
                RadioRow(
                    label = "Any",
                    checked = selected == null && selectedCount == 0,
                    onToggle = { if (selected != null || selectedCount > 0) onSelect(null) },
                )
                options.forEach { (value, label) ->
                    val checked = if (multiOptions.isNotEmpty()) {
                        multiOptions[value] == true
                    } else {
                        selected == value
                    }
                    RadioRow(
                        label = label,
                        checked = checked,
                        onToggle = {
                            if (multiOptions.isNotEmpty()) onMultiToggle(value, !checked)
                            else onSelect(if (checked) null else value)
                        },
                    )
                }
            }
        }
    }
}

/** Site radio row: size-4 rounded-full border; label text-sm medium zinc-500. */
@Composable
private fun RadioRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    val theme = LocalAnikageTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (checked) theme.fg else Color.Transparent)
                .border(
                    width = if (checked) 5.dp else 1.dp,
                    color = if (checked) theme.fg else Color(0x33FFFFFF),
                    shape = CircleShape,
                ),
        )
        Text(
            text = label,
            style = WebTextStyles.sm,
            color = if (checked) theme.fg else Color(0xFF71717A),
            fontWeight = FontWeight.Medium,
        )
    }
}

// ---------------------------------------------------------------------------
//  Shared controls
// ---------------------------------------------------------------------------

/** Site search input (desktop, labelled variant uses the same box). */
@Composable
private fun SiteSearchInput(value: String, onValueChange: (String) -> Unit) {
    val theme = LocalAnikageTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x0DFFFFFF))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = theme.fgMuted,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("Search", style = WebTextStyles.sm, color = Color(0xFF71717A))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = WebTextStyles.sm.copy(color = theme.fg),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Site select — button: flex w-full justify-between rounded-xl
 * border-white/8 bg-white/5 px-3 py-2.5 text-sm text-zinc-500 +
 * chevrons-up-down icon; opens a dropdown menu (site: listbox popover).
 */
@Composable
private fun FilterSelect(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    multi: Boolean,
    checked: (String) -> Boolean,
    onPick: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** "Any" — clears this filter back to its default (site's reset row). */
    onClear: () -> Unit = {},
) {
    val theme = LocalAnikageTheme.current
    var open by remember { mutableStateOf(false) }
    val display = selected.ifBlank { "Any" }

    Column(modifier = modifier) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = WebTextStyles.sm,
                color = theme.fg,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x0DFFFFFF))
                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                    .clickable { open = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = display,
                    style = WebTextStyles.sm,
                    color = if (selected.isBlank()) Color(0xFF71717A) else theme.fg,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color(0xFF71717A),
                    modifier = Modifier.size(16.dp),
                )
            }
            androidx.compose.material3.DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                modifier = Modifier
                    .background(Color(0xFF151515))
                    .width(220.dp),
            ) {
                // Clear option — resets the filter (site: "Any").
                DropdownRow(
                    label = "Any",
                    checked = selected.isBlank(),
                ) { onClear(); open = false }
                options.forEach { (value, optionLabel) ->
                    DropdownRow(
                        label = optionLabel,
                        checked = checked(value),
                    ) {
                        onPick(value, !checked(value))
                        if (!multi) open = false
                    }
                }
            }
        }
    }
}

@Composable
private fun DropdownRow(label: String, checked: Boolean, onClick: () -> Unit) {
    val theme = LocalAnikageTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (checked) theme.fg else Color.Transparent)
                .border(
                    width = if (checked) 5.dp else 1.dp,
                    color = if (checked) theme.fg else Color(0x33FFFFFF),
                    shape = CircleShape,
                ),
        )
        Text(
            text = label,
            style = WebTextStyles.sm,
            color = if (checked) theme.fg else Color(0xFFA1A1AA),
        )
        if (checked) {
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.Check, contentDescription = null, tint = theme.fg, modifier = Modifier.size(14.dp))
        }
    }
}

// ---------------------------------------------------------------------------
//  Filter option data — site values (API enums) + display labels
// ---------------------------------------------------------------------------

private val SortOptions = listOf(
    "popularity" to "Popularity",
    "trending" to "Trending",
    "score" to "Score",
    "favourites" to "Favourites",
    "newest" to "Newest",
)

private val Seasons = listOf("WINTER", "SPRING", "SUMMER", "FALL")
private fun seasonLabel(v: String) = when (v) {
    "WINTER" -> "Winter"; "SPRING" -> "Spring"; "SUMMER" -> "Summer"; else -> "Fall"
}

private val Formats = listOf("TV", "TV_SHORT", "MOVIE", "SPECIAL", "OVA", "ONA")
private fun formatLabel(v: String) = when (v) {
    "TV" -> "TV"; "TV_SHORT" -> "TV Short"; "MOVIE" -> "Movie"; "SPECIAL" -> "Special"
    "OVA" -> "OVA"; else -> "ONA"
}

private val Statuses = listOf("FINISHED", "RELEASING", "NOT_YET_RELEASED", "CANCELLED")
private fun statusLabel(v: String) = when (v) {
    "FINISHED" -> "Finished"; "RELEASING" -> "Releasing"
    "NOT_YET_RELEASED" -> "Not Yet Released"; else -> "Cancelled"
}

private val Origins = listOf("JP", "KR", "CN", "TW")
private fun originLabel(v: String) = when (v) {
    "JP" -> "Japan"; "KR" -> "South Korea"; "CN" -> "China"; else -> "Taiwan"
}

private val Genres = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy",
    "Horror", "Mahou Shoujo", "Mecha", "Music", "Mystery", "Psychological",
    "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller",
)
private fun genreLabel(v: String) = when (v) {
    "Mahou Shoujo" -> "Mahou Shoujo"; "Sci-Fi" -> "Sci-Fi"; else -> v
}

private val Years = (java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) + 1) downTo 1960

private fun sortLabel(v: String) = SortOptions.firstOrNull { it.first == v }?.second ?: "Popularity"
