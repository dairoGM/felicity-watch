package com.dairoroberto.felicitywatch.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dairoroberto.felicitywatch.data.local.PowerReadingEntity
import com.dairoroberto.felicitywatch.domain.usecase.HourlyConsumption
import com.dairoroberto.felicitywatch.domain.usecase.WindowConsumption
import com.dairoroberto.felicitywatch.domain.usecase.computeHourlyConsumption
import com.dairoroberto.felicitywatch.domain.usecase.groupIntoWindows
import com.dairoroberto.felicitywatch.domain.usecase.predictByWeekday
import com.dairoroberto.felicitywatch.ui.components.HorizontalBarEntry
import com.dairoroberto.felicitywatch.ui.components.HorizontalBarList
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Reporte de consumo por FRANJA HORARIA configurable (por defecto la noche,
 * 10pm-8am) — dos objetivos, en dos secciones:
 * 1) Ver el consumo de cada ventana (noche) del periodo filtrado arriba,
 *    con detalle expandible hora por hora.
 * 2) Predecir el consumo de un día a partir del promedio histórico para
 *    ESE día de la semana (ej. "los lunes se consume en promedio X kWh"),
 *    calculado sobre TODO el historial disponible (no solo el periodo
 *    filtrado arriba, que puede ser muy corto para promediar).
 */
@Composable
internal fun NightConsumptionReportCard(
    readings: List<PowerReadingEntity>,
    allReadingsInRetention: List<PowerReadingEntity>,
    startHour: Int,
    endHour: Int,
    onStartHourChange: (Int) -> Unit,
    onEndHourChange: (Int) -> Unit,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
) {
    val zone = ZoneId.systemDefault()

    Column {
        WindowConfigCard(
            startHour = startHour,
            endHour = endHour,
            onStartHourChange = onStartHourChange,
            onEndHourChange = onEndHourChange,
            colors = colors
        )

        val windowsInPeriod = remember(readings, startHour, endHour) {
            groupIntoWindows(computeHourlyConsumption(readings, zone), startHour, endHour, zone)
        }

        WindowListCard(
            windows = windowsInPeriod,
            startHour = startHour,
            endHour = endHour,
            colors = colors,
            modifier = Modifier.padding(top = 14.dp)
        )

        val windowsInHistory = remember(allReadingsInRetention, startHour, endHour) {
            groupIntoWindows(computeHourlyConsumption(allReadingsInRetention, zone), startHour, endHour, zone)
        }

        PredictionCard(
            windows = windowsInHistory,
            colors = colors,
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}

@Composable
private fun WindowConfigCard(
    startHour: Int,
    endHour: Int,
    onStartHourChange: (Int) -> Unit,
    onEndHourChange: (Int) -> Unit,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NightsStay, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                Text(
                    "FRANJA HORARIA",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.textHi,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            Text(
                "Por defecto, la noche (10pm-8am). Ajusta las horas para analizar cualquier otro periodo del día.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow,
                modifier = Modifier.padding(top = 2.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HourStepper(label = "Desde", hour = startHour, onChange = onStartHourChange, colors = colors)
                HourStepper(label = "Hasta", hour = endHour, onChange = onEndHourChange, colors = colors)
            }
        }
    }
}

@Composable
private fun HourStepper(
    label: String,
    hour: Int,
    onChange: (Int) -> Unit,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textLow)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            IconButton(onClick = { onChange((hour + 23) % 24) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Hora anterior", modifier = Modifier.size(20.dp))
            }
            Text(
                hourLabel(hour),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textHi,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            IconButton(onClick = { onChange((hour + 1) % 24) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Hora siguiente", modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun hourLabel(hour: Int): String {
    val ampm = if (hour < 12) "am" else "pm"
    val display = if (hour % 12 == 0) 12 else hour % 12
    return "%d%s".format(display, ampm)
}

@Composable
private fun WindowListCard(
    windows: List<WindowConsumption>,
    startHour: Int,
    endHour: Int,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "CONSUMO POR NOCHE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = colors.textHi
            )
            Text(
                "De ${hourLabel(startHour)} a ${hourLabel(endHour)}. Toca una noche para ver el detalle por hora.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            if (windows.isEmpty()) {
                Text(
                    "No hay suficiente historial registrado en esta franja para el periodo elegido arriba.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid,
                    modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)
                )
                return@Column
            }

            val maxTotal = windows.maxOf { it.totalKwh }.coerceAtLeast(0.01)
            windows.forEachIndexed { index, window ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = colors.hairline)
                WindowRow(window = window, maxTotal = maxTotal, colors = colors)
            }
        }
    }
}

