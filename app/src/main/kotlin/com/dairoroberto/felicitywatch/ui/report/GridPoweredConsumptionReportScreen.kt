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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dairoroberto.felicitywatch.data.local.PowerReadingEntity
import com.dairoroberto.felicitywatch.domain.usecase.ConsumptionSplit
import com.dairoroberto.felicitywatch.ui.components.DayTrendChart
import com.dairoroberto.felicitywatch.ui.components.TrendPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.round
import kotlin.math.roundToInt

private enum class ConsumptionFilter { TOTAL, GRID, BATTERY }

private val HOUR_MILLIS = 60 * 60 * 1000L

/** Redondea a 2 decimales antes de comparar — el consumo se integra sumando
 * muchos intervalos pequeños, así que dos horas "iguales" a la vista (ej.
 * ambas "1.10 kWh") pueden diferir por un residuo de punto flotante
 * invisible en la cifra mostrada; comparar el valor crudo sin redondear
 * marcaba una tendencia ↑/↓ que no existía para el usuario. */
private fun round2(value: Double): Double = round(value * 100) / 100.0

/**
 * Reporte único de consumo eléctrico — una barra HORIZONTAL por cada hora
 * real del periodo elegido arriba (mismo patrón visual que la vista
 * mensual de Generación PV, que usa una fila por día), en vez de un
 * gráfico vertical: la etiqueta de cada hora queda siempre visible junto a
 * su barra, sin tener que hacer zoom para leerla.
 */
