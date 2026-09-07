package com.anikage.app.ui.player

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Image
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.anikage.app.Config
import com.anikage.app.core.data.AnikageRepository
import com.anikage.app.core.data.api.AnikageComment
import com.anikage.app.core.settings.SettingsState
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.WebTextStyles
import com.anikage.app.ui.components.ErrorOrEmptyState
import com.anikage.app.ui.components.SkeletonBlock
import java.time.Duration
import java.time.Instant
import android.content.pm.ActivityInfo

/**
 * Watch screen — 1:1 layout of the site's watch page:
 *  stage (player / E-server embed) → meta (EP chip, views, action buttons)
 *  → server panel (SUB/DUB + servers + E-servers) → tabs (Episodes | Info)
 *  → episodes / info → comments. Wide layout mirrors the site's 2-col grid.
 *
 *  v2.0.0: full custom player (all controls working), fullscreen with
 *  orientation + immersive bars, hardware-keyboard shortcuts, download +
 *  report dialogs (real API calls), E-server WebView embeds, and every
 *  player-related setting wired.
 */
@Composable
fun WatchScreen(
    animeId: Int,
    initialEpisode: Int,
    slug: String? = null,
    onBackClick: () -> Unit = {},
    onOpenInfo: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val repo = remember { AnikageRepository.get(context) }
    val viewModel: WatchViewModel = viewModel(
        factory = WatchViewModel.factory(app, repo, animeId, initialEpisode, slug),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val theme = LocalAnikageTheme.current
    val configuration = LocalConfiguration.current
    val isWide = configuration.screenWidthDp.dp >= 840.dp // site lg (2-col watch grid)

    // Mobile tab: 0 = Episodes, 1 = Info (site's .wl-tabs: Episodes | Info).
    var tab by remember { mutableIntStateOf(0) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showListSheet by remember { mutableStateOf(false) }

    val activity = context as? Activity

    // Fullscreen stage — same player instance (VM-owned), landscape lock +
    // immersive bars below; position/episode/controls state all preserved.
    val fullscreenStage: @Composable () -> Unit = {
        PlayerStage(
            state = state,
            viewModel = viewModel,
            isFullscreen = true,
            onToggleFullscreen = { isFullscreen = false },
            onOpenReport = { showReportDialog = true },
        )
    }

    // System back exits fullscreen FIRST (restores the previous UI state);
    // a second back leaves the screen.
    BackHandler(enabled = isFullscreen) { isFullscreen = false }

    // ── fullscreen: landscape lock + immersive system bars (site: data-orientation)
    DisposableEffect(isFullscreen) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, window.decorView) }
        if (isFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ── leave: save progress, restore orientation, auto-PiP ──────────────
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation
        onDispose {
            viewModel.saveProgressNow()
            if (originalOrientation != null) {
                activity?.requestedOrientation = originalOrientation
            }
            if (Config.Player.AUTO_PIP_ON_LEAVE &&
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                viewModel.isPlayingForPip()
            ) {
                try {
                    val params = android.app.PictureInPictureParams.Builder()
                        .setAspectRatio(android.util.Rational(16, 9))
                        .build()
                    activity?.enterPictureInPictureMode(params)
                } catch (_: Exception) { /* ignore */ }
            }
        }
    }

    // ── hardware-keyboard shortcuts (site: anikage-watch-hotkeys) ────────
    val keyHandler = Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || state.streamUrl == null || state.embedActive != null) {
            return@onPreviewKeyEvent false
        }
        val speedStep = {
            val next = (state.speed + 0.25f).coerceIn(0.25f, 2f)
            viewModel.setSpeed(next)
            true
        }
        val speedDown = {
            val next = (state.speed - 0.25f).coerceIn(0.25f, 2f)
            viewModel.setSpeed(next)
            true
        }
        when (event.key) {
            Key.Spacebar, Key.K -> { viewModel.togglePlayPause(); true }
            Key.M -> { viewModel.toggleMuted(); true }
            Key.F -> { isFullscreen = !isFullscreen; true }
            Key.C -> { viewModel.toggleCaptions(); true }
            Key.I -> { viewModel.enterPictureInPicture(); true }
            Key.J -> { viewModel.seekBy(-10_000L); true }
            Key.L -> { viewModel.seekBy(10_000L); true }
            Key.DirectionRight -> { viewModel.seekBy(5_000L); true }
            Key.DirectionLeft -> { viewModel.seekBy(-5_000L); true }
            Key.MoveHome -> { viewModel.seekTo(0L); true }
            Key.MoveEnd -> { viewModel.seekTo(state.durationMs); true }
            Key.Period -> speedStep()
            Key.Comma -> speedDown()
            else -> false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface)
            .then(keyHandler),
    ) {
        if (isFullscreen) {
            // ── fullscreen: the stage alone fills the screen ──────────────
            fullscreenStage()
        } else {
            Column(Modifier.fillMaxSize()) {
                if (state.loading) {
                    WatchSkeleton(isWide = isWide)
                    return@Column
                }

                if (state.error != null && state.details == null) {
                    ErrorOrEmptyState(
                        title = "Couldn't load anime",
                        subtitle = state.error ?: "",
                        actionText = "Back",
                        onAction = onBackClick,
                    )
                    return@Column
                }

                if (isWide) {
                    WatchWideLayout(
                        state = state,
                        viewModel = viewModel,
                        onEnterFullscreen = { isFullscreen = true },
                        onOpenInfo = onOpenInfo,
                        onDownload = { showDownloadDialog = true },
                        onReport = { showReportDialog = true },
                        onList = { showListSheet = true },
                    )
                } else {
                    WatchMobileLayout(
                        state = state,
                        viewModel = viewModel,
                        onEnterFullscreen = { isFullscreen = true },
                        tab = tab,
                        onTabChange = { tab = it },
                        onOpenInfo = onOpenInfo,
                        onDownload = { showDownloadDialog = true },
                        onReport = { showReportDialog = true },
                        onList = { showListSheet = true },
                    )
                }
            }
        }

        // ── dialogs (site: download links / report / list sheet) ──────────
        if (showDownloadDialog) {
            DownloadDialog(
                viewModel = viewModel,
                episode = state.episode,
                onDismiss = { showDownloadDialog = false },
            )
        }
        if (showReportDialog) {
            ReportDialog(
                viewModel = viewModel,
                onDismiss = { showReportDialog = false },
                onRefetch = {
                    showReportDialog = false
                    viewModel.reloadStream(refresh = true)
                },
            )
        }
        if (showListSheet) {
            ListSheet(onDismiss = { showListSheet = false })
        }
    }
}

