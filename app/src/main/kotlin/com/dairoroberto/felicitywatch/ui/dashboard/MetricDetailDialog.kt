package com.dairoroberto.felicitywatch.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dairoroberto.felicitywatch.data.local.PowerReadingEntity
import com.dairoroberto.felicitywatch.domain.model.BatteryReading
import com.dairoroberto.felicitywatch.domain.model.InverterReading
import com.dairoroberto.felicitywatch.ui.components.DayTrendChart
import com.dairoroberto.felicitywatch.ui.components.TrendPoint
import com.dairoroberto.felicitywatch.ui.theme.JetBrainsMonoFamily
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/** Cual de las metricas del Panel se esta detallando. */
enum class MetricDetail(val title: String, val unit: String) {
    PV("Generación solar", "W"),
    BATTERY("Carga de batería", "%"),
    LOAD("Consumo de la casa", "W"),
    VOLTAGE("Voltaje", "V")
}

/**
 * Voltaje de la vía que alimentaba la casa en el momento de [reading], en
 * voltios AC — el mismo criterio que usa el card del Panel, aplicado al
 * historial.
 *
 * Cada lectura guarda AMBOS voltajes, no uno u otro: el equipo reporta la
 * entrada AC incluso sin corriente de la calle, dejando ahí un residuo de
 * fracciones de voltio. Así que la vía no se puede deducir de "cuál de los dos
 * viene"; se deduce del estado de red de esa lectura, con el mismo umbral que
 * [MonitoringStateHolder] y [GridStateDebouncer] usan en vivo (gridPower < 1W
 * = sin corriente).
 *
 * Devuelve null si el equipo no reportó el voltaje de la vía que corresponde:
 * un hueco en la serie es honesto, un cero inventaría un apagón.
 */
private fun voltageFor(reading: PowerReadingEntity): Double? {
    val online = (reading.gridPowerWatts ?: 0) >= 1
    val volts = if (online) reading.gridVoltage else reading.outputVoltage
    // El residuo de la vía inactiva no es una medición: por debajo de 1V no
    // hay nada que graficar (la casa alimentada nunca está a 0,6V).
    return volts?.takeIf { it >= 1.0 }
}

/**
 * Modal con la evolucion de una metrica a lo largo del dia de hoy.
 *
 * Se alimenta del historial local que el Panel ya tiene cargado
 * (allReadingsInRetention), filtrado al dia actual — no hace consultas nuevas
 * ni depende del endpoint de historial de Felicity.
 *
 * Para PV y Batería, agrega un segundo tab con el detalle que trae la ULTIMA
 * lectura en vivo del inversor ([liveInverter]/[liveBattery]) — replicando
 * las pantallas físicas "Solar" y "Batería" del propio equipo. Ese detalle no
 * se persiste en el historial (solo vive en memoria mientras la app corre),
 * así que no puede salir de [readings] como el resto del modal.
 */
