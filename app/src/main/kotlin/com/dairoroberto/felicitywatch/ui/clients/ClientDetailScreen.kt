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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.data.local.AppPreferences
import com.dairoroberto.felicitywatch.ui.dashboard.DashboardContent
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors

/**
 * Detalle de UN cliente para la master — reusa DashboardContent (el mismo
 * cuerpo visual del Panel: hero de red, cards de PV/Batería/Consumo con
 * sparklines, anillo de Autonomía, Excedente Solar, y sus mismos modales de
 * detalle al tocar cada card) con un DashboardUiState reconstruido a partir
 * de las lecturas que ese cliente ya sube a Supabase con su propio
 * device_id — sin necesitar sus credenciales de FSolar (ver
 * ClientDetailViewModel).
 *
 * Sin pull-to-refresh (no hay una lectura "ahora mismo" que forzar en un
 * dispositivo remoto) ni fila de canales de aviso (son configuración de
 * ESTE teléfono, no del cliente).
 */
@Composable
fun ClientDetailScreen(
    deviceId: String,
    displayName: String?,
    onBack: () -> Unit,
    viewModel: ClientDetailViewModel = hiltViewModel()
) {
    val colors = LocalFelicityColors.current
    val state by viewModel.state.collectAsState()

    LaunchedEffect(deviceId) { viewModel.load(deviceId) }

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
}
