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
import androidx.compose.foundation.layout.size
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
import com.dairoroberto.felicitywatch.ui.components.ChartPoint
import com.dairoroberto.felicitywatch.ui.components.DailyBarChart
import com.dairoroberto.felicitywatch.ui.components.LineAreaChart
import com.dairoroberto.felicitywatch.ui.components.niceAxis
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class ConsumptionGranularity { DAY, MONTH }
private enum class ConsumptionView { PERIOD, HOURLY, HOURLY_LINE }

/**
 * Reporte de consumo eléctrico — dos vistas de igual jerarquía elegidas
 * con un selector superior ("Por periodo" / "Por hora"), en vez de dos
 * tarjetas apiladas donde la segunda quedaba enterrada tras scroll y
 * parecía menos importante. Cada vista ocupa el ancho completo.
 *
 * En "Por periodo" se muestran Con Red y Sin Red apiladas verticalmente
 * compartiendo la MISMA escala del eje Y, para que las alturas sean
 * comparables entre sí (antes cada gráfico normalizaba a su propio
 * máximo y 6.9 kWh se veía igual de alto que 9.2 kWh).
 */
@Composable
internal fun GridPoweredConsumptionCard(
    readings: List<PowerReadingEntity>,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
) {
    var view by remember { mutableStateOf(ConsumptionView.PERIOD) }
    var granularity by remember { mutableStateOf(ConsumptionGranularity.DAY) }

    Column {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            SegmentedButton(
                selected = view == ConsumptionView.PERIOD,
                onClick = { view = ConsumptionView.PERIOD },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
            ) { Text("Periodo") }
            SegmentedButton(
                selected = view == ConsumptionView.HOURLY,
                onClick = { view = ConsumptionView.HOURLY },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
            ) { Text("Por hora") }
            SegmentedButton(
                selected = view == ConsumptionView.HOURLY_LINE,
                onClick = { view = ConsumptionView.HOURLY_LINE },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
            ) { Text("Curva") }
        }

        when (view) {
            ConsumptionView.PERIOD -> {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
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
            }
            ConsumptionView.HOURLY -> {
                HourlyConsumptionCard(readings, colors, modifier = Modifier.padding(top = 14.dp))
            }
            ConsumptionView.HOURLY_LINE -> {
                HourlyConsumptionLineCard(readings, colors, modifier = Modifier.padding(top = 14.dp))
            }
        }
    }
}

/**
 * Curva de consumo por hora del día — misma agregación que la vista de
 * barras, pero con gráfica de línea: la forma continua hace evidente la
 * TENDENCIA (cómo sube y baja el consumo a lo largo del día) mejor que 24
 * barras sueltas, que se leen como valores independientes.
 */
