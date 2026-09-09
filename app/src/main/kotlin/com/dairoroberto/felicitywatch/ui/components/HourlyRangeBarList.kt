package com.dairoroberto.felicitywatch.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Un resumen de hora: mínimo, promedio y máximo de la magnitud registrada
 * en esa hora del día. `null` si esa hora no tiene lecturas.
 */
data class HourlyRange(
    val hour: Int,
    val min: Float,
    val average: Float,
    val max: Float
)

/**
 * Lista de 24 filas (una por hora), con una barra de rango con degradado
 * entre mín/máx y una marca de promedio — pensada para leerse como una
 * tarjeta compacta por hora, no como una tabla de números.
 *
 * Diseño, y por qué:
 *
 * - CADA FILA es su propia "cápsula" redondeada con fondo tenue, en vez de
 *   líneas sueltas separadas por espacio en blanco — separa visualmente una
 *   hora de la siguiente sin necesitar divisores, y da al conjunto un aire
 *   de tarjetas apilables en vez de una tabla plana.
 * - La barra de rango usa un DEGRADADO horizontal (no un color plano):
 *   sugiere "de mínimo a máximo" como una transición continua, coherente
 *   con que el voltaje es un valor que fluctúa, no un estado discreto.
 * - El promedio se marca con un punto más grande y un halo suave detrás
 *   (dos círculos, no una línea), que se lee como "aquí" en vez de un
 *   simple marcador técnico.
 * - Barra flotante (no arranca en 0V) porque el voltaje oscila en una
 *   banda estrecha (ej. 110-125V): arrancar en cero haría que todas las
 *   horas se vieran con una barra casi llena idéntica.
 * - Las horas sin lecturas se muestran atenuadas con un guion, en vez de
 *   omitirse, para que se note de un vistazo en qué horas faltó monitoreo.
 *
 * El orden en que se listan las horas lo decide quien llama — para el uso
 * más habitual (mostrar desde la hora actual hacia atrás) se pasa la lista
 * ya en ese orden.
 */
@Composable
fun HourlyRangeBarList(
    entries: List<HourlyRange?>,
    barColor: Color,
    trackColor: Color,
    labelColor: Color,
    valueColor: Color,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 34.dp,
    valueFormatter: (Float) -> String = { "%.0f".format(it) },
    /** Si una hora registró algún valor por debajo de este umbral, la fila
     * se resalta con [lowColor] en vez del color normal de la serie — pensado
     * para el reporte de Voltaje, donde ver "qué hora tuvo voltaje bajo" es
     * más importante que el número exacto. `null` desactiva el resaltado. */
    lowThreshold: Float? = null,
    lowColor: Color = Color.Red
) {
    val known = entries.filterNotNull()
    if (known.isEmpty()) return

    // Rango global del eje: el mismo para todas las filas, así las barras
    // son comparables entre sí de un vistazo (una hora más inestable se ve
    // con una barra más ancha, no solo con números distintos).
    val axisMin = known.minOf { it.min }
    val axisMax = known.maxOf { it.max }
    val span = (axisMax - axisMin).coerceAtLeast(0.1f)
    val paddedMin = axisMin - span * 0.08f
    val paddedMax = axisMax + span * 0.08f
    val paddedSpan = (paddedMax - paddedMin).coerceAtLeast(0.1f)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entries.forEach { entry ->
            val isLow = lowThreshold != null && entry != null && entry.min < lowThreshold
            HourlyRangeRow(
                entry = entry,
                paddedMin = paddedMin,
                paddedSpan = paddedSpan,
                barColor = if (isLow) lowColor else barColor,
                trackColor = trackColor,
                labelColor = labelColor,
                valueColor = if (isLow) lowColor else valueColor,
                rowHeight = rowHeight,
                valueFormatter = valueFormatter,
                isLow = isLow
            )
        }
    }
}

@Composable
private fun HourlyRangeRow(
    entry: HourlyRange?,
    paddedMin: Float,
    paddedSpan: Float,
    barColor: Color,
    trackColor: Color,
    labelColor: Color,
    valueColor: Color,
    rowHeight: Dp,
    valueFormatter: (Float) -> String,
    isLow: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isLow -> barColor.copy(alpha = 0.14f)
                    entry != null -> barColor.copy(alpha = 0.06f)
                    else -> Color.Transparent
                }
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLow) {
            Icon(
                Icons.Default.WarningAmber,
                contentDescription = "Voltaje bajo",
                tint = barColor,
                modifier = Modifier.size(13.dp).padding(end = 3.dp)
            )
        }
        Text(
            "%02dh".format(entry?.hour ?: 0),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (entry != null) labelColor else labelColor.copy(alpha = 0.5f),
            modifier = Modifier.width(if (isLow) 22.dp else 30.dp)
        )

        if (entry == null) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                    drawLine(
                        color = trackColor,
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = 2f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3f, 6f))
                    )
                }
            }
            Text(
                "sin datos",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = labelColor.copy(alpha = 0.5f),
                modifier = Modifier.width(78.dp)
            )
        } else {
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp)
            ) {
                val centerY = size.height / 2f
                val minX = ((entry.min - paddedMin) / paddedSpan) * size.width
                val maxX = ((entry.max - paddedMin) / paddedSpan) * size.width
                val avgX = ((entry.average - paddedMin) / paddedSpan) * size.width
                val barHeight = 7f
                val barLeft = minX
                val barWidth = (maxX - barLeft).coerceAtLeast(3f)

                // Pista de fondo, sutil.
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(0f, centerY - 1.5f),
                    size = Size(size.width, 3f),
                    cornerRadius = CornerRadius(1.5f)
                )
                // Rango min-max con degradado: sugiere una transición
                // continua, no un valor plano.
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(barColor.copy(alpha = 0.55f), barColor),
                        startX = barLeft,
                        endX = barLeft + barWidth
                    ),
                    topLeft = Offset(barLeft, centerY - barHeight / 2f),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barHeight / 2f)
                )
                // Marca del promedio: halo + punto, más "vivo" que una
                // simple línea vertical.
                drawCircle(color = valueColor.copy(alpha = 0.22f), radius = 8f, center = Offset(avgX, centerY))
                drawCircle(color = valueColor, radius = 3.5f, center = Offset(avgX, centerY))
            }
            Text(
                "${valueFormatter(entry.min)}–${valueFormatter(entry.max)}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = valueColor,
                modifier = Modifier.width(78.dp)
            )
        }
    }
}
