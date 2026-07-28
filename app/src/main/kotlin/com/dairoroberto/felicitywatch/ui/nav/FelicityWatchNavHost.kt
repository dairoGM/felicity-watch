package com.dairoroberto.felicitywatch.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dairoroberto.felicitywatch.ui.alerts.AlertsScreen
import com.dairoroberto.felicitywatch.ui.dashboard.DashboardScreen
import com.dairoroberto.felicitywatch.ui.history.HistoryScreen
import com.dairoroberto.felicitywatch.ui.onboarding.OnboardingScreen
import com.dairoroberto.felicitywatch.ui.settings.SettingsScreen
import com.dairoroberto.felicitywatch.ui.theme.ThemeViewModel

private sealed class MainDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : MainDestination("dashboard", "Panel", Icons.Default.SpaceDashboard)
    data object Alerts : MainDestination("alerts", "Alertas", Icons.Default.NotificationsActive)
    data object History : MainDestination("history", "Historial", Icons.Default.History)
    data object Settings : MainDestination("settings", "Ajustes", Icons.Default.Settings)
}

private val bottomDestinations = listOf(
    MainDestination.Dashboard,
    MainDestination.Alerts,
    MainDestination.History,
    MainDestination.Settings
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FelicityWatchNavHost(
    rootViewModel: RootViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel()
) {
    val onboardingCompleted by rootViewModel.onboardingCompleted.collectAsState()

    if (!onboardingCompleted) {
        OnboardingScreen(onFinished = { rootViewModel.completeOnboarding() })
        return
    }

    val navController = rememberNavController()
    val darkModeEnabled by themeViewModel.darkModeEnabled.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Felicity Watch") }) },
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route

            NavigationBar {
                bottomDestinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = MainDestination.Dashboard.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(MainDestination.Dashboard.route) { DashboardScreen() }
            composable(MainDestination.Alerts.route) { AlertsScreen() }
            composable(MainDestination.History.route) { HistoryScreen() }
            composable(MainDestination.Settings.route) {
                SettingsScreen(
                    darkModeEnabled = darkModeEnabled,
                    onToggleDarkMode = themeViewModel::setDarkMode,
                    onLoggedOut = { rootViewModel.resetOnboarding() }
                )
            }
        }
    }
}
