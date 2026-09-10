package com.dairoroberto.felicitywatch.ui.dashboard

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Bolt
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dairoroberto.felicitywatch.ui.components.GridSegment
import com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * Modal con la línea de tiempo de HOY: una barra horizontal de 00 a 23
 * coloreada en verde (con corriente) / rojo (sin corriente), para ver de un
 * vistazo en qué horas hubo cortes sin tener que leer el historial hora por
 * hora. Reutiliza los mismos [GridSegment] que ya calcula el card "Estado
 * de la red" y el Reporte (pestaña Corriente), así que nunca puede
 * discrepar de lo que esas vistas muestran.
 */
@Composable
fun GridDayTimelineDialog(
    segments: List<GridSegment>,
    onDismiss: () -> Unit
) {
    val colors = LocalFelicityColors.current
    val zone = remember { ZoneId.systemDefault() }

    val todaySegments = remember(segments) {
        clipSegmentsToToday(segments, zone)
    }

    val onlineSeconds = todaySegments.filter { it.online }.sumOf { (it.endEpochMillis - it.startEpochMillis) / 1000 }
    val offlineSeconds = todaySegments.filter { !it.online }.sumOf { (it.endEpochMillis - it.startEpochMillis) / 1000 }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface2,
        tonalElevation = 0.dp,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = colors.accent, modifier = Modifier.size(19.dp))
                }
                Column(Modifier.padding(start = 10.dp)) {
                    Text(
                        "Corriente de hoy",
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
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (todaySegments.isEmpty()) {
                    Text(
                        "Todavía no hay suficiente historial de hoy para dibujar la línea de tiempo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMid
                    )
                    return@Column
                }

                GridDayTimelineBar(segments = todaySegments, colors = colors)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    (0..23 step 2).forEach { hour ->
                        Text(
                            "%02d".format(hour),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = colors.textLow
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    LegendDot(color = colors.green, label = "Con corriente", colors = colors)
                    LegendDot(color = MaterialTheme.colorScheme.error, label = "Sin corriente", colors = colors, modifier = Modifier.padding(start = 16.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Con corriente: ${formatDuration(onlineSeconds)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMid
                    )
                    Text(
                        "Sin corriente: ${formatDuration(offlineSeconds)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMid
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
private fun GridDayTimelineBar(segments: List<GridSegment>, colors: FelicitySemanticColors) {
    val zone = remember { ZoneId.systemDefault() }
    val dayStart = remember(zone) { LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli() }
    val dayLengthMillis = Duration.ofDays(1).toMillis().toFloat()
    val green = colors.green
    val red = MaterialTheme.colorScheme.error
    val track = colors.hairline

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
    ) {
        drawRoundRect(
            color = track,
            cornerRadius = CornerRadius(6f, 6f)
        )
        segments.forEach { segment ->
            val startFraction = ((segment.startEpochMillis - dayStart) / dayLengthMillis).coerceIn(0f, 1f)
            val endFraction = ((segment.endEpochMillis - dayStart) / dayLengthMillis).coerceIn(0f, 1f)
            if (endFraction <= startFraction) return@forEach
            val left = startFraction * size.width
            val right = endFraction * size.width
            drawRect(
                color = if (segment.online) green else red,
                topLeft = Offset(left, 0f),
                size = Size(right - left, size.height)
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String, colors: FelicitySemanticColors, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textMid, modifier = Modifier.padding(start = 6.dp))
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}min" else "${minutes}min"
}

/**
 * Recorta los tramos al día de HOY (00:00-24:00 hora local), partiendo el
 * tramo que cruza medianoche en dos: la parte de ayer se descarta y la de
 * hoy queda desde las 00:00. Sin este recorte, un corte que empezó ayer se
 * dibujaría completo desde su hora de ayer, desbordando la barra del día.
 */
private fun clipSegmentsToToday(segments: List<GridSegment>, zone: ZoneId): List<GridSegment> {
    val today = LocalDate.now(zone)
    val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    return segments.mapNotNull { segment ->
        val clippedStart = segment.startEpochMillis.coerceAtLeast(dayStart)
        val clippedEnd = segment.endEpochMillis.coerceAtMost(dayEnd)
        if (clippedEnd <= clippedStart) null else segment.copy(startEpochMillis = clippedStart, endEpochMillis = clippedEnd)
    }
}