// ---------------------------------------------------------------------------
//  Mobile — single scrolling column (site's stacked grid areas)
// ---------------------------------------------------------------------------

@Composable
private fun WatchMobileLayout(
    state: WatchUiState,
    viewModel: WatchViewModel,
    onEnterFullscreen: () -> Unit,
    tab: Int,
    onTabChange: (Int) -> Unit,
    onOpenInfo: (Int) -> Unit,
    onDownload: () -> Unit,
    onReport: () -> Unit,
    onList: () -> Unit,
) {
    // ONE sort per episode-list change (was re-sorted on every position
    // tick — O(n log n) 4x/second for 1000+ episode lists).
    val episodes = remember(state.episodes, SettingsState.episodeSortOrder) {
        if (SettingsState.episodeSortOrder == "desc") state.episodes.sortedByDescending { it.number }
        else state.episodes.sortedBy { it.number }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Site: .watch-layout pt-20 lg:pt-23 — clears the fixed top nav.
        contentPadding = PaddingValues(top = 64.dp),
    ) {
        item(key = "player") {
            PlayerStage(
                state = state,
                viewModel = viewModel,
                isFullscreen = false,
                onToggleFullscreen = onEnterFullscreen,
                onOpenReport = onReport,
            )
        }
        item(key = "notice-meta") {
            Column(Modifier.padding(horizontal = 16.dp)) {
                ServerNotice()
                Spacer(Modifier.height(12.dp))
                MetaRow(
                    state = state,
                    viewModel = viewModel,
                    onDownload = onDownload,
                    onReport = onReport,
                    onList = onList,
                )
            }
        }
        item(key = "servers") {
            ServerPanel(state = state, viewModel = viewModel)
        }
        item(key = "tabs") { MobileTabs(state = state, tab = tab, onTabChange = onTabChange, onOpenInfo = onOpenInfo) }

        if (tab == 0) {
            items(episodes, key = { it.number }) { ep ->
                EpisodeRow(
                    ep = ep,
                    active = ep.number == state.episode,
                    onClick = { viewModel.switchEpisode(ep.number) },
                )
            }
        } else {
            item(key = "info") {
                WatchInfoSection(state = state, onOpenInfo = onOpenInfo)
            }
        }

        item(key = "comments") {
            CommentsSection(
                state = state.comments,
                episode = state.episode,
                onRefresh = viewModel::refreshComments,
            )
        }
        item(key = "bottom-space") { Spacer(Modifier.height(96.dp)) }
    }
}

