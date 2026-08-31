package com.anikage.app.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.anikage.app.Config
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.model.Anime
import com.anikage.app.core.data.model.AnimeDetails
import com.anikage.app.ui.components.AnimeCard
import com.anikage.app.ui.components.ErrorOrEmptyState
import com.anikage.app.ui.components.LoadingSpinner

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailsScreen(
    animeId: Int,
    onBackClick: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
    onWatchClick: (Int, Int) -> Unit,    // (animeId, episode)
) {
    val context = LocalContext.current
    val repo = remember { AnikageRepository.get(context) }
    val viewModel: DetailsViewModel = viewModel(
        factory = DetailsViewModel.factory(repo, animeId)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.loading) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = Config.Spacing.sm, top = Config.Spacing.xl)
                    .statusBarsPadding(),
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            LoadingSpinner(modifier = Modifier.fillMaxSize())
        }
        return
    }

    if (state.error != null && state.details == null) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            ErrorOrEmptyState(
                title = "Couldn't load anime",
                subtitle = state.error ?: "",
                onAction = viewModel::load,
            )
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp).statusBarsPadding(),
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        }
        return
    }

    val details = state.details ?: return

    LazyColumn(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Banner with back button + title overlay
        item {
            Box(modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
            ) {
                AsyncImage(
                    model = details.bannerImage ?: details.coverUrl(),
                    contentDescription = details.displayTitle(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.4f),
                        0.5f to Color.Transparent,
                        1.0f to MaterialTheme.colorScheme.background,
                    )))
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .statusBarsPadding(),
                ) {
                    Surface(color = Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(50)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back",
                            tint = Color.White, modifier = Modifier.padding(8.dp))
                    }
                }
                Column(modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = Config.Spacing.lg, vertical = Config.Spacing.md)
                ) {
                    Text(
                        text = details.displayTitle(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                    )
                    details.studios?.mainStudio()?.let { studio ->
                        Text(
                            text = studio.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }

        // Stats row — score, format, episodes, year
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Config.Spacing.lg, vertical = Config.Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Config.Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                details.averageScore?.let { score ->
                    StatChip(icon = Icons.Default.Star, label = "${score / 10.0}")
                }
                details.format?.let { StatChip(label = it) }
                details.episodes?.let { if (it > 0) StatChip(label = "$it ep") }
                details.seasonYear?.let { StatChip(label = it.toString()) }
            }
        }

        // Genre chips
        if (details.genres.isNotEmpty()) {
            item {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Config.Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Config.Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Config.Spacing.sm),
                ) {
                    details.genres.forEach { genre ->
                        AssistChip(
                            onClick = { /* could navigate to Browse with this genre filter */ },
                            label = { Text(genre) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        }

        // Watch CTA
        if (state.episodes.isNotEmpty()) {
            item {
                Button(
                    onClick = { onWatchClick(details.id, 1) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Config.Spacing.lg, vertical = Config.Spacing.lg),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    shape = RoundedCornerShape(Config.Shape.button),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Watch episode 1", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Description
        item {
            Text(
                text = "Overview",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Config.Spacing.lg, vertical = Config.Spacing.md),
            )
            Text(
                text = details.description?.let { stripHtml(it) } ?: "No description available.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Config.Spacing.lg, vertical = Config.Spacing.sm),
                overflow = TextOverflow.Visible,
            )
        }

        // Episodes list
        if (state.episodes.isNotEmpty()) {
            item {
                Text(
                    text = "Episodes",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Config.Spacing.lg, vertical = Config.Spacing.lg),
                )
            }
            items(state.episodes, key = { it.number }) { ep ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onWatchClick(details.id, ep.number) }
                        .padding(horizontal = Config.Spacing.lg, vertical = Config.Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Episode thumbnail (site uses TheTVDB stills) with number badge
                    Box(modifier = Modifier.size(width = 72.dp, height = 44.dp)) {
                        if (ep.thumbnail != null) {
                            AsyncImage(
                                model = ep.thumbnail,
                                contentDescription = "Episode ${ep.number}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .background(
                                        Color.Black.copy(alpha = 0.7f),
                                        RoundedCornerShape(topEnd = 8.dp),
                                    )
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    text = ep.number.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = ep.number.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(Config.Spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ep.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (ep.airedAt != null) {
                                Text(
                                    text = ep.airedAt,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (ep.isFiller) {
                                if (ep.airedAt != null) Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "FILLER",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            if (ep.isRecap) {
                                if (ep.airedAt != null || ep.isFiller) Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "RECAP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    if (!ep.hasAired) {
                        Text(
                            text = "Upcoming",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Characters (top 6) — compact horizontal row
        details.characters?.nodes?.take(6)?.let { chars ->
            if (chars.isNotEmpty()) {
                item {
                    Text(
                        text = "Characters",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Config.Spacing.lg, vertical = Config.Spacing.lg),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Config.Spacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(Config.Spacing.md),
                    ) {
                        items(chars) { c ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(100.dp)) {
                                AsyncImage(
                                    model = c.image?.large ?: c.image?.medium,
                                    contentDescription = c.name.preferred(),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(90.dp, 120.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = c.name.preferred(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 2,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Related
        details.relations?.nodes?.let { related ->
            if (related.isNotEmpty()) {
                item {
                    Text(
                        text = "Related",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Config.Spacing.lg, vertical = Config.Spacing.lg),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Config.Spacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(Config.Spacing.md),
                    ) {
                        items(related) { a ->
                            AnimeCard(anime = a, onClick = onAnimeClick, modifier = Modifier.width(120.dp))
                        }
                    }
                }
            }
        }

        // Recommendations
        details.recommendations?.nodes?.mapNotNull { it.mediaRecommendation }?.let { recs ->
            if (recs.isNotEmpty()) {
                item {
                    Text(
                        text = "Recommendations",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Config.Spacing.lg, vertical = Config.Spacing.lg),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Config.Spacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(Config.Spacing.md),
                    ) {
                        items(recs) { a ->
                            AnimeCard(anime = a, onClick = onAnimeClick, modifier = Modifier.width(120.dp))
                        }
                    }
                }
            }
        }

        // Trailer (YouTube) — open in browser via external intent
        details.trailer?.let { tr ->
            if (tr.id != null && tr.site == "youtube") {
                item {
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun StatChip(icon: androidx.compose.ui.graphics.vector.ImageVector? = null, label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(50),
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Strip AniList's HTML tags from the description. */
private fun stripHtml(html: String): String {
    return android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
        .trim()
}
