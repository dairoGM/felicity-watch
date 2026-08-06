package com.dairoroberto.felicitywatch.ui.devices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SolarPower
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.dairoroberto.felicitywatch.domain.model.PlantInfo
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors
import java.util.Locale

private val DEVICES_TABS = listOf("Planta", "Dispositivos")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(viewModel: DevicesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalFelicityColors.current
    var selectedTab by remember { mutableIntStateOf(0) }

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
            Column(Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = selectedTab, containerColor = colors.surface2) {
                    DEVICES_TABS.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedTab) {
                        0 -> items(state.plants, key = { it.plantId }) { plant ->
                            PlantDetailCard(plant, state.inverterReading)
                        }
                        1 -> items(state.devices, key = { it.serialNumber }) { device ->
                            DeviceRow(device, state.inverterReading, state.batteryReading)
                        }
                    }
                }
            }
        }
    }
}

private fun formatWatts(watts: Int): String =
    if (watts >= 1000) String.format(Locale("es", "ES"), "%.2f kW", watts / 1000.0) else "$watts W"

/**
 * Detalle de la planta, siempre visible (no requiere expandir — la
 * pestaña "Planta" ya ES el detalle). Tipo de planta y fecha de instalación
 * NO se muestran: no vienen en el endpoint de listado de dispositivos
 * (list_device_all_type) que consume esta app, solo en la tabla web de
 * Felicity, cuyo endpoint de detalle de planta no está identificado/
 * verificado todavía — decisión explícita de no mostrar filas vacías en
 * "—" para datos que no existen en ningún endpoint confirmado.
 */
@Composable
private fun PlantDetailCard(plant: PlantInfo, inverterReading: InverterReading?) {
    val colors = LocalFelicityColors.current
    val online = plant.devices.any { it.status != null }
    val pvPower = inverterReading?.pvPowerWatts

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (online) colors.green else colors.textLow)
                )
                Box(
                    Modifier
                        .padding(start = 10.dp)
                        .size(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(colors.tealDim),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.SolarPower, contentDescription = "Planta", tint = colors.accent, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(plant.plantName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${plant.devices.size} equipos" + (plant.countryName?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMid
                    )
                }
                Text(
                    pvPower?.let { formatWatts(it) } ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.accent
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = colors.hairline)

            DeviceDetailRow(label = "Propietario", value = plant.ownerName ?: "—")
            DeviceDetailRow(label = "País", value = plant.countryName ?: "—")
            DeviceDetailRow(
                label = "Potencia FV (en vivo)",
                value = pvPower?.let { formatWatts(it) } ?: "—",
                valueColor = colors.accent
            )
            DeviceDetailRow(
                label = "Capacidad instalada",
                value = plant.ratedPowerKw?.let { "${it.toInt()} kW" } ?: "—"
            )
        }
    }
}

@Composable
private fun DeviceRow(device: DeviceInfo, inverterReading: InverterReading?, batteryReading: BatteryReading?) {
    val colors = LocalFelicityColors.current
    val (icon, roleLabel) = when (device.role) {
        DeviceRole.INVERTER -> Icons.Default.ElectricBolt to "Inversor"
        DeviceRole.BATTERY -> Icons.Default.BatteryChargingFull to "Batería"
        DeviceRole.OTHER -> Icons.Default.DeviceUnknown to "Dispositivo"
    }
    val online = device.status != null
    val expandable = device.role == DeviceRole.INVERTER || device.role == DeviceRole.BATTERY
    var expanded by remember(device.serialNumber) { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = if (expandable) Modifier.fillMaxWidth().clickable { expanded = !expanded } else Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
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
                if (expandable) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Contraer" else "Ver detalle",
                        tint = colors.textLow
                    )
                }
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
                    Icon(icon, contentDescription = roleLabel, tint = colors.accent)
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
                        DeviceDetailRow(label = "Planta", value = device.plantName, valueColor = colors.accent)
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = colors.hairline)
                    when (device.role) {
                        DeviceRole.INVERTER -> {
                            val batteryWatts = batteryReading?.current?.let { current ->
                                batteryReading.voltage?.let { voltage -> (current * voltage).toInt() }
                            }
                            FlowDiagram(
                                pvWatts = inverterReading?.pvPowerWatts,
                                gridWatts = inverterReading?.gridPowerWatts,
                                loadWatts = inverterReading?.loadPowerWatts,
                                batteryWatts = batteryWatts,
                                socPercent = batteryReading?.socPercent
                            )
                            InverterEnergyGrid(
                                inverter = inverterReading,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                        DeviceRole.BATTERY -> BatteryDetailCard(battery = batteryReading)
                        DeviceRole.OTHER -> Unit
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
