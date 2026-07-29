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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
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
    var showPeriodMenu by remember { mutableStateOf(false) }
    var showCustomPickers by remember { mutableStateOf(false) }

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
            val isToday = dateRange.start == dateRange.end && dateRange.start == LocalDate.now()
            val isYesterday = dateRange.start == dateRange.end && dateRange.start == LocalDate.now().minusDays(1)
            val isLast7 = dateRange.start == LocalDate.now().minusDays(6) && dateRange.end == LocalDate.now()
            val isLast30 = dateRange.start == LocalDate.now().minusDays(29) && dateRange.end == LocalDate.now()
            val periodLabel = when {
                isToday -> "Hoy"
                isYesterday -> "Ayer"
                isLast7 -> "7 días"
                isLast30 -> "30 días"
                else -> "Personalizado"
            }
            val rangeLabel = if (dateRange.start == dateRange.end) {
                dateFormatter.format(dateRange.start)
            } else {
                "${dateFormatter.format(dateRange.start)} — ${dateFormatter.format(dateRange.end)}"
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface2),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("PERIODO", style = MaterialTheme.typography.labelSmall, color = colors.textLow)

                        Box {
                            OutlinedButton(onClick = { showPeriodMenu = true }) {
                                Text(periodLabel)
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
                            }
                            DropdownMenu(expanded = showPeriodMenu, onDismissRequest = { showPeriodMenu = false }) {
                                DropdownMenuItem(text = { Text("Hoy") }, onClick = {
                                    viewModel.setToday(); showPeriodMenu = false
                                })
                                DropdownMenuItem(text = { Text("Ayer") }, onClick = {
                                    viewModel.setYesterday(); showPeriodMenu = false
                                })
                                DropdownMenuItem(text = { Text("7 días") }, onClick = {
                                    viewModel.setLast7Days(); showPeriodMenu = false
                                })
                                DropdownMenuItem(text = { Text("30 días") }, onClick = {
                                    viewModel.setLast30Days(); showPeriodMenu = false
                                })
                                DropdownMenuItem(text = { Text("Personalizado") }, onClick = {
                                    showPeriodMenu = false
                                    showCustomPickers = true
                                })
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.shiftRange(forward = false) }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Periodo anterior")
                        }
                        Text(
                            rangeLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(onClick = { viewModel.shiftRange(forward = true) }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Periodo siguiente")
                        }
                    }

                    if (showCustomPickers) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
                        val tooltipTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale("es", "ES"))
                        Box(modifier = Modifier.padding(top = 10.dp)) {
                            LineAreaChart(
                                points = points,
                                lineColor = Teal,
                                gridColor = colors.hairline,
                                maxYOverride = maxYValue,
                                tooltipLabel = { point ->
                                    val watts = point.y.toInt()
                                    val valueText = if (watts >= 1000) {
                                        String.format(Locale("es", "ES"), "PV: %.2f kW", watts / 1000.0)
                                    } else {
                                        "PV: $watts W"
                                    }
                                    val time = tooltipTimeFormatter.format(
                                        Instant.ofEpochMilli(minEpochMillis + point.x.toLong()).atZone(ZoneId.systemDefault())
                                    )
                                    valueText to time
                                }
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
                                val targetX = minTime + (maxTime - minTime) * fraction
                                val nearestPoint = points.minBy { kotlin.math.abs(it.x - targetX) }
                                val epochMillis = minEpochMillis + nearestPoint.x.toLong()
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        timeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textLow
                                    )
                                    Text(
                                        "${nearestPoint.y.toInt()} W",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textMid
                                    )
                                }
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