// ---------------------------------------------------------------------------
//  Wide — site grid: player column (1fr) + episodes side panel (28-35%)
// ---------------------------------------------------------------------------

@Composable
private fun WatchWideLayout(
    state: WatchUiState,
    viewModel: WatchViewModel,
    onEnterFullscreen: () -> Unit,
    onOpenInfo: (Int) -> Unit,
    onDownload: () -> Unit,
    onReport: () -> Unit,
    onList: () -> Unit,
) {
    val episodes = remember(state.episodes, SettingsState.episodeSortOrder) {
        if (SettingsState.episodeSortOrder == "desc") state.episodes.sortedByDescending { it.number }
        else state.episodes.sortedBy { it.number }
    }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 72.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp), // site: column-gap 1.25rem
    ) {
        // Left column — stage + meta + comments (site: stage/lower areas).
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            PlayerStage(
                state = state,
                viewModel = viewModel,
                isFullscreen = false,
                onToggleFullscreen = onEnterFullscreen,
                onOpenReport = onReport,
            )
            ServerNotice()
            Spacer(Modifier.height(12.dp))
            MetaRow(
                state = state,
                viewModel = viewModel,
                onDownload = onDownload,
                onReport = onReport,
                onList = onList,
                horizontalPadding = PaddingValues(0.dp),
            )
            ServerPanel(state = state, viewModel = viewModel, horizontalPadding = PaddingValues(0.dp))
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Comments",
                style = WebTextStyles.titleSection,
                color = LocalAnikageTheme.current.fg,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            CommentsSection(
                state = state.comments,
                episode = state.episode,
                onRefresh = viewModel::refreshComments,
                horizontalPadding = PaddingValues(0.dp),
            )
            Spacer(Modifier.height(48.dp))
        }

        // Side panel — episode list (site: wl-side, w-35%→28%). LAZY: huge
        // catalogs (1000+ episodes) compose only the visible rows.
        Column(
            modifier = Modifier
                .width(340.dp)
                .fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Episodes",
                    style = WebTextStyles.titleSection,
                    color = LocalAnikageTheme.current.fg,
                )
                Text(
                    text = "${state.episodes.size}",
                    style = WebTextStyles.xs,
                    color = LocalAnikageTheme.current.fgMuted,
                )
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                items(episodes, key = { it.number }) { ep ->
                    EpisodeRow(
                        ep = ep,
                        active = ep.number == state.episode,
                        onClick = { viewModel.switchEpisode(ep.number) },
                        horizontalPadding = PaddingValues(0.dp),
                    )
                }
            }
            if (state.episodes.isEmpty()) {
                Text(
                    text = "Episode list unavailable.",
                    style = WebTextStyles.sm,
                    color = LocalAnikageTheme.current.fgMuted,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ---------------------------------------------------------------------------
//  Player stage — player-glass rounded-2xl (site exact). In fullscreen the
//  stage fills the whole screen (no glass chrome).
// ---------------------------------------------------------------------------

@Composable
private fun PlayerStage(
    state: WatchUiState,
    viewModel: WatchViewModel,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onOpenReport: () -> Unit,
) {
    val ambientUrl = state.episodes.firstOrNull { it.number == state.episode }?.thumbnail
        ?: state.details?.bannerImage
        ?: state.details?.coverImage?.best()

    Box(
        modifier = if (isFullscreen) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp) // site: player-glass mb-3
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
        },
    ) {
        if (state.embedActive != null) {
            // ── E-server mode: the site's iframe player in a WebView ──────
            EmbedPlayer(
                url = state.embedUrl,
                loading = state.embedLoading,
                error = state.embedError,
                onRetry = { viewModel.reloadStream(refresh = true) },
            )
        } else {
            AnikagePlayer(
                state = state,
                viewModel = viewModel,
                isFullscreen = isFullscreen,
                onToggleFullscreen = onToggleFullscreen,
                onBack = onOpenReport,
                onOpenReport = onOpenReport,
                modifier = Modifier.fillMaxSize(),
            )
            // Stream resolution in progress — thin top progress shimmer.
            if (state.streamLoading && state.streamUrl == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

/** E-server embed: megaplay.buzz player in a WebView (site iframe 1:1). */
@Composable
private fun EmbedPlayer(
    url: String?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (url != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.loadsImagesAutomatically = true
                        settings.mixedContentMode =
                            android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        webViewClient = android.webkit.WebViewClient()
                        loadUrl(url)
                    }
                },
            )
        } else if (loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(26.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error ?: "This embed server has no source for the episode.",
                    style = WebTextStyles.sm,
                    color = Color(0xFFD4D4D8),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Retry",
                    style = WebTextStyles.xs,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Info tab (site: wl-info — status, title, synopsis, facts)
// ---------------------------------------------------------------------------

@Composable
private fun WatchInfoSection(state: WatchUiState, onOpenInfo: (Int) -> Unit) {
    val theme = LocalAnikageTheme.current
    val details = state.details ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            details.status?.let { status ->
                Text(
                    text = status.replace('_', ' '),
                    style = WebTextStyles.xs,
                    color = theme.action,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x14FFFFFF))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        // Title opens the full info page (site: title links to /anime/info).
        Text(
            text = state.title,
            style = WebTextStyles.lg,
            color = theme.fg,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onOpenInfo(details.id) },
        )
        details.description?.let { desc ->
            Text(
                text = com.anikage.app.core.util.HtmlText.clean(desc),
                style = WebTextStyles.sm,
                color = Color(0xFFA1A1AA),
                lineHeight = 20.sp,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            details.episodes?.let {
                InfoFact("Episodes", it.toString())
            }
            details.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                InfoFact("Genres", genres.take(3).joinToString())
            }
            details.season?.let { season ->
                InfoFact("Season", "${season.replaceFirstChar { it.uppercase() }} ${details.seasonYear ?: ""}")
            }
        }
    }
}

@Composable
private fun InfoFact(label: String, value: String) {
    Column {
        Text(value, style = WebTextStyles.sm, color = LocalAnikageTheme.current.fg, fontWeight = FontWeight.Medium)
        Text(label, style = WebTextStyles.xs2, color = LocalAnikageTheme.current.fgMuted)
    }
}

// ---------------------------------------------------------------------------
//  Meta row + site's server notice
// ---------------------------------------------------------------------------

/** Site: rounded-xl bg-red-500/30 px-3 py-2 text-sm + flag icon notice. */
@Composable
private fun ServerNotice(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x4DEF4444))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = Color(0xFFFCA5A5),
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "If the current server doesn't work, feel free to try the other available servers.",
            style = WebTextStyles.sm,
            color = Color(0xFFFEE2E2),
        )
    }
}

