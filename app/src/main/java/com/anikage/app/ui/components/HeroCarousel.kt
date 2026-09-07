package com.anikage.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.anikage.app.Config
import com.anikage.app.core.data.model.Anime
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.WebTextStyles
import com.anikage.app.core.util.HtmlText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * HERO CAROUSEL — 1:1 port of the Anikage website hero (`.hero-shell`).
 *
 * Site structure (extracted from the live DOM):
 *   section.hero-shell h-[72vh] (md:90vh, lg:screen)
 *     ├─ img.hero-bg-layer  — TVDB series background artwork (spotlight.fanart)
 *     ├─ .hero-gradients    — 3 stacked gradients:
 *     │    bg-linear-to-t from-surface via-surface/30 to-surface/10
 *     │    bg-linear-to-r from-surface/60 via-surface/30 to-transparent
 *     │    top-1/4 bg-linear-to-b from-surface/40 to-transparent
 *     ├─ .hero-content (container-custom, bottom-left, pb-16)
 *     │    ├─ TVDB clearlogo img  max-h-[80px] md:max-h-[130px]
 *     │    │   drop-shadow(0 4px 24px rgba(0,0,0,.8))
 *     │    ├─ meta pills (score=yellow, year, episodes, format)
 *     │    ├─ genre chips (border-white/10 bg-black/50)
 *     │    ├─ synopsis line-clamp-2 text-base text-zinc-400
 *     │    └─ Watch Now (btn-primary WHITE) + More Info (blur secondary)
 *     └─ bottom bar: progress dots (w-4, active w-8 + animated fill),
 *        "N/M" counter (text-xs white/35 tabular), blur arrow buttons
 *
 * Auto-advance: 7s per slide, fill animates inside the active dot.
 */
