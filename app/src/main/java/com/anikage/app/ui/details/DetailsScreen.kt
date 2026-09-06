package com.anikage.app.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.anikage.app.core.data.model.AnimeDetails
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.WebTextStyles
import com.anikage.app.core.util.HtmlText
import com.anikage.app.ui.components.ErrorOrEmptyState
import com.anikage.app.ui.components.LucideStarFilled
import com.anikage.app.ui.components.SectionBadge
import com.anikage.app.ui.components.SectionHeader
import com.anikage.app.ui.components.SiteCarouselRow
import com.anikage.app.ui.components.SkeletonBlock

/**
 * DETAILS — 1:1 port of anikage.cc/anime/info/{id} (mobile).
 *
 * Site layout:
 *   ├─ banner area h-350px (AniList banner + bottom gradient to surface)
 *   ├─ poster 170x245 centered, overlapping the banner by ~58%
 *   ├─ title text-3xl extrabold (gradient fg→fg/75, centered, drop shadow)
 *   ├─ meta chips h-7 rounded-lg: score(amber) status(emerald) episodes
 *   ├─ Play Now split-button (bg-action h-9 rounded-xl) + 3 round icon btns
 *   ├─ tab pills: Overview / Seasons / Characters / Artwork / Music
 *   ├─ Overview: next-ep banner + info strip + synopsis card + genres
 *   ├─ Episodes: rows (h-76 thumb + EP badge + title + desc)
 *   └─ Recommendations carousel
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsScreen(
    animeId: Int,
    onBackClick: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
    onWatchClick: (Int, Int, String?) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { AnikageRepository.get(context) }
    val viewModel: DetailsViewModel = viewModel(
        factory = DetailsViewModel.factory(repo, animeId)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val theme = LocalAnikageTheme.current

    if (state.loading) {
        // Branded loading state — the site pulses surface-card placeholders
        // (animate-pulse bg-surface-card) in every image slot, never a bare
        // spinner on black.
        DetailsSkeleton()
        return
    }

    if (state.error != null && state.details == null) {
        Box(modifier = Modifier.fillMaxSize().background(theme.surface)) {
            ErrorOrEmptyState(
                title = "Couldn't load anime",
                subtitle = state.error ?: "",
                onAction = viewModel::load,
            )
        }
        return
    }

    val details = state.details ?: return

    LazyColumn(modifier = Modifier.fillMaxSize().background(theme.surface)) {
        // ── Banner + poster + title block ────────────────────────────────
        item(key = "hero") {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Banner: site h-[350px] with bottom gradient.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                ) {
                    AsyncImage(
                        model = details.bannerImage ?: details.coverUrl(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Color.Transparent,
                                    0.5f to theme.surface.copy(alpha = 0.70f),
                                    0.9f to theme.surface,
                                )
                            )
                    )
                }
                // Site: the fixed top nav floats over the banner — no
                // in-page back button on the info page.

                // Poster — 170x245 centered, overlaps banner bottom.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(y = (350 - 122).dp)
                        .fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .width(170.dp)
                            .height(245.dp)
                            .shadow(16.dp, RoundedCornerShape(12.dp), spotColor = Color(0x80000000))
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x08FFFFFF))
                            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(12.dp)),
                    ) {
                        details.coverUrl()?.let { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = details.displayTitle(),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    // Title — gradient text, centered (site: text-3xl 700 tracking-tight).
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = details.displayTitle(),
                        style = WebTextStyles.titleHero,
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        color = theme.fg,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    // Romaji subtitle (site: text-sm fg-muted/90, sm+ only).
                    details.title.romaji?.takeIf { it != details.displayTitle() }?.let { romaji ->
                        Text(
                            text = romaji,
                            style = WebTextStyles.sm,
                            color = theme.fgMuted,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 2.dp),
                        )
                    }

                    // Meta chips — site: h-7 rounded-lg, gap-1.5.
                    Row(
                        modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        details.averageScore?.let { score ->
                            DetailChip(
                                leading = {
                                    // Site: lucide-star h-3.5 fill-amber-400/90.
                                    LucideStarFilled(
                                        tint = Color(0xE6FBBF24),
                                        modifier = Modifier.size(14.dp),
                                    )
                                },
                                text = "%.1f".format(score / 10.0),
                                textTint = Color(0xFFFDE68A),      // amber-200
                                bg = theme.surface.copy(alpha = 0.60f),
                                border = Color(0x40FBBF24),
                                bold = true,
                            )
                        }
                        details.status?.let { st ->
                            DetailChip(
                                text = st.replace('_', ' ').replaceFirstChar { it.uppercase() },
                                textTint = Color(0xFF6EE7B7),      // emerald-300
                                bg = Color(0x3322C55E),
                                border = Color(0x3322C55E),
                                bold = true,
                                upperCase = true,
                            )
                        }
                        details.episodes?.takeIf { it > 0 }?.let { eps ->
                            DetailChip(
                                text = "$eps Episodes",
                                textTint = theme.fgMuted,
                                bg = theme.surface.copy(alpha = 0.60f),
                                border = Color(0x1AFFFFFF),
                            )
                        }
                    }

                    // Play Now — split button (site: bg-action + border-l edit).
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier
                                .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = Color(0x4D000000))
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.action)
                                .clickable { onWatchClick(details.id, 1, state.slug) }
                                .height(36.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clickable { onWatchClick(details.id, 1, state.slug) }
                                    .padding(start = 16.dp, end = 14.dp)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = theme.actionFg,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = "Play Now",
                                    style = WebTextStyles.sm,
                                    color = theme.actionFg,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            // Split edit button (site: border-l bg-surface-input).
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(theme.surfaceInput)
                                    .clickable { /* list editor — site opens manage dialog */ }
                                    .width(44.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit list entry",
                                    tint = theme.fgMuted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        // Round icon buttons (site: size-9, share/bookmark).
                        RoundAction(Icons.Default.Share) { /* share */ }
                        RoundAction(Icons.Default.BookmarkAdd) { /* bookmark */ }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // ── Overview: synopsis card + genres + next-episode banner ─────────
        item(key = "overview") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Honest degraded-mode notice: the page is rendering from the
                // list data the app already had (real data, not placeholders);
                // full enrichment (cast/relations) is temporarily unavailable.
                state.degradedNotice?.let { notice ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x1AF59E0B))
                            .border(1.dp, Color(0x33F59E0B), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = notice,
                            style = WebTextStyles.xs,
                            color = Color(0xFFFDE68A),
                        )
                    }
                }
                // Next-episode banner (site: emerald-500/10 border pill).
                details.nextAiringEpisode?.let { next ->
                    val days = next.timeUntilAiring / 86400
                    val hours = (next.timeUntilAiring % 86400) / 3600
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x1A22C55E))
                            .border(1.dp, Color(0x3322C55E), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "Episode ${next.episode}",
                            style = WebTextStyles.sm,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "airing in ${days}d ${hours}h",
                            style = WebTextStyles.sm,
                            color = Color(0xCCFFFFFF),
                        )
                    }
                }

                // Info strip (site: rounded-2xl border white/6 bg white/3).
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x08FFFFFF))
                        .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    details.format?.let { fmt ->
                        Pill(text = fmt.uppercase(), bold = true)
                    }
                    details.startDate?.let { d ->
                        Pill(text = "Aired ${d.formatShort()}", muted = true)
                    }
                    details.seasonYear?.let { y ->
                        if (details.format == null && details.startDate == null) {
                            Pill(text = y.toString(), muted = true)
                        }
                    }
                }

                // Synopsis card (site: rounded-2xl p-3, SYNOPSIS label 2xs).
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x08FFFFFF))
                        .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                ) {
                    Text(
                        text = "SYNOPSIS",
                        style = WebTextStyles.xs2,
                        color = Color(0x66FFFFFF),
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Text(
                        text = details.description?.let(HtmlText::clean) ?: "No description available.",
                        style = WebTextStyles.sm,
                        color = Color(0xBFFFFFFF),   // site: text-white/75
                        lineHeight = 20.sp,
                    )
                }

                // Genres (site: flex-wrap gap-1.5; first 3 accent-tinted).
                if (details.genres.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        details.genres.forEachIndexed { idx, genre ->
                            val accent = idx < 3
                            Text(
                                text = genre,
                                style = WebTextStyles.xs,
                                color = if (accent) Color(0xFFA6A6A6) else Color(0xFFA6A6A6),
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (accent) theme.accent.copy(alpha = 0.10f) else Color(0x0DFFFFFF))
                                    .border(
                                        1.dp,
                                        if (accent) theme.accent.copy(alpha = 0.25f) else Color(0x0FFFFFFF),
                                        RoundedCornerShape(50),
                                    )
                                    .padding(horizontal = 10.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }

        // ── Episodes ──────────────────────────────────────────────────────
        if (state.episodes.isNotEmpty()) {
            item(key = "episodes-header") {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(16.dp))
                    SectionHeader(title = "Episodes")
                }
            }
            items(state.episodes, key = { it.number }) { ep ->
                EpisodeRow(
                    ep = ep,
                    active = false,
                    onClick = { onWatchClick(details.id, ep.number, state.slug) },
                )
            }
        }

        // ── Characters (site: Characters tab content) ─────────────────────
        details.characters?.nodes?.take(12)?.let { chars ->
            if (chars.isNotEmpty()) {
                item(key = "characters") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Spacer(Modifier.height(8.dp))
                        SectionHeader(title = "Characters")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(chars) { c ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(100.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(theme.surfaceCard),
                                    ) {
                                        AsyncImage(
                                            model = c.image?.large ?: c.image?.medium,
                                            contentDescription = c.name.preferred(),
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                    Text(
                                        text = c.name.preferred(),
                                        style = WebTextStyles.xs,
                                        color = theme.fgMuted,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Recommendations ───────────────────────────────────────────────
        val recs = details.recommendations?.nodes
            ?.mapNotNull { it.mediaRecommendation }
            ?.take(15)
            .orEmpty()
        if (recs.isNotEmpty()) {
            item(key = "recommendations") {
                Column(
                    modifier = Modifier.padding(top = 24.dp),
                ) {
                    SectionHeader(
                        title = "Recommendations",
                        badge = SectionBadge.NONE,
                    )
                    SiteCarouselRow(items = recs, onClick = onAnimeClick)
                }
            }
        }
    }
}

/** Site meta chip — h-7 rounded-lg px-3 text-sm. */
@Composable
private fun DetailChip(
    text: String,
    textTint: Color,
    bg: Color,
    border: Color,
    bold: Boolean = false,
    upperCase: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        leading?.invoke()
        Text(
            text = if (upperCase) text.uppercase() else text,
            style = WebTextStyles.sm,
            color = textTint,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = if (upperCase) 0.8.sp else 0.sp,
        )
    }
}

@Composable
private fun Pill(text: String, bold: Boolean = false, muted: Boolean = false) {
    val theme = LocalAnikageTheme.current
    Text(
        text = text,
        style = WebTextStyles.xs,
        color = if (muted) Color(0xB3FFFFFF) else Color(0xCCFFFFFF),
        fontWeight = if (bold) FontWeight.Medium else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x0DFFFFFF))
            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** Site: round icon button — size-9 rounded-full border-white/15 bg-white/10. */
@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0x1AFFFFFF))
            .border(1.dp, Color(0x26FFFFFF), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xB3FFFFFF), modifier = Modifier.size(16.dp))
    }
}