@Composable
private fun MetaRow(
    state: WatchUiState,
    viewModel: WatchViewModel,
    onDownload: () -> Unit,
    onReport: () -> Unit,
    onList: () -> Unit,
    horizontalPadding: PaddingValues = PaddingValues(horizontal = 0.dp),
) {
    val theme = LocalAnikageTheme.current
    Column(Modifier.fillMaxWidth().padding(horizontalPadding)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // EP chip — site: rounded-xl border-white/6 bg-white/3.
                Text(
                    text = "EP ${state.episode}",
                    style = WebTextStyles.base,
                    color = theme.fg,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x08FFFFFF))
                        .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
                state.viewCount?.let { vc ->
                    Text(
                        text = "%,d views".format(vc),
                        style = WebTextStyles.xs,
                        color = Color(0xFF71717A),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Prev / Next episode (site: player control bar arrows).
                EpisodeNavButton(
                    label = "Prev",
                    enabled = state.episode > 1,
                    onClick = { viewModel.switchEpisode(state.episode - 1) },
                )
                EpisodeNavButton(
                    label = "Next",
                    enabled = state.episode < state.totalEpisodes,
                    onClick = { viewModel.switchEpisode(state.episode + 1) },
                )
                // Refresh stream (re-resolve sources for this episode).
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x08FFFFFF))
                        .clickable { viewModel.reloadStream() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Reload",
                        tint = theme.fgMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        // Site's action row: Add to List / Download / Report.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            ActionButton(
                icon = Icons.Default.Edit,
                label = "Add to List",
                onClick = onList,
            )
            ActionButton(
                icon = Icons.Filled.FileDownload,
                label = "Download",
                onClick = onDownload,
            )
            ActionButton(
                icon = Icons.Default.Flag,
                label = "Report",
                onClick = onReport,
            )
        }
    }
}

