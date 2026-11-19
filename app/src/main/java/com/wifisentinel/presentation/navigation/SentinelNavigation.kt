package com.wifisentinel.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wifisentinel.domain.model.AppSettings
import com.wifisentinel.presentation.dashboard.DashboardScreen
import com.wifisentinel.presentation.dashboard.DashboardViewModel
import com.wifisentinel.presentation.localization.LocalSentinelStrings
import com.wifisentinel.presentation.networks.NetworksScreen
import com.wifisentinel.presentation.networks.NetworksViewModel
import com.wifisentinel.presentation.securitylab.PasswordAnalyzerScreen
import com.wifisentinel.presentation.securitylab.SecureResultScreen
import com.wifisentinel.presentation.securitylab.SecurityLabScreen
import com.wifisentinel.presentation.securitylab.SecurityLabViewModel
import com.wifisentinel.presentation.settings.SettingsScreen
import com.wifisentinel.presentation.settings.SettingsViewModel
import com.wifisentinel.presentation.theme.LocalSentinelColors
import com.wifisentinel.presentation.wordlists.WordlistDetailScreen
import com.wifisentinel.presentation.wordlists.WordlistsScreen
import com.wifisentinel.presentation.wordlists.WordlistsViewModel
import kotlinx.coroutines.launch

object SentinelRoutes {
    const val DASHBOARD = "dashboard"
    const val NETWORKS = "networks"
    const val WORDLISTS = "wordlists"
    const val SECURITY_LAB = "security_lab"
    const val SETTINGS = "settings"
    const val WORDLIST_DETAIL = "wordlist_detail/{wordlistId}"
    const val SECURE_RESULT = "secure_result/{assessmentId}"
    const val PASSWORD_ANALYZER = "password_analyzer"

    fun wordlistDetail(id: Long) = "wordlist_detail/$id"
    fun secureResult(id: String) = "secure_result/$id"
}

private data class TopLevelDestination(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
    val labelProvider: @Composable () -> String
)

private val TopLevelDestinations = listOf(
    TopLevelDestination(
        route = SentinelRoutes.DASHBOARD,
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard,
        testTag = "nav_tab_dashboard",
        labelProvider = { LocalSentinelStrings.current.navDashboard }
    ),
    TopLevelDestination(
        route = SentinelRoutes.NETWORKS,
        selectedIcon = Icons.Filled.Radar,
        unselectedIcon = Icons.Outlined.Radar,
        testTag = "nav_tab_networks",
        labelProvider = { LocalSentinelStrings.current.navNetworks }
    ),
    TopLevelDestination(
        route = SentinelRoutes.WORDLISTS,
        selectedIcon = Icons.Filled.Description,
        unselectedIcon = Icons.Outlined.Description,
        testTag = "nav_tab_wordlists",
        labelProvider = { LocalSentinelStrings.current.navWordlists }
    ),
    TopLevelDestination(
        route = SentinelRoutes.SECURITY_LAB,
        selectedIcon = Icons.Filled.Security,
        unselectedIcon = Icons.Outlined.Security,
        testTag = "nav_tab_security_lab",
        labelProvider = { LocalSentinelStrings.current.navSecurityLab }
    ),
    TopLevelDestination(
        route = SentinelRoutes.SETTINGS,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_tab_settings",
        labelProvider = { LocalSentinelStrings.current.navSettings }
    )
)

@Composable
fun SentinelNavShell(
    settings: AppSettings,
    dashboardViewModel: DashboardViewModel,
    networksViewModel: NetworksViewModel,
    wordlistsViewModel: WordlistsViewModel,
    securityLabViewModel: SecurityLabViewModel,
    settingsViewModel: SettingsViewModel,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ext = LocalSentinelColors.current

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val isTopLevelRoute = TopLevelDestinations.any { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val useNavigationRail = maxWidth >= 840.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail && isTopLevelRoute) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .border(0.5.dp, ext.borderCrimson)
                            .testTag("sentinel_bottom_nav")
                    ) {
                        TopLevelDestinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            val label = dest.labelProvider()
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ext.neonAccent,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = ext.crimsonSurface,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail && isTopLevelRoute) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .border(0.5.dp, ext.borderCrimson)
                    ) {
                        TopLevelDestinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            val label = dest.labelProvider()
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label, maxLines = 1) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ext.neonAccent,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = ext.crimsonSurface,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = SentinelRoutes.DASHBOARD,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 760.dp)
                    ) {
                        composable(SentinelRoutes.DASHBOARD) {
                            DashboardScreen(
                                viewModel = dashboardViewModel,
                                onScanNetworks = { navigateTopLevel(SentinelRoutes.NETWORKS) },
                                onImportWordlist = { navigateTopLevel(SentinelRoutes.WORDLISTS) },
                                onAnalyzePassword = { navController.navigate(SentinelRoutes.PASSWORD_ANALYZER) },
                                onOpenSecurityLab = { navigateTopLevel(SentinelRoutes.SECURITY_LAB) },
                                onOpenSettings = { navigateTopLevel(SentinelRoutes.SETTINGS) }
                            )
                        }

                        composable(SentinelRoutes.NETWORKS) {
                            NetworksScreen(
                                viewModel = networksViewModel,
                                onInspectInLab = { network ->
                                    securityLabViewModel.prefillFromNetwork(network)
                                    navigateTopLevel(SentinelRoutes.SECURITY_LAB)
                                }
                            )
                        }

                        composable(SentinelRoutes.WORDLISTS) {
                            WordlistsScreen(
                                viewModel = wordlistsViewModel,
                                onOpenWordlist = { id ->
                                    navController.navigate(SentinelRoutes.wordlistDetail(id))
                                }
                            )
                        }

                        composable(
                            route = SentinelRoutes.WORDLIST_DETAIL,
                            arguments = listOf(navArgument("wordlistId") { type = NavType.LongType })
                        ) { entry ->
                            val wordlistId = entry.arguments?.getLong("wordlistId") ?: 0L
                            WordlistDetailScreen(
                                wordlistId = wordlistId,
                                viewModel = wordlistsViewModel,
                                maskByDefault = settings.maskPasswordsByDefault,
                                autoClearClipboard = settings.autoClearClipboard,
                                onShowSnackbar = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(SentinelRoutes.SECURITY_LAB) {
                            SecurityLabScreen(
                                viewModel = securityLabViewModel,
                                maskByDefault = settings.maskPasswordsByDefault,
                                onOpenAssessmentResult = { id ->
                                    navController.navigate(SentinelRoutes.secureResult(id))
                                }
                            )
                        }

                        composable(
                            route = SentinelRoutes.SECURE_RESULT,
                            arguments = listOf(navArgument("assessmentId") { type = NavType.StringType })
                        ) { entry ->
                            val assessmentId = entry.arguments?.getString("assessmentId").orEmpty()
                            SecureResultScreen(
                                assessmentId = assessmentId,
                                viewModel = securityLabViewModel,
                                autoClearClipboard = settings.autoClearClipboard,
                                onShowSnackbar = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(SentinelRoutes.PASSWORD_ANALYZER) {
                            PasswordAnalyzerScreen(
                                viewModel = securityLabViewModel,
                                maskByDefault = settings.maskPasswordsByDefault,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(SentinelRoutes.SETTINGS) {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onShowSnackbar = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                            )
                        }
                    }
                }
            }
        }
    }
}