/** Site episode row — h-76: aspect-video thumb + EP badge + title + desc. */
@Composable
private fun EpisodeRow(
    ep: EpisodeUi,
    active: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalAnikageTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Color(0x14FFFFFF) else Color.Transparent)
            .border(
                1.dp,
                if (active) theme.accent else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail — site: aspect-video h-19 (76px) rounded-xl.
        Box(
            modifier = Modifier
                .height(76.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(theme.surfaceElevated),
        ) {
            ep.thumbnail?.let { thumb ->
                AsyncImage(
                    model = thumb,
                    contentDescription = "Episode ${ep.number}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // EP badge — site: absolute bottom-1.5 left-1.5 rounded-md bg-surface/85.
            Text(
                text = "EP ${ep.number}",
                style = WebTextStyles.xs2,
                color = theme.fg,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(theme.surface.copy(alpha = 0.85f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = ep.title,
                style = WebTextStyles.sm,
                color = theme.fg,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!ep.hasAired) {
                Text(
                    text = "Upcoming",
                    style = WebTextStyles.xs,
                    color = theme.fgMuted,
                )
            } else if (ep.isFiller) {
                Text(
                    text = "Filler",
                    style = WebTextStyles.xs,
                    color = Color(0xFFFB923C),
                )
            } else if (ep.airedAt != null) {
                Text(
                    text = ep.airedAt,
                    style = WebTextStyles.xs,
                    color = theme.fgMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Branded loading state — mirrors the details page structure with pulsing
 * surface-card placeholders (site: `animate-pulse bg-surface-card`), so the
 * transition into the page is smooth instead of a black screen + spinner.
 */
@Composable
private fun DetailsSkeleton() {
    val theme = LocalAnikageTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface),
    ) {
        // Banner block.
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp),
            corner = 0.dp,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .offset(y = (-122).dp)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
        ) {
            // Poster.
            SkeletonBlock(
                modifier = Modifier
                    .width(170.dp)
                    .height(245.dp),
            )
            Spacer(Modifier.height(16.dp))
            // Title bars.
            SkeletonBlock(
                modifier = Modifier
                    .width(220.dp)
                    .height(26.dp),
                corner = 8.dp,
            )
            Spacer(Modifier.height(10.dp))
            SkeletonBlock(
                modifier = Modifier
                    .width(140.dp)
                    .height(14.dp),
                corner = 7.dp,
            )
            Spacer(Modifier.height(14.dp))
            // Meta chips row.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBlock(modifier = Modifier.width(64.dp).height(28.dp), corner = 8.dp)
                SkeletonBlock(modifier = Modifier.width(84.dp).height(28.dp), corner = 8.dp)
                SkeletonBlock(modifier = Modifier.width(110.dp).height(28.dp), corner = 8.dp)
            }
            Spacer(Modifier.height(18.dp))
            // Play Now button.
            SkeletonBlock(
                modifier = Modifier.width(180.dp).height(36.dp),
                corner = 12.dp,
            )
            Spacer(Modifier.height(28.dp))
        }
        // Synopsis card.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                corner = 16.dp,
            )
        }
    }
}
