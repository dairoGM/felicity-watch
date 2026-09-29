package com.dairoroberto.felicitywatch.ui.clients

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.data.local.AppPreferences
import com.dairoroberto.felicitywatch.ui.dashboard.DashboardContent
import com.dairoroberto.felicitywatch.ui.devices.DevicesContent
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors

private val CLIENT_DETAIL_TABS = listOf("Panel", "Equipos")

/**
 * Detalle de UN cliente para la master, con las mismas dos vistas que la
 * propia app del cliente: "Panel" (DashboardContent — hero de red, cards de
 * PV/Batería/Consumo con sparklines, anillo de Autonomía, Excedente Solar,
 * y sus mismos modales de detalle) y "Equipos" (DevicesContent — inversor/
 * batería con alias, modelo, planta, diagrama de flujo). Ambas se ven
 * exactamente como en la app del cliente, reconstruidas desde lo que ya
 * sincroniza a Supabase (lecturas de potencia + snapshot de equipos), sin
 * necesitar sus credenciales de FSolar.
 */
@Composable
fun ClientDetailScreen(
    deviceId: String,
    displayName: String?,
    onBack: () -> Unit,
    dashboardViewModel: ClientDetailViewModel = hiltViewModel(),
    equipmentViewModel: ClientEquipmentViewModel = hiltViewModel()
) {
    val colors = LocalFelicityColors.current
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(deviceId) {
        dashboardViewModel.load(deviceId)
        equipmentViewModel.load(deviceId)
    }

    // Sin Scaffold/TopAppBar propio: esta pantalla vive bajo el NavHost
    // general (Más > Clientes > detalle), que ya trae su propia TopAppBar
    // fija "Felicity Watch" — un segundo Scaffold aquí duplicaría la barra
    // superior, mismo patrón que el resto de pantallas de "Más"
    // (SettingsScreen, BillingScreen, etc. tampoco traen la suya). El botón
    // "Volver" queda como una fila de cabecera dentro del contenido.
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(
                displayName ?: "Dispositivo ${deviceId.take(8)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textHi
            )
        }

        TabRow(selectedTabIndex = selectedTab, containerColor = colors.surface2) {
            CLIENT_DETAIL_TABS.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        when (selectedTab) {
            0 -> ClientPanelTab(dashboardViewModel)
            1 -> ClientEquipmentTab(equipmentViewModel)
        }
    }
}

@Composable
private fun ClientPanelTab(viewModel: ClientDetailViewModel) {
    val colors = LocalFelicityColors.current
    val state by viewModel.state.collectAsState()

    when (val current = state) {
        is ClientDetailState.Loading -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }

        is ClientDetailState.Error -> Box(
            Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No se pudo consultar: ${current.message}",
                color = colors.textMid,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        is ClientDetailState.NoData -> Box(
            Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Este dispositivo todavía no reportó datos. Debe tener la " +
                    "\"Sincronización en la nube\" activada en su propio Ajustes > Sistema.",
                color = colors.textMid,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        is ClientDetailState.Loaded -> DashboardContent(
            state = current.uiState,
            isRefreshing = false,
            onRefresh = {},
            pollingIntervalSeconds = AppPreferences.DEFAULT_POLLING_INTERVAL_SECONDS,
            lowVoltageThreshold = AppPreferences.DEFAULT_LOW_VOLTAGE_THRESHOLD,
            trialDaysRemaining = null,
            showPullToRefresh = false,
            showChannelsRow = false
        )
    }
}

@Composable
private fun ClientEquipmentTab(viewModel: ClientEquipmentViewModel) {
    val state by viewModel.state.collectAsState()
    Box(Modifier.fillMaxSize()) {
        DevicesContent(state)
    }
}
