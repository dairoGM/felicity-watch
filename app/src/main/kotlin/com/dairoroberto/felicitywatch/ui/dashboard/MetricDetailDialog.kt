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
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.dairoroberto.felicitywatch.ui.components.DayTrendChart
import com.dairoroberto.felicitywatch.ui.components.TrendPoint
import com.dairoroberto.felicitywatch.ui.theme.JetBrainsMonoFamily
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Cual de las tres metricas del Panel se esta detallando. */
enum class MetricDetail(val title: String, val unit: String) {
    PV("Generación solar", "W"),
    BATTERY("Carga de batería", "%"),
    LOAD("Consumo de la casa", "W")
}

/**
 * Modal con la evolucion de una metrica a lo largo del dia de hoy.
 *
 * Se alimenta del historial local que el Panel ya tiene cargado
 * (allReadingsInRetention), filtrado al dia actual — no hace consultas nuevas
 * ni depende del endpoint de historial de Felicity.
 */
@Composable
fun MetricDetailDialog(
    detail: MetricDetail,
    readings: List<PowerReadingEntity>,
    onDismiss: () -> Unit
) {
    val colors = LocalFelicityColors.current
    val zone = remember { ZoneId.systemDefault() }

    val accent = when (detail) {
        MetricDetail.PV -> colors.pvAccent
        MetricDetail.BATTERY -> colors.chargeAccent
        MetricDetail.LOAD -> colors.accent
    }
    val icon = when (detail) {
        MetricDetail.PV -> Icons.Default.WbSunny
        MetricDetail.BATTERY -> Icons.Default.BatteryChargingFull
        MetricDetail.LOAD -> Icons.Default.Bolt
    }

    // Serie del dia de hoy. Se descartan las lecturas sin el campo que
    // interesa: un null no es un cero, y dibujarlo como cero inventaria una
    // caida a cero que no ocurrio.
    val points = remember(readings, detail) {
        val today = LocalDate.now(zone)
        val raw = readings
            .asSequence()
            .mapNotNull { reading ->
                val value = when (detail) {
                    MetricDetail.PV -> reading.pvPowerWatts
                    MetricDetail.BATTERY -> reading.socPercent
                    MetricDetail.LOAD -> reading.loadPowerWatts
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

        // Para PV se recorta la madrugada/noche sin generación (0W): de lo
        // contrario más de medio día es una línea plana en 0 sin nada que
        // mostrar, y el eje arranca a las 00:00 en vez de a la hora real en
        // que empieza a generar el sol.
        if (detail == MetricDetail.PV) {
            val firstGenerating = raw.indexOfFirst { it.value > 0f }
            val lastGenerating = raw.indexOfLast { it.value > 0f }
            if (firstGenerating == -1) raw else raw.subList(firstGenerating, lastGenerating + 1)
        } else {
            raw
        }
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
                currentValue = points.lastOrNull()?.value
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
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
                    valueFormatter = { value ->
                        if (detail == MetricDetail.BATTERY) value.roundToInt().toString()
                        else formatWattsCompact(value)
                    }
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
                    if (detail == MetricDetail.BATTERY) value.roundToInt().toString()
                    else formatWattsCompact(value),
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

    val format: (Float) -> String = { value ->
        if (detail == MetricDetail.BATTERY) value.roundToInt().toString() + "%"
        else formatWattsCompact(value) + " W"
    }

    val stats: List<Pair<String, String>> = when (detail) {
        MetricDetail.BATTERY -> listOf(
            "MÍNIMO" to format(values.min()),
            "MÁXIMO" to format(values.max()),
            "LECTURAS" to points.size.toString()
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
