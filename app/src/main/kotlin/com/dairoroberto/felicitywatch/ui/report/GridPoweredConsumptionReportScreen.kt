package com.dairoroberto.felicitywatch.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dairoroberto.felicitywatch.data.local.PowerReadingEntity
import com.dairoroberto.felicitywatch.ui.components.BarChartEntry
import com.dairoroberto.felicitywatch.ui.components.DailyBarChart
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class ConsumptionGranularity { DAY, MONTH }

/**
 * Reporte de consumo eléctrico rediseñado — muestra siempre las dos
 * columnas "Con Red" y "Sin Red" lado a lado, sin filtro de fuente
 * (antes había un segmentado Todo/Con red/Sin red que ocultaba una
 * fuente u otra). Incluye promedio de consumo diario por fuente.
 *
 * Los dos gráficos (consumo por periodo + perfil por hora) se muestran
 * como tarjetas separadas para evitar que el segundo quede enterrado
 * detrás de scroll excesivo.
 */
@Composable
internal fun GridPoweredConsumptionCard(
    readings: List<PowerReadingEntity>,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
) {
    var granularity by remember { mutableStateOf(ConsumptionGranularity.DAY) }

    Column {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            SegmentedButton(
                selected = granularity == ConsumptionGranularity.DAY,
                onClick = { granularity = ConsumptionGranularity.DAY },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("Por día") }
            SegmentedButton(
                selected = granularity == ConsumptionGranularity.MONTH,
                onClick = { granularity = ConsumptionGranularity.MONTH },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text("Por mes") }
        }

        ConsumptionReportCard(readings, granularity, colors, modifier = Modifier.padding(top = 14.dp))
        HourlyConsumptionCard(readings, colors, modifier = Modifier.padding(top = 14.dp))
    }
}

