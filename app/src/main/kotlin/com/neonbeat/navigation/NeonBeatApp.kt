package com.neonbeat.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowWidthSizeClass
import com.neonbeat.feature.library.LibraryScreen
import com.neonbeat.feature.player.NowPlayingScreen
import com.neonbeat.feature.search.SearchScreen
import com.neonbeat.feature.settings.SettingsScreen
import com.neonbeat.ui.MiniPlayer

/** Top-level destinations shown in the navigation bar / rail. */
enum class TopLevelDestination(val route: String, val label: String, val icon: ImageVector) {
    LIBRARY("library", "Library", Icons.Default.LibraryMusic),
    SEARCH("search", "Search", Icons.Default.Search),
    SETTINGS("settings", "Settings", Icons.Default.Settings),
}

/**
 * Root composable: adaptive navigation shell plus the now-playing overlay.
 *
 * [NavigationSuiteScaffold] switches automatically between a bottom bar on
 * phones, a navigation rail on unfolded foldables and landscape, and a
 * permanent drawer on tablets, so no per-form-factor layout code is needed.
 *
 * The now-playing screen is an overlay rather than a navigation destination:
 * it must be able to expand over any tab and collapse back to the mini player
 * without disturbing the underlying back stack.
 */
@Composable
fun NeonBeatApp(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: TopLevelDestination.LIBRARY.route
    var playerExpanded by remember { mutableStateOf(false) }

    val widthSizeClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
    val isWide = widthSizeClass != WindowWidthSizeClass.COMPACT
    val gridColumns = when (widthSizeClass) {
        WindowWidthSizeClass.EXPANDED -> 5
        WindowWidthSizeClass.MEDIUM -> 3
        else -> 2
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                item(
                    selected = currentRoute == destination.route,
                    onClick = {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(destination.icon, contentDescription = null) },
                    label = { Text(destination.label) },
                )
            }
        },
    ) {
        Scaffold { innerPadding ->
            val layoutDirection = LocalLayoutDirection.current
            // Leave room for the mini player so the last list row is never hidden.
            val contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 76.dp,
            )

            Box(Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = TopLevelDestination.LIBRARY.route,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(TopLevelDestination.LIBRARY.route) {
                        LibraryScreen(
                            onSongClick = { _, _ -> playerExpanded = true },
                            onAlbumClick = {},
                            onArtistClick = {},
                            contentPadding = contentPadding,
                            gridColumns = gridColumns,
                        )
                    }
                    composable(TopLevelDestination.SEARCH.route) {
                        SearchScreen(
                            onSongClick = { _, _ -> playerExpanded = true },
                            onAlbumClick = {},
                            onArtistClick = {},
                            contentPadding = contentPadding,
                        )
                    }
                    composable(TopLevelDestination.SETTINGS.route) {
                        SettingsScreen(contentPadding = contentPadding)
                    }
                }

                MiniPlayer(
                    onExpand = { playerExpanded = true },
                    modifier = Modifier.fillMaxSize(),
                    bottomInset = innerPadding.calculateBottomPadding(),
                )

                AnimatedVisibility(
                    visible = playerExpanded,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                ) {
                    NowPlayingScreen(
                        onCollapse = { playerExpanded = false },
                        wideLayout = isWide,
                    )
                }
            }
        }
    }
}
