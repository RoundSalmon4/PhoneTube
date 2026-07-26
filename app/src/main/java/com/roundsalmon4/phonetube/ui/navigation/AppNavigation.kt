package com.roundsalmon4.phonetube.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.roundsalmon4.phonetube.ui.channel.ChannelScreen
import com.roundsalmon4.phonetube.ui.home.HomeScreen
import com.roundsalmon4.phonetube.ui.library.LibraryScreen
import com.roundsalmon4.phonetube.ui.library.playlist.PlaylistDetailScreen
import com.roundsalmon4.phonetube.ui.player.PlayerScreen
import com.roundsalmon4.phonetube.ui.search.SearchScreen
import com.roundsalmon4.phonetube.ui.settings.CreditsScreen
import com.roundsalmon4.phonetube.ui.settings.LicenseScreen
import com.roundsalmon4.phonetube.ui.settings.SettingsScreen

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: Route
)

val bottomNavItems = listOf(
    BottomNavItem("Home", Icons.Default.Home, Route.Home),
    BottomNavItem("Search", Icons.Default.Search, Route.Search),
    BottomNavItem("Library", Icons.Default.LibraryMusic, Route.Library),
    BottomNavItem("Settings", Icons.Default.Settings, Route.Settings)
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.route::class.qualifiedName } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentDestination?.hierarchy?.any {
                                it.route == item.route::class.qualifiedName
                            } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Route.Home,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<Route.Home> {
                HomeScreen(
                    onVideoClick = { videoId ->
                        navController.navigate(Route.Player(videoId))
                    },
                    onChannelClick = { channelId ->
                        navController.navigate(Route.Channel(channelId))
                    }
                )
            }

            composable<Route.Player> { backStackEntry ->
                val route = backStackEntry.toRoute<Route.Player>()
                PlayerScreen(
                    videoId = route.videoId,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable<Route.Search> {
                SearchScreen(
                    onVideoClick = { videoId ->
                        navController.navigate(Route.Player(videoId))
                    },
                    onChannelClick = { channelId ->
                        navController.navigate(Route.Channel(channelId))
                    }
                )
            }

            composable<Route.Channel> { backStackEntry ->
                val route = backStackEntry.toRoute<Route.Channel>()
                ChannelScreen(
                    channelId = route.channelId,
                    onVideoClick = { videoId ->
                        navController.navigate(Route.Player(videoId))
                    },
                    onChannelClick = { channelId ->
                        navController.navigate(Route.Channel(channelId))
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable<Route.Library> {
                LibraryScreen(
                    onVideoClick = { videoId ->
                        navController.navigate(Route.Player(videoId))
                    },
                    onChannelClick = { channelId ->
                        navController.navigate(Route.Channel(channelId))
                    },
                    onPlaylistClick = { playlistId ->
                        navController.navigate(Route.PlaylistDetail(playlistId))
                    }
                )
            }

            composable<Route.PlaylistDetail> { backStackEntry ->
                val route = backStackEntry.toRoute<Route.PlaylistDetail>()
                PlaylistDetailScreen(
                    onVideoClick = { videoId ->
                        navController.navigate(Route.Player(videoId))
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable<Route.Settings> {
                SettingsScreen(
                    onLicenseClick = { navController.navigate(Route.License) },
                    onCreditsClick = { navController.navigate(Route.Credits) }
                )
            }

            composable<Route.License> {
                LicenseScreen(onBackClick = { navController.popBackStack() })
            }

            composable<Route.Credits> {
                CreditsScreen(onBackClick = { navController.popBackStack() })
            }
        }
    }
}