@Composable
private fun ConsumptionReportCard(
    readings: List<PowerReadingEntity>,
    granularity: ConsumptionGranularity,
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
            Text("CONSUMO POR DÍA", style = MaterialTheme.typography.labelSmall, color = colors.textLow)
            
            val zone = ZoneId.systemDefault()
            val byDayAndSource = computeConsumptionByDayAndSource(readings, zone)

            if (byDayAndSource.isEmpty()) {
                Text(
                    "No hay suficiente historial registrado en este periodo.\nEl historial se acumula localmente mientras la app monitorea.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid,
                    modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)
                )
                return@Column
            }

            val groupedGrid: Map<String, Double>
            val groupedBattery: Map<String, Double>

            when (granularity) {
                ConsumptionGranularity.DAY -> {
                    groupedGrid = byDayAndSource.mapKeys { (date, _) ->
                        DateTimeFormatter.ofPattern("d MMM").withLocale(Locale("es", "ES")).format(date)
                    }.mapValues { it.value.gridKwh }
                    
                    groupedBattery = byDayAndSource.mapKeys { (date, _) ->
                        DateTimeFormatter.ofPattern("d MMM").withLocale(Locale("es", "ES")).format(date)
                    }.mapValues { it.value.batteryKwh }
                }
                ConsumptionGranularity.MONTH -> {
                    val groupedByMonth = byDayAndSource.entries.groupBy { YearMonth.from(it.key) }
                    groupedGrid = groupedByMonth.toSortedMap().mapKeys { (yearMonth, _) ->
                        DateTimeFormatter.ofPattern("MMM yyyy").withLocale(Locale("es", "ES")).format(yearMonth)
                    }.mapValues { (_, entries) -> entries.sumOf { it.value.gridKwh } }
                    
                    groupedBattery = groupedByMonth.toSortedMap().mapKeys { (yearMonth, _) ->
                        DateTimeFormatter.ofPattern("MMM yyyy").withLocale(Locale("es", "ES")).format(yearMonth)
                    }.mapValues { (_, entries) -> entries.sumOf { it.value.batteryKwh } }
                }
            }

            val totalGrid = groupedGrid.values.sum()
            val averageGrid = totalGrid / groupedGrid.size.coerceAtLeast(1)
            
            val totalBattery = groupedBattery.values.sum()
            val averageBattery = totalBattery / groupedBattery.size.coerceAtLeast(1)

            val gridColorAccent = colors.accent
            val batteryColorAccent = colors.chargeAccent

            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Con Red", style = MaterialTheme.typography.labelMedium, color = gridColorAccent, fontWeight = FontWeight.Bold)
                    GenerationStatTile(
                        label = "Total",
                        value = String.format(Locale("es", "ES"), "%.1f", totalGrid),
                        unit = "kWh",
                        color = gridColorAccent,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    GenerationStatTile(
                        label = if (granularity == ConsumptionGranularity.DAY) "Promedio diario" else "Promedio mensual",
                        value = String.format(Locale("es", "ES"), "%.1f", averageGrid),
                        unit = "kWh",
                        color = colors.textMid,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    val entriesGrid = groupedGrid.map { (label, kwh) -> BarChartEntry(label = label, value = kwh.toFloat()) }
                    DailyBarChart(
                        entries = entriesGrid,
                        barColor = gridColorAccent,
                        gridColor = colors.hairline,
                        textColor = colors.textLow,
                        valueFormatter = { "%.1f kWh".format(it) },
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sin Red (Batería)", style = MaterialTheme.typography.labelMedium, color = batteryColorAccent, fontWeight = FontWeight.Bold)
                    GenerationStatTile(
                        label = "Total",
                        value = String.format(Locale("es", "ES"), "%.1f", totalBattery),
                        unit = "kWh",
                        color = batteryColorAccent,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    GenerationStatTile(
                        label = if (granularity == ConsumptionGranularity.DAY) "Promedio diario" else "Promedio mensual",
                        value = String.format(Locale("es", "ES"), "%.1f", averageBattery),
                        unit = "kWh",
                        color = colors.textMid,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    val entriesBattery = groupedBattery.map { (label, kwh) -> BarChartEntry(label = label, value = kwh.toFloat()) }
                    DailyBarChart(
                        entries = entriesBattery,
                        barColor = batteryColorAccent,
                        gridColor = colors.hairline,
                        textColor = colors.textLow,
                        valueFormatter = { "%.1f kWh".format(it) },
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }

            val entriesForLabels = groupedGrid.map { (label, kwh) -> BarChartEntry(label = label, value = kwh.toFloat()) }
            val labelStep = (entriesForLabels.size / 4).coerceAtLeast(1)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                entriesForLabels.forEachIndexed { index, entry ->
                    if (index % labelStep == 0) {
                        Text(entry.label, style = MaterialTheme.typography.labelSmall, color = colors.textLow)
                    }
                }
            }

            val combinedTotal = (totalGrid + totalBattery).coerceAtLeast(0.0001)
            HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 12.dp), color = colors.hairline)
            Text(
                "Proporción de consumo total combinado",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
            ) {
                Box(
                    Modifier
                        .weight((totalGrid / combinedTotal).toFloat().coerceIn(0.0001f, 1f))
                        .fillMaxHeight()
                        .background(gridColorAccent)
                )
                Box(
                    Modifier
                        .weight((totalBattery / combinedTotal).toFloat().coerceIn(0.0001f, 1f))
                        .fillMaxHeight()
                        .background(batteryColorAccent)
                )
            }
            Text(
                "Total combinado: ${String.format(Locale("es", "ES"), "%.1f", totalGrid + totalBattery)} kWh",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/**
 * Línea de tiempo de consumo por hora del día — responde directamente
 * "¿a qué hora consumo más?" con una barra por franja horaria (12:00am-01:00am,
 * 01:00am-02:00am, ...) y el valor exacto en kWh al tocarla. Las
 * etiquetas usan formato 12h (01:00pm en vez de 13:00) para
 * consistencia con el resto de la app.
 */
@Composable
private fun HourlyConsumptionCard(
    readings: List<PowerReadingEntity>,
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
            Text("PERFIL DE CONSUMO POR HORA", style = MaterialTheme.typography.labelSmall, color = colors.textLow)
            Text(
                "Toca una barra para ver el consumo exacto de esa hora.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMid,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )

            val zone = ZoneId.systemDefault()
            val byHour = computeConsumptionByHourOfDay(readings, zone)

            if (byHour.isEmpty()) {
                Text(
                    "No hay suficiente historial registrado en este periodo.\nEl historial se acumula localmente mientras la app monitorea.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid,
                    modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)
                )
                return@Column
            }

            val peakHour = byHour.maxByOrNull { it.value }
            if (peakHour != null) {
                val startAmpm = if (peakHour.key < 12) "am" else "pm"
                val startHour = if (peakHour.key % 12 == 0) 12 else peakHour.key % 12
                val nextHour = (peakHour.key + 1) % 24
                val endAmpm = if (nextHour < 12) "am" else "pm"
                val endHour = if (nextHour % 12 == 0) 12 else nextHour % 12
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GenerationStatTile(
                        label = "Hora de mayor consumo",
                        value = "%02d%s–%02d%s".format(startHour, startAmpm, endHour, endAmpm),
                        unit = "",
                        color = colors.accent,
                        modifier = Modifier.weight(1f)
                    )
                    GenerationStatTile(
                        label = "Consumo en esa hora",
                        value = String.format(Locale("es", "ES"), "%.1f", peakHour.value),
                        unit = "kWh",
                        color = colors.textMid,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            val entries = (0..23).map { hour ->
                val startAmpm = if (hour < 12) "am" else "pm"
                val startHour = if (hour % 12 == 0) 12 else hour % 12
                val nextHour = (hour + 1) % 24
                val endAmpm = if (nextHour < 12) "am" else "pm"
                val endHour = if (nextHour % 12 == 0) 12 else nextHour % 12
                
                BarChartEntry(label = "%02d%s–%02d%s".format(startHour, startAmpm, endHour, endAmpm), value = (byHour[hour] ?: 0.0).toFloat())
            }
            DailyBarChart(
                entries = entries,
                barColor = MaterialTheme.colorScheme.secondary,
                gridColor = colors.hairline,
                textColor = colors.textLow,
                valueFormatter = { "%.2f kWh".format(it) },
                modifier = Modifier.padding(top = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (hour in 0..23 step 4) {
                    val ampm = if (hour < 12) "am" else "pm"
                    val displayHour = if (hour % 12 == 0) 12 else hour % 12
                    Text(
                        "%02d:00%s".format(displayHour, ampm),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow
                    )
                }
            }
        }
    }
}

/**
 * Perfil de consumo por hora del día (0-23) — suma el consumo de cada
 * franja horaria a través de TODOS los días del periodo filtrado (ej. en
 * un rango de 7 días, la barra de "14:00-15:00" acumula el consumo de esa
 * hora en los 7 días), siguiendo el mismo patrón de perfil horario que
 * usan las distribuidoras eléctricas para identificar horas pico/valle —
 * más útil que solo mostrar un día suelto cuando el rango es más amplio.
 */
private fun computeConsumptionByHourOfDay(
    readings: List<PowerReadingEntity>,
    zone: ZoneId
): Map<Int, Double> {
    val sorted = readings
        .filter { it.loadEnergyTodayKwh != null }
        .sortedBy { it.timestampEpochMillis }

    val result = mutableMapOf<Int, Double>()
    for (i in 0 until sorted.size - 1) {
        val current = sorted[i]
        val next = sorted[i + 1]
        val delta = next.loadEnergyTodayKwh!! - current.loadEnergyTodayKwh!!
        // Delta negativo = cruzó medianoche y el contador del inversor se
        // reinició a 0 — se descarta el intervalo en vez de restar
        // energía inexistente (mismo criterio que computeConsumptionByDayAndSource).
        if (delta <= 0) continue
        val hour = Instant.ofEpochMilli(current.timestampEpochMillis).atZone(zone).hour
        result[hour] = (result[hour] ?: 0.0) + delta
    }
    return result
}

/** Consumo del día, separado por fuente (red vs batería) — se calculan
 * ambos siempre, independiente del filtro elegido en la UI, para que la
 * totalización de abajo pueda comparar los dos sin recalcular. */
private data class DailyConsumptionSplit(val gridKwh: Double, val batteryKwh: Double)

/**
 * Para cada intervalo entre dos lecturas consecutivas, atribuye el delta de
 * energía de carga (eLoadToday) al día, separado en gridKwh o batteryKwh
 * según gridPowerWatts en la lectura DE INICIO del intervalo (mismo criterio
 * que el resto de tramos de corriente en la app, ej. GridTimelineChart). Si
 * el delta es negativo (cruzó medianoche y el contador del inversor se
 * reinició a 0), se descarta ese intervalo en vez de restar energía
 * inexistente.
 */
private fun computeConsumptionByDayAndSource(
    readings: List<PowerReadingEntity>,
    zone: ZoneId
): Map<LocalDate, DailyConsumptionSplit> {
    val sorted = readings
        .filter { it.loadEnergyTodayKwh != null && it.gridPowerWatts != null }
        .sortedBy { it.timestampEpochMillis }

    val gridResult = mutableMapOf<LocalDate, Double>()
    val batteryResult = mutableMapOf<LocalDate, Double>()
    for (i in 0 until sorted.size - 1) {
        val current = sorted[i]
        val next = sorted[i + 1]
        val delta = next.loadEnergyTodayKwh!! - current.loadEnergyTodayKwh!!
        if (delta <= 0) continue
        val online = (current.gridPowerWatts ?: 0) >= 1
        val date = Instant.ofEpochMilli(current.timestampEpochMillis).atZone(zone).toLocalDate()
        if (online) {
            gridResult[date] = (gridResult[date] ?: 0.0) + delta
        } else {
            batteryResult[date] = (batteryResult[date] ?: 0.0) + delta
        }
    }

    val allDates = (gridResult.keys + batteryResult.keys).toSortedSet()
    return allDates.associateWith { date ->
        DailyConsumptionSplit(
            gridKwh = gridResult[date] ?: 0.0,
            batteryKwh = batteryResult[date] ?: 0.0
        )
    }
}
