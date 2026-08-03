package com.dairoroberto.felicitywatch.ui.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

/**
 * Misma fórmula que la tarjeta de autonomía del Panel (capacidad Ah ×
 * voltaje × SOC%/100 / consumo W), pero con SOC y consumo editables a mano
 * para simular escenarios ("¿cuánto aguantaría con 500W de consumo si la
 * batería estuviera al 50%?") en vez de depender de la lectura en vivo.
 */
@Composable
fun BatteryAutonomyCalculatorScreen(viewModel: BatteryAutonomyCalculatorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalFelicityColors.current

    var socInput by remember(state.liveSocPercent) { mutableStateOf(state.liveSocPercent?.toString() ?: "") }
    var loadInput by remember(state.liveLoadWatts) { mutableStateOf(state.liveLoadWatts?.toString() ?: "") }

    val soc = socInput.toDoubleOrNull()?.coerceIn(0.0, 100.0)
    val loadWatts = loadInput.toDoubleOrNull()?.takeIf { it > 0 }

    val runtimeHours: Double? = if (soc != null && loadWatts != null && state.capacityAh != null && state.voltage != null) {
        val availableWh = state.capacityAh!! * state.voltage!! * (soc / 100.0)
        availableWh / loadWatts
    } else null

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        onValueChange = { socInput = it },
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
                        onValueChange = { loadInput = it },
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
                        "Valores actuales: SOC ${state.liveSocPercent ?: "—"}%, consumo ${state.liveLoadWatts ?: "—"} W (se rellenan solos, edítalos para simular otro escenario).",
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
                                "Capacidad: ${state.capacityAh?.toInt() ?: "—"} Ah",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textLow
                            )
                            Text(
                                "Voltaje: ${state.voltage?.let { String.format(Locale("es", "ES"), "%.1f V", it) } ?: "—"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textLow
                            )
                        }
                    } else {
                        Text(
                            if (state.capacityAh == null || state.voltage == null) {
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
}