@Composable
fun HeroCarousel(
    items: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    onWatchClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val theme = LocalAnikageTheme.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isWide = configuration.screenWidthDp >= 600
    // Site: h-[72vh] mobile · 90vh tablet · h-screen desktop.
    val heroHeight = when {
        configuration.screenWidthDp >= 900 -> configuration.screenHeightDp.dp
        configuration.screenWidthDp >= 600 -> configuration.screenHeightDp.dp * 0.90f
        else -> configuration.screenHeightDp.dp * 0.72f
    }
    val slideMillis = 7000
    val scope = rememberCoroutineScope()

    val totalItems = items.size
    // Swipeable hero: HorizontalPager gives native drag + snap + momentum;
    // arrows stay as secondary controls (site keeps them too).
    // beyondBoundsPageCount = 1: the NEIGHBOUR slides stay composed, so
    // their artwork is loaded BEFORE the swipe starts — no black slides
    // mid-transition, ever (the old black-image bug).
    val pagerState = rememberPagerState(pageCount = { totalItems })
    var progress by remember(items) { mutableFloatStateOf(0f) }

    // Last user touch on the hero — pauses auto-advance so a programmed
    // scroll can NEVER fight the user's drag (the old "inconsistent
    // swiping" bug: the timer fired between touch-down and scroll-start,
    // and animateScrollToPage cancelled the gesture). The parent Box's
    // pointerInput observes raw touch-downs without consuming them.
    var lastTouchMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) lastTouchMs = System.currentTimeMillis()
        }
    }

    // Preload neighbour artwork into Coil's caches so swipes are instant.
    LaunchedEffect(pagerState.currentPage, totalItems, items) {
        val current = pagerState.currentPage
        val neighbours = listOf(current - 1, current + 1)
            .map { (it + totalItems) % totalItems }
            .filter { it in items.indices }
            .distinct()
        neighbours.forEach { idx ->
            val candidate = items[idx].fanartUrl ?: items[idx].bannerImage ?: items[idx].coverUrl()
            val logo = items[idx].clearLogoUrl
            listOfNotNull(candidate, logo).forEach { url ->
                if (url != null) {
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .memoryCacheKey(url)
                        .build()
                    runCatching { context.imageLoader.enqueue(request) }
                }
            }
        }
    }

    // Auto-advance with progress fill (site animates the active dot's fill).
    // Restarts on every page change (swipe, dot tap, arrow) so the fill
    // always tracks the VISIBLE slide. While the user interacts the window
    // RESETS (never fights the drag, and never dies so autoplay resumes).
    LaunchedEffect(pagerState.currentPage, totalItems) {
        if (totalItems < 2) return@LaunchedEffect
        progress = 0f
        var start = System.currentTimeMillis()
        while (true) {
            delay(50)
            if (pagerState.isScrollInProgress || System.currentTimeMillis() - lastTouchMs < 1500L) {
                progress = 0f
                start = System.currentTimeMillis()
                continue
            }
            val elapsed = System.currentTimeMillis() - start
            progress = (elapsed.toFloat() / slideMillis).coerceIn(0f, 1f)
            if (elapsed >= slideMillis) {
                if (!pagerState.isScrollInProgress) {
                    pagerState.animateScrollToPage((pagerState.currentPage + 1) % totalItems)
                }
                break
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondBoundsPageCount = 1, // preload neighbour slides (no black gaps)
        ) { page ->
            val current = items[page.coerceIn(0, totalItems - 1)]
            val isSettled = pagerState.currentPage == page && !pagerState.isScrollInProgress

        // ── Layer 0: background artwork with a REAL fallback chain — if the
        // TVDB fanart 404s or the network hiccups, the banner/cover art takes
        // over instead of a black slide. Never a blank hero again.
        HeroArtwork(
            fanart = current.fanartUrl,
            banner = current.bannerImage,
            cover = current.coverUrl(),
            contentDescription = current.displayTitle(),
        )

        // ── Layer 0.5: muted trailer video (site: autoplayHeroTrailer plays
        // the YouTube trailer full-bleed beneath the same gradient stack).
        // Only mounted on the SETTLED page — a WebView churn during swipes
        // was exactly the "image turns black" trigger (the pager disposes
        // off-screen pages; each remount reloaded a black WebView).
        if (com.anikage.app.core.settings.SettingsState.autoplayHeroTrailer &&
            !current.trailerId.isNullOrBlank() && isSettled
        ) {
            HeroTrailerLayer(
                trailerId = current.trailerId,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ── Layer 1: gradient stack (exact site values)
        // bottom-up: from-surface via-surface/30 to-surface/10
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to theme.surface.copy(alpha = 0.10f),
                        0.5f to theme.surface.copy(alpha = 0.30f),
                        1.0f to theme.surface,
                    )
                )
        )
        // left-right: from-surface/60 via-surface/30 to-transparent
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to theme.surface.copy(alpha = 0.60f),
                        0.5f to theme.surface.copy(alpha = 0.30f),
                        1.0f to Color.Transparent,
                    )
                )
        )
        // top quarter: from-surface/40 to-transparent (nav readability)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight * 0.25f)
                .background(
                    Brush.verticalGradient(
                        0.0f to theme.surface.copy(alpha = 0.40f),
                        1.0f to Color.Transparent,
                    )
                )
        )

        // ── Layer 2: content column (site: .hero-content bottom-left, pb-16)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.92f)
                .padding(start = 16.dp, end = 16.dp, bottom = 64.dp),
        ) {
            // TVDB clearlogo — the anime's title ARTWORK, not text.
            val clearLogo = current.clearLogoUrl
            if (clearLogo != null) {
                // Logo with error fallback to the styled title text — a dead
                // logo URL no longer leaves a hole in the hero.
                var logoFailed by remember(clearLogo) { mutableStateOf(false) }
                if (!logoFailed) {
                    AsyncImage(
                        model = clearLogo,
                        contentDescription = current.displayTitle(),
                        contentScale = ContentScale.Fit,
                        error = null,
                        onError = { logoFailed = true },
                        modifier = Modifier
                            .heightIn(max = if (isWide) 130.dp else 80.dp)
                            .padding(bottom = 16.dp),
                    )
                } else {
                    Text(
                        text = current.displayTitle(),
                        style = WebTextStyles.titleHero,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            } else {
                // Fallback only when the API payload has no logo (offline mode).
                Text(
                    text = current.displayTitle(),
                    style = WebTextStyles.titleHero,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            // ── Meta pills row (site: .meta-pill-blur)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                current.averageScore?.let { score ->
                    // Lucide star + % — the site's hero score pill
                    // (border-yellow-500/40 bg-yellow-500/20 text-yellow-400).
                    MetaPill(
                        lucideStar = true,
                        text = "${score}%",
                        iconTint = Color(0xFFFACC15),
                        border = Color(0x66FACC15),       // yellow-500/40
                        background = Color(0x33FACC15),   // yellow-500/20
                        textTint = Color(0xFFFACC15),
                        bold = true,
                    )
                }
                current.seasonYear?.let { year ->
                    MetaPill(icon = Icons.Default.CalendarMonth, text = year.toString())
                }
                current.episodes?.takeIf { it > 0 }?.let { eps ->
                    MetaPill(icon = Icons.Default.Layers, text = "$eps Episodes")
                }
                current.duration?.takeIf { it > 0 }?.let { dur ->
                    MetaPill(text = "$dur min")
                }
                current.format?.let { fmt ->
                    MetaPill(icon = Icons.Default.Tv, text = fmt, upperCase = true)
                }
            }

            // ── Genre chips (site: rounded-full border-white/10 bg-black/50)
            if (current.genres.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    current.genres.take(3).forEach { genre ->
                        GenreChip(text = genre)
                    }
                }
            }

            // ── Synopsis (site: line-clamp-2 text-base leading-relaxed text-zinc-400)
            current.description?.takeIf { it.isNotBlank() }?.let { synopsis ->
                Text(
                    text = HtmlText.clean(synopsis),
                    color = Color(0xFFA1A1AA),            // zinc-400
                    style = WebTextStyles.base,
                    lineHeight = 24.sp,
                    maxLines = if (isWide) 3 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(top = 12.dp, bottom = 20.dp),
                )
            }

            // ── Buttons (site: btn-primary = WHITE bg / black text)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeroPrimaryButton(
                    icon = Icons.Default.PlayArrow,
                    text = "Watch Now",
                    onClick = { onWatchClick(current) },
                )
                HeroSecondaryButton(
                    icon = Icons.Default.Info,
                    text = "More Info",
                    onClick = { onAnimeClick(current) },
                )
            }
        }
        } // end pager page

        // ── Bottom controls: dots + counter + arrows (site: bottom-4)
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Progress dots — inactive w-4; active w-8 with animated fill.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { idx, _ ->
                    val active = idx == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .width(if (active) 32.dp else 16.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x1FFFFFFF))       // white/12
                            .clickable { scope.launch { pagerState.animateScrollToPage(idx) } }
                    ) {
                        if (active) {
                            // Animated fill — mirrors the site's CSS transition
                            // of the active dot's inner progress bar.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(theme.action)
                            )
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Slide counter (site: text-xs text-white/35 tabular-nums)
                Text(
                    text = "${pagerState.currentPage + 1}/$totalItems",
                    style = WebTextStyles.xs,
                    color = Color(0x59FFFFFF),
                )
                HeroArrowButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft) {
                    val target = if (pagerState.currentPage == 0) totalItems - 1 else pagerState.currentPage - 1
                    scope.launch { pagerState.animateScrollToPage(target) }
                }
                HeroArrowButton(Icons.AutoMirrored.Filled.KeyboardArrowRight) {
                    scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1) % totalItems) }
                }
            }
        }
    }
}