@Composable
fun MetricDetailDialog(
    detail: MetricDetail,
    readings: List<PowerReadingEntity>,
    liveInverter: InverterReading? = null,
    liveBattery: BatteryReading? = null,
    onDismiss: () -> Unit
) {
    // Todas las métricas tienen un segundo tab con el detalle en vivo del
    // inversor, replicando sus pantallas físicas (Solar/Red/Carga/Batería).
    val hasInverterTab = when (detail) {
        MetricDetail.PV, MetricDetail.VOLTAGE, MetricDetail.LOAD -> liveInverter != null
        MetricDetail.BATTERY -> liveBattery != null
    }
    var selectedTab by remember(detail) { mutableIntStateOf(0) }
    val colors = LocalFelicityColors.current
    val zone = remember { ZoneId.systemDefault() }

    val accent = when (detail) {
        MetricDetail.PV -> colors.pvAccent
        MetricDetail.BATTERY -> colors.chargeAccent
        MetricDetail.LOAD -> colors.accent
        MetricDetail.VOLTAGE -> colors.green
    }
    val icon = when (detail) {
        MetricDetail.PV -> Icons.Default.WbSunny
        MetricDetail.BATTERY -> Icons.Default.BatteryChargingFull
        MetricDetail.LOAD -> Icons.Default.Bolt
        MetricDetail.VOLTAGE -> Icons.Default.Bolt
    }

    // Serie del dia de hoy. Se descartan las lecturas sin el campo que
    // interesa: un null no es un cero, y dibujarlo como cero inventaria una
    // caida a cero que no ocurrio.
    // Se calculan juntos porque salen de la misma serie, pero NO son lo mismo:
    // el gráfico usa la serie recortada y el encabezado el último valor real
    // del día (ver más abajo por qué difieren en PV de noche).
    val (points, latestValue) = remember(readings, detail) {
        val today = LocalDate.now(zone)
        val raw = readings
            .asSequence()
            .mapNotNull { reading ->
                val value = when (detail) {
                    MetricDetail.PV -> reading.pvPowerWatts
                    MetricDetail.BATTERY -> reading.socPercent
                    MetricDetail.LOAD -> reading.loadPowerWatts
                    // Se elige la fuente por el ESTADO DE RED de esa misma
                    // lectura, igual que el card del Panel — no con un elvis
                    // entre ambos campos.
                    //
                    // El inversor reporta los dos campos SIEMPRE, y sin
                    // corriente de la calle deja en la entrada AC un residuo
                    // de fracciones de voltio (0,5-0,6V medidos) en vez de un
                    // 0 limpio. Con `gridVoltage ?: outputVoltage` ese residuo
                    // ganaba — no es null, y tampoco lo descarta un filtro
                    // `> 0` — y el gráfico caía a ~0V mientras el card mostraba
                    // los 119,9V reales de la salida del inversor. Filtrar por
                    // un umbral de "voltaje plausible" sería frágil; el estado
                    // de red dice sin ambigüedad qué vía alimenta la casa.
                    MetricDetail.VOLTAGE -> voltageFor(reading)
                }?.toFloat() ?: return@mapNotNull null

                val time = Instant.ofEpochMilli(reading.timestampEpochMillis).atZone(zone)
                if (time.toLocalDate() != today) return@mapNotNull null

                TrendPoint(
                    hourOfDay = time.hour + time.minute / 60f + time.second / 3600f,
                    value = value
                )
            }
            .sortedBy { it.hourOfDay }
            .toList()

        // El valor del encabezado sale de la serie COMPLETA, antes del recorte
        // de abajo. Con el recorte, de noche el último punto de `series` es el
        // atardecer — la última vez que hubo generación — y el encabezado
        // anunciaba esos watts como si el sistema estuviera generando a las
        // 10pm. Sin recortar, el último punto es la lectura más reciente del
        // día, que de noche es 0W: lo correcto.
        val latest = raw.lastOrNull()?.value

        // Para PV se recorta la madrugada/noche sin generación (0W): de lo
        // contrario más de medio día es una línea plana en 0 sin nada que
        // mostrar, y el eje arranca a las 00:00 en vez de a la hora real en
        // que empieza a generar el sol.
        val series = if (detail == MetricDetail.PV) {
            val firstGenerating = raw.indexOfFirst { it.value > 0f }
            val lastGenerating = raw.indexOfLast { it.value > 0f }
            if (firstGenerating == -1) raw else raw.subList(firstGenerating, lastGenerating + 1)
        } else {
            raw
        }

        series to latest
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface2,
        // Sin elevacion tonal ni sombra: la elevacion por defecto del
        // AlertDialog tinta la superficie por encima del color del tema, y ese
        // tinte se mezclaba con el degradado del area de la grafica dejandola
        // turbia. Con 0 el fondo es exactamente surface2 y el degradado se ve
        // limpio.
        tonalElevation = 0.dp,
        title = {
            MetricDialogHeader(
                detail = detail,
                icon = icon,
                accent = accent,
                currentValue = latestValue
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                // El TabRow solo aparece para PV con datos de inversor: para
                // el resto de métricas el modal se ve exactamente igual que
                // antes, sin agregar un tab que no tendría contenido.
                if (hasInverterTab) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = accent,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Evolución", style = MaterialTheme.typography.labelSmall) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Inversor", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                if (hasInverterTab && selectedTab == 1) {
                    when (detail) {
                        MetricDetail.PV -> InverterDetailTab(inverter = liveInverter!!, colors = colors)
                        MetricDetail.VOLTAGE -> GridDetailTab(inverter = liveInverter!!, colors = colors)
                        MetricDetail.LOAD -> LoadDetailTab(inverter = liveInverter!!, colors = colors)
                        MetricDetail.BATTERY -> BatteryDetailTab(battery = liveBattery!!, colors = colors)
                    }
                    return@Column
                }

                if (points.size < 2) {
                    Text(
                        "Todavía no hay suficientes lecturas de hoy para dibujar la evolución. " +
                            "La gráfica se construye con el historial que la app va guardando " +
                            "en cada consulta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMid
                    )
                    return@Column
                }

                DayTrendChart(
                    points = points,
                    lineColor = accent,
                    gridColor = colors.hairline,
                    labelColor = colors.textMid,
                    tooltipBackground = colors.surface2,
                    surfaceColor = colors.surface2,
                    unit = detail.unit,
                    // El porcentaje se fija a 0..100: con escala automatica,
                    // una bateria que hoy solo llego a 60% dibujaria una curva
                    // que parece llena, y el usuario leeria mal el estado.
                    maxValueOverride = if (detail == MetricDetail.BATTERY) 100f else null,
                    valueFormatter = { value -> formatMetricValue(detail, value) }
                )

                MetricStatStrip(detail = detail, points = points, accent = accent)

                Text(
                    "Toca o arrastra sobre la gráfica para ver el valor de cada momento.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

/**
 * Tab "Inversor": el mismo detalle que muestra la pantalla física del
 * inversor bajo "Solar" — V/I/P por string de paneles, energía de hoy y
 * energía de por vida.
 *
 * A diferencia del tab de Evolución, esto NO es historial: es la ÚLTIMA
 * lectura en vivo, porque el detalle por string no se persiste (solo el
 * total combinado sí se guarda en el historial). Por eso no hay gráfica
 * aquí — solo el snapshot actual, igual que se ve al mirar la pantalla del
 * equipo en el momento.
 */
@Composable
private fun InverterDetailTab(
    inverter: InverterReading,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
) {
    Column(Modifier.fillMaxWidth()) {
        if (inverter.pvStrings.isEmpty()) {
            Text(
                "El inversor no reportó detalle por string en la última lectura.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMid
            )
        } else {
            Text(
                "SOLAR — POR STRING",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textLow
            )
            inverter.pvStrings.forEach { string ->
                PvStringRow(string = string, colors = colors)
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .padding(top = 4.dp)
                .background(colors.hairline)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            InverterStatColumn(
                label = "HOY",
                value = inverter.pvEnergyTodayKwh?.let { String.format(Locale("es", "ES"), "%.1f kWh", it) } ?: "—",
                colors = colors
            )
            InverterStatColumn(
                label = "TOTAL",
                value = inverter.pvEnergyTotalKwh?.let { String.format(Locale("es", "ES"), "%.1f kWh", it) } ?: "—",
                colors = colors
            )
            InverterStatColumn(
                label = "POTENCIA TOTAL",
                value = inverter.pvPowerWatts?.let { formatWattsCompact(it.toFloat()) + " W" } ?: "—",
                colors = colors,
                alignEnd = true
            )
        }

        inverter.deviceReportedAt?.let { reportedAt ->
            val formatter = remember {
                java.time.format.DateTimeFormatter.ofPattern("hh:mm").withLocale(Locale("es", "ES"))
            }
            val zoned = reportedAt.atZone(ZoneId.systemDefault())
            val suffix = if (zoned.hour < 12) "am" else "pm"
            Text(
                "Dato del inversor: ${formatter.format(zoned)}$suffix",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow,
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}

/**
 * Tab "Inversor" para Voltaje: replica la pantalla física del inversor bajo
 * "Red" — voltaje/potencia/CT por fase, frecuencia, y energía vendida a la
 * red (Vender). "Comprar" no se incluye: solo viene confirmado dentro de un
 * campo anidado que el mapper todavía no parsea.
 */
@Composable
private fun GridDetailTab(
    inverter: InverterReading,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
) {
    Column(Modifier.fillMaxWidth()) {
        if (inverter.gridPhases.isEmpty()) {
            Text(
                "El inversor no reportó detalle de red en la última lectura.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMid
            )
        } else {
            Text(
                "RED — POR FASE",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textLow
            )
            inverter.gridPhases.forEach { phase ->
                GridPhaseRow(phase = phase, colors = colors)
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .padding(top = 4.dp)
                .background(colors.hairline)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            InverterStatColumn(
                label = "VENDER HOY",
                value = inverter.gridFeedEnergyTodayKwh?.let { String.format(Locale("es", "ES"), "%.1f kWh", it) } ?: "—",
                colors = colors
            )
            InverterStatColumn(
                label = "VENDER TOTAL",
                value = inverter.gridFeedEnergyTotalKwh?.let { String.format(Locale("es", "ES"), "%.1f kWh", it) } ?: "—",
                colors = colors,
                alignEnd = true
            )
        }

        inverter.deviceReportedAt?.let { reportedAt ->
            val formatter = remember {
                java.time.format.DateTimeFormatter.ofPattern("hh:mm").withLocale(Locale("es", "ES"))
            }
            val zoned = reportedAt.atZone(ZoneId.systemDefault())
            val suffix = if (zoned.hour < 12) "am" else "pm"
            Text(
                "Dato del inversor: ${formatter.format(zoned)}$suffix",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow,
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}

/** Una fila del detalle de red: "L1  105.0V  P1  420W  F 60.1Hz  CT1 0W". */
@Composable
private fun GridPhaseRow(
    phase: com.dairoroberto.felicitywatch.domain.model.GridPhaseReading,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
) {
    val phaseLetter = when (phase.index) { 1 -> "L1"; 2 -> "L2"; else -> "L3" }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        PvStringValue(label = phaseLetter, value = phase.voltage?.let { "%.1f V".format(Locale("es", "ES"), it) }, colors = colors)
        PvStringValue(label = "P${phase.index}", value = phase.powerWatts?.let { formatWattsCompact(it.toFloat()) + "W" }, colors = colors)
        PvStringValue(label = "F", value = phase.frequencyHz?.let { "%.1f Hz".format(Locale("es", "ES"), it) }, colors = colors)
        PvStringValue(label = "CT${phase.index}", value = phase.ctPowerWatts?.let { "$it W" }, colors = colors)
    }
}

/**
 * Tab "Inversor" para Consumo: replica la pantalla física del inversor bajo
 * "Carga" — voltaje/potencia por fase hacia la carga de respaldo, y consumo
 * total (Hoy/Total).
 */
@Composable
private fun LoadDetailTab(
    inverter: InverterReading,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
) {
    Column(Modifier.fillMaxWidth()) {
        if (inverter.loadPhases.isEmpty()) {
            Text(
                "El inversor no reportó detalle de carga en la última lectura.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMid
            )
        } else {
            Text(
                "CARGA — POR FASE",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textLow
            )
            inverter.loadPhases.forEach { phase ->
                LoadPhaseRow(phase = phase, colors = colors)
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .padding(top = 4.dp)
                .background(colors.hairline)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            InverterStatColumn(
                label = "HOY",
                value = inverter.loadEnergyTodayKwh?.let { String.format(Locale("es", "ES"), "%.1f kWh", it) } ?: "—",
                colors = colors
            )
            InverterStatColumn(
                label = "TOTAL",
                value = inverter.loadEnergyTotalKwh?.let { String.format(Locale("es", "ES"), "%.1f kWh", it) } ?: "—",
                colors = colors,
                alignEnd = true
            )
        }

        inverter.deviceReportedAt?.let { reportedAt ->
            val formatter = remember {
                java.time.format.DateTimeFormatter.ofPattern("hh:mm").withLocale(Locale("es", "ES"))
            }
            val zoned = reportedAt.atZone(ZoneId.systemDefault())
            val suffix = if (zoned.hour < 12) "am" else "pm"
            Text(
                "Dato del inversor: ${formatter.format(zoned)}$suffix",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow,
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}

/** Una fila del detalle de carga: "L1  104.8V  1.2kW". */
@Composable
private fun LoadPhaseRow(
    phase: com.dairoroberto.felicitywatch.domain.model.LoadPhaseReading,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
) {
    val phaseLetter = when (phase.index) { 1 -> "L1"; 2 -> "L2"; else -> "L3" }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        PvStringValue(label = phaseLetter, value = phase.voltage?.let { "%.1f V".format(Locale("es", "ES"), it) }, colors = colors)
        PvStringValue(label = "P${phase.index}", value = phase.powerWatts?.let { formatWattsCompact(it.toFloat()) + "W" }, colors = colors)
    }
}

/** Una fila del detalle por string: "V1  33.2V   I1  0.0A   P1  0 W". */
@Composable
private fun PvStringRow(
    string: com.dairoroberto.felicitywatch.domain.model.PvStringReading,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        PvStringValue(label = "V${string.index}", value = string.voltage?.let { "%.1f V".format(Locale("es", "ES"), it) }, colors = colors)
        PvStringValue(label = "I${string.index}", value = string.currentAmps?.let { "%.1f A".format(Locale("es", "ES"), it) }, colors = colors)
        PvStringValue(label = "P${string.index}", value = string.powerWatts?.let { "$it W" }, colors = colors)
    }
}

@Composable
private fun PvStringValue(
    label: String,
    value: String?,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = colors.pvAccent
        )
        Text(
            value ?: "—",
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = colors.textHi,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun InverterStatColumn(
    label: String,
    value: String,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors,
    alignEnd: Boolean = false
) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textLow
        )
        Text(
            value,
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = colors.textHi,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

/**
 * Tab "Inversor" para Batería: replica la pantalla física del inversor bajo
 * "Batería" (BAT/U/Temp a la izquierda, Potencia/I/SOC a la derecha).
 *
 * Igual que [InverterDetailTab], es un snapshot en vivo, no historial: el
 * estado textual (Charge/Discharge/Idle) y la temperatura no se guardan en
 * la base de datos local, solo viven en la última lectura en memoria.
 */
@Composable
private fun BatteryDetailTab(
    battery: BatteryReading,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
) {
    // Mismo criterio que usa la pantalla física: el signo de la potencia
    // decide el estado textual, sin la lógica de "red siempre carga" que usa
    // el resto de la app para el indicador del Panel — aquí se busca replicar
    // el dato crudo del equipo tal cual se ve en su pantalla.
    val statusText = when {
        battery.powerWatts == null -> "—"
        battery.powerWatts > 1.0 -> "Charge"
        battery.powerWatts < -1.0 -> "Discharge"
        else -> "Idle"
    }
    val statusColor = when {
        battery.powerWatts == null -> colors.textHi
        battery.powerWatts > 1.0 -> colors.green
        battery.powerWatts < -1.0 -> MaterialTheme.colorScheme.error
        else -> colors.textMid
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            "BATERÍA",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textLow
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "BAT",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textLow
                )
                Text(
                    statusText,
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = statusColor,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            InverterStatColumn(
                label = "POTENCIA",
                value = battery.powerWatts?.let { "${it.roundToInt()} W" } ?: "—",
                colors = colors,
                alignEnd = true
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            InverterStatColumn(
                label = "U",
                value = battery.voltage?.let { String.format(Locale("es", "ES"), "%.1f V", it) } ?: "—",
                colors = colors
            )
            InverterStatColumn(
                label = "I",
                value = battery.current?.let { String.format(Locale("es", "ES"), "%.1f A", it) } ?: "—",
                colors = colors,
                alignEnd = true
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            InverterStatColumn(
                label = "TEMP",
                value = battery.temperatureCelsius?.let { String.format(Locale("es", "ES"), "%.1f°C", it) } ?: "—",
                colors = colors
            )
            InverterStatColumn(
                label = "SOC",
                value = battery.socPercent?.let { "$it%" } ?: "—",
                colors = colors,
                alignEnd = true
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .padding(top = 18.dp)
                .background(colors.hairline)
        )

        battery.deviceReportedAt?.let { reportedAt ->
            val formatter = remember {
                java.time.format.DateTimeFormatter.ofPattern("hh:mm").withLocale(Locale("es", "ES"))
            }
            val zoned = reportedAt.atZone(ZoneId.systemDefault())
            val suffix = if (zoned.hour < 12) "am" else "pm"
            Text(
                "Dato del inversor: ${formatter.format(zoned)}$suffix",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow,
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}

/**
 * Encabezado del modal: icono en pastilla del color de la serie, titulo, y el
 * valor actual grande a la derecha.
 *
 * El valor actual va en el encabezado y no entre las cifras de resumen porque
 * es el dato que el usuario ya venia mirando en el card del Panel: sirve de
 * puente entre lo que toco y lo que se abrio.
 */
@Composable
private fun MetricDialogHeader(
    detail: MetricDetail,
    icon: ImageVector,
    accent: Color,
    currentValue: Float?
) {
    val colors = LocalFelicityColors.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(19.dp)
            )
        }

        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(
                detail.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = colors.textHi,
                fontSize = 16.sp
            )
            Text(
                "HOY",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textLow
            )
        }

        currentValue?.let { value ->
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatMetricValue(detail, value),
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = accent
                )
                Text(
                    detail.unit,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = colors.textLow,
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp)
                )
            }
        }
    }
}

/**
 * Cifras que resumen el dia, en una tira con separadores.
 *
 * Cambian segun la metrica: en PV y consumo interesan el maximo y el
 * promedio; en bateria, el minimo y el maximo, que es lo que dice si llego a
 * un punto critico.
 */
@Composable
private fun MetricStatStrip(
    detail: MetricDetail,
    points: List<TrendPoint>,
    accent: Color
) {
    val colors = LocalFelicityColors.current
    val values = points.map { it.value }

    val format: (Float) -> String = { value -> formatMetricValue(detail, value) + " " + detail.unit }

    val stats: List<Pair<String, String>> = when (detail) {
        MetricDetail.BATTERY -> listOf(
            "MÍNIMO" to format(values.min()),
            "MÁXIMO" to format(values.max()),
            "LECTURAS" to points.size.toString()
        )
        // El voltaje se beneficia de las tres cifras (min/promedio/max), a
        // diferencia de PV/consumo donde el mínimo del día suele ser 0 y no
        // aporta nada: aqui interesa saber si hubo caidas fuertes.
        MetricDetail.VOLTAGE -> listOf(
            "MÍNIMO" to format(values.min()),
            "PROMEDIO" to format(values.average().toFloat()),
            "MÁXIMO" to format(values.max())
        )
        else -> listOf(
            "MÁXIMO" to format(values.max()),
            "PROMEDIO" to format(values.average().toFloat()),
            "LECTURAS" to points.size.toString()
        )
    }

    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        // Filete degradado del color de la serie: cierra la grafica y ata la
        // tira de cifras a lo que esta arriba.
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            accent.copy(alpha = 0.45f),
                            accent.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            stats.forEachIndexed { index, (label, value) ->
                if (index > 0) {
                    Box(
                        Modifier
                            .padding(horizontal = 12.dp)
                            .size(width = 1.dp, height = 26.dp)
                            .background(colors.hairline)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textLow
                    )
                    Text(
                        value,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = colors.textHi,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }
    }
}

/** Formatea un valor segun la unidad de la metrica, en un solo lugar para
 * que el encabezado, la grafica y la tira de cifras nunca se desincronicen. */
private fun formatMetricValue(detail: MetricDetail, value: Float): String = when (detail) {
    MetricDetail.BATTERY -> value.roundToInt().toString()
    MetricDetail.VOLTAGE -> String.format(java.util.Locale("es", "ES"), "%.1f", value)
    else -> formatWattsCompact(value)
}

/** 1450 -> "1.4k" para que la etiqueta del eje no desborde. */
private fun formatWattsCompact(watts: Float): String {
    val rounded = watts.roundToInt()
    return if (rounded >= 1000) {
        // Un decimal basta: "1.4k" comunica lo mismo que "1.45k" en un eje.
        val kilo = rounded / 100
        (kilo / 10).toString() + "." + (kilo % 10).toString() + "k"
    } else {
        rounded.toString()
    }
}
