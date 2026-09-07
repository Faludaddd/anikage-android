package com.anikage.app.ui.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.db.SubscriptionEntity
import com.anikage.app.core.data.model.Anime
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.WebTextStyles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * SUBSCRIPTIONS (user directive #13) — a completely custom page listing
 * every anime the user subscribed to, with:
 *  - poster + title
 *  - latest known episode + NEW-EPISODE indicator
 *  - release status (RELEASING / FINISHED / …)
 *  - last watched episode (from real watch progress)
 *  - subscription state (unsubscribes right here)
 *  - quick Watch button (opens the watch screen at the next episode)
 *  - sorting: recently updated / new episodes / alphabetical / recently
 *    subscribed.
 */
class SubscriptionsViewModel(
    repo: AnikageRepository,
) : ViewModel() {
    /** One row in the grid: subscription + live watch state. */
    data class Row(
        val sub: SubscriptionEntity,
        val lastWatched: Int,
        val hasNewEpisode: Boolean,
    )

    enum class Sort(val label: String) {
        RECENTLY_SUBSCRIBED("Recently subscribed"),
        RECENTLY_UPDATED("Recently updated"),
        NEW_EPISODES("New episodes"),
        ALPHABETICAL("Alphabetical"),
    }

    private val progressFlow = MutableStateFlow<Map<Int, Int>>(emptyMap())

    val rows: StateFlow<List<Row>> =
        combine(repo.observeSubscriptions(), progressFlow) { subs, progress ->
            subs.map { sub ->
                val watched = progress[sub.animeId] ?: 0
                Row(
                    sub = sub,
                    lastWatched = watched,
                    // A NEW episode is one released beyond the highest
                    // episode the user has already watched (or seen at
                    // subscribe time) AND beyond the last notified count.
                    hasNewEpisode = sub.lastKnownEpisodes > maxOf(watched, sub.lastNotifiedEpisode),
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sort: StateFlow<Sort> = MutableStateFlow(Sort.RECENTLY_UPDATED).asStateFlow()

    init {
        // Load last-watched episodes for the watched indicators.
        viewModelScope.launch {
            val progress = mutableMapOf<Int, Int>()
            rows.value.forEach { row ->
                val all = repo.loadProgressForAnime(row.sub.animeId)
                progress[row.sub.animeId] = all.maxByOrNull { it.episode }?.episode ?: 0
            }
            progressFlow.value = progress
        }
    }

    fun setSort(sort: Sort) {
        // Sorting is a UI-level concern handled in the composable; kept
        // here for future persistence.
    }

    fun unsubscribe(repo: AnikageRepository, animeId: Int) {
        viewModelScope.launch { repo.unsubscribe(animeId) }
    }

    companion object {
        fun factory(repo: AnikageRepository) = viewModelFactory {
            initializer { SubscriptionsViewModel(repo) }
        }
    }
}

@Composable
fun SubscriptionsScreen(
    onWatchClick: (Anime) -> Unit,
    onOpenDetails: (Anime) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { AnikageRepository.get(context) }
    val viewModel: SubscriptionsViewModel = viewModel(
        factory = SubscriptionsViewModel.factory(repo),
    )
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val theme = com.anikage.app.core.theme.LocalAnikageTheme.current
    var sort by remember { mutableStateOf(SubscriptionsViewModel.Sort.RECENTLY_UPDATED) }

    val sorted = remember(rows, sort) {
        when (sort) {
            SubscriptionsViewModel.Sort.RECENTLY_SUBSCRIBED -> rows.sortedByDescending { it.sub.subscribedAt }
            SubscriptionsViewModel.Sort.RECENTLY_UPDATED -> rows.sortedByDescending { it.sub.lastCheckedAt }
            SubscriptionsViewModel.Sort.NEW_EPISODES -> rows.sortedByDescending { it.hasNewEpisode }
            SubscriptionsViewModel.Sort.ALPHABETICAL -> rows.sortedBy {
                (it.sub.titleEnglish ?: it.sub.titleRomaji ?: "").lowercase()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(theme.surface)
            .padding(top = 80.dp),
    ) {
        // ── Header ────────────────────────────────────────────────────────
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = theme.fgMuted,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "NEW EPISODE ALERTS",
                    style = WebTextStyles.xs2,
                    color = theme.fgMuted,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                )
            }
            Text(
                text = "Subscriptions",
                style = WebTextStyles.titleHero,
                color = theme.fg,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = "Anime you're tracking — we check for new episodes and notify you.",
                style = WebTextStyles.sm,
                color = theme.fgMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
        }

        if (rows.isEmpty()) {
            // Empty state (site-style icon + heading + body).
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 72.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0x0DFFFFFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = theme.fgMuted,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Text(
                    text = "No subscriptions yet",
                    style = WebTextStyles.titleSection,
                    color = theme.fg,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    text = "Subscribe from any anime's page and you'll get a " +
                        "notification here the moment a new episode drops.",
                    style = WebTextStyles.sm,
                    color = theme.fgMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth(0.8f),
                )
            }
        } else {
            // ── Sort chips ────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SubscriptionsViewModel.Sort.values().forEach { s ->
                    val active = s == sort
                    Text(
                        text = s.label,
                        style = WebTextStyles.xs,
                        color = if (active) theme.actionFg else Color(0xFFA1A1AA),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (active) theme.action else Color(0x0DFFFFFF))
                            .border(
                                1.dp,
                                if (active) Color.Transparent else Color(0x14FFFFFF),
                                RoundedCornerShape(50),
                            )
                            .clickable { sort = s }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            // ── 2x2 grid of subscription cards ────────────────────────────
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(sorted, key = { it.sub.animeId }) { row ->
                    SubscriptionCard(
                        row = row,
                        onWatch = {
                            val anime = Anime(
                                id = row.sub.animeId,
                                slug = row.sub.slug,
                                title = com.anikage.app.core.data.model.AnimeTitle(
                                    romaji = row.sub.titleRomaji,
                                    english = row.sub.titleEnglish,
                                ),
                                coverImage = com.anikage.app.core.data.model.CoverImage(
                                    large = row.sub.posterUrl,
                                    extraLarge = row.sub.posterUrl,
                                ),
                            )
                            onWatchClick(anime)
                        },
                        onOpen = {
                            val anime = Anime(
                                id = row.sub.animeId,
                                slug = row.sub.slug,
                                title = com.anikage.app.core.data.model.AnimeTitle(
                                    romaji = row.sub.titleRomaji,
                                    english = row.sub.titleEnglish,
                                ),
                                coverImage = com.anikage.app.core.data.model.CoverImage(
                                    large = row.sub.posterUrl,
                                    extraLarge = row.sub.posterUrl,
                                ),
                            )
                            onOpenDetails(anime)
                        },
                        onUnsubscribe = { viewModel.unsubscribe(repo, row.sub.animeId) },
                    )
                }
            }
        }
    }
}

/** One subscription card: poster, title, latest EP, status, watch, unsub. */
@Composable
private fun SubscriptionCard(
    row: SubscriptionsViewModel.Row,
    onWatch: () -> Unit,
    onOpen: () -> Unit,
    onUnsubscribe: () -> Unit,
) {
    val theme = com.anikage.app.core.theme.LocalAnikageTheme.current
    val sub = row.sub
    val title = sub.titleEnglish ?: sub.titleRomaji ?: "Anime"
    val status = sub.releaseStatus?.replace('_', ' ')?.lowercase() ?: "unknown"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x08FFFFFF))
            .border(
                1.dp,
                if (row.hasNewEpisode) theme.action.copy(alpha = 0.45f) else Color(0x0FFFFFFF),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onOpen)
            .padding(10.dp),
    ) {
        // Poster with the NEW-EPISODE badge.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(theme.surfaceElevated),
        ) {
            sub.posterUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (row.hasNewEpisode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(theme.action)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(theme.actionFg),
                    )
                    Text(
                        text = "NEW EP",
                        style = WebTextStyles.xs2,
                        color = theme.actionFg,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                }
            }
            // Quick watch button over the poster.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC000000))
                    .border(1.dp, Color(0x33FFFFFF), CircleShape)
                    .clickable(onClick = onWatch),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Watch",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = WebTextStyles.sm,
            color = theme.fg,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Release status dot + label.
            val dot = when {
                status.contains("releas") -> Color(0xFF34D399)
                status.contains("finish") -> Color(0xFFFB7185)
                status.contains("not yet") -> Color(0xFFFBBF24)
                else -> Color(0x59FFFFFF)
            }
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dot),
            )
            Text(
                text = status.replaceFirstChar { it.uppercase() },
                style = WebTextStyles.xs2,
                color = theme.fgMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Unsubscribe.
            Icon(
                Icons.Default.NotificationsOff,
                contentDescription = "Unsubscribe",
                tint = Color(0x66A1A1AA),
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onUnsubscribe)
                    .padding(3.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "EP ${row.lastWatched.ifZero(sub.lastNotifiedEpisode)} / ${sub.lastKnownEpisodes}",
                style = WebTextStyles.xs2,
                color = Color(0xB3FFFFFF),
                fontWeight = FontWeight.Medium,
            )
            if (row.lastWatched > 0) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = Color(0x59FFFFFF),
                    modifier = Modifier.size(10.dp),
                )
                Text(
                    text = "last watched",
                    style = WebTextStyles.xs2,
                    color = Color(0x59FFFFFF),
                )
            }
        }
    }
}

private fun Int.ifZero(fallback: Int): Int = if (this > 0) this else fallback