/** Site: .action-btn — rounded-xl white/5 border white/6, hover white/8. */
@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val theme = LocalAnikageTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x08FFFFFF))
            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = theme.fg, modifier = Modifier.size(14.dp))
        Text(
            text = label,
            style = WebTextStyles.sm,
            color = theme.fg,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ---------------------------------------------------------------------------
//  Server panel — chips gated by subType support + E-servers (site 1:1)
// ---------------------------------------------------------------------------

@Composable
private fun ServerPanel(
    state: WatchUiState,
    viewModel: WatchViewModel,
    horizontalPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    val theme = LocalAnikageTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontalPadding)
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x08FFFFFF))
            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        ) {
            Text(
                text = "Servers (${state.availableServers.size.coerceAtLeast(1)})",
                style = WebTextStyles.base,
                color = theme.fg,
                fontWeight = FontWeight.SemiBold,
            )
            // SUB / DUB segmented (site: btn-xs, active bg-action).
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x14FFFFFF))
                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(8.dp)),
            ) {
                LangChip(
                    label = "SUB",
                    active = state.streamLang == "sub",
                    onClick = { viewModel.setStreamLang("sub") },
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(Color(0x14FFFFFF)),
                )
                LangChip(
                    label = "DUB",
                    active = state.streamLang == "dub",
                    onClick = { viewModel.setStreamLang("dub") },
                )
            }
        }
        // Stream resolution failure — actionable message.
        state.streamError?.let { err ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x1AEF4444))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFCA5A5),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = err,
                    style = WebTextStyles.xs,
                    color = Color(0xFFFEE2E2),
                )
            }
        }
        // Server chips (site: flex-wrap gap-2 rounded-lg bg-white/5).
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val serverList = if (state.servers.isEmpty()) {
                listOf(StreamServer(state.streamServer, state.streamServer, true, true, true))
            } else {
                state.servers
            }
            items(serverList, key = { it.id }) { server ->
                val langSupported = if (state.streamLang == "dub") server.supportsDub else server.supportsSub
                val active = (server.name.equals(state.streamServer, ignoreCase = true) ||
                    server.id.equals(state.streamServer, ignoreCase = true)) && state.embedActive == null
                val enabled = langSupported
                ServerChip(
                    label = server.name,
                    active = active,
                    enabled = enabled,
                    onClick = { viewModel.setStreamServer(server.name) },
                )
            }
        }
        // E-Server chips (site: E-Koto / E-Neko … — iframe embeds).
        if (state.embedServers.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "E-Server",
                style = WebTextStyles.xs2,
                color = theme.fgMuted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.embedServers, key = { "embed-${it.key}" }) { embed ->
                    val active = state.embedActive?.key == embed.key
                    ServerChip(
                        label = embed.label,
                        active = active,
                        enabled = true,
                        onClick = {
                            if (active) viewModel.setEmbedServer(null)
                            else viewModel.setEmbedServer(embed)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerChip(label: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val theme = LocalAnikageTheme.current
    Text(
        text = label,
        style = WebTextStyles.xs,
        color = when {
            active -> theme.actionFg
            enabled -> Color(0xFFA1A1AA)
            else -> Color(0x50A1A1AA)
        },
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    active -> theme.action
                    enabled -> Color(0x0DFFFFFF)
                    else -> Color(0x05FFFFFF)
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

// ---------------------------------------------------------------------------
//  Mobile tabs (site: Episodes | Info)
// ---------------------------------------------------------------------------

@Composable
private fun MobileTabs(
    state: WatchUiState,
    tab: Int,
    onTabChange: (Int) -> Unit,
    onOpenInfo: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x08FFFFFF))
            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TabChip(
            icon = Icons.Default.List,
            label = "Episodes",
            count = state.episodes.size,
            active = tab == 0,
            onClick = { onTabChange(0) },
            modifier = Modifier.weight(1f),
        )
        TabChip(
            icon = Icons.Default.Info,
            label = "Info",
            count = 0,
            active = tab == 1,
            onClick = { onTabChange(1) },
            modifier = Modifier.weight(1f),
        )
        // Site: title link back to the anime info page.
        if (state.details != null) {
            TabChip(
                icon = Icons.Default.PlayCircleOutline,
                label = "About",
                count = 0,
                active = false,
                onClick = { onOpenInfo(animeIdOf(state)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun animeIdOf(state: WatchUiState): Int = state.details?.id ?: 0

// ---------------------------------------------------------------------------
//  Dialogs — download links / report / list (all real API-backed actions)
// ---------------------------------------------------------------------------

@Composable
private fun DownloadDialog(
    viewModel: WatchViewModel,
    episode: Int,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var links by remember {
        mutableStateOf<List<com.anikage.app.core.data.api.AnikageDownloadLink>>(emptyList())
    }
    LaunchedEffect(episode) {
        loading = true
        error = null
        viewModel.downloadLinks()
            .onSuccess { response ->
                links = response.downloads.filter { it.link != null }
                if (links.isEmpty()) error = "No download links available for this episode."
            }
            .onFailure { e ->
                error = e.message ?: "Failed to load download links."
            }
        loading = false
    }

    SiteDialog(
        title = "Download Episode $episode",
        onDismiss = onDismiss,
    ) {
        if (loading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LocalAnikageTheme.current.action, strokeWidth = 2.dp)
            }
        } else if (error != null) {
            Text(
                text = error ?: "",
                style = WebTextStyles.sm,
                color = Color(0xFFD4D4D8),
                modifier = Modifier.padding(16.dp),
            )
        } else {
            val byAudio = links.groupBy { it.audio ?: "sub" }
            byAudio.forEach { (audio, audioLinks) ->
                Text(
                    text = if (audio == "dub") "Dubbed" else "Subtitled",
                    style = WebTextStyles.xs,
                    color = LocalAnikageTheme.current.fgMuted,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
                audioLinks.sortedByDescending { it.resolution?.filter { c -> c.isDigit() }?.toIntOrNull() ?: 0 }
                    .forEach { link ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    link.link?.let { url ->
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                                            )
                                        }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = link.resolution ?: "Download",
                                style = WebTextStyles.sm,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = link.size ?: "Get",
                                style = WebTextStyles.xs,
                                color = Color(0x70FFFFFF),
                            )
                        }
                    }
            }
            Text(
                text = "Links open the provider's download page.",
                style = WebTextStyles.xs,
                color = Color(0x6BFFFFFF),
                modifier = Modifier.padding(14.dp),
            )
        }
    }
}

@Composable
private fun ReportDialog(
    viewModel: WatchViewModel,
    onDismiss: () -> Unit,
    onRefetch: () -> Unit,
) {
    val context = LocalContext.current
    var type by remember { mutableStateOf("refetch") }
    var note by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }

    val reasons = listOf(
        ReportReason("refetch", "Video won't play", "Black screen, error, or endless loading"),
        ReportReason("wrong_metadata", "Wrong episode info", "Wrong titles, thumbnails, or air dates"),
        ReportReason("wrong_count", "Wrong episode count", "Missing or extra episodes in the list"),
        ReportReason("wrong_video", "Wrong anime or episode", "A different show or episode plays here"),
        ReportReason("out_of_sync", "Episodes are shifted", "Right show, but the numbering is offset"),
        ReportReason("other", "Something else", ""),
    )

    SiteDialog(
        title = "Report",
        onDismiss = onDismiss,
    ) {
        if (done) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = LocalAnikageTheme.current.action,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Thanks — report sent to the team",
                    style = WebTextStyles.sm,
                    color = Color.White,
                )
            }
        } else {
            reasons.forEach { reason ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (type == reason.value) Color(0x14FFFFFF) else Color.Transparent)
                        .clickable { type = reason.value }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(reason.label, style = WebTextStyles.sm, color = Color.White, fontWeight = FontWeight.Medium)
                        if (reason.hint.isNotEmpty()) {
                            Text(reason.hint, style = WebTextStyles.xs, color = Color(0x6BFFFFFF))
                        }
                    }
                    if (type == reason.value) {
                        Icon(
                            Icons.Filled.RadioButtonChecked,
                            contentDescription = null,
                            tint = LocalAnikageTheme.current.action,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            // Note field (site: textarea).
            OutlinedTextFieldSite(
                value = note,
                onValueChange = { note = it },
                placeholder = "Anything else we should know? (optional)",
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 12.dp)) {
                SiteDialogButton(
                    label = if (type == "refetch") "Fix it" else "Send report",
                    primary = true,
                    enabled = !sending,
                ) {
                    if (type == "refetch") {
                        onRefetch()
                    } else {
                        sending = true
                        viewModel.submitReport(type, note) { success ->
                            sending = false
                            if (success) done = true
                            else android.widget.Toast.makeText(context, "Could not send report", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                SiteDialogButton(label = "Cancel", primary = false, enabled = true, onClick = onDismiss)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private data class ReportReason(val value: String, val label: String, val hint: String)

/**
 * "Add to List" sheet — the site's list sync needs an anikage.cc account
 * (auth.anikage.cc SSO). The app is account-free, so this opens the site's
 * watch page in the browser where the user is signed in. Honest, real action
 * — not a dead button.
 */
@Composable
private fun ListSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    SiteDialog(title = "Add to List", onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                text = "List syncing uses your Anikage account. Open the site to manage your list — " +
                    "watch progress saved in this app stays here on your device.",
                style = WebTextStyles.sm,
                color = Color(0xFFA1A1AA),
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(14.dp))
            SiteDialogButton(label = "Open anikage.cc", primary = true, enabled = true) {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(Config.ANIKAGE_SITE_ORIGIN)),
                    )
                }
                onDismiss()
            }
        }
    }
}

/** Site dialog shell — rounded-2xl dark panel, scrim, centered. */
@Composable
private fun SiteDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF101010))
                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = title,
                    style = WebTextStyles.base,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Color(0xFFA1A1AA),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x12FFFFFF)))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                content()
            }
        }
    }
}