@Composable
internal fun GridPoweredConsumptionCard(
    readings: List<PowerReadingEntity>,
    dateRange: DateRange,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
) {
    var filter by remember { mutableStateOf(ConsumptionFilter.TOTAL) }
    val zone = ZoneId.systemDefault()

    val startOfDay = dateRange.start.atStartOfDay(zone).toInstant().toEpochMilli()
    val endOfDay = dateRange.end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    // Un slot por cada hora REAL del periodo elegido (no un perfil de 0-23
    // agregado): así "7 días" muestra las horas de esos 7 días tal cual
    // ocurrieron, y el listado se ajusta exactamente al filtro de arriba.
    val hourSlots = computeConsumptionByHourSlot(readings, startOfDay, endOfDay, zone)

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("CONSUMO POR HORA", style = MaterialTheme.typography.labelSmall, color = colors.textLow)

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                SegmentedButton(
                    selected = filter == ConsumptionFilter.TOTAL,
                    onClick = { filter = ConsumptionFilter.TOTAL },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                ) { Text("Total") }
                SegmentedButton(
                    selected = filter == ConsumptionFilter.GRID,
                    onClick = { filter = ConsumptionFilter.GRID },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                ) { Text("Con corriente") }
                SegmentedButton(
                    selected = filter == ConsumptionFilter.BATTERY,
                    onClick = { filter = ConsumptionFilter.BATTERY },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                ) { Text("Sin corriente") }
            }

            if (hourSlots.all { it.totalKwh == 0.0 }) {
                Text(
                    "No hay suficiente historial registrado en este periodo.\nEl historial se acumula localmente mientras la app monitorea.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid,
                    modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)
                )
                return@Column
            }

            val totalGrid = hourSlots.sumOf { it.split.gridKwh }
            val totalBattery = hourSlots.sumOf { it.split.batteryKwh }
            val totalOfPeriod = totalGrid + totalBattery
            val filteredTotal = when (filter) {
                ConsumptionFilter.TOTAL -> totalOfPeriod
                ConsumptionFilter.GRID -> totalGrid
                ConsumptionFilter.BATTERY -> totalBattery
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GenerationStatTile(
                    label = "Consumo total del periodo",
                    value = String.format(Locale("es", "ES"), "%.2f", totalOfPeriod),
                    unit = "kWh",
                    color = colors.accent,
                    modifier = Modifier.weight(1f)
                )
                GenerationStatTile(
                    label = when (filter) {
                        ConsumptionFilter.TOTAL -> "Total (filtro actual)"
                        ConsumptionFilter.GRID -> "Con corriente"
                        ConsumptionFilter.BATTERY -> "Sin corriente"
                    },
                    value = String.format(Locale("es", "ES"), "%.2f", filteredTotal),
                    unit = "kWh",
                    color = if (filter == ConsumptionFilter.BATTERY) colors.chargeAccent else colors.accent,
                    modifier = Modifier.weight(1f)
                )
            }

            if (filter == ConsumptionFilter.TOTAL) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(color = colors.accent, label = "Con corriente", textColor = colors.textMid)
                    LegendDot(
                        color = colors.chargeAccent,
                        label = "Sin corriente",
                        textColor = colors.textMid,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }

            // Evolución en VATIOS, la misma gráfica que el modal del card de
            // Consumo en el Panel. Complementa las barras de abajo en lugar de
            // repetirlas: aquellas son energía ACUMULADA por hora (kWh, "cuánto
            // gasté"), esta es la potencia INSTANTÁNEA a lo largo del día
            // ("cuándo se disparó el consumo"). Un pico corto de 3kW se ve aquí
            // como un pico, mientras que diluido en el kWh de su hora pasa
            // desapercibido.
            //
            // Solo con UN día seleccionado: el eje X es la hora del día (0-23),
            // así que con un rango de varios días los puntos de cada día se
            // superpondrían sobre las mismas horas, dibujando un garabato sin
            // significado. Para varios días el detalle por hora de abajo ya
            // responde la pregunta.
            if (dateRange.start == dateRange.end) {
                val trendPoints = remember(readings, dateRange) {
                    readings
                        .asSequence()
                        .filter { it.loadPowerWatts != null }
                        .map { reading ->
                            val time = Instant.ofEpochMilli(reading.timestampEpochMillis).atZone(zone)
                            TrendPoint(
                                hourOfDay = time.hour + time.minute / 60f + time.second / 3600f,
                                value = reading.loadPowerWatts!!.toFloat()
                            )
                        }
                        .sortedBy { it.hourOfDay }
                        .toList()
                }

                if (trendPoints.size >= 2) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = colors.hairline)

                    Text(
                        "EVOLUCIÓN DEL DÍA",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow
                    )
                    Text(
                        "Potencia instantánea en vatios. Toca o arrastra sobre la gráfica para ver el valor de cada momento.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                    )

                    DayTrendChart(
                        points = trendPoints,
                        lineColor = colors.accent,
                        gridColor = colors.hairline,
                        labelColor = colors.textMid,
                        tooltipBackground = colors.surface2,
                        surfaceColor = colors.surface2,
                        unit = "W",
                        modifier = Modifier.fillMaxWidth()
                    )

                    val values = trendPoints.map { it.value }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GenerationStatTile(
                            label = "Pico del día",
                            value = values.max().roundToInt().toString(),
                            unit = "W",
                            color = colors.accent,
                            modifier = Modifier.weight(1f)
                        )
                        GenerationStatTile(
                            label = "Promedio del día",
                            value = values.average().roundToInt().toString(),
                            unit = "W",
                            color = colors.accent,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = colors.hairline)

            Text(
                "DETALLE POR HORA",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow
            )
            Text(
                "La hora más reciente primero. Los iconos comparan cada franja con la anterior en el tiempo.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
            )

            fun valueFor(slot: HourSlot): Double = round2(
                when (filter) {
                    ConsumptionFilter.TOTAL -> slot.split.totalKwh
                    ConsumptionFilter.GRID -> slot.split.gridKwh
                    ConsumptionFilter.BATTERY -> slot.split.batteryKwh
                }
            )

            val maxValue = hourSlots.maxOf { valueFor(it) }.coerceAtLeast(0.01)

            // Se calcula la tendencia en orden cronológico (cada franja
            // contra la INMEDIATAMENTE anterior en el tiempo) y luego se
            // invierte solo para mostrar la más reciente primero.
            val rows = hourSlots.mapIndexed { index, slot ->
                val current = valueFor(slot)
                val previous = if (index == 0) null else valueFor(hourSlots[index - 1])
                val trend = when {
                    previous == null -> null
                    current > previous -> true
                    current < previous -> false
                    else -> null
                }
                slot to trend
            }

            Column(Modifier.padding(top = 8.dp)) {
                rows.reversed().forEach { (slot, trend) ->
                    HourlyConsumptionRow(
                        instant = Instant.ofEpochMilli(startOfDay + slot.x.toLong()),
                        gridKwh = slot.split.gridKwh,
                        batteryKwh = slot.split.batteryKwh,
                        filter = filter,
                        maxValue = maxValue,
                        trend = trend,
                        colors = colors
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(
    color: androidx.compose.ui.graphics.Color,
    label: String,
    textColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = textColor, modifier = Modifier.padding(start = 6.dp))
    }
}

/**
 * Fila de detalle por franja horaria: hora + barra HORIZONTAL apilada
 * (con/sin corriente) proporcional al máximo del periodo + icono de
 * tendencia contra la franja anterior. Se usan iconos de tendencia (▲▼ de
 * línea, no flechas rojas/verdes de "bien/mal"): más consumo no es
 * necesariamente negativo, así que el color semántico rojo/verde era
 * engañoso — el ícono ya comunica la dirección por su forma.
 */
@Composable
private fun HourlyConsumptionRow(
    instant: Instant,
    gridKwh: Double,
    batteryKwh: Double,
    filter: ConsumptionFilter,
    maxValue: Double,
    trend: Boolean?,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
) {
    val zone = ZoneId.systemDefault()
    val zoned = instant.atZone(zone)
    val dayFormatter = DateTimeFormatter.ofPattern("d MMM").withLocale(Locale("es", "ES"))
    val timeFormatter = DateTimeFormatter.ofPattern("hh:mm").withLocale(Locale("es", "ES"))
    val label = "${dayFormatter.format(zoned)} · ${format12Hour(timeFormatter, zoned)}"

    val gridValue = if (filter == ConsumptionFilter.BATTERY) 0.0 else gridKwh
    val batteryValue = if (filter == ConsumptionFilter.GRID) 0.0 else batteryKwh
    val total = gridValue + batteryValue

    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = colors.textHi,
                modifier = Modifier.weight(1f)
            )
            if (trend != null) {
                Icon(
                    if (trend) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                    contentDescription = if (trend) "Más que la hora anterior" else "Menos que la hora anterior",
                    tint = colors.textMid,
                    modifier = Modifier.size(16.dp).padding(end = 6.dp)
                )
            }
            Text(
                "%.2f kWh".format(total),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textHi
            )
        }

        // Barra horizontal apilada, proporcional al máximo del periodo —
        // mismo lenguaje visual que la lista mensual de Generación PV
        // (una fila por día, barra creciendo hacia la derecha).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(colors.hairline.copy(alpha = 0.4f))
        ) {
            val remaining = (maxValue - total).coerceAtLeast(0.0)
            if (gridValue > 0.0) {
                Box(Modifier.weight(gridValue.coerceAtLeast(maxValue * 0.01).toFloat()).fillMaxHeight().background(colors.accent))
            }
            if (batteryValue > 0.0) {
                Box(Modifier.weight(batteryValue.coerceAtLeast(maxValue * 0.01).toFloat()).fillMaxHeight().background(colors.chargeAccent))
            }
            if (remaining > 0.0) {
                Spacer(modifier = Modifier.weight(remaining.toFloat()))
            }
        }

        if (filter == ConsumptionFilter.TOTAL) {
            Text(
                "%.2f con corriente · %.2f sin corriente".format(gridKwh, batteryKwh),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** Una franja horaria: [x] es el epoch-millis relativo al inicio del
 * periodo (misma convención que [com.dairoroberto.felicitywatch.ui.components.ChartPoint]). */
private data class HourSlot(val x: Float, val split: ConsumptionSplit) {
    val totalKwh: Double get() = split.totalKwh
}

/**
 * Perfil de consumo por HORA REAL dentro de [startOfDay]..[endOfDay] — a
 * diferencia de una agregación por hora-del-día, cada slot corresponde a un
 * momento único del calendario, con su propio día. Slots sin lecturas se
 * completan en cero para que la lista cubra el periodo completo sin
 * huecos. Delega la atribución con/sin corriente en
 * [com.dairoroberto.felicitywatch.domain.usecase.ConsumptionSplitCalculator],
 * la misma que usa Factura y ahorro, para que el mismo rango de fechas
 * siempre dé el mismo número en ambas pantallas.
 */
private fun computeConsumptionByHourSlot(
    readings: List<PowerReadingEntity>,
    startOfDay: Long,
    endOfDay: Long,
    zone: ZoneId
): List<HourSlot> {
    val splitBySlot = com.dairoroberto.felicitywatch.domain.usecase.ConsumptionSplitCalculator
        .splitByHour(readings, zone)

    fun slotStart(epochMillis: Long): Long {
        val zoned = Instant.ofEpochMilli(epochMillis).atZone(zone)
        return zoned.withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()
    }

    val firstSlot = slotStart(startOfDay)
    val lastSlot = slotStart(endOfDay)
    val slots = generateSequence(firstSlot) { it + HOUR_MILLIS }.takeWhile { it <= lastSlot }.toList()

    return slots.map { slot ->
        HourSlot(
            x = (slot - startOfDay).toFloat(),
            split = splitBySlot[slot] ?: ConsumptionSplit(0.0, 0.0)
        )
    }
}