@Composable
private fun HourlyConsumptionLineCard(
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
            Text("CURVA DE CONSUMO POR HORA", style = MaterialTheme.typography.labelSmall, color = colors.textLow)
            Text(
                "Cómo varía el consumo a lo largo del día. Toca un punto para ver el valor exacto.",
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

            // Un punto por cada hora (0-23), con 0 en las horas sin datos —
            // así la curva siempre cubre el día completo y el eje X es
            // comparable entre periodos distintos.
            val points = (0..23).map { hour ->
                ChartPoint(hour.toFloat(), (byHour[hour] ?: 0.0).toFloat())
            }
            val maxKwh = points.maxOf { it.y }.coerceAtLeast(0.1f)
            val yAxis = niceAxis(0f, maxKwh)

            val totalKwh = byHour.values.sum()
            val peakHour = byHour.maxByOrNull { it.value }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GenerationStatTile(
                    label = "Total del periodo",
                    value = String.format(Locale("es", "ES"), "%.1f", totalKwh),
                    unit = "kWh",
                    color = colors.accent,
                    modifier = Modifier.weight(1f)
                )
                GenerationStatTile(
                    label = "Promedio por hora",
                    value = String.format(Locale("es", "ES"), "%.2f", totalKwh / 24.0),
                    unit = "kWh",
                    color = colors.textMid,
                    modifier = Modifier.weight(1f)
                )
            }

            Box(modifier = Modifier.padding(top = 14.dp)) {
                LineAreaChart(
                    points = points,
                    lineColor = MaterialTheme.colorScheme.secondary,
                    gridColor = colors.hairline,
                    minY = 0f,
                    maxYOverride = yAxis.max,
                    minXOverride = 0f,
                    maxXOverride = 23f,
                    yAxis = yAxis,
                    yUnit = "kWh",
                    textColor = colors.textLow,
                    tooltipLabel = { point ->
                        val hour = point.x.toInt().coerceIn(0, 23)
                        val ampm = if (hour < 12) "am" else "pm"
                        val displayHour = if (hour % 12 == 0) 12 else hour % 12
                        val nextHour = (hour + 1) % 24
                        val nextAmpm = if (nextHour < 12) "am" else "pm"
                        val nextDisplay = if (nextHour % 12 == 0) 12 else nextHour % 12
                        String.format(Locale("es", "ES"), "%.2f kWh", point.y) to
                            "%02d%s–%02d%s".format(displayHour, ampm, nextDisplay, nextAmpm)
                    }
                )
            }

            // Eje X en formato 12h cada 4 horas, consistente con el resto.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (hour in 0..23 step 4) {
                    val ampm = if (hour < 12) "am" else "pm"
                    val displayHour = if (hour % 12 == 0) 12 else hour % 12
                    Text(
                        "%02d%s".format(displayHour, ampm),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow
                    )
                }
            }

            if (peakHour != null) {
                val ampm = if (peakHour.key < 12) "am" else "pm"
                val displayHour = if (peakHour.key % 12 == 0) 12 else peakHour.key % 12
                Text(
                    "Hora pico: %02d%s con %s kWh".format(
                        displayHour, ampm, String.format(Locale("es", "ES"), "%.2f", peakHour.value)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.green,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
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
            Text(
                if (granularity == ConsumptionGranularity.DAY) "CONSUMO POR DÍA" else "CONSUMO POR MES",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow
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

            val entriesGrid = groupedGrid.map { (label, kwh) -> BarChartEntry(label = label, value = kwh.toFloat()) }
            val entriesBattery = groupedBattery.map { (label, kwh) -> BarChartEntry(label = label, value = kwh.toFloat()) }
            // Escala común a ambos gráficos: sin esto cada uno normaliza a
            // su propio máximo y una barra de 6.9 kWh se ve igual de alta
            // que otra de 9.2 kWh, haciendo imposible comparar a simple
            // vista cuál fuente consumió más.
            val sharedMax = maxOf(
                entriesGrid.maxOfOrNull { it.value } ?: 0f,
                entriesBattery.maxOfOrNull { it.value } ?: 0f
            ).coerceAtLeast(0.01f)
            val averageLabel = if (granularity == ConsumptionGranularity.DAY) "Promedio diario" else "Promedio mensual"

            // Resumen numérico de ambas fuentes en una fila compacta arriba,
            // y luego cada gráfico a ancho completo apilado — así las barras
            // tienen espacio real en vez de quedar comprimidas en media
            // pantalla cada una.
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GenerationStatTile(
                    label = "Con Red · total",
                    value = String.format(Locale("es", "ES"), "%.1f", totalGrid),
                    unit = "kWh",
                    color = gridColorAccent,
                    modifier = Modifier.weight(1f)
                )
                GenerationStatTile(
                    label = "Sin Red · total",
                    value = String.format(Locale("es", "ES"), "%.1f", totalBattery),
                    unit = "kWh",
                    color = batteryColorAccent,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GenerationStatTile(
                    label = "Con Red · $averageLabel",
                    value = String.format(Locale("es", "ES"), "%.1f", averageGrid),
                    unit = "kWh",
                    color = colors.textMid,
                    modifier = Modifier.weight(1f)
                )
                GenerationStatTile(
                    label = "Sin Red · $averageLabel",
                    value = String.format(Locale("es", "ES"), "%.1f", averageBattery),
                    unit = "kWh",
                    color = colors.textMid,
                    modifier = Modifier.weight(1f)
                )
            }

            ConsumptionSourceChart(
                title = "Con Red",
                entries = entriesGrid,
                barColor = gridColorAccent,
                sharedMax = sharedMax,
                colors = colors
            )
            ConsumptionSourceChart(
                title = "Sin Red (Batería)",
                entries = entriesBattery,
                barColor = batteryColorAccent,
                sharedMax = sharedMax,
                colors = colors
            )

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
 * Un gráfico de consumo por fuente (Con Red o Sin Red), a ancho completo
 * con su título y etiquetas de eje X. [sharedMax] fuerza la misma escala
 * vertical en ambas fuentes para que sus alturas sean comparables.
 */
@Composable
private fun ConsumptionSourceChart(
    title: String,
    entries: List<BarChartEntry>,
    barColor: androidx.compose.ui.graphics.Color,
    sharedMax: Float,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Box(
                Modifier
                    .padding(end = 6.dp)
                    .size(9.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
            Text(title, style = MaterialTheme.typography.labelMedium, color = barColor, fontWeight = FontWeight.Bold)
        }
        DailyBarChart(
            entries = entries,
            barColor = barColor,
            gridColor = colors.hairline,
            textColor = colors.textLow,
            valueFormatter = { "%.1f kWh".format(it) },
            maxValueOverride = sharedMax,
            chartHeight = 150.dp,
            modifier = Modifier.padding(top = 8.dp)
        )
        val labelStep = (entries.size / 5).coerceAtLeast(1)
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
