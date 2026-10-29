package dev.sshmobile.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.sshmobile.R
import dev.sshmobile.ui.servers.DashboardScreen
import dev.sshmobile.ui.servers.ServerEditScreen
import dev.sshmobile.ui.sessions.SessionsScreen
import dev.sshmobile.ui.settings.KnownHostsScreen
import dev.sshmobile.ui.settings.SettingsScreen
import dev.sshmobile.ui.sftp.SftpScreen
import dev.sshmobile.ui.terminal.TerminalScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val SESSIONS = "sessions"
    const val SETTINGS = "settings"
    const val KNOWN_HOSTS = "known_hosts"
    const val SFTP = "sftp"
    const val SERVER_EDIT = "server/edit?serverId={serverId}"
    const val TERMINAL = "terminal/{sessionId}"

    fun serverEdit(serverId: Long): String = "server/edit?serverId=$serverId"
    fun terminal(sessionId: String): String = "terminal/$sessionId"
}

private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.DASHBOARD, R.string.nav_servers, Icons.Rounded.Dns, Icons.Outlined.Dns),
    TopLevelDestination(Routes.SESSIONS, R.string.nav_sessions, Icons.Rounded.Layers, Icons.Outlined.Layers),
    TopLevelDestination(Routes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings),
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = topLevelDestinations.any { it.route == currentRoute }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                ) {
                    topLevelDestinations.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { if (!selected) navigateToTab(destination.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon else destination.icon,
                                    contentDescription = stringResource(destination.labelRes),
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onConnect = { sessionId ->
                        navController.navigate(Routes.terminal(sessionId))
                    },
                    onAddServer = {
                        navController.navigate(Routes.serverEdit(0))
                    },
                    onEditServer = { serverId ->
                        navController.navigate(Routes.serverEdit(serverId))
                    },
                )
            }

            composable(Routes.SESSIONS) {
                SessionsScreen(
                    onOpenSession = { sessionId ->
                        navController.navigate(Routes.terminal(sessionId))
                    },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenKnownHosts = { navController.navigate(Routes.KNOWN_HOSTS) },
                    onOpenSftp = { navController.navigate(Routes.SFTP) },
                )
            }

            composable(
                route = Routes.SERVER_EDIT,
                arguments = listOf(
                    navArgument("serverId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                ),
            ) { entry ->
                val serverId = entry.arguments?.getLong("serverId") ?: 0L
                ServerEditScreen(
                    serverId = serverId,
                    onDone = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.TERMINAL,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) { entry ->
                val sessionId = entry.arguments?.getString("sessionId").orEmpty()
                TerminalScreen(
                    sessionId = sessionId,
                    onBack = { navController.popBackStack() },
                    onOpenSessions = {
                        navController.navigate(Routes.SESSIONS) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }

            composable(Routes.KNOWN_HOSTS) {
                KnownHostsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SFTP) {
                SftpScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
