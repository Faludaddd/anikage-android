package com.anikage.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.anikage.app.core.data.model.Anime
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.WebTextStyles
import kotlinx.coroutines.launch

/**
 * Site-exact building blocks, ported 1:1 from the anikage.cc DOM + CSS.
 * Geometry, colours and typography tokens mirror the live site at 412px.
 */

// ---------------------------------------------------------------------------
//  Section header — site: `.cv-section > .mb-4.flex.items-center.justify-between`
//  [4.8px accent bar] "Trending Now" [HOT badge]        [View All →]
// ---------------------------------------------------------------------------

enum class SectionBadge(
    val label: String,
    val tint: Color,
    val bg: Color,
    val border: Color,
) {
    NONE("", Color.Transparent, Color.Transparent, Color.Transparent),
    HOT("HOT", Color(0xFFFB923C), Color(0x1AF97316), Color(0x33F97316)),            // orange-400 / orange-500/10 / orange-500/20
    SEASONAL("SEASONAL", Color(0xFF4ADE80), Color(0x1A22C55E), Color(0x3322C55E)),  // green-400 / green-500/10 / green-500/20
    TOP("TOP", Color(0xFFFACC15), Color(0x1AF59E0B), Color(0x33F59E0B)),            // yellow-400 / amber-500/10 / amber-500/20
    UPCOMING("UPCOMING", Color(0xFF38BDF8), Color(0x1A0EA5E9), Color(0x330EA5E9)),  // sky-400 / sky-500/10 / sky-500/20
}

@Composable
fun SectionHeader(
    title: String,
    badge: SectionBadge = SectionBadge.NONE,
    onViewAll: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val theme = LocalAnikageTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            // Accent bar — h-6 w-[0.30rem] rounded-full bg-action
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .width(4.8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(theme.action)
            )
            Text(
                text = title,
                style = WebTextStyles.titleSection,
                color = theme.fg,
            )
            if (badge != SectionBadge.NONE) {
                Text(
                    text = badge.label,
                    style = WebTextStyles.xs2,
                    fontWeight = FontWeight.SemiBold,
                    color = badge.tint,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(badge.bg)
                        .border(1.dp, badge.border, RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else if (onViewAll != null) {
            ViewAllPill(onClick = onViewAll)
        }
    }
}

/** site: "View All →" — rounded-full border-white/8 bg-white/4 px-2.5 py-1 text-xs zinc-400 */
@Composable
fun ViewAllPill(onClick: () -> Unit, label: String = "View All") {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x0AFFFFFF))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = WebTextStyles.xs,
            color = Color(0xFFA1A1AA),   // zinc-400
            fontWeight = FontWeight.Medium,
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFFA1A1AA),
            modifier = Modifier.size(14.dp),
        )
    }
}

// ---------------------------------------------------------------------------
//  Anime card — site: `a.card-link` (115px wide on phone).
//  ├─ .card-pill  "Ep 22" (top-right, green=releasing / red=finished)
//  ├─ .card-cover → .card-cover-frame (2:3, rounded-xl, white/3 bg, border
//  │   white/6, shadow 4px 0 5px black/30) → cover img
//  └─ .card-title  width minus 10px, CENTERED, text-xs, medium, fg-muted
// ---------------------------------------------------------------------------

