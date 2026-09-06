package com.anikage.app.core.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.anikage.app.Config
import com.anikage.app.ui.about.AboutScreen
import com.anikage.app.ui.browse.BrowseScreen
import com.anikage.app.ui.components.FloatingBottomNav
import com.anikage.app.ui.components.FloatingTopBar
import com.anikage.app.ui.details.DetailsScreen
import com.anikage.app.ui.home.HomeScreen
import com.anikage.app.ui.music.MusicScreen
import com.anikage.app.ui.player.WatchScreen
import com.anikage.app.ui.schedule.ScheduleScreen
import com.anikage.app.ui.search.SearchScreen
import com.anikage.app.ui.settings.LoggerScreen
import com.anikage.app.ui.settings.SettingsScreen
import com.anikage.app.ui.torrents.TorrentsScreen

@Composable
fun AnikageApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val showChrome = currentRoute in Routes.topBarScreens
    val showTopBar = showChrome && Config.Nav.SHOW_TOP_BAR
    val showBottomNav = showChrome && currentRoute in Routes.bottomNav
    // Home page hero is full-bleed and goes BEHIND the top bar; other pages need top padding.
    val isHome = currentRoute == Routes.HOME

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Main content
            NavHost(
                navController = navController,
                startDestination = Config.Nav.DEFAULT_SCREEN,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (showTopBar && !isHome) 76.dp else 0.dp),
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onAnimeClick = { navController.navigate(Routes.details(it.id)) },
                        onWatchClick = { navController.navigate(Routes.watch(it.id, 1)) },
                        onSeeAllClick = { navController.navigate(Routes.BROWSE) },
                    )
                }
                composable(Routes.BROWSE) {
                    BrowseScreen(
                        onAnimeClick = { navController.navigate(Routes.details(it.id)) },
                    )
                }
                composable(Routes.SCHEDULE) {
                    ScheduleScreen(
                        onAnimeClick = { navController.navigate(Routes.details(it.id)) },
                    )
                }
                composable(Routes.SEARCH) {
                    SearchScreen(
                        onAnimeClick = { navController.navigate(Routes.details(it.id)) },
                    )
                }
                composable(Routes.MUSIC) {
                    MusicScreen(
                        onAnimeClick = { navController.navigate(Routes.details(it.id)) },
                    )
                }
                composable(Routes.TORRENTS) {
                    TorrentsScreen()
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBackClick = { navController.popBackStack() },
                        onOpenLogger = { navController.navigate(Routes.LOGGER) },
                    )
                }
                composable(Routes.LOGGER) {
                    LoggerScreen(
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
                        onAnimeClick = { navController.navigate(Routes.details(it.id)) },
                        onWatchClick = { aId, ep ->
                            navController.navigate(Routes.watch(aId, ep))
                        },
                    )
                }
                composable(
                    route = Routes.WATCH,
                    arguments = listOf(
                        navArgument("id") { type = NavType.IntType },
                        navArgument("episode") { type = NavType.IntType },
                    ),
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getInt("id") ?: return@composable
                    val ep = backStackEntry.arguments?.getInt("episode") ?: 1
                    WatchScreen(
                        animeId = id,
                        initialEpisode = ep,
                        onBackClick = { navController.popBackStack() },
                    )
                }
                composable(Routes.ABOUT) {
                    AboutScreen()
                }
            }

            // Floating top bar (overlay)
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
                    modifier = Modifier.zIndex(10f),
                )
            }

            // Floating bottom mobile nav (overlay)
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
                        .align(androidx.compose.ui.Alignment.BottomEnd)
                        .zIndex(10f),
                )
            }
        }
    }
}
