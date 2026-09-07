package com.anikage.app.ui.player

import android.app.Activity
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
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
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
import com.anikage.app.core.download.EpisodeDownloadEngine
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
 *  v2.1.0: all player functions live IN the player (quick menus); Report
 *  removed; in-app downloads with live progress; episode rows show
 *  watched state + progress + filler; local anime list; avatars fixed.
 */
@Composable
fun WatchScreen(
    animeId: Int,
    initialEpisode: Int,
    slug: String? = null,
    localFile: String? = null,
    onBackClick: () -> Unit = {},
    onOpenInfo: (Int) -> Unit = {},
    onOpenDownloads: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val repo = remember { AnikageRepository.get(context) }
    val viewModel: WatchViewModel = viewModel(
        factory = WatchViewModel.factory(app, repo, animeId, initialEpisode, slug, localFile),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val downloadStates by EpisodeDownloadEngine.statesFlow.collectAsStateWithLifecycle()
    val theme = LocalAnikageTheme.current
    val configuration = LocalConfiguration.current
    val isWide = configuration.screenWidthDp.dp >= 840.dp // site lg (2-col watch grid)

    // Mobile tab: 0 = Episodes, 1 = Info (site's .wl-tabs: Episodes | Info).
    var tab by remember { mutableIntStateOf(0) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showListSheet by remember { mutableStateOf(false) }

    val activity = context as? Activity

    // Fullscreen stage — same player instance (VM-owned), orientation +
    // immersive bars below; position/episode/controls state all preserved.
    val fullscreenStage: @Composable () -> Unit = {
        PlayerStage(
            state = state,
            viewModel = viewModel,
            isFullscreen = true,
            onToggleFullscreen = { isFullscreen = false },
        )
    }

    // System back exits fullscreen FIRST (restores the previous UI state);
    // a second back leaves the screen.
    BackHandler(enabled = isFullscreen) { isFullscreen = false }

    // ── fullscreen: orientation per setting + immersive system bars ────
    DisposableEffect(isFullscreen) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, window.decorView) }
        if (isFullscreen) {
            activity?.requestedOrientation = when (SettingsState.fullscreenOrientation) {
                "sensor" -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                "none" -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
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
    val seekSec = SettingsState.seekAmountSec.coerceIn(5, 60)
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
            Key.J -> { viewModel.seekBy(-seekSec * 1000L); true }
            Key.L -> { viewModel.seekBy(seekSec * 1000L); true }
            Key.N -> { viewModel.nextEpisode(); true }
            Key.P -> { viewModel.previousEpisode(); true }
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
                        downloadStates = downloadStates,
                        onEnterFullscreen = { isFullscreen = true },
                        onOpenInfo = onOpenInfo,
                        onDownload = { showDownloadDialog = true },
                        onList = { showListSheet = true },
                        onOpenDownloads = onOpenDownloads,
                    )
                } else {
                    WatchMobileLayout(
                        state = state,
                        viewModel = viewModel,
                        downloadStates = downloadStates,
                        onEnterFullscreen = { isFullscreen = true },
                        tab = tab,
                        onTabChange = { tab = it },
                        onOpenInfo = onOpenInfo,
                        onDownload = { showDownloadDialog = true },
                        onList = { showListSheet = true },
                        onOpenDownloads = onOpenDownloads,
                    )
                }
            }
        }

        // ── dialogs (in-app download / local list sheet) ─────────────
        if (showDownloadDialog) {
            DownloadDialog(
                animeId = animeId,
                viewModel = viewModel,
                downloadStates = downloadStates,
                onOpenDownloads = {
                    showDownloadDialog = false
                    onOpenDownloads()
                },
                onDismiss = { showDownloadDialog = false },
            )
        }
        if (showListSheet) {
            ListSheet(
                viewModel = viewModel,
                onDismiss = { showListSheet = false },
            )
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
    downloadStates: List<EpisodeDownloadEngine.DownloadState>,
    onEnterFullscreen: () -> Unit,
    tab: Int,
    onTabChange: (Int) -> Unit,
    onOpenInfo: (Int) -> Unit,
    onDownload: () -> Unit,
    onList: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    // ONE sort per episode-list change (was re-sorted on every position
    // tick — O(n log n) 4x/second for 1000+ episode lists).
    val episodes = remember(state.episodes, SettingsState.episodeSortOrder) {
        if (SettingsState.episodeSortOrder == "desc") state.episodes.sortedByDescending { it.number }
        else state.episodes.sortedBy { it.number }
    }
    val context = LocalContext.current
    var downloadedEpNumbers by remember { mutableStateOf<Set<Int>>(emptySet()) }
    LaunchedEffect(state.slug, state.episodes.size) {
        runCatching {
            val repo = AnikageRepository.get(context)
            downloadedEpNumbers = repo.downloadedForAnime(animeIdOf(state)).map { it.episode }.toSet()
        }
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
                    onList = onList,
                )
                Spacer(Modifier.height(8.dp))
                EpisodeDownloadCard(
                    animeId = animeIdOf(state),
                    state = state,
                    downloadStates = downloadStates,
                    onOpenDownloads = onOpenDownloads,
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
                    progress = state.episodeProgress[ep.number],
                    downloaded = downloadedEpNumbers.contains(ep.number),
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
    downloadStates: List<EpisodeDownloadEngine.DownloadState>,
    onEnterFullscreen: () -> Unit,
    onOpenInfo: (Int) -> Unit,
    onDownload: () -> Unit,
    onList: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val episodes = remember(state.episodes, SettingsState.episodeSortOrder) {
        if (SettingsState.episodeSortOrder == "desc") state.episodes.sortedByDescending { it.number }
        else state.episodes.sortedBy { it.number }
    }
    val context = LocalContext.current
    var downloadedEpNumbers by remember { mutableStateOf<Set<Int>>(emptySet()) }
    LaunchedEffect(state.slug, state.episodes.size) {
        runCatching {
            val repo = AnikageRepository.get(context)
            downloadedEpNumbers = repo.downloadedForAnime(animeIdOf(state)).map { it.episode }.toSet()
        }
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
            )
            ServerNotice()
            Spacer(Modifier.height(12.dp))
            MetaRow(
                state = state,
                viewModel = viewModel,
                onDownload = onDownload,
                onList = onList,
                horizontalPadding = PaddingValues(0.dp),
            )
            Spacer(Modifier.height(8.dp))
            EpisodeDownloadCard(
                animeId = animeIdOf(state),
                state = state,
                downloadStates = downloadStates,
                onOpenDownloads = onOpenDownloads,
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
                        progress = state.episodeProgress[ep.number],
                        downloaded = downloadedEpNumbers.contains(ep.number),
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
                onBack = onToggleFullscreen,
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
        // Site's action row: Add to List / Download (Report removed).
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
    animeId: Int,
    viewModel: WatchViewModel,
    downloadStates: List<EpisodeDownloadEngine.DownloadState>,
    onOpenDownloads: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val theme = LocalAnikageTheme.current
    val state = viewModel.state.collectAsStateWithLifecycle().value

    // Resolve qualities the first time the dialog opens (per episode).
    LaunchedEffect(state.episode, state.streamServer, state.streamLang) {
        viewModel.resolveDownloadQualities()
    }

    val engineStates = downloadStates.filter { it.animeId == animeId && it.episode == state.episode }

    SiteDialog(
        title = "Download Episode ${state.episode}",
        onDismiss = onDismiss,
    ) {
        if (state.playingDownloaded) {
            // Offline copy playing — offer to go back to the live stream.
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = "You're watching the downloaded copy of this episode.",
                    style = WebTextStyles.sm,
                    color = Color(0xFF6EE7B7),
                )
                Spacer(Modifier.height(12.dp))
                SiteDialogButton(label = "Stream instead", primary = true, enabled = true) {
                    viewModel.playStreamVersion()
                    onDismiss()
                }
            }
        }

        // Completed download for this episode -> play it.
        state.downloaded?.let { row ->
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.DownloadDone, null, tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                    Text(
                        text = "Downloaded — ${row.quality}, ${row.sizeBytes / 1_000_000} MB",
                        style = WebTextStyles.sm,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SiteDialogButton(label = "Play download", primary = true, enabled = true) {
                        viewModel.playDownloadedCopy()
                        onDismiss()
                    }
                    SiteDialogButton(label = "Delete", primary = false, enabled = true) {
                        EpisodeDownloadEngine.deleteDownloaded(context, row.downloadKey)
                        viewModel.refreshDownloadedForEpisode(state.episode)
                    }
                }
            }
        }

        // Active / paused / failed downloads for this episode.
        engineStates.filter { it.status != EpisodeDownloadEngine.Status.COMPLETED }.forEach { dl ->
            DownloadProgressRow(
                state = dl,
                onPause = { EpisodeDownloadEngine.pause(dl.key) },
                onResume = { EpisodeDownloadEngine.resume(context, dl.key) },
                onRetry = { EpisodeDownloadEngine.retry(context, dl.key) },
                onCancel = { EpisodeDownloadEngine.cancel(context, dl.key) },
            )
        }

        // Quality picker (resolves the real HLS renditions).
        if (state.downloadQualitiesLoading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = theme.action, strokeWidth = 2.dp)
            }
        } else if (state.downloadQualitiesError != null) {
            Text(
                text = state.downloadQualitiesError ?: "",
                style = WebTextStyles.sm,
                color = Color(0xFFFCA5A5),
                modifier = Modifier.padding(16.dp),
            )
        } else if (state.slug != null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                Text(
                    text = "DOWNLOAD TO THIS DEVICE",
                    style = WebTextStyles.xs2,
                    color = theme.fgMuted,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                )
            }
            state.downloadQualities.forEach { q ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            viewModel.startDownload(q.height)
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = q.label,
                        style = WebTextStyles.sm,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                    )
                    Icon(
                        Icons.Filled.FileDownload,
                        contentDescription = null,
                        tint = theme.fgMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                text = "Downloads include the softsub subtitles and play offline inside Anikage.",
                style = WebTextStyles.xs,
                color = Color(0x6BFFFFFF),
                modifier = Modifier.padding(14.dp),
            )
        } else {
            Text(
                text = "This anime isn't in the Anikage catalogue, so it can't be downloaded.",
                style = WebTextStyles.sm,
                color = Color(0xFFD4D4D8),
                modifier = Modifier.padding(16.dp),
            )
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = "View all downloads",
                style = WebTextStyles.xs,
                color = theme.action,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenDownloads)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