@Composable
fun SiteAnimeCard(
    anime: Anime,
    onClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 115.dp,
) {
    val theme = LocalAnikageTheme.current
    val releasing = anime.status == "RELEASING"
    val airingEp = anime.nextAiringEpisode?.episode?.minus(1)?.takeIf { it > 0 }
    // Site: .card-link:active { scale: .97 } — spring back on release.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "cardPress",
    )
    Column(
        modifier = modifier
            .width(cardWidth)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null) { onClick(anime) },
    ) {
        Box(
            modifier = Modifier
                .width(cardWidth)
                .aspectRatio(2f / 3f)
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(12.dp),
                    spotColor = Color(0x4D000000),
                    ambientColor = Color(0x1A000000),
                )
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x08FFFFFF))
                .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(12.dp)),
        ) {
            anime.coverUrl()?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = anime.displayTitle(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // card-pill — top-1.5 right-1.5, rounded-full, surface/90 bg.
            val pillText = when {
                airingEp != null -> "Ep $airingEp"
                anime.episodes != null && anime.episodes > 0 -> "Ep ${anime.episodes}"
                else -> null
            }
            if (pillText != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                        .shadow(2.dp, RoundedCornerShape(50))
                        .clip(RoundedCornerShape(50))
                        .background(theme.surface.copy(alpha = 0.90f))
                        .border(
                            1.dp,
                            if (releasing) Color(0x4D22C55E) else Color(0x4DEF4444),
                            RoundedCornerShape(50),
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (releasing) Color(0xFF4ADE80) else Color(0xFFF87171))
                    )
                    Text(
                        text = pillText,
                        style = WebTextStyles.xs2,
                        color = if (releasing) Color(0xE6FFFFFF) else Color(0xFFFCA5A5),
                        fontWeight = if (releasing) FontWeight.SemiBold else FontWeight.Bold,
                    )
                }
            }
        }

        // card-title — centered, xs, medium, muted, line-clamp-2.
        Text(
            text = anime.displayTitle(),
            style = WebTextStyles.xs,
            color = theme.fgMuted,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
            modifier = Modifier
                .width(cardWidth - 10.dp)
                .align(Alignment.CenterHorizontally)
                .padding(top = 6.dp),
        )
    }
}

// ---------------------------------------------------------------------------
//  Carousel row — site: `.no-scrollbar overflow-x-auto .inline-flex` cards +
//  edge fade arrows (h-[101%], gradient from-surface, disabled opacity-20).
// ---------------------------------------------------------------------------

