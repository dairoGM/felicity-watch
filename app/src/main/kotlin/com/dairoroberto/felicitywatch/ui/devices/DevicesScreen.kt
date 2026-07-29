package com.dairoroberto.felicitywatch.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.domain.model.BatteryReading
import com.dairoroberto.felicitywatch.domain.model.DeviceInfo
import com.dairoroberto.felicitywatch.domain.model.DeviceRole
import com.dairoroberto.felicitywatch.domain.model.InverterReading
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors
import com.dairoroberto.felicitywatch.ui.theme.Teal
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(viewModel: DevicesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalFelicityColors.current

    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        val error = state.error
        if (error != null) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = colors.dangerBg), shape = RoundedCornerShape(12.dp)) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else if (state.devices.isEmpty() && !state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Todavía no se encontraron dispositivos.\nDesliza hacia abajo para reintentar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMid
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.devices, key = { it.serialNumber }) { device ->
                    DeviceCard(device, state.inverterReading, state.batteryReading)
                }
            }
        }
    }
}

private fun formatWatts(watts: Int): String =
    if (watts >= 1000) String.format(Locale("es", "ES"), "%.2f kW", watts / 1000.0) else "$watts W"

@Composable
private fun DeviceCard(device: DeviceInfo, inverterReading: InverterReading?, batteryReading: BatteryReading?) {
    val colors = LocalFelicityColors.current
    val (icon, roleLabel) = when (device.role) {
        DeviceRole.INVERTER -> Icons.Default.ElectricBolt to "Inversor"
        DeviceRole.BATTERY -> Icons.Default.BatteryChargingFull to "Batería"
        DeviceRole.OTHER -> Icons.Default.DeviceUnknown to "Dispositivo"
    }
    val online = device.status != null

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Cabecera: punto de estado + tipo + N/S, como en la app oficial
            // de Felicity — el punto verde ES el indicador de estado, sin
            // texto adicional que lo repita.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (online) colors.green else colors.textLow)
                )
                Text(
                    roleLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp)
                )
                Text(
                    device.serialNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid,
                    modifier = Modifier.padding(start = 10.dp).weight(1f)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = colors.hairline)

            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.tealDim),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = roleLabel, tint = Teal)
                }

                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    DeviceDetailRow(
                        label = "Alias del dispositivo",
                        value = device.alias?.takeIf { it.isNotBlank() } ?: "—"
                    )
                    when (device.role) {
                        DeviceRole.INVERTER -> DeviceDetailRow(
                            label = "Potencia FV",
                            value = inverterReading?.pvPowerWatts?.let { formatWatts(it) } ?: "—"
                        )
                        DeviceRole.BATTERY -> DeviceDetailRow(
                            label = "SOC de la batería",
                            value = batteryReading?.socPercent?.let { "$it %" } ?: "—"
                        )
                        DeviceRole.OTHER -> Unit
                    }
                    DeviceDetailRow(label = "Modelo del dispositivo", value = device.model ?: "—")
                    if (device.plantName != null) {
                        DeviceDetailRow(label = "Planta", value = device.plantName, valueColor = Teal)
                    }
                }
            }
        }
    }
}

/** Fila "etiqueta / valor" con la etiqueta en una columna angosta fija y el
 * valor a continuación — mismo patrón visual de la app oficial de Felicity
 * (Alias del dispositivo / Potencia FV / Modelo del dispositivo en fila). */
@Composable
private fun DeviceDetailRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color? = null) {
    val colors = LocalFelicityColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMid,
            modifier = Modifier.width(150.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}
