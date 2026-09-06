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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.anikage.app.Config
import com.anikage.app.core.data.model.Anime

/**
 * Anikage-style anime card.
 *
 * Real Anikage card structure:
 *   - Cover image (aspect-ratio 2:3, rounded corners)
 *   - "Ep N" pill at top-right corner (green dot + text) for currently-airing anime
 *   - Title below the cover (line-clamp-2)
 *   - Subtle gradient at the bottom of the cover
 *
 * The whole card is clickable.
 */
@Composable
fun AnimeCard(
    anime: Anime,
    onClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick(anime) },
    ) {
        // Cover image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(Config.Images.POSTER_ASPECT)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val imageUrl = anime.coverUrl()
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = anime.displayTitle(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Bottom gradient (subtle dark overlay)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.7f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.4f),
                        )
                    )
            )

            // "Ep N" pill at top-right (only for currently-airing anime)
            anime.nextAiringEpisode?.let { airing ->
                val nextEp = airing.episode
                val airedEp = nextEp - 1
                if (airedEp > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.30f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                // Pulsing green dot (we use a static dot since Compose's
                                // infinite animations need rememberInfiniteTransition)
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFF4ADE80))
                                )
                                Text(
                                    text = "Ep $airedEp",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
            }

            // Score pill at bottom-left
            anime.averageScore?.let { score ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = 0.6f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            com.anikage.app.ui.components.LucideStarFilled(
                                tint = Config.Theme.star,
                                modifier = Modifier.size(10.dp),
                            )
                            Text(
                                text = "%.1f".format(score / 10.0),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }
        }

        // Title below cover
        Spacer(Modifier.height(6.dp))
        Text(
            text = anime.displayTitle(),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
        )

        // Meta line (format • status • episodes)
        val meta = buildString {
            anime.format?.let { append(it) }
            anime.status?.let {
                if (isNotEmpty()) append(" • ")
                val s = when (it) {
                    "RELEASING" -> "Releasing"
                    "FINISHED" -> "Finished"
                    "NOT_YET_RELEASED" -> "Upcoming"
                    "CANCELLED" -> "Cancelled"
                    else -> it
                }
                append(s)
            }
            anime.episodes?.let {
                if (isNotEmpty()) append(" • ")
                append("Ep $it")
            }
        }
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp,
            )
        }
    }
}
