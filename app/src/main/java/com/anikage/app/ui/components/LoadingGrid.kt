package com.anikage.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.aspectRatio
import com.anikage.app.Config
import com.anikage.app.core.data.model.Anime

/**
 * Loading placeholder grid — shows shimmer-less blank cards sized to match
 * the real card grid. Used while data is loading from API or cache.
 */
@Composable
fun LoadingGrid(
    columns: Int = 3,
    rows: Int = 4,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = rememberLazyGridState(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = Config.Spacing.screenHorizontal,
            vertical = Config.Spacing.lg,
        ),
        horizontalArrangement = Arrangement.spacedBy(Config.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Config.Spacing.md),
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false,
    ) {
        items(columns * rows) { idx ->
            Box(
                modifier = Modifier
                    .aspectRatio(Config.Images.POSTER_ASPECT)
                    .clip(RoundedCornerShape(Config.Shape.image))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

/** Tiny helper so we can use `items(count) {}` from this import. */
private fun androidx.compose.foundation.lazy.grid.LazyGridScope.items(
    count: Int,
    itemContent: @Composable (Int) -> Unit,
) {
    items(count = count, key = { it }, itemContent = { itemContent(it) })
}

/**
 * Horizontal carousel of anime cards — used on the Home screen for sections
 * like "Trending Now", "Popular This Season", etc.
 */
@Composable
fun AnimeRow(
    items: List<Anime>,
    onClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    itemWidth: androidx.compose.ui.unit.Dp = 130.dp,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Config.Spacing.carouselGap),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = Config.Spacing.screenHorizontal,
        ),
    ) {
        items(items.size) { idx ->
            AnimeCard(
                anime = items[idx],
                onClick = onClick,
                modifier = Modifier.width(itemWidth),
            )
        }
    }
}

/** Section header used above carousels on Home. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Config.Spacing.screenHorizontal)
            .padding(top = Config.Spacing.xl, bottom = Config.Spacing.sm),
    )
}

/** Centered infinite spinner — used by the player screen on first load. */
@Composable
fun LoadingSpinner(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp,
            modifier = Modifier.size(40.dp),
        )
    }
}