/** One download's live progress row (pause / resume / retry / cancel). */
@Composable
private fun DownloadProgressRow(
    state: EpisodeDownloadEngine.DownloadState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val theme = LocalAnikageTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x08FFFFFF))
            .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val label = when (state.status) {
                EpisodeDownloadEngine.Status.DOWNLOADING -> "${(state.progress * 100).toInt()}%"
                EpisodeDownloadEngine.Status.PAUSED -> "Paused"
                EpisodeDownloadEngine.Status.FAILED -> "Failed"
                EpisodeDownloadEngine.Status.RESOLVING -> "Resolving…"
                EpisodeDownloadEngine.Status.QUEUED -> "Queued"
                EpisodeDownloadEngine.Status.COMPLETED -> "Done"
            }
            Text(
                text = label,
                style = WebTextStyles.sm,
                color = when (state.status) {
                    EpisodeDownloadEngine.Status.FAILED -> Color(0xFFFCA5A5)
                    EpisodeDownloadEngine.Status.PAUSED -> Color(0xFFFBBF24)
                    else -> Color.White
                },
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = state.qualityLabel + " · " + formatBytes(state.bytesDone),
                style = WebTextStyles.xs,
                color = theme.fgMuted,
            )
            Spacer(Modifier.weight(1f))
            when (state.status) {
                EpisodeDownloadEngine.Status.DOWNLOADING -> {
                    Text("Pause", style = WebTextStyles.xs, color = Color.White, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x1FFFFFFF))
                            .clickable(onClick = onPause)
                            .padding(horizontal = 10.dp, vertical = 5.dp))
                }
                EpisodeDownloadEngine.Status.PAUSED -> {
                    Text("Resume", style = WebTextStyles.xs, color = theme.actionFg, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.action)
                            .clickable(onClick = onResume)
                            .padding(horizontal = 10.dp, vertical = 5.dp))
                }
                EpisodeDownloadEngine.Status.FAILED -> {
                    Text("Retry", style = WebTextStyles.xs, color = theme.actionFg, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.action)
                            .clickable(onClick = onRetry)
                            .padding(horizontal = 10.dp, vertical = 5.dp))
                }
                else -> {}
            }
            if (state.status != EpisodeDownloadEngine.Status.COMPLETED) {
                Text("Cancel", style = WebTextStyles.xs, color = Color(0xFFFCA5A5), fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x14EF4444))
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
        if (state.status == EpisodeDownloadEngine.Status.DOWNLOADING || state.status == EpisodeDownloadEngine.Status.PAUSED) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x14FFFFFF)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(state.progress)
                        .fillMaxSize()
                        .background(if (state.status == EpisodeDownloadEngine.Status.PAUSED) Color(0xFFFBBF24) else theme.action),
                )
            }
            if (state.status == EpisodeDownloadEngine.Status.DOWNLOADING) {
                Text(
                    text = "${formatBytes(state.bytesPerSec)}/s · ${state.segmentsDone}/${state.segmentsTotal} segments",
                    style = WebTextStyles.xs2,
                    color = theme.fgMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        state.error?.let { err ->
            Text(
                text = err,
                style = WebTextStyles.xs,
                color = Color(0xFFFCA5A5),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1e9)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1e6)
    bytes >= 1_000 -> "%d KB".format(bytes / 1_000)
    else -> "$bytes B"
}

/** Avatar fallback color from the author's initial. */
private fun avatarColor(initial: String): Color {
    val colors = listOf(
        Color(0xFF7C3AED), Color(0xFF2563EB), Color(0xFF0891B2), Color(0xFF059669),
        Color(0xFFD97706), Color(0xFFDC2626), Color(0xFFDB2777), Color(0xFF4F46E5),
    )
    return colors[(initial.hashCode().let { if (it < 0) -it else it }) % colors.size]
}

/** Live download status card under the meta row (current episode). */
@Composable
private fun EpisodeDownloadCard(
    animeId: Int,
    state: WatchUiState,
    downloadStates: List<EpisodeDownloadEngine.DownloadState>,
    onOpenDownloads: () -> Unit,
    horizontalPadding: PaddingValues = PaddingValues(horizontal = 0.dp),
) {
    val context = LocalContext.current
    val theme = LocalAnikageTheme.current
    val dl = downloadStates.firstOrNull { it.animeId == animeId && it.episode == state.episode } ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontalPadding)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x08FFFFFF))
            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.Filled.FileDownload,
                contentDescription = null,
                tint = theme.action,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = when (dl.status) {
                    EpisodeDownloadEngine.Status.DOWNLOADING -> "Downloading — ${(dl.progress * 100).toInt()}%"
                    EpisodeDownloadEngine.Status.PAUSED -> "Download paused"
                    EpisodeDownloadEngine.Status.FAILED -> "Download failed"
                    EpisodeDownloadEngine.Status.RESOLVING -> "Preparing download…"
                    EpisodeDownloadEngine.Status.QUEUED -> "Queued"
                    EpisodeDownloadEngine.Status.COMPLETED -> "Downloaded"
                },
                style = WebTextStyles.sm,
                color = theme.fg,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            when (dl.status) {
                EpisodeDownloadEngine.Status.DOWNLOADING -> {
                    Text("Pause", style = WebTextStyles.xs, color = Color.White, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x1FFFFFFF))
                            .clickable { EpisodeDownloadEngine.pause(dl.key) }
                            .padding(horizontal = 10.dp, vertical = 5.dp))
                }
                EpisodeDownloadEngine.Status.PAUSED, EpisodeDownloadEngine.Status.FAILED -> {
                    Text(
                        if (dl.status == EpisodeDownloadEngine.Status.PAUSED) "Resume" else "Retry",
                        style = WebTextStyles.xs, color = theme.actionFg, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.action)
                            .clickable {
                                if (dl.status == EpisodeDownloadEngine.Status.PAUSED) {
                                    EpisodeDownloadEngine.resume(context, dl.key)
                                } else {
                                    EpisodeDownloadEngine.retry(context, dl.key)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp))
                }
                else -> {}
            }
            Text("View", style = WebTextStyles.xs, color = theme.fgMuted, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenDownloads)
                    .padding(horizontal = 8.dp, vertical = 5.dp))
        }
        if (dl.status == EpisodeDownloadEngine.Status.DOWNLOADING || dl.status == EpisodeDownloadEngine.Status.PAUSED) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x14FFFFFF)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(dl.progress)
                        .fillMaxSize()
                        .background(if (dl.status == EpisodeDownloadEngine.Status.PAUSED) Color(0xFFFBBF24) else theme.action),
                )
            }
        }
    }
}


