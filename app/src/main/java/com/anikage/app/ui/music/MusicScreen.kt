package com.anikage.app.ui.music

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.api.AnikageMusicAnime
import com.anikage.app.core.data.api.AnikageMusicTheme
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.WebTextStyles
import com.anikage.app.ui.components.SkeletonBlock
import com.anikage.app.ui.components.siteCardWidth

/**
 * MUSIC — 1:1 port of anikage.cc/music (node 16).
 *
 * Site structure:
 *   ├─ hero: "Music Explorer" gradient title + subtitle
 *   ├─ search: max-w-3xl rounded-2xl px-14 py-5 text-lg + lucide icon +
 *   │  disc-3 spinner while loading (debounce 500ms)
 *   ├─ results grid: minmax(105/135/155px,1fr) gap-4
 *   ├─ card: 2:3 cover + count pill (bg-black/70) + hover play pill +
 *   │  centred 2-line title
 *   ├─ skeleton: 18 pulsing cards, staggered
 *   └─ empty: disc icon circle + "Silence…"
 * Card click -> track modal (site Dialog, max-w-md): cover + name + meta +
 * track rows (number square + title + OP/ED badge) -> music info player.
 */
@Composable
fun MusicScreen(
    onOpenTheme: (slug: String, type: String) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { AnikageRepository.get(context) }
    val viewModel: MusicViewModel = viewModel(factory = MusicViewModel.factory(repo))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val theme = LocalAnikageTheme.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val cardWidth = siteCardWidth(screenWidthDp)
    // Card grid columns — site: auto-fill minmax(105/135/155,1fr).
    val minCard = cardWidth

    // Track modal (site: <Dialog> on card click).
    var selected by remember { mutableStateOf<AnikageMusicAnime?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface)
            .padding(top = 80.dp),
    ) {
        // ── Hero (site: text-center, gradient title) ──────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "Music Explorer",
                style = WebTextStyles.titleHero.copy(
                    brush = Brush.verticalGradient(listOf(Color.White, theme.fgMuted)),
                ),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Search thousands of openings, endings, and soundtracks",
                style = WebTextStyles.base,
                color = theme.fgMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            // ── Search input (site: max-w-3xl rounded-2xl px-14 py-5) ────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = 0.86f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(theme.surfaceCard)
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp)),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = theme.fgMuted,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (state.query.isEmpty()) {
                                Text(
                                    text = "Type anime title (e.g. 'Chainsaw Man')...",
                                    style = WebTextStyles.base,
                                    color = theme.fgMuted.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = state.query,
                                onValueChange = viewModel::onQueryChange,
                                singleLine = true,
                                textStyle = WebTextStyles.base.copy(color = theme.fg),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (state.loading) {
                            CircularProgressIndicator(
                                color = theme.action,
                                strokeWidth = 2.dp,
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(start = 12.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // ── Results / skeleton / empty ────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.loading -> MusicSkeleton(minCard = minCard)
                state.error != null && state.results.isEmpty() -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                ) {
                    Text(
                        text = "Search failed",
                        style = WebTextStyles.titleSection,
                        color = theme.fg,
                    )
                    Text(
                        text = state.error ?: "",
                        style = WebTextStyles.sm,
                        color = theme.fgMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        text = "Retry",
                        style = WebTextStyles.sm,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 14.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.action)
                            .clickable { viewModel.retry() }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
                state.results.isNotEmpty() -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minCard),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.results, key = { it.id ?: it.slug.hashCode() }) { anime ->
                        MusicCard(
                            anime = anime,
                            onOpen = { selected = anime },
                        )
                    }
                }
                state.searched -> MusicEmpty(query = state.query)
                else -> Text(
                    text = "Search an anime above to find its openings, endings and soundtracks.",
                    style = WebTextStyles.sm,
                    color = theme.fgMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                )
            }
        }
    }

    // Track modal (site: Dialog max-w-md with cover header + track list).
    selected?.let { anime ->
        TrackDialog(
            anime = anime,
            onDismiss = { selected = null },
            onOpenTheme = onOpenTheme,
        )
    }
}

// ---------------------------------------------------------------------------
//  Card — site: aspect-2/3 cover, count pill, hover play, centred title
// ---------------------------------------------------------------------------

