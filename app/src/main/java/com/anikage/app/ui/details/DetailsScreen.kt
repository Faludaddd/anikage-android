package com.anikage.app.ui.details

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
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
import com.anikage.app.core.download.EpisodeDownloadEngine
import com.anikage.app.core.settings.SettingsState
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.WebTextStyles
import com.anikage.app.core.util.HtmlText
import com.anikage.app.ui.components.ErrorOrEmptyState
import com.anikage.app.ui.components.LucideStarFilled
import com.anikage.app.ui.components.SectionBadge
import com.anikage.app.ui.components.SectionHeader
import com.anikage.app.ui.components.SiteCarouselRow
import com.anikage.app.ui.components.SkeletonBlock
import kotlinx.coroutines.launch

/**
 * DETAILS — the app's full custom anime page (directive #14): hero banner +
 * poster + title + meta chips, an action row (Continue Watching / Play Now,
 * Subscribe, Add to List, Trailer, Share), a rich info grid (studio, year,
 * type, episodes, duration, season, status), synopsis + genres, an episode
 * list with SEASON SWITCHING + watched/downloaded markers + selection
 * downloads (directive #5/#6), characters, relations and recommendations.
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
    var showListSheet by remember { mutableStateOf(false) }
    // Selection downloads (directive #5) straight from the episode layout.
    var selectionMode by remember { mutableStateOf(false) }
    var selectedEpisodes by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val downloadStates by EpisodeDownloadEngine.statesFlow.collectAsStateWithLifecycle()

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
    val visibleEpisodes = remember(state.episodes, state.selectedSeason) {
        state.seasons.firstOrNull { it.key == state.selectedSeason }?.episodes ?: state.episodes
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.surface)) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // ── Banner + poster + title + meta + actions ─────────────────────
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
                // Back button over the banner (site: info page back button).
                Box(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(start = 16.dp, top = 10.dp)
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x08FFFFFF))
                        .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(12.dp))
                        .clickable(onClick = onBackClick)
                        .align(Alignment.TopStart),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = theme.fg,
                        modifier = Modifier.size(18.dp),
                    )
                }

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

                    // Title — gradient text, centered.
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
                    // Romaji subtitle.
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

                    // Meta chips — score / status / episodes / format.
                    Row(
                        modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        details.averageScore?.let { score ->
                            DetailChip(
                                leading = {
                                    LucideStarFilled(
                                        tint = Color(0xE6FBBF24),
                                        modifier = Modifier.size(14.dp),
                                    )
                                },
                                text = "%.1f".format(score / 10.0),
                                textTint = Color(0xFFFDE68A),
                                bg = theme.surface.copy(alpha = 0.60f),
                                border = Color(0x40FBBF24),
                                bold = true,
                            )
                        }
                        details.status?.let { st ->
                            DetailChip(
                                text = st.replace('_', ' ').replaceFirstChar { it.uppercase() },
                                textTint = Color(0xFF6EE7B7),
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
                        details.format?.let { fmt ->
                            DetailChip(
                                text = fmt,
                                textTint = theme.fgMuted,
                                bg = theme.surface.copy(alpha = 0.60f),
                                border = Color(0x1AFFFFFF),
                                upperCase = true,
                            )
                        }
                    }

                    // ── Action row (directive #14): Continue/Play, Subscribe,
                    //    List, Trailer, Share — all REAL actions. ───────────
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val continueEp = state.continueEpisode
                        val label = if (continueEp > 0) "Continue EP $continueEp" else "Play Now"
                        Row(
                            modifier = Modifier
                                .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = Color(0x4D000000))
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.action)
                                .clickable {
                                    val ep = if (continueEp > 0) continueEp else 0
                                    onWatchClick(details.id, ep, state.slug)
                                }
                                .height(36.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = theme.actionFg,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = label,
                                style = WebTextStyles.sm,
                                color = theme.actionFg,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        // Subscribe (directive #12) — real notification engine.
                        RoundStateAction(
                            icon = if (state.subscribed) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                            active = state.subscribed,
                            description = if (state.subscribed) "Subscribed" else "Subscribe",
                        ) { viewModel.toggleSubscription() }
                        // Add to list — local list (same store as watch page).
                        RoundStateAction(
                            icon = if (state.listStatus != null) Icons.Default.BookmarkAdded else Icons.Default.BookmarkAdd,
                            active = state.listStatus != null,
                            description = "Add to list",
                        ) { showListSheet = true }
                        // Trailer — opens the real YouTube trailer (site: trailerId).
                        if (!details.trailerId.isNullOrBlank() || details.trailer?.id != null) {
                            RoundAction(Icons.Default.Movie) {
                                val id = details.trailerId ?: details.trailer?.id
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://www.youtube.com/watch?v=$id"),
                                        ),
                                    )
                                }
                            }
                        }
                        // Share — real share sheet with the site's info URL.
                        RoundAction(Icons.Default.Share) {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TITLE, details.displayTitle())
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "${details.displayTitle()} — watch on Anikage: " +
                                        "https://anikage.cc/anime/info/${state.slug ?: details.id}",
                                )
                            }
                            context.startActivity(Intent.createChooser(send, "Share"))
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // ── Overview: next-episode + info grid + synopsis + genres ────────
        item(key = "overview") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Honest degraded-mode notice.
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
                        if (state.subscribed) {
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "You'll be notified",
                                style = WebTextStyles.xs,
                                color = Color(0xFF6EE7B7),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                // ── Info grid (directive #14: studio, year, type, episodes,
                //    duration, season, status) — site: rounded-2xl card. ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x08FFFFFF))
                        .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        details.mainStudio()?.name?.let { InfoCell("Studio", it) }
                        details.seasonYear?.let { InfoCell("Year", it.toString()) }
                        details.format?.let { InfoCell("Type", it.uppercase()) }
                        details.duration?.takeIf { it > 0 }?.let { InfoCell("Duration", "${it}m") }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        details.season?.let {
                            InfoCell("Season", "${it.replaceFirstChar { c -> c.uppercase() }} ${details.seasonYear ?: ""}")
                        }
                        details.status?.let { InfoCell("Status", it.replace('_', ' ')) }
                        details.episodes?.takeIf { it > 0 }?.let { InfoCell("Episodes", it.toString()) }
                        details.favourites?.takeIf { it > 0 }?.let { InfoCell("Favourites", "%,d".format(it)) }
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

                // Genres (site: flex-wrap gap-1.5).
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
                                color = Color(0xFFA6A6A6),
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (accent) theme.accent.copy(alpha = 0.10f) else Color(0x0DFFFFFF),
                                    )
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

        // ── Episodes: season selector + selection downloads + rows ──────
        if (state.episodes.isNotEmpty()) {
            item(key = "episodes-header") {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(16.dp))
                    SectionHeader(title = "Episodes")
                }
            }
            // Season chips (directive #6) + selection toolbar (directive #5).
            if (state.seasons.size > 1 || true) {
                item(key = "season-bar") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        if (state.seasons.size > 1) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 8.dp),
                            ) {
                                items(state.seasons, key = { it.key }) { season ->
                                    val selected = season.key == state.selectedSeason
                                    Text(
                                        text = season.label,
                                        style = WebTextStyles.xs,
                                        color = if (selected) theme.actionFg else Color(0xFFD4D4D8),
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(if (selected) theme.action else Color(0x0DFFFFFF))
                                            .border(
                                                1.dp,
                                                if (selected) Color.Transparent else Color(0x14FFFFFF),
                                                RoundedCornerShape(50),
                                            )
                                            .clickable { viewModel.selectSeason(season.key) }
                                            .padding(horizontal = 14.dp, vertical = 7.dp),
                                    )
                                }
                            }
                        }
                        // Select episodes → download (directive #5).
                        if (selectionMode) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x14FFFFFF))
                                    .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle, null,
                                    tint = theme.action,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "${selectedEpisodes.size} selected",
                                    style = WebTextStyles.sm,
                                    color = theme.fg,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "All",
                                    style = WebTextStyles.xs, color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x1FFFFFFF))
                                        .clickable {
                                            selectedEpisodes = visibleEpisodes.map { it.number }.toSet()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                                Text(
                                    text = "Download",
                                    style = WebTextStyles.xs, color = theme.actionFg,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(theme.action)
                                        .clickable(enabled = selectedEpisodes.isNotEmpty()) {
                                            downloadEpisodes(context, viewModel, state, animeId, selectedEpisodes.toList())
                                            selectionMode = false
                                            selectedEpisodes = emptySet()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                                Icon(
                                    Icons.Default.Close, "Cancel selection",
                                    tint = Color(0xFFD4D4D8),
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            selectionMode = false
                                            selectedEpisodes = emptySet()
                                        }
                                        .padding(4.dp),
                                )
                            }
                        } else if (state.slug != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x0DFFFFFF))
                                        .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(8.dp))
                                        .clickable { selectionMode = true }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Icon(
                                        Icons.Default.SelectAll, null,
                                        tint = theme.fgMuted,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = "Select episodes",
                                        style = WebTextStyles.xs,
                                        color = theme.fgMuted,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            items(visibleEpisodes, key = { it.number }) { ep ->
                EpisodeRow(
                    ep = ep,
                    active = false,
                    watched = (state.episodeProgress[ep.number] ?: 0f) >= 0.95f,
                    progressFraction = state.episodeProgress[ep.number] ?: 0f,
                    downloaded = state.downloadedEpisodes.contains(ep.number),
                    selectionMode = selectionMode,
                    selected = selectedEpisodes.contains(ep.number),
                    onToggleSelect = {
                        selectedEpisodes = if (selectedEpisodes.contains(ep.number)) {
                            selectedEpisodes - ep.number
                        } else selectedEpisodes + ep.number
                    },
                    onClick = { onWatchClick(details.id, ep.number, state.slug) },
                )
            }
            // Active download status lines (real engine states).
            val active = downloadStates.filter { it.animeId == animeId }
            if (active.isNotEmpty()) {
                item(key = "download-status") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        active.take(3).forEach { dl ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x08FFFFFF))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Icon(
                                    Icons.Default.FileDownload, null,
                                    tint = theme.action,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = "EP ${dl.episode} — " + when (dl.status) {
                                        EpisodeDownloadEngine.Status.DOWNLOADING -> "downloading ${(dl.progress * 100).toInt()}%"
                                        EpisodeDownloadEngine.Status.PAUSED -> "paused"
                                        EpisodeDownloadEngine.Status.FAILED -> "failed"
                                        EpisodeDownloadEngine.Status.RESOLVING -> "preparing…"
                                        EpisodeDownloadEngine.Status.QUEUED -> "queued"
                                        EpisodeDownloadEngine.Status.COMPLETED -> "downloaded"
                                    },
                                    style = WebTextStyles.xs,
                                    color = theme.fg,
                                )
                            }
                        }
                        LaunchedEffect(active.size) { viewModel.refreshDownloads() }
                    }
                }
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
                            items(chars, key = { "char-${it.id}" }) { c ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(100.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            // Site: character card -> AniList page.
                                            runCatching {
                                                context.startActivity(
                                                    Intent(
                                                        Intent.ACTION_VIEW,
                                                        Uri.parse("https://anilist.co/character/${c.id}"),
                                                    ),
                                                )
                                            }
                                        },
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

        // ── Relations (site: relations rail before recommendations) ──────
        val relations = details.relations?.nodes?.take(15).orEmpty()
        if (relations.isNotEmpty()) {
            item(key = "relations") {
                Column(
                    modifier = Modifier.padding(top = 24.dp),
                ) {
                    SectionHeader(
                        title = "Relations",
                        badge = SectionBadge.NONE,
                    )
                    SiteCarouselRow(items = relations, onClick = onAnimeClick)
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

        item(key = "bottom-space") { Spacer(Modifier.height(96.dp)) }
    }

        // ── List sheet — local anime list (same store as the watch page) ──
        if (showListSheet) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showListSheet = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF101010))
                        .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
                        .padding(20.dp),
                ) {
                    Text(
                        text = "Add to your list",
                        style = WebTextStyles.base,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        "watching" to "Watching",
                        "planned" to "Plan to Watch",
                        "completed" to "Completed",
                        "on_hold" to "On Hold",
                        "dropped" to "Dropped",
                    ).forEach { (key, label) ->
                        val selectedStatus = state.listStatus == key
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedStatus) Color(0x14FFFFFF) else Color.Transparent)
                                .clickable {
                                    viewModel.setListStatus(if (selectedStatus) null else key)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = label,
                                style = WebTextStyles.sm,
                                color = if (selectedStatus) theme.action else Color.White,
                                fontWeight = if (selectedStatus) FontWeight.SemiBold else FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            if (selectedStatus) {
                                Icon(
                                    Icons.Default.Check, null,
                                    tint = theme.action,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Done",
                        style = WebTextStyles.sm,
                        color = theme.actionFg,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.action)
                            .clickable { showListSheet = false }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}

/** Batch-download the selected episodes (real engine; skips existing). */
private fun downloadEpisodes(
    context: android.content.Context,
    viewModel: DetailsViewModel,
    state: DetailsUiState,
    animeId: Int,
    episodes: List<Int>,
) {
    val slug = state.slug ?: return
    val details = state.details
    val repo = AnikageRepository.get(context)
    val height = SettingsState.downloadQualityHeight
    kotlinx.coroutines.MainScope().launch {
        val existing = repo.downloadedForAnime(animeId).map { it.episode }.toSet()
        episodes.filter { it !in existing }.forEach { ep ->
            EpisodeDownloadEngine.enqueue(
                context,
                EpisodeDownloadEngine.DownloadRequest(
                    animeId = animeId,
                    slug = slug,
                    episode = ep,
                    provider = com.anikage.app.Config.DEFAULT_STREAM_PROVIDER,
                    lang = SettingsState.streamLang,
                    height = height,
                    titleRomaji = details?.title?.romaji,
                    titleEnglish = details?.title?.english,
                    episodeTitle = state.episodes.firstOrNull { it.number == ep }?.title,
                    posterUrl = details?.coverImage?.best(),
                ),
            )
        }
        viewModel.refreshDownloads()
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

/** One cell of the info grid (label over value). */
@Composable
private fun InfoCell(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Text(
            text = value,
            style = WebTextStyles.sm,
            color = Color(0xE6FFFFFF),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label.uppercase(),
            style = WebTextStyles.xs2,
            color = Color(0x59FFFFFF),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        )
    }
}

/** Round action with an active accent state (subscribe / list). */
@Composable
private fun RoundStateAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    val theme = LocalAnikageTheme.current
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (active) theme.action.copy(alpha = 0.18f) else Color(0x1AFFFFFF))
            .border(
                1.dp,
                if (active) theme.action.copy(alpha = 0.55f) else Color(0x26FFFFFF),
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (active) theme.action else Color(0xB3FFFFFF),
            modifier = Modifier.size(16.dp),
        )
    }
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
    watched: Boolean,
    progressFraction: Float,
    downloaded: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
) {
    val theme = LocalAnikageTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    selected -> theme.action.copy(alpha = 0.18f)
                    active -> Color(0x14FFFFFF)
                    else -> Color.Transparent
                },
            )
            .border(
                1.dp,
                when {
                    selected -> theme.action.copy(alpha = 0.55f)
                    active -> theme.accent
                    else -> Color.Transparent
                },
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (selected) theme.action else Color(0x0DFFFFFF))
                    .border(
                        1.dp,
                        if (selected) theme.action else Color(0x26FFFFFF),
                        CircleShape,
                    )
                    .clickable(onClick = onToggleSelect),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Default.Check, "Selected",
                        tint = theme.actionFg,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
        }
        // Thumbnail — site: aspect-video h-19 (76px) rounded-xl.
        Box(
            modifier = Modifier
                .height(76.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(theme.surfaceElevated),
        ) {
            if (SettingsState.episodeThumbnails) {
                ep.thumbnail?.let { thumb ->
                    AsyncImage(
                        model = thumb,
                        contentDescription = "Episode ${ep.number}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (watched) {
                Box(Modifier.fillMaxSize().background(Color(0x66000000)))
                if (!selectionMode) {
                    Icon(
                        Icons.Default.Check, "Watched",
                        tint = Color(0xFF34D399),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(28.dp)
                            .background(Color(0x8C000000), CircleShape)
                            .padding(5.dp),
                    )
                }
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
            if (downloaded) {
                Icon(
                    Icons.Default.DownloadDone, "Downloaded",
                    tint = Color(0xFF34D399),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(18.dp),
                )
            }
            // Watch progress bar.
            if (progressFraction in 0.01f..0.94f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color(0x33FFFFFF)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .fillMaxSize()
                            .background(Color(0xFF34D399)),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = ep.number.toString(),
                    style = WebTextStyles.sm,
                    color = theme.fg,
                    fontWeight = FontWeight.Bold,
                )
                if (ep.isFiller) {
                    Text(
                        text = "FILLER",
                        style = WebTextStyles.xs2,
                        color = Color(0xFFFDBA74),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x1AFB923C))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                } else if (ep.isRecap) {
                    Text(
                        text = "RECAP",
                        style = WebTextStyles.xs2,
                        color = theme.fgMuted,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x14FFFFFF))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
                if (downloaded) {
                    Text(
                        text = "OFFLINE",
                        style = WebTextStyles.xs2,
                        color = Color(0xFF6EE7B7),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x1A34D399))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
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
                    text = "Filler episode",
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