@Composable
fun SiteCarouselRow(
    items: List<Anime>,
    onClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 115.dp,
) {
    val theme = LocalAnikageTheme.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var canScrollBack by remember { mutableStateOf(false) }
    var canScrollFwd by remember { mutableStateOf(true) }

    // Track edge state for arrow enable/disable (site: disabled at edges).
    LaunchedEffect(items.size, listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            Triple(listState.firstVisibleItemIndex, lastVisible, info.totalItemsCount)
        }.collect { (first, last, total) ->
            canScrollBack = first > 0
            canScrollFwd = total > 0 && last < total - 1
        }
    }

    // Page-walk helpers (3 cards per press, clamped — like the site).
    val scrollByCards: (Int) -> Unit = { delta ->
        scope.launch {
            val current = listState.firstVisibleItemIndex
            val target = (current + delta).coerceIn(0, (items.size - 1).coerceAtLeast(0))
            listState.animateScrollToItem(target)
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            // Site: container-custom max-width 96% ≈ 8px inset each side.
            contentPadding = PaddingValues(horizontal = 8.dp),
            // Site: .card-link margin-right calc(4px*1.5) = 6px between cards.
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(items, key = { "${it.id}-${it.displayTitle()}" }) { anime ->
                SiteAnimeCard(anime = anime, onClick = onClick, cardWidth = cardWidth)
            }
        }

        // Left arrow — gradient fade from surface, hidden at start (site: disabled).
        if (canScrollBack) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(40.dp)
                    .background(
                        Brush.horizontalGradient(
                            0.0f to theme.surface,
                            1.0f to Color.Transparent,
                        )
                    )
                    .clickable { scrollByCards(-3) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        // Right arrow — mirrors site disabled state (opacity 20%).
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(40.dp)
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color.Transparent,
                        1.0f to theme.surface,
                    )
                )
                .alpha(if (canScrollFwd) 1f else 0.2f)
                .clickable(enabled = canScrollFwd) {
                    scrollByCards(3)
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next",
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
//  Top 10 ranked row — 1:1 from the live DOM:
//  div.group.relative.flex.items-center
//    └ div.rounded-lg.border-white/6.bg-white/3.p-1.5.pr-3 (sm:rounded-xl p-2)
//        ├ a h-[81px] w-[57px] (sm: 84x59)
//        │   ├ .#N badge — abs -top-1 -left-1, size-5 rounded-full, bg=RANK
//        │   │   color, text-2xs font-bold text-BLACK
//        │   └ poster (rounded-md, object-cover)
//        ├ column: title (text-base font-medium, line-clamp-2 leading-tight)
//        │   + meta `★ 9.1 • FALL • FINISHED` (text-xs zinc-500)
//        └ right (sm+): "TV Show" (text-sm font-semibold) + "28 ep"
//            (text-xs zinc-500), min-w-100px
//  Rank colors extracted per-rank from the site's inline styles.
// ---------------------------------------------------------------------------

/** Per-rank badge colors — exact inline values from the live site. */
private val RankColors = listOf(
    0xFFBBF1A1, 0xFFE48643, 0xFFE4C993, 0xFFE43550, 0xFFE4935D,
    0xFFE45D86, 0xFFE49350, 0xFFE49350, 0xFFE4D650, 0xFF5DBBE4,
)

/** Popular Movies palette (site restarts the cycle for the movie rows). */
private val MovieRankColors = listOf(
    0xFF0DA1E4, 0xFF5DC9F1, 0xFFE45D5D, 0xFFF1C95D, 0xFFF1C9F1,
    0xFF0DAEE4, 0xFF5DBBE4, 0xFFF19335, 0xFFE4935D, 0xFFE4C993,
)

fun top10RankColor(rank: Int, isMovieRow: Boolean = false): Color {
    val palette = if (isMovieRow) MovieRankColors else RankColors
    return Color(palette[(rank - 1).coerceIn(0, palette.size - 1)])
}

@Composable
fun Top10Row(
    rank: Int,
    anime: Anime,
    onClick: (Anime) -> Unit,
    isMovieRow: Boolean = false,
) {
    val theme = LocalAnikageTheme.current
    val rankColor = top10RankColor(rank, isMovieRow)
    // Site: container p-1.5 pr-3 → poster 57 + gap 8 + text + pr-12.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x08FFFFFF))            // bg-white/3
            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(8.dp))  // border-white/6
            .clickable { onClick(anime) }
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Poster with rank badge overlapping the top-left corner.
        Box(
            modifier = Modifier
                .width(57.dp)
                .height(81.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(theme.surfaceCard)
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
            // Rank badge — site: -top-1 -left-1 size-5 rounded-full bg-[rank
            // color] text-2xs font-bold text-black.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-4).dp, y = (-4).dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(rankColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "#$rank",
                    style = WebTextStyles.xs2,
                    color = Color(0xFF0A0A0A),       // text-black
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = anime.displayTitle(),
                style = WebTextStyles.base,
                color = theme.fg,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
            )
            // Meta: ★ 9.1 • FALL • FINISHED (text-xs zinc-500).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                anime.averageScore?.let { score ->
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFA1A1AA),
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = "%.1f".format(score / 10.0),
                        style = WebTextStyles.xs,
                        color = Color(0xFF71717A),
                    )
                }
                anime.season?.let { s ->
                    if (anime.averageScore != null) MetaDot()
                    Text(
                        text = s,
                        style = WebTextStyles.xs,
                        color = Color(0xFF71717A),
                    )
                }
                anime.status?.let { st ->
                    MetaDot()
                    Text(
                        text = st.replace('_', ' '),
                        style = WebTextStyles.xs,
                        color = Color(0xFF71717A),
                    )
                }
            }
        }

        // Right column (site sm+): format + episode count.
        Column(
            modifier = Modifier
                .padding(start = 10.dp)
                .align(Alignment.CenterVertically),
            horizontalAlignment = Alignment.End,
        ) {
            val formatLabel = when (anime.format) {
                "MOVIE" -> "Movie"
                "TV", "TV_SHORT" -> "TV Show"
                "OVA", "ONA" -> (anime.format ?: "")
                else -> anime.format?.lowercase()?.replaceFirstChar { it.uppercase() } ?: ""
            }
            if (formatLabel.isNotBlank()) {
                Text(
                    text = formatLabel,
                    style = WebTextStyles.sm,
                    color = theme.fg,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            anime.episodes?.let { eps ->
                if (eps > 0) {
                    Text(
                        text = "$eps ep",
                        style = WebTextStyles.xs,
                        color = Color(0xFF71717A),
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaDot() {
    Text(
        text = "•",
        style = WebTextStyles.xs,
        color = Color(0xFF52525B),   // zinc-600
    )
}

// ---------------------------------------------------------------------------
//  Featured banner — site: "Featured Anime / Editor's Pick" card.
//  rounded-[1.75rem] bg-white/[0.04]; inner h-[212px]; banner bg + 3 gradients
//  + accent blur glow + 1px top line; poster 83x124 + score pill + title +
//  synopsis + Watch Now.
// ---------------------------------------------------------------------------

@Composable
fun FeaturedBanner(
    anime: Anime,
    onWatchClick: (Anime) -> Unit,
    onClick: (Anime) -> Unit,
) {
    val theme = LocalAnikageTheme.current
    Column {
        SectionHeader(
            title = "Featured Anime",
            trailing = {
                Text(
                    text = "EDITOR'S PICK",
                    style = WebTextStyles.xs2,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0x73FFFFFF),
                    letterSpacing = 2.5.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x0DFFFFFF))
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(28.dp), spotColor = Color(0xB3000000))
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0x0AFFFFFF))
                .clickable { onClick(anime) },
        ) {
            // Inner stage — h-[212px] (site sm:252 / md:300).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(212.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(theme.surface)
            ) {
                // Banner artwork.
                anime.bannerImage?.let { banner ->
                    AsyncImage(
                        model = banner,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Gradient left→right: from-surface via-surface/70 to-transparent.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0.0f to theme.surface,
                                0.5f to theme.surface.copy(alpha = 0.70f),
                                1.0f to Color.Transparent,
                            )
                        )
                )
                // Gradient bottom→up: from-surface/80.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                1.0f to theme.surface.copy(alpha = 0.80f),
                            )
                        )
                )
                // Accent glow — blur-[80px] bg-action/15 top-left.
                Box(
                    modifier = Modifier
                        .offset(x = (-80).dp, y = (-100).dp)
                        .size(width = 260.dp, height = 260.dp)
                        .background(theme.action.copy(alpha = 0.15f), CircleShape)
                        .blurEffect()
                )
                // Top hairline — 1px white/20.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                0.0f to Color.Transparent,
                                0.5f to Color(0x33FFFFFF),
                                1.0f to Color.Transparent,
                            )
                        )
                )

                // Content row: poster + text block.
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Poster 83x124, rounded-[0.65rem], ring white/15.
                    Box(
                        modifier = Modifier
                            .width(83.dp)
                            .height(124.dp)
                            .shadow(16.dp, RoundedCornerShape(10.dp), spotColor = Color(0xD9000000))
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x1AFFFFFF))
                            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(10.dp)),
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

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Score pill — yellow-400/10 bg, yellow-300 text.
                        anime.averageScore?.let { score ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0x1AFACC15))
                                    .border(1.dp, Color(0x40FACC15), RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 2.dp),
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFDE047),
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = "${score}%",
                                    style = WebTextStyles.xs,
                                    color = Color(0xFFFDE047),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Text(
                            text = anime.displayTitle(),
                            style = WebTextStyles.titleSection,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        anime.description?.let { desc ->
                            Text(
                                text = desc.replace(Regex("<[^>]*>"), "").trim(),
                                style = WebTextStyles.xs,
                                color = Color(0xFFA1A1AA),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 16.sp,
                            )
                        }
                        // Watch Now — small primary pill (white / action).
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .shadow(4.dp, RoundedCornerShape(50), spotColor = Color(0x4D000000))
                                .clip(RoundedCornerShape(50))
                                .background(theme.action)
                                .clickable { onWatchClick(anime) }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                androidx.compose.material.icons.Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = theme.actionFg,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = "Watch Now",
                                style = WebTextStyles.xs,
                                color = theme.actionFg,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Radial blur approximation of the site's blur-[80px] glow. */
private fun Modifier.blurEffect(): Modifier = this.alpha(0.6f)