@Composable
private fun SiteDialogButton(
    label: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalAnikageTheme.current
    Text(
        text = label,
        style = WebTextStyles.sm,
        color = if (primary) theme.actionFg else Color.White,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    primary -> theme.action
                    enabled -> Color(0x14FFFFFF)
                    else -> Color(0x08FFFFFF)
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

/** Minimal single-line text field matching the site's input styling. */
@Composable
private fun OutlinedTextFieldSite(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(placeholder, style = WebTextStyles.sm, color = Color(0x61FFFFFF))
        },
        singleLine = false,
        maxLines = 3,
        textStyle = WebTextStyles.sm.copy(color = Color.White),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LocalAnikageTheme.current.action,
            unfocusedBorderColor = Color(0x24FFFFFF),
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color(0x08FFFFFF),
            cursorColor = LocalAnikageTheme.current.action,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

// ---------------------------------------------------------------------------
//  Primitives
// ---------------------------------------------------------------------------

/** Prev/Next episode chip — site player control arrows, pill styling. */
@Composable
private fun EpisodeNavButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalAnikageTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) Color(0x0DFFFFFF) else Color(0x05FFFFFF))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        if (label == "Prev") {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = if (enabled) theme.fg else Color(0x50FFFFFF),
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = label,
            style = WebTextStyles.xs,
            color = if (enabled) theme.fg else Color(0x50FFFFFF),
            fontWeight = FontWeight.Medium,
        )
        if (label == "Next") {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (enabled) theme.fg else Color(0x50FFFFFF),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** site: .mobile-tab — icon + label + count badge, active = bg-action. */
@Composable
private fun TabChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAnikageTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) theme.action else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) theme.actionFg else theme.fgMuted,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = WebTextStyles.xs,
            color = if (active) theme.actionFg else theme.fgMuted,
            fontWeight = FontWeight.Medium,
        )
        if (count > 0) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = count.toString(),
                style = WebTextStyles.xs2,
                color = if (active) theme.actionFg.copy(alpha = 0.60f) else theme.fgMuted.copy(alpha = 0.60f),
            )
        }
    }
}

