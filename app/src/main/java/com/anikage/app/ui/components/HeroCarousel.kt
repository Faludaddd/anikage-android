package com.anikage.app.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.anikage.app.Config
import com.anikage.app.core.data.model.Anime
import kotlinx.coroutines.delay

/**
 * Anikage-style hero carousel — full-bleed, gradient-overlayed, with logo
 * image + meta pills + genre chips + description + Watch Now/More info
 * buttons + progress dots + nav arrows.
 *
 * Real Anikage hero is:
 *   - h-[72vh] on mobile, h-[90vh] on tablet, h-screen on desktop
 *   - cover image with multi-direction gradients
 *   - bottom-left content: anime logo image (we use title text — we don't
 *     have anime logo images), meta pills row, genre chips, description
 *   - bottom-center: progress dots + slide counter + nav arrows
 *
 * Auto-advances every 7 seconds (matches Anikage behaviour).
 */
@Composable
fun HeroCarousel(
    items: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    onWatchClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    // Anikage uses 72vh on mobile — we approximate with 0.6 of screen height
    // (gives space for content below on smaller phones).
    val heroHeight = screenHeight * 0.62f

    var currentIndex by remember { mutableStateOf(0) }
    val totalItems = items.size
    val current = items[currentIndex]

    // Auto-advance
    LaunchedEffect(totalItems) {
        while (true) {
            delay(7000)
            currentIndex = (currentIndex + 1) % totalItems
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
    ) {
        // Layer 0: cover image
        AsyncImage(
            model = current.bannerImage ?: current.coverUrl(),
            contentDescription = current.displayTitle(),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Layer 1: gradients (matches Anikage's hero-gradients)
        // - Bottom-up: from-surface via-surface/30 to-surface/10
        // - Left-to-right: from-surface/60 via-surface/30 to-transparent
        // - Top-down: from-surface/40 to-transparent (only top 1/4)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.5f to MaterialTheme.colorScheme.background.copy(alpha = 0.3f),
                        1.0f to MaterialTheme.colorScheme.background,
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                        0.5f to MaterialTheme.colorScheme.background.copy(alpha = 0.3f),
                        1.0f to Color.Transparent,
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to MaterialTheme.colorScheme.background.copy(alpha = 0.4f),
                        0.25f to Color.Transparent,
                        1.0f to Color.Transparent,
                    )
                )
        )

        // Layer 2: content (bottom-left, max-w-2xl)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.85f)
                .padding(start = 16.dp, end = 16.dp, bottom = 80.dp),
        ) {
            // Anime "logo" — we use big bold text because AniList doesn't give us the anime's
            // logo image (Anikage uses the official logo image which they fetch from a separate
            // source — TheTVDB clearlogo. AniList's API doesn't expose logo images).
            Text(
                text = current.displayTitle(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(16.dp))

            // Meta pills row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Star pill (yellow, for score)
                current.averageScore?.let { score ->
                    MetaPill(
                        icon = Icons.Default.Star,
                        text = "${score}%",
                        iconTint = Config.Theme.star,
                        border = Config.Theme.star.copy(alpha = 0.4f),
                        background = Config.Theme.star.copy(alpha = 0.2f),
                        textTint = Config.Theme.star,
                    )
                }
                // Year pill
                current.seasonYear?.let { year ->
                    MetaPill(
                        icon = Icons.Default.CalendarMonth,
                        text = year.toString(),
                    )
                }
                // Episodes pill
                current.episodes?.let { eps ->
                    if (eps > 0) {
                        MetaPill(
                            icon = Icons.Default.Layers,
                            text = "$eps Episodes",
                        )
                    }
                }
                // Duration pill
                current.duration?.let { dur ->
                    if (dur > 0) {
                        MetaPill(
                            icon = Icons.Default.Schedule,
                            text = "$dur min",
                        )
                    }
                }
                // Format pill
                current.format?.let { fmt ->
                    MetaPill(
                        icon = Icons.Default.Tv,
                        text = fmt,
                        upperCase = true,
                    )
                }
            }

            // Genre chips
            if (current.genres.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    current.genres.take(4).forEach { genre ->
                        GenreChip(text = genre)
                    }
                }
            }

            // Description
            // (current.description is in the Anime model — but our hero uses the
            // basic Anime object that doesn't include description; we skip the
            // description text on the home hero to avoid an empty line. The full
            // description appears on the Anime Details screen.)

            Spacer(Modifier.height(16.dp))

            // Action buttons row
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // "Watch Now" — primary CTA
                PrimaryActionButton(
                    icon = Icons.Default.PlayArrow,
                    text = "Watch Now",
                    onClick = { onWatchClick(current) },
                )
                // "More info"
                SecondaryActionButton(
                    icon = Icons.Default.Info,
                    text = "More info",
                    onClick = { onAnimeClick(current) },
                )
            }
        }

        // Bottom row: progress dots + counter + nav arrows
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Progress dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { idx, _ ->
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .width(if (idx == currentIndex) 32.dp else 16.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (idx == currentIndex) Color.White.copy(alpha = 0.8f)
                                else Color.White.copy(alpha = 0.12f)
                            )
                            .clickable { currentIndex = idx }
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Slide counter
                Text(
                    text = "${currentIndex + 1}/${totalItems}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
                // Nav arrows
                NavArrowButton(icon = Icons.Default.ChevronLeft) {
                    currentIndex = if (currentIndex == 0) totalItems - 1 else currentIndex - 1
                }
                NavArrowButton(icon = Icons.Default.ChevronRight) {
                    currentIndex = (currentIndex + 1) % totalItems
                }
            }
        }
    }
}

@Composable
private fun MetaPill(
    icon: ImageVector? = null,
    text: String,
    iconTint: Color = Color.White.copy(alpha = 0.6f),
    border: Color = Color.White.copy(alpha = 0.15f),
    background: Color = Color.Black.copy(alpha = 0.4f),
    textTint: Color = Color.White.copy(alpha = 0.7f),
    upperCase: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = background,
        border = androidx.compose.foundation.BorderStroke(1.dp, border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(12.dp),
                )
            }
            Text(
                text = if (upperCase) text.uppercase() else text,
                style = MaterialTheme.typography.labelSmall,
                color = textTint,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun GenreChip(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun PrimaryActionButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 8.dp,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SecondaryActionButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.10f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun NavArrowButton(
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = Modifier
            .size(36.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