@Composable
private fun MusicCard(
    anime: AnikageMusicAnime,
    onOpen: () -> Unit,
) {
    val theme = LocalAnikageTheme.current
    val trackCount = anime.animethemes.size
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(theme.surfaceCard),
        ) {
            anime.coverUrl()?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = anime.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Count pill — site: top-1.5 right-1.5 bg-black/70 backdrop-blur.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xB3000000))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp),
                )
                Text(
                    text = trackCount.toString(),
                    style = WebTextStyles.xs2,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            text = anime.name ?: "Unknown",
            style = WebTextStyles.sm,
            color = theme.fgMuted,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            minLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
    }
}

// ---------------------------------------------------------------------------
//  Track dialog — site: max-w-md, cover header + meta + scrollable tracks
// ---------------------------------------------------------------------------

@Composable
private fun TrackDialog(
    anime: AnikageMusicAnime,
    onDismiss: () -> Unit,
    onOpenTheme: (String, String) -> Unit,
) {
    val theme = LocalAnikageTheme.current
    val tracks = anime.animethemes
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(fraction = 0.92f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xF20A0A0A))
                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp)),
        ) {
            // Header — site: border-b p-4: cover + name + meta.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.surfaceCard),
                ) {
                    anime.coverUrl()?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = anime.name ?: "Unknown",
                        style = WebTextStyles.titleSection,
                        color = theme.fg,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        anime.mediaFormat?.let { fmt ->
                            Text(
                                text = fmt,
                                style = WebTextStyles.xs2,
                                color = theme.fgMuted,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (anime.year != null) {
                            Text(
                                text = "· ${anime.season ?: ""} ${anime.year}",
                                style = WebTextStyles.xs2,
                                color = theme.fgMuted,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            text = "· ${tracks.size} tracks",
                            style = WebTextStyles.xs2,
                            color = theme.action,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(1.dp)
                    .background(Color(0x0DFFFFFF)),
            )
            // Tracks — site: max-h-55vh overflow-y-auto p-2.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
            ) {
                if (tracks.isEmpty()) {
                    Text(
                        text = "No tracks available for this title.",
                        style = WebTextStyles.sm,
                        color = theme.fgMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    tracks.forEachIndexed { index, t ->
                        TrackRow(
                            index = index,
                            track = t,
                            onClick = {
                                onDismiss()
                                onOpenTheme(anime.slug ?: "", t.slug ?: "")
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Site track row: number square (h-8 w-8) + title + OP/ED badge. */
@Composable
private fun TrackRow(
    index: Int,
    track: AnikageMusicTheme,
    onClick: () -> Unit,
) {
    val theme = LocalAnikageTheme.current
    val isOp = (track.type ?: track.slug ?: "").uppercase().startsWith("OP")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        // Number square — site: h-8 w-8 rounded-lg bg-fg/6, hover bg-action.
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x0FFFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = String.format("%02d", index + 1),
                style = WebTextStyles.xs2,
                color = theme.fgMuted,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = track.song?.title ?: "Untitled Track",
            style = WebTextStyles.sm,
            color = theme.fg,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // Badge — site: OP = bg-action/15 text-action, else bg-fg/8.
        Text(
            text = (track.slug ?: track.type ?: "").uppercase(),
            style = WebTextStyles.xs2,
            color = if (isOp) theme.action else theme.fgMuted,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isOp) Color(0x26FFFFFF) else Color(0x14FFFFFF))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

// ---------------------------------------------------------------------------
//  Skeleton + empty — site: 18 pulsing cards / "Silence…" disc state
// ---------------------------------------------------------------------------

@Composable
private fun MusicSkeleton(minCard: androidx.compose.ui.unit.Dp) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minCard),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(18) { i ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                    corner = 12.dp,
                )
                SkeletonBlock(
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

@Composable
private fun MusicEmpty(query: String) {
    val theme = LocalAnikageTheme.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 96.dp, horizontal = 24.dp),
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0x0DFFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color(0x33FFFFFF),
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            text = "Silence...",
            style = WebTextStyles.titleSection,
            color = theme.fg,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = "We couldn't find any music for \"$query\". Try checking the spelling!",
            style = WebTextStyles.sm,
            color = theme.fgMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth(),
        )
    }
}