@Composable
private fun WindowRow(
    window: WindowConsumption,
    maxTotal: Double,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
) {
    var expanded by remember(window.startDate) { mutableStateOf(false) }
    val dateFormatter = DateTimeFormatter.ofPattern("EEE d MMM").withLocale(Locale("es", "ES"))

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    dateFormatter.format(window.startDate).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.textHi
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, end = 12.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.hairline.copy(alpha = 0.4f))
                ) {
                    val remaining = (maxTotal - window.totalKwh).coerceAtLeast(0.0)
                    if (window.gridKwh > 0.0) {
                        Box(Modifier.weight(window.gridKwh.coerceAtLeast(maxTotal * 0.01).toFloat()).fillMaxHeight().background(colors.accent))
                    }
                    if (window.batteryKwh > 0.0) {
                        Box(Modifier.weight(window.batteryKwh.coerceAtLeast(maxTotal * 0.01).toFloat()).fillMaxHeight().background(colors.chargeAccent))
                    }
                    if (remaining > 0.0) {
                        Spacer(modifier = Modifier.weight(remaining.toFloat()))
                    }
                }
                Text(
                    "%.2f con corriente · %.2f sin corriente".format(window.gridKwh, window.batteryKwh),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "%.2f kWh".format(window.totalKwh),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textHi
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Ocultar detalle" else "Ver detalle por hora",
                    tint = colors.textLow
                )
            }
        }

        if (expanded) {
            Column(Modifier.padding(top = 10.dp)) {
                window.hours.forEach { hour ->
                    HourDetailRow(hour, colors)
                }
            }
        }
    }
}

@Composable
private fun HourDetailRow(hour: HourlyConsumption, colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors) {
    val zone = ZoneId.systemDefault()
    val zoned = Instant.ofEpochMilli(hour.hourStartEpochMillis).atZone(zone)
    val timeFormatter = DateTimeFormatter.ofPattern("hh:mm").withLocale(Locale("es", "ES"))

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            format12Hour(timeFormatter, zoned),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMid
        )
        Text(
            "%.2f kWh (%.2f con corriente · %.2f sin corriente)".format(hour.totalKwh, hour.gridKwh, hour.batteryKwh),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textLow
        )
    }
}

private val WEEKDAY_ORDER = listOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
)

@Composable
private fun PredictionCard(
    windows: List<WindowConsumption>,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors,
    modifier: Modifier = Modifier
) {
    val predictions = remember(windows) { predictByWeekday(windows) }
    var selectedDay by remember { mutableStateOf(LocalDate.now().plusDays(1).dayOfWeek) }

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "PREDICCIÓN POR DÍA DE LA SEMANA",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = colors.textHi
            )
            Text(
                "Promedio histórico de esta franja para cada día de la semana, con todo el historial disponible (no solo el periodo elegido arriba).",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            if (predictions.isEmpty()) {
                Text(
                    "Todavía no hay suficiente historial para calcular un promedio por día de la semana.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid
                )
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WEEKDAY_ORDER.forEach { day ->
                    val shortLabel = day.getDisplayName(TextStyle.SHORT, Locale("es", "ES")).take(2).replaceFirstChar { it.uppercase() }
                    FilterChip(
                        selected = selectedDay == day,
                        onClick = { selectedDay = day },
                        label = { Text(shortLabel) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.tealDim),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            val selectedPrediction = predictions[selectedDay]
            val fullDayName = selectedDay.getDisplayName(TextStyle.FULL, Locale("es", "ES")).replaceFirstChar { it.uppercase() }
            if (selectedPrediction != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.accent.copy(alpha = 0.10f))
                        .padding(14.dp)
                ) {
                    Text(
                        "Predicción para el próximo $fullDayName",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMid
                    )
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            "%.2f".format(selectedPrediction.averageKwh),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                        Text(
                            " kWh",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textLow,
                            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                        )
                    }
                    Text(
                        "Basado en el promedio de ${selectedPrediction.sampleCount} ${if (selectedPrediction.sampleCount == 1) "noche registrada" else "noches registradas"} en $fullDayName.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                Text(
                    "Todavía no hay ninguna noche registrada en $fullDayName para poder predecir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = colors.hairline)

            Text(
                "Promedio por día de la semana",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val barEntries = WEEKDAY_ORDER.map { day ->
                val label = day.getDisplayName(TextStyle.SHORT, Locale("es", "ES")).replaceFirstChar { it.uppercase() }
                HorizontalBarEntry(
                    label = label,
                    value = (predictions[day]?.averageKwh ?: 0.0).toFloat(),
                    highlighted = day == selectedDay
                )
            }
            HorizontalBarList(
                entries = barEntries,
                barColor = colors.accent,
                trackColor = colors.hairline.copy(alpha = 0.4f),
                labelColor = colors.textMid,
                valueColor = colors.textHi,
                labelWidth = 34.dp,
                valueFormatter = { "%.2f kWh".format(it) }
            )
        }
    }
}
