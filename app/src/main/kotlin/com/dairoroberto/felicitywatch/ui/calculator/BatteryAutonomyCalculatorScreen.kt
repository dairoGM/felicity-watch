package com.dairoroberto.felicitywatch.ui.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors
import java.util.Locale

private val CALCULATOR_TABS = listOf("Autonomía", "Proyección de SOC")

/**
 * Dos calculadoras relacionadas, cada una en su propia pestaña: "Autonomía"
 * (SOC+consumo → horas restantes) y "Proyección de SOC" (SOC+consumo+tiempo
 * → % futuro, la inversa). Antes vivían apiladas en la misma pantalla y el
 * campo de horas quedaba varias tarjetas más abajo, fuera de vista cuando
 * aparecía el teclado — separarlas en tabs además de imePadding() asegura
 * que el campo enfocado siempre esté visible.
 */
@Composable
fun BatteryAutonomyCalculatorScreen(viewModel: BatteryAutonomyCalculatorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalFelicityColors.current
    var selectedTab by remember { mutableIntStateOf(0) }

    var socInput by remember(state.liveSocPercent) { mutableStateOf(state.liveSocPercent?.toString() ?: "") }
    var loadInput by remember(state.liveLoadWatts) { mutableStateOf(state.liveLoadWatts?.toString() ?: "") }
    var hoursAheadInput by remember { mutableStateOf("") }

    val soc = socInput.toDoubleOrNull()?.coerceIn(0.0, 100.0)
    val loadWatts = loadInput.toDoubleOrNull()?.takeIf { it > 0 }
    val hoursAhead = hoursAheadInput.toDoubleOrNull()?.takeIf { it > 0 }

    val runtimeHours: Double? = if (soc != null && loadWatts != null && state.capacityAh != null && state.voltage != null) {
        val availableWh = state.capacityAh!! * state.voltage!! * (soc / 100.0)
        availableWh / loadWatts
    } else null

    // Inversa de la fórmula de autonomía: cuánto % se consume en X horas a
    // ese ritmo de consumo, restado del SOC de partida (nunca por debajo de 0).
    val projectedSoc: Double? = if (soc != null && loadWatts != null && hoursAhead != null &&
        state.capacityAh != null && state.voltage != null
    ) {
        val totalCapacityWh = state.capacityAh!! * state.voltage!!
        val consumedWh = loadWatts * hoursAhead
        val consumedSocPercent = (consumedWh / totalCapacityWh) * 100.0
        (soc - consumedSocPercent).coerceIn(0.0, 100.0)
    } else null

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab, containerColor = colors.surface2) {
            CALCULATOR_TABS.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (selectedTab) {
                0 -> autonomyTabContent(
                    colors = colors,
                    socInput = socInput,
                    onSocChange = { socInput = it },
                    loadInput = loadInput,
                    onLoadChange = { loadInput = it },
                    liveSocPercent = state.liveSocPercent,
                    liveLoadWatts = state.liveLoadWatts,
                    runtimeHours = runtimeHours,
                    capacityAh = state.capacityAh,
                    voltage = state.voltage
                )
                1 -> projectionTabContent(
                    colors = colors,
                    socInput = socInput,
                    onSocChange = { socInput = it },
                    loadInput = loadInput,
                    onLoadChange = { loadInput = it },
                    hoursAheadInput = hoursAheadInput,
                    onHoursAheadChange = { hoursAheadInput = it },
                    projectedSoc = projectedSoc
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.autonomyTabContent(
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors,
    socInput: String,
    onSocChange: (String) -> Unit,
    loadInput: String,
    onLoadChange: (String) -> Unit,
    liveSocPercent: Int?,
    liveLoadWatts: Int?,
    runtimeHours: Double?,
    capacityAh: Double?,
    voltage: Double?
) {
    item {
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface2),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "CALCULADORA DE AUTONOMÍA",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow
                )
                Text(
                    "Simula cuánto tiempo duraría la batería con un nivel de carga y consumo distintos a los actuales.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                )

                OutlinedTextField(
                    value = socInput,
                    onValueChange = onSocChange,
                    label = { Text("Carga de la batería (%)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        focusedLabelColor = colors.accent
                    )
                )
                OutlinedTextField(
                    value = loadInput,
                    onValueChange = onLoadChange,
                    label = { Text("Consumo estimado (W)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        focusedLabelColor = colors.accent
                    )
                )

                Text(
                    "Valores actuales: SOC ${liveSocPercent ?: "—"}%, consumo ${liveLoadWatts ?: "—"} W (se rellenan solos, edítalos para simular otro escenario).",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    item {
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface2),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "AUTONOMÍA ESTIMADA",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow
                )
                if (runtimeHours != null) {
                    val hours = runtimeHours.toLong()
                    val minutes = ((runtimeHours - hours) * 60).toLong()
                    val runtimeText = if (hours > 0) "${hours}h ${minutes}min" else "${minutes}min"
                    val runtimeColor = when {
                        runtimeHours > 5 -> colors.green
                        runtimeHours > 2 -> MaterialTheme.colorScheme.secondary
                        else -> colors.error
                    }
                    Text(
                        runtimeText,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium,
                        color = runtimeColor,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Capacidad: ${capacityAh?.toInt() ?: "—"} Ah",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textLow
                        )
                        Text(
                            "Voltaje: ${voltage?.let { String.format(Locale("es", "ES"), "%.1f V", it) } ?: "—"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textLow
                        )
                    }
                } else {
                    Text(
                        if (capacityAh == null || voltage == null) {
                            "Todavía no se ha leído la capacidad/voltaje real de la batería. Abre el Panel una vez para obtenerlos."
                        } else {
                            "Ingresa un % de carga y un consumo válidos para calcular."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMid,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.projectionTabContent(
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors,
    socInput: String,
    onSocChange: (String) -> Unit,
    loadInput: String,
    onLoadChange: (String) -> Unit,
    hoursAheadInput: String,
    onHoursAheadChange: (String) -> Unit,
    projectedSoc: Double?
) {
    item {
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface2),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "PROYECCIÓN DE BATERÍA",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow
                )
                Text(
                    "Dado un % de carga y consumo, ¿en qué % quedaría la batería tras un tiempo determinado?",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                )

                OutlinedTextField(
                    value = socInput,
                    onValueChange = onSocChange,
                    label = { Text("Carga de la batería (%)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        focusedLabelColor = colors.accent
                    )
                )
                OutlinedTextField(
                    value = loadInput,
                    onValueChange = onLoadChange,
                    label = { Text("Consumo estimado (W)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        focusedLabelColor = colors.accent
                    )
                )
                OutlinedTextField(
                    value = hoursAheadInput,
                    onValueChange = onHoursAheadChange,
                    label = { Text("Tiempo transcurrido (horas)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        focusedLabelColor = colors.accent
                    )
                )
            }
        }
    }

    item {
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface2),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "SOC PROYECTADO",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow
                )
                if (projectedSoc != null) {
                    val projectedColor = when {
                        projectedSoc <= 0.0 -> colors.error
                        projectedSoc > 50.0 -> colors.green
                        projectedSoc > 20.0 -> MaterialTheme.colorScheme.secondary
                        else -> colors.error
                    }
                    Text(
                        "${projectedSoc.toInt()} %",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium,
                        color = projectedColor,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        if (projectedSoc <= 0.0) {
                            "La batería se agotaría antes de cumplirse ese tiempo."
                        } else {
                            "Carga estimada dentro de ${hoursAheadInput}h, manteniendo un consumo de $loadInput W."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Text(
                        "Ingresa % de carga, consumo y tiempo en horas para ver la proyección.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMid,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }
    }
}