/**
 * "Add to List" sheet — the app's LOCAL anime list (device-side; the site's
 * list needs an anikage.cc account). Statuses mirror the site's list
 * statuses; the player's bookmark quick menu edits the same data.
 */
@Composable
private fun ListSheet(
    viewModel: WatchViewModel,
    onDismiss: () -> Unit,
) {
    val theme = LocalAnikageTheme.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SiteDialog(title = "Add to List", onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                text = "Your list is stored on this device — no account needed.",
                style = WebTextStyles.sm,
                color = Color(0xFFA1A1AA),
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(14.dp))
            listOf(
                "watching" to "Watching",
                "planned" to "Plan to Watch",
                "completed" to "Completed",
                "on_hold" to "On Hold",
                "dropped" to "Dropped",
            ).forEach { (key, label) ->
                val selected = state.listStatus == key
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Color(0x14FFFFFF) else Color.Transparent)
                        .clickable {
                            viewModel.setListStatus(if (selected) null else key)
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = label,
                        style = WebTextStyles.sm,
                        color = if (selected) theme.action else Color.White,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = theme.action, modifier = Modifier.size(16.dp))
                    }
                }
            }
            if (state.listStatus != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Remove from list",
                    style = WebTextStyles.sm,
                    color = Color(0xFFFCA5A5),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x14EF4444))
                        .clickable { viewModel.setListStatus(null) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            SiteDialogButton(label = "Done", primary = true, enabled = true, onClick = onDismiss)
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

/** site episode row — thumb + number + title + filler + watched + progress. */
@Composable
private fun EpisodeRow(
    ep: EpisodeItem,
    active: Boolean,
    progress: EpisodeProgress?,
    downloaded: Boolean,
    onClick: () -> Unit,
    horizontalPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    val theme = LocalAnikageTheme.current
    val watched = progress?.watched == true
    val inProgress = progress?.inProgress == true
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
            // Watched: dim the thumbnail (site: watched episodes fade).
            if (watched) {
                Box(Modifier.fillMaxSize().background(Color(0x66000000)))
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
            // Downloaded badge.
            if (downloaded) {
                Icon(
                    Icons.Filled.DownloadDone,
                    contentDescription = "Downloaded",
                    tint = Color(0xFF34D399),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp),
                )
            }
            // Watched checkmark.
            if (watched && !active) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Watched",
                    tint = Color(0xFF34D399),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(30.dp)
                        .background(Color(0x8C000000), CircleShape)
                        .padding(5.dp),
                )
            }
            // Watch progress bar along the bottom of the thumbnail.
            if (inProgress && progress != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color(0x33FFFFFF)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.fraction)
                            .fillMaxSize()
                            .background(if (active) theme.action else Color(0xFF34D399)),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = ep.number.toString(),
                    style = WebTextStyles.sm,
                    color = if (active) theme.action else theme.fg,
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
                text = if (ep.title.startsWith("Episode")) "Episode ${ep.number}" else ep.title,
                style = WebTextStyles.sm,
                color = if (active) theme.fg.copy(alpha = 0.90f) else theme.fg,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (watched) {
                Text(
                    text = "Watched",
                    style = WebTextStyles.xs,
                    color = Color(0xFF34D399),
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else if (inProgress && progress != null) {
                Text(
                    text = "${(progress.fraction * 100).toInt()}% watched",
                    style = WebTextStyles.xs,
                    color = theme.fgMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Comments — site: header card ("N Comments" + EP pill) + list
// ---------------------------------------------------------------------------

private val AvatarBase = com.anikage.app.Config.ANIKAGE_SITE_ORIGIN

/** Full avatar URL: the API returns site-relative paths (/assets/…). */
private fun avatarUrl(path: String?): String? = path?.let {
    if (it.startsWith("http")) it else "$AvatarBase$it"
}

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
        // Avatar with initial-letter fallback (never a blank hole).
        val author = comment.author
        val displayName = author?.displayName ?: author?.username ?: "Anonymous"
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(theme.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            val url = avatarUrl(author?.avatar)
            if (url != null) {
                coil.compose.AsyncImage(
                    model = url,
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (url == null) {
                // Fallback: colored circle with the author's initial.
                val initial = displayName.firstOrNull()?.uppercase() ?: "?"
                val bg = avatarColor(initial)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(bg, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initial,
                        style = WebTextStyles.sm,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = displayName,
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