/** site: SUB/DUB chip — active bg-action text-action-fg, btn-xs. */
@Composable
private fun LangChip(label: String, active: Boolean, onClick: () -> Unit) {
    val theme = LocalAnikageTheme.current
    Text(
        text = label,
        style = WebTextStyles.xs,
        color = if (active) theme.actionFg else Color(0xFF71717A),
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) theme.action else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** site episode row — h-76 thumb + play overlay + title + badges. */
@Composable
private fun EpisodeRow(
    ep: EpisodeItem,
    active: Boolean,
    onClick: () -> Unit,
    horizontalPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    val theme = LocalAnikageTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontalPadding)
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Color(0x14FFFFFF) else Color.Transparent)
            .border(
                1.dp,
                if (active) Color(0x26FFFFFF) else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .height(76.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(theme.surfaceElevated),
        ) {
            // Thumbnails setting (site: episodeThumbnails) gates the image.
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
            // Active episode: play overlay.
            if (active) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
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
        ) {
            Text(
                text = if (ep.title.startsWith("Episode")) "${ep.number}." else ep.title,
                style = WebTextStyles.sm,
                color = if (active) theme.fg.copy(alpha = 0.90f) else theme.fg,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (ep.isFiller) {
                Text(
                    text = "Filler",
                    style = WebTextStyles.xs,
                    color = Color(0xFFFB923C),
                )
            } else if (ep.isRecap) {
                Text(
                    text = "Recap",
                    style = WebTextStyles.xs,
                    color = theme.fgMuted,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Comments — site: header card ("N Comments" + EP pill) + list
// ---------------------------------------------------------------------------

private val AvatarBase = "https://auth.anikage.cc"

@Composable
private fun CommentsSection(
    state: CommentsUiState,
    episode: Int,
    onRefresh: () -> Unit,
    horizontalPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    val theme = LocalAnikageTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Header card (site: rounded-xl border-white/8 bg-white/[0.02]).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x05FFFFFF))
                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                .padding(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x0AFFFFFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.ChatBubble,
                        contentDescription = null,
                        tint = Color(0xFFD4D4D8),
                        modifier = Modifier.size(14.dp),
                    )
                }
                Column {
                    Text(
                        text = "${state.total} Comments",
                        style = WebTextStyles.base,
                        color = theme.fg,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Talk about this episode without spoiling others.",
                        style = WebTextStyles.sm,
                        color = Color(0xFF71717A),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "EP $episode",
                    style = WebTextStyles.xs,
                    color = Color(0xFFD4D4D8),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x08FFFFFF))
                        .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onRefresh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh comments",
                        tint = Color(0xFF71717A),
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }

        if (!com.anikage.app.core.settings.SettingsState.commentsEnabled) {
            Text(
                text = "Comments are turned off in Settings.",
                style = WebTextStyles.sm,
                color = theme.fgMuted,
                modifier = Modifier.padding(16.dp),
            )
        } else if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = theme.action, strokeWidth = 2.dp)
            }
        } else if (state.comments.isEmpty()) {
            Text(
                text = "No comments yet — be the first to share your thoughts.",
                style = WebTextStyles.sm,
                color = theme.fgMuted,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            state.comments.forEach { comment ->
                CommentRow(comment)
            }
        }
    }
}

