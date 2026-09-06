package com.anikage.app.core.nav

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.anikage.app.Config
import com.anikage.app.core.data.AnimePreviewStore
import com.anikage.app.core.log.AppLogger
import com.anikage.app.core.log.SessionLogger
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.WebTextStyles
import com.anikage.app.ui.about.AboutScreen
import com.anikage.app.ui.browse.BrowseScreen
import com.anikage.app.ui.components.FloatingBottomNav
import com.anikage.app.ui.components.FloatingTopBar
import com.anikage.app.ui.details.DetailsScreen
import com.anikage.app.ui.home.HomeScreen
import com.anikage.app.ui.music.MusicInfoScreen
import com.anikage.app.ui.music.MusicScreen
import com.anikage.app.ui.notifications.NotificationsScreen
import com.anikage.app.ui.player.WatchScreen
import com.anikage.app.ui.schedule.ScheduleDetailScreen
import com.anikage.app.ui.schedule.ScheduleScreen
import com.anikage.app.ui.search.SearchScreen
import com.anikage.app.ui.settings.DiagnosticsScreen
import com.anikage.app.ui.settings.SettingsScreen
import com.anikage.app.ui.torrents.TorrentsScreen

@Composable
fun AnikageApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Site: the fixed top nav floats over EVERY content page (home, browse,
    // schedule, music, torrents, info, watch). Account pages (settings,
    // profile, notifications) use their own back headers instead.
    val showTopBar = currentRoute !in Routes.accountScreens && currentRoute != Routes.DIAGNOSTICS && currentRoute != Routes.ABOUT
    val showBottomNav = currentRoute in Routes.bottomNav

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Main content — screens handle their own top clearance for the
            // floating nav (site: content starts under the floating bar).
            NavHost(
                navController = navController,
                startDestination = Config.Nav.DEFAULT_SCREEN,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onAnimeClick = { anime ->
                            AnimePreviewStore.put(anime)
                            navController.navigate(Routes.details(anime.id))
                        },
                        onWatchClick = { anime ->
                            AnimePreviewStore.put(anime)
                            navController.navigate(Routes.watch(anime.id, 1, anime.slug))
                        },
                        onSeeAllClick = { navController.navigate(Routes.BROWSE) },
                    )
                }
                composable(Routes.BROWSE) {
                    BrowseScreen(
                        onAnimeClick = { anime ->
                            AnimePreviewStore.put(anime)
                            navController.navigate(Routes.details(anime.id))
                        },
                    )
                }
                composable(Routes.SCHEDULE) {
                    ScheduleScreen(
                        onEntryClick = { schedule ->
                            AnimePreviewStore.put(schedule.media)
                            navController.navigate(
                                Routes.scheduleDetails(schedule.media.id, schedule.episode, schedule.airingAt)
                            )
                        },
                    )
                }
                composable(Routes.SEARCH) {
                    SearchScreen(
                        onAnimeClick = { anime ->
                            AnimePreviewStore.put(anime)
                            navController.navigate(Routes.details(anime.id))
                        },
                        onBackClick = { navController.popBackStack() },
                    )
                }
                composable(Routes.MUSIC) {
                    MusicScreen(
                        onOpenTheme = { slug, type ->
                            navController.navigate(Routes.musicInfo(slug, type))
                        },
                    )
                }
                composable(Routes.TORRENTS) {
                    TorrentsScreen()
                }
                composable(Routes.NOTIFICATIONS) {
                    NotificationsScreen(
                        onBackClick = { navController.popBackStack() },
                        onOpenSettings = {
                            navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                        },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBackClick = { navController.popBackStack() },
                        onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                    )
                }
                composable(Routes.DIAGNOSTICS) {
                    DiagnosticsScreen(
                        onBackClick = { navController.popBackStack() },
                    )
                }
                composable(
                    route = Routes.DETAILS,
                    arguments = listOf(navArgument("id") { type = NavType.IntType }),
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getInt("id") ?: return@composable
                    DetailsScreen(
                        animeId = id,
                        onBackClick = { navController.popBackStack() },
                        onAnimeClick = { anime ->
                            AnimePreviewStore.put(anime)
                            navController.navigate(Routes.details(anime.id))
                        },
                        onWatchClick = { aId, ep, slug ->
                            navController.navigate(Routes.watch(aId, ep, slug))
                        },
                    )
                }
                composable(
                    route = Routes.WATCH,
                    arguments = listOf(
                        navArgument("id") { type = NavType.IntType },
                        navArgument("episode") { type = NavType.IntType; defaultValue = 1 },
                        navArgument("slug") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getInt("id") ?: return@composable
                    val ep = backStackEntry.arguments?.getInt("episode") ?: 1
                    val slug = backStackEntry.arguments
                        ?.getString("slug")
                        ?.takeIf { it.isNotBlank() }
                    WatchScreen(
                        animeId = id,
                        initialEpisode = ep,
                        slug = slug,
                        onBackClick = { navController.popBackStack() },
                    )
                }
                composable(
                    route = Routes.MUSIC_INFO,
                    arguments = listOf(
                        navArgument("slug") { type = NavType.StringType },
                        navArgument("type") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { backStackEntry ->
                    val slug = backStackEntry.arguments?.getString("slug") ?: return@composable
                    val type = backStackEntry.arguments?.getString("type") ?: ""
                    MusicInfoScreen(
                        slug = slug,
                        type = type,
                        onBackClick = { navController.popBackStack() },
                    )
                }
                composable(
                    route = Routes.SCHEDULE_DETAILS,
                    arguments = listOf(
                        navArgument("id") { type = NavType.IntType },
                        navArgument("episode") { type = NavType.IntType; defaultValue = 1 },
                        navArgument("airingAt") { type = NavType.LongType; defaultValue = 0L },
                    ),
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getInt("id") ?: return@composable
                    val ep = backStackEntry.arguments?.getInt("episode") ?: 1
                    val airingAt = backStackEntry.arguments?.getLong("airingAt") ?: 0L
                    ScheduleDetailScreen(
                        animeId = id,
                        episode = ep,
                        airingAt = airingAt,
                        onBackClick = { navController.popBackStack() },
                        onWatchClick = { aId, aEp, slug ->
                            navController.navigate(Routes.watch(aId, aEp, slug))
                        },
                        onViewAnime = { aId ->
                            // The schedule entry seeded the preview store; use
                            // it (with its slug) so the info page opens via the
                            // Anikage path without needing AniList.
                            AnimePreviewStore.byId(aId)?.let { navController.navigate(Routes.details(it.id)) }
                                ?: navController.navigate(Routes.details(aId))
                        },
                    )
                }
                composable(Routes.ABOUT) {
                    AboutScreen()
                }
            }

            // Floating top bar (overlay) — site's fixed nav, all content pages.
            if (showTopBar) {
                FloatingTopBar(
                    currentRoute = currentRoute ?: "home",
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onSearchClick = {
                        navController.navigate(Routes.SEARCH) { launchSingleTop = true }
                    },
                    onNotificationsClick = {
                        navController.navigate(Routes.NOTIFICATIONS) { launchSingleTop = true }
                    },
                    modifier = Modifier.zIndex(10f),
                )
            }

            // Crash recovery banner — shown once after a crashed session.
            CrashRecoveryBanner(
                onOpenLogs = {
                    navController.navigate(Routes.DIAGNOSTICS) { launchSingleTop = true }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp)
                    .zIndex(20f),
            )

            // Floating bottom mobile nav (site: centered glass pill).
            if (showBottomNav) {
                FloatingBottomNav(
                    currentRoute = currentRoute ?: "home",
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(10f),
                )
            }
        }
    }
}

