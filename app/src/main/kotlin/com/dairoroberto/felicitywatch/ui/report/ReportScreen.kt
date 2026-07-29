package com.dairoroberto.felicitywatch.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.ui.components.ChartPoint
import com.dairoroberto.felicitywatch.ui.components.LineAreaChart
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors
import com.dairoroberto.felicitywatch.ui.theme.Teal
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(viewModel: ReportViewModel = hiltViewModel()) {
    val dateRange by viewModel.dateRange.collectAsState()
    val readings by viewModel.readings.collectAsState()
    val colors = LocalFelicityColors.current

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    // Igual que en el Panel: "hace X" depende del reloj, no solo de los
    // datos — sin este tick, el texto de última lectura queda congelado.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            now = Instant.now()
        }
    }

    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale("es", "ES"))

    if (showStartPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dateRange.start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val newStart = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        viewModel.setCustomRange(newStart, dateRange.end)
                    }
                    showStartPicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = state) }
    }

    if (showEndPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dateRange.end.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val newEnd = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        viewModel.setCustomRange(dateRange.start, newEnd)
                    }
                    showEndPicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = state) }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface2),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("PERIODO", style = MaterialTheme.typography.labelSmall, color = colors.textLow)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = dateRange.start == dateRange.end && dateRange.start == LocalDate.now(),
                            onClick = { viewModel.setToday() },
                            label = { Text("Hoy") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.tealDim)
                        )
                        FilterChip(
                            selected = dateRange.start == dateRange.end && dateRange.start == LocalDate.now().minusDays(1),
                            onClick = { viewModel.setYesterday() },
                            label = { Text("Ayer") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.tealDim)
                        )
                        FilterChip(
                            selected = dateRange.start == LocalDate.now().minusDays(6) && dateRange.end == LocalDate.now(),
                            onClick = { viewModel.setLast7Days() },
                            label = { Text("7 días") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.tealDim)
                        )
                        FilterChip(
                            selected = dateRange.start == LocalDate.now().minusDays(29) && dateRange.end == LocalDate.now(),
                            onClick = { viewModel.setLast30Days() },
                            label = { Text("30 días") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.tealDim)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f)) {
                            Text(dateFormatter.format(dateRange.start))
                        }
                        Text("—", modifier = Modifier.padding(top = 12.dp))
                        OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.weight(1f)) {
                            Text(dateFormatter.format(dateRange.end))
                        }
                    }
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
                        "GENERACIÓN FOTOVOLTAICA",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow
                    )

                    // epoch millis no cabe con precisión en Float (13 dígitos vs
                    // ~7 significativos): se resta el mínimo antes de convertir
                    // para no perder resolución de tiempo entre puntos.
                    val filteredReadings = readings.filter { it.pvPowerWatts != null }
                    val minEpochMillis = filteredReadings.minOfOrNull { it.timestampEpochMillis } ?: 0L
                    val points = filteredReadings
                        .map { ChartPoint((it.timestampEpochMillis - minEpochMillis).toFloat(), it.pvPowerWatts!!.toFloat()) }

                    if (points.size < 2) {
                        Text(
                            "No hay suficiente historial registrado en este periodo.\nEl historial se acumula localmente mientras la app monitorea.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMid,
                            modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)
                        )
                    } else {
                        val maxYValue = points.maxOf { it.y }.coerceAtLeast(1f)
                        Box(modifier = Modifier.padding(top = 10.dp)) {
                            LineAreaChart(
                                points = points,
                                lineColor = Teal,
                                gridColor = colors.hairline,
                                maxYOverride = maxYValue
                            )
                            Text(
                                "${maxYValue.toInt()} W",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textLow,
                                modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                            )
                            Text(
                                "0 W",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textLow,
                                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                            )
                        }
                        val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale("es", "ES"))
                        val axisLabelCount = 5
                        val minTime = points.minOf { it.x }
                        val maxTime = points.maxOf { it.x }.coerceAtLeast(minTime + 1f)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (i in 0 until axisLabelCount) {
                                val fraction = i.toFloat() / (axisLabelCount - 1)
                                val epochMillis = minEpochMillis + (minTime + (maxTime - minTime) * fraction).toLong()
                                Text(
                                    timeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textLow
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Máximo: ${points.maxOf { it.y }.toInt()} W",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textLow
                            )
                            Text(
                                "Promedio: ${(points.sumOf { it.y.toDouble() } / points.size).toInt()} W",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textLow
                            )
                            Text(
                                "${points.size} lecturas",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textLow
                            )
                        }
                        val lastReadingMillis = filteredReadings.maxOf { it.timestampEpochMillis }
                        val lastReadingInstant = Instant.ofEpochMilli(lastReadingMillis)
                        val secondsAgo = Duration.between(lastReadingInstant, now).seconds.coerceAtLeast(0)
                        val lastReadingTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale("es", "ES"))
                        Text(
                            "Última lectura: ${lastReadingTimeFormatter.format(lastReadingInstant.atZone(ZoneId.systemDefault()))} (hace ${secondsAgo}s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textLow,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }
        }
    }
}
