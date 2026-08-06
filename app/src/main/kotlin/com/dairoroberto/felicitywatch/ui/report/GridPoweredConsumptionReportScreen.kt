package com.dairoroberto.felicitywatch.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
private enum class ConsumptionSource { GRID, BATTERY, BOTH }

/**
 * Reporte de consumo eléctrico, filtrable por fuente: con corriente de la
 * calle (red), sin corriente (abastecido por la batería durante cortes), o
 * ambos combinados — antes solo cubría "con red", sin forma de saber cuánto
 * se consumió durante los cortes. Es una pestaña más de Reporte, reutiliza
 * el mismo selector de periodo/fecha de las demás pestañas.
 */
@Composable
internal fun GridPoweredConsumptionCard(
    readings: List<PowerReadingEntity>,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
) {
    var granularity by remember { mutableStateOf(ConsumptionGranularity.DAY) }
    var source by remember { mutableStateOf(ConsumptionSource.BOTH) }

    Column {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = source == ConsumptionSource.BOTH,
                onClick = { source = ConsumptionSource.BOTH },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
            ) { Text("Todo") }
            SegmentedButton(
                selected = source == ConsumptionSource.GRID,
                onClick = { source = ConsumptionSource.GRID },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
            ) { Text("Con red") }
            SegmentedButton(
                selected = source == ConsumptionSource.BATTERY,
                onClick = { source = ConsumptionSource.BATTERY },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
            ) { Text("Sin red") }
        }

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

        ConsumptionReportCard(readings, granularity, source, colors, modifier = Modifier.padding(top = 14.dp))
    }
}

@Composable
private fun ConsumptionReportCard(
    readings: List<PowerReadingEntity>,
    granularity: ConsumptionGranularity,
    source: ConsumptionSource,
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
            val (title, subtitle) = when (source) {
                ConsumptionSource.GRID -> "CONSUMO CON CORRIENTE DE LA CALLE" to
                    "Solo la energía consumida mientras había corriente eléctrica de la red."
                ConsumptionSource.BATTERY -> "CONSUMO SIN CORRIENTE (BATERÍA)" to
                    "Solo la energía consumida durante cortes, abastecida por la batería."
                ConsumptionSource.BOTH -> "CONSUMO TOTAL" to
                    "Toda la energía consumida, venga de la red o de la batería durante cortes."
            }
            // Un color fijo por fuente en todo el reporte (barra principal +
            // resumen final), para que el usuario identifique de un vistazo
            // qué está viendo sin leer el título — antes el gráfico principal
            // usaba siempre el mismo color sin importar el filtro elegido.
            val gridColorAccent = colors.accent
            val batteryColorAccent = colors.chargeAccent
            val chartBarColor = when (source) {
                ConsumptionSource.GRID -> gridColorAccent
                ConsumptionSource.BATTERY -> batteryColorAccent
                ConsumptionSource.BOTH -> MaterialTheme.colorScheme.secondary
            }
            Text(title, style = MaterialTheme.typography.labelSmall, color = colors.textLow)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMid,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )

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

            val dailyKwh: Map<LocalDate, Double> = byDayAndSource.mapValues { (_, split) ->
                when (source) {
                    ConsumptionSource.GRID -> split.gridKwh
                    ConsumptionSource.BATTERY -> split.batteryKwh
                    ConsumptionSource.BOTH -> split.gridKwh + split.batteryKwh
                }
            }

            val grouped: Map<String, Double> = when (granularity) {
                ConsumptionGranularity.DAY -> dailyKwh.mapKeys { (date, _) ->
                    DateTimeFormatter.ofPattern("d MMM").withLocale(Locale("es", "ES")).format(date)
                }
                ConsumptionGranularity.MONTH -> dailyKwh.entries
                    .groupBy { YearMonth.from(it.key) }
                    .mapValues { (_, entries) -> entries.sumOf { it.value } }
                    .toSortedMap()
                    .mapKeys { (yearMonth, _) ->
                        DateTimeFormatter.ofPattern("MMM yyyy").withLocale(Locale("es", "ES")).format(yearMonth)
                    }
            }

            val total = grouped.values.sum()
            val average = total / grouped.size

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GenerationStatTile(
                    label = "Total del periodo",
                    value = String.format(Locale("es", "ES"), "%.1f", total),
                    unit = "kWh",
                    color = colors.accent,
                    modifier = Modifier.weight(1f)
                )
                GenerationStatTile(
                    label = if (granularity == ConsumptionGranularity.DAY) "Promedio diario" else "Promedio mensual",
                    value = String.format(Locale("es", "ES"), "%.1f", average),
                    unit = "kWh",
                    color = colors.textMid,
                    modifier = Modifier.weight(1f)
                )
            }

            val entries = grouped.map { (label, kwh) -> BarChartEntry(label = label, value = kwh.toFloat()) }
            DailyBarChart(
                entries = entries,
                barColor = chartBarColor,
                gridColor = colors.hairline,
                textColor = colors.textLow,
                valueFormatter = { "%.1f kWh".format(it) },
                modifier = Modifier.padding(top = 16.dp)
            )

            val labelStep = (entries.size / 6).coerceAtLeast(1)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                entries.forEachIndexed { index, entry ->
                    if (index % labelStep == 0) {
                        Text(entry.label, style = MaterialTheme.typography.labelSmall, color = colors.textLow)
                    }
                }
            }

            // Totalización con/sin red del periodo completo, independiente
            // del filtro elegido arriba — para que el usuario siempre pueda
            // comparar ambas fuentes de un vistazo sin cambiar de pestaña.
            val totalGridKwh = byDayAndSource.values.sumOf { it.gridKwh }
            val totalBatteryKwh = byDayAndSource.values.sumOf { it.batteryKwh }
            val combinedTotal = (totalGridKwh + totalBatteryKwh).coerceAtLeast(0.0001)

            HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 12.dp), color = colors.hairline)
            Text(
                "TOTAL DEL PERIODO POR FUENTE",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GenerationStatTile(
                    label = "Con red",
                    value = String.format(Locale("es", "ES"), "%.1f", totalGridKwh),
                    unit = "kWh",
                    color = gridColorAccent,
                    modifier = Modifier.weight(1f)
                )
                GenerationStatTile(
                    label = "Sin red (batería)",
                    value = String.format(Locale("es", "ES"), "%.1f", totalBatteryKwh),
                    unit = "kWh",
                    color = batteryColorAccent,
                    modifier = Modifier.weight(1f)
                )
            }
            // Barra apilada simple: proporción visual de cuánto del consumo
            // total vino de cada fuente. Mismos colores que arriba (teal =
            // red, morado = batería) para que la relación sea inmediata.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
            ) {
                Box(
                    Modifier
                        .weight((totalGridKwh / combinedTotal).toFloat().coerceIn(0.0001f, 1f))
                        .fillMaxHeight()
                        .background(gridColorAccent)
                )
                Box(
                    Modifier
                        .weight((totalBatteryKwh / combinedTotal).toFloat().coerceIn(0.0001f, 1f))
                        .fillMaxHeight()
                        .background(batteryColorAccent)
                )
            }
            Text(
                "Total combinado: ${String.format(Locale("es", "ES"), "%.1f", totalGridKwh + totalBatteryKwh)} kWh",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
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