/**
 * Recovery banner (user spec #12): after a crash, tell the user what
 * happened and let them jump straight into the crashed session's logs.
 * Dismissed per-process; never deletes the crashed session.
 */
@Composable
private fun CrashRecoveryBanner(
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val crash = AppLogger.previousCrash
    var dismissed by remember { mutableStateOf(false) }
    if (crash == null || dismissed) return
    val theme = LocalAnikageTheme.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x1AEC4899))
            .border(1.dp, Color(0x33EC4899), RoundedCornerShape(14.dp))
            .clickable { onOpenLogs() }
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)
            .animateContentSize(),
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            tint = Color(0xFFF87171),
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Previous session crashed",
                style = WebTextStyles.sm,
                color = Color(0xFFFECACA),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Session #${crash.seq} — ${crash.errorCount} error${if (crash.errorCount == 1) "" else "s"} recorded",
                style = WebTextStyles.xs,
                color = Color(0xFFFCA5A5),
            )
        }
        Text(
            text = "View logs",
            style = WebTextStyles.xs,
            color = Color(0xFFFECACA),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x33EC4899))
                .clickable { onOpenLogs() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Icon(
            Icons.Default.Close,
            contentDescription = "Dismiss",
            tint = Color(0xFFFCA5A5),
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(50))
                .clickable { dismissed = true }
                .padding(6.dp),
        )
    }
}
