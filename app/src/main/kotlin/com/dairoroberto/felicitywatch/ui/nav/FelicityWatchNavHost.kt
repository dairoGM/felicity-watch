package com.dairoroberto.felicitywatch.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dairoroberto.felicitywatch.ui.alerts.AlertsHostScreen
import com.dairoroberto.felicitywatch.ui.calculator.BatteryAutonomyCalculatorScreen
import com.dairoroberto.felicitywatch.ui.dashboard.DashboardScreen
import com.dairoroberto.felicitywatch.ui.devices.DevicesScreen
import com.dairoroberto.felicitywatch.ui.more.MoreMenuItem
import com.dairoroberto.felicitywatch.ui.more.MoreScreen
import com.dairoroberto.felicitywatch.ui.onboarding.OnboardingScreen
import com.dairoroberto.felicitywatch.ui.report.ReportScreen
import com.dairoroberto.felicitywatch.ui.settings.SettingsScreen
import com.dairoroberto.felicitywatch.ui.theme.ThemeViewModel

/**
 * Barra inferior fija a 4 destinos de uso diario; todo lo demás (Alertas,
 * Calculadora, Ajustes, y lo que se agregue después) vive en "Más" como una
 * lista de filas — así el menú escala sin saturar la barra inferior (más de
 * 5 ítems ahí se ve apretado y deja de ser legible en pantallas angostas).
 */
private sealed class MainDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Dashboard : MainDestination("dashboard", "Panel", Icons.Default.SpaceDashboard)
    data object Devices : MainDestination("devices", "Equipos", Icons.Default.Memory)
    data object Report : MainDestination("report", "Reporte", Icons.AutoMirrored.Filled.ShowChart)
    data object More : MainDestination("more", "Más", Icons.Default.MoreHoriz)
}

private sealed class MoreDestination(val route: String) {
    data object Alerts : MoreDestination("alerts")
    data object Calculator : MoreDestination("calculator")
    data object Settings : MoreDestination("settings")
}

private val bottomDestinations = listOf(
    MainDestination.Dashboard,
    MainDestination.Devices,
    MainDestination.Report,
    MainDestination.More
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

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Solo pedimos confirmación de salida si el usuario intenta presionar atrás
    // desde la pestaña principal (Panel). Si está en otra pestaña, el botón atrás
    // simplemente lo regresará al Panel (comportamiento estándar).
    var showExitConfirm by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? android.app.Activity
    BackHandler(enabled = currentRoute == MainDestination.Dashboard.route) { showExitConfirm = true }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("¿Salir de Felicity Watch?") },
            text = { Text("El monitoreo en segundo plano sigue activo aunque cierres la app.") },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    activity?.finish()
                }) { Text("Salir") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Felicity Watch") }) },
        bottomBar = {
            NavigationBar {
                val moreRoutes = setOf(
                    MainDestination.More.route,
                    MoreDestination.Alerts.route,
                    MoreDestination.Calculator.route,
                    MoreDestination.Settings.route
                )
                bottomDestinations.forEach { destination ->
                    val isMoreTab = destination == MainDestination.More
                    val isCurrentlyInMoreTab = currentRoute in moreRoutes
                    NavigationBarItem(
                        selected = if (isMoreTab) isCurrentlyInMoreTab else currentRoute == destination.route,
                        onClick = {
                            // Cada ítem de la barra es una pestaña independiente:
                            // popUpTo(startDestination) descarta CUALQUIER resto
                            // de las otras ramas (incluida la de "Más" con sus
                            // subrutas acumuladas) antes de entrar, así nunca se
                            // apilan Dashboard→More→Alerts→More→Alerts... — sin
                            // esto, "atrás" desde Panel tenía que atravesar cada
                            // capa vieja en vez de ir directo a la anterior real.
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = !isMoreTab
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
            composable(MainDestination.Devices.route) { DevicesScreen() }
            composable(MainDestination.Report.route) { ReportScreen() }
            composable(MainDestination.More.route) {
                MoreScreen(
                    items = listOf(
                        MoreMenuItem(
                            label = "Alertas",
                            subtitle = "Reglas, eventos y notificaciones push",
                            icon = Icons.Default.DisplaySettings,
                            onClick = { navController.navigate(MoreDestination.Alerts.route) { launchSingleTop = true } }
                        ),
                        MoreMenuItem(
                            label = "Calculadora de autonomía",
                            subtitle = "Simula cuánto duraría la batería con otro consumo",
                            icon = Icons.Default.Calculate,
                            onClick = { navController.navigate(MoreDestination.Calculator.route) { launchSingleTop = true } }
                        ),
                        MoreMenuItem(
                            label = "Ajustes",
                            subtitle = "Cuenta, tema, polling y diagnóstico",
                            icon = Icons.Default.Settings,
                            onClick = { navController.navigate(MoreDestination.Settings.route) { launchSingleTop = true } }
                        )
                    )
                )
            }
            composable(MoreDestination.Alerts.route) { AlertsHostScreen() }
            composable(MoreDestination.Calculator.route) { BatteryAutonomyCalculatorScreen() }
            composable(MoreDestination.Settings.route) {
                SettingsScreen(
                    darkModeEnabled = darkModeEnabled,
                    onToggleDarkMode = themeViewModel::setDarkMode,
                    onLoggedOut = { rootViewModel.resetOnboarding() }
                )
            }
        }
    }
}