@Composable
private fun CommentRow(comment: AnikageComment) {
    val theme = LocalAnikageTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AsyncImage(
            model = comment.author?.avatar?.let { "$AvatarBase$it" },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(theme.surfaceElevated),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = comment.author?.displayName ?: comment.author?.username ?: "Anonymous",
                    style = WebTextStyles.xs,
                    color = theme.fg,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = relativeTime(comment.createdAt),
                    style = WebTextStyles.xs,
                    color = theme.fgMuted,
                )
            }
            Text(
                text = comment.content,
                style = WebTextStyles.sm,
                color = Color(0xFFD4D4D8),
                lineHeight = 19.5.sp,
            )
        }
    }
}

private fun relativeTime(iso: String?): String {
    if (iso == null) return ""
    return try {
        val then = Instant.parse(iso)
        val dur = Duration.between(then, Instant.now())
        val minutes = dur.toMinutes()
        when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 60 * 24 -> "${minutes / 60}h ago"
            else -> "${minutes / (60 * 24)}d ago"
        }
    } catch (_: Exception) {
        ""
    }
}

// ---------------------------------------------------------------------------
//  Loading skeleton — site: animate-pulse surface-card blocks in the exact
//  watch-page shape (player 16:9, notice, meta, server panel, rows).
// ---------------------------------------------------------------------------

@Composable
private fun WatchSkeleton(isWide: Boolean) {
    val theme = LocalAnikageTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface)
            .padding(top = 64.dp),
    ) {
        SkeletonBlock(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            corner = 16.dp,
        )
        Spacer(Modifier.height(12.dp))
        SkeletonBlock(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(36.dp),
            corner = 12.dp,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SkeletonBlock(modifier = Modifier.width(90.dp).height(32.dp), corner = 12.dp)
            SkeletonBlock(modifier = Modifier.width(150.dp).height(30.dp), corner = 10.dp)
        }
        Spacer(Modifier.height(12.dp))
        SkeletonBlock(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(96.dp),
            corner = 16.dp,
        )
        Spacer(Modifier.height(12.dp))
        SkeletonBlock(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(44.dp),
            corner = 12.dp,
        )
        Spacer(Modifier.height(12.dp))
        repeat(if (isWide) 2 else 3) {
            SkeletonBlock(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .height(92.dp),
                corner = 12.dp,
            )
        }
    }
}
