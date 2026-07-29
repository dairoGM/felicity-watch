package com.dairoroberto.felicitywatch.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

data class ChartPoint(val x: Float, val y: Float)

/**
 * Gráfico de área/línea liviano dibujado a mano con Canvas (sin librería de
 * terceros): línea suave con relleno degradado bajo la curva y líneas de
 * cuadrícula punteadas, look moderno consistente con el resto de la app.
 */
@Composable
fun LineAreaChart(
    points: List<ChartPoint>,
    lineColor: Color,
    gridColor: Color,
    modifier: Modifier = Modifier,
    minY: Float = 0f,
    maxYOverride: Float? = null
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        if (points.size < 2) return@Canvas

        val maxY = (maxYOverride ?: points.maxOf { it.y }).coerceAtLeast(1f)
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }.coerceAtLeast(minX + 1f)

        fun xToPx(x: Float) = (x - minX) / (maxX - minX) * size.width
        fun yToPx(y: Float) = size.height - ((y - minY) / (maxY - minY) * size.height)

        // Líneas de cuadrícula horizontales punteadas
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = size.height * i / gridLines
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
                pathEffect = dashEffect
            )
        }

        val linePath = androidx.compose.ui.graphics.Path()
        val fillPath = androidx.compose.ui.graphics.Path()

        points.forEachIndexed { index, point ->
            val px = xToPx(point.x)
            val py = yToPx(point.y)
            if (index == 0) {
                linePath.moveTo(px, py)
                fillPath.moveTo(px, size.height)
                fillPath.lineTo(px, py)
            } else {
                linePath.lineTo(px, py)
                fillPath.lineTo(px, py)
            }
        }
        fillPath.lineTo(xToPx(points.last().x), size.height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.35f), lineColor.copy(alpha = 0.02f))
            )
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 3f)
        )
    }
}