/** site: .meta-pill-blur — rounded-full border-white/15 bg-black/40 px-3 py-1 text-xs */
@Composable
private fun MetaPill(
    icon: ImageVector? = null,
    lucideStar: Boolean = false,
    text: String,
    iconTint: Color = Color(0x99FFFFFF),
    border: Color = Color(0x26FFFFFF),
    background: Color = Color(0x66000000),
    textTint: Color = Color(0xB3FFFFFF),
    bold: Boolean = false,
    upperCase: Boolean = false,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(enabled = false) {}
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (lucideStar) {
            com.anikage.app.ui.components.LucideStarFilled(
                tint = iconTint,
                modifier = Modifier.size(12.dp),
            )
        } else if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(
            text = if (upperCase) text.uppercase() else text,
            style = WebTextStyles.xs,
            color = textTint,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = if (upperCase) 0.8.sp else 0.sp,
        )
    }
}

/** site: genre chips — rounded-full border-white/10 bg-black/50 px-3 py-1 text-xs */
@Composable
private fun GenreChip(text: String) {
    Text(
        text = text,
        style = WebTextStyles.xs,
        color = Color(0xB3FFFFFF),
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x80000000))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** site: .btn.btn-md.btn-primary — WHITE background, near-black text, pill. */
@Composable
private fun HeroPrimaryButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    val theme = LocalAnikageTheme.current
    Row(
        modifier = Modifier
            .shadow(6.dp, RoundedCornerShape(50), spotColor = Color(0x4D000000))
            .clip(RoundedCornerShape(50))
            .background(theme.action)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = theme.actionFg, modifier = Modifier.size(16.dp))
        Text(
            text = text,
            style = WebTextStyles.sm,
            color = theme.actionFg,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** site: .cta-secondary-blur — border-white/15 bg-white/10, pill. */
@Composable
private fun HeroSecondaryButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .shadow(6.dp, RoundedCornerShape(50), spotColor = Color(0x4D000000))
            .clip(RoundedCornerShape(50))
            .background(Color(0x1AFFFFFF))
            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color(0x99FFFFFF), modifier = Modifier.size(16.dp))
        Text(
            text = text,
            style = WebTextStyles.sm,
            color = Color.White,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** site: .nav-arrow-blur — h-9 w-9 round, border-white/15. */
@Composable
private fun HeroArrowButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0x59000000))
            .border(1.dp, Color(0x26FFFFFF), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Hero background artwork with a candidate fallback chain:
 * fanart -> banner -> cover. On load error the next candidate loads; the
 * previous bitmap stays on screen until then (no black flash), and Coil's
 * crossfade keeps the switch smooth. Only when ALL candidates fail does a
 * dim placeholder color remain (never during normal swipes — neighbours
 * are preloaded).
 */
@Composable
private fun HeroArtwork(
    fanart: String?,
    banner: String?,
    cover: String?,
    contentDescription: String,
) {
    val candidates = remember(fanart, banner, cover) {
        listOfNotNull(fanart, banner, cover)
    }
    val theme = LocalAnikageTheme.current
    if (candidates.isEmpty()) {
        Box(Modifier.fillMaxSize().background(theme.surfaceElevated))
        return
    }
    var attempt by remember(candidates) { mutableIntStateOf(0) }
    val url = candidates[attempt.coerceIn(0, candidates.lastIndex)]
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(220)
            .memoryCacheKey(url)
            .build(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        onError = { if (attempt < candidates.lastIndex) attempt += 1 },
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * Muted, looping YouTube trailer layer — the site's autoplayHeroTrailer
 * setting: the spotlight slide's trailer plays full-bleed beneath the hero
 * gradient stack (youtube-nocookie iframe embed, no controls, muted,
 * looped), exactly like anikage.cc's hero.
 */
@Composable
private fun HeroTrailerLayer(
    trailerId: String,
    modifier: Modifier = Modifier,
) {
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.loadsImagesAutomatically = false
                isClickable = false
                // Transparent until the iframe paints — the artwork shows
                // through instead of a black rectangle while YouTube loads.
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        // Page finished: let the video layer show.
                    }
                }
                loadUrl(
                    "https://www.youtube-nocookie.com/embed/$trailerId" +
                        "?autoplay=1&mute=1&loop=1&playlist=$trailerId" +
                        "&controls=0&modestbranding=1&playsinline=1&rel=0&disablekb=1",
                )
            }
        },
        update = { view ->
            val url = "https://www.youtube-nocookie.com/embed/$trailerId" +
                "?autoplay=1&mute=1&loop=1&playlist=$trailerId" +
                "&controls=0&modestbranding=1&playsinline=1&rel=0&disablekb=1"
            if (view.url != url) view.loadUrl(url)
        },
        onRelease = { it.destroy() },
    )
}
