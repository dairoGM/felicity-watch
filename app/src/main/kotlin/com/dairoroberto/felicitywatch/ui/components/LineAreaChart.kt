package com.dairoroberto.felicitywatch.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

data class ChartPoint(val x: Float, val y: Float)

/**
 * Gráfico de área/línea liviano dibujado a mano con Canvas (sin librería de
 * terceros): línea suave con relleno degradado bajo la curva, líneas de
 * cuadrícula punteadas, y un tooltip al tocar/arrastrar sobre la curva
 * (mismo patrón visual que el gráfico web de Felicity: burbuja con hora y
 * valor sobre el punto más cercano al dedo).
 */
@Composable
fun LineAreaChart(
    points: List<ChartPoint>,
    lineColor: Color,
    gridColor: Color,
    modifier: Modifier = Modifier,
    minY: Float = 0f,
    maxYOverride: Float? = null,
    tooltipLabel: (ChartPoint) -> Pair<String, String> = { it.y.toInt().toString() to "" }
) {
    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .pointerInput(points) {
                detectTapGestures { offset ->
                    selectedIndex = nearestIndex(points, offset.x, size.width.toFloat())
                }
            }
            .pointerInput(points) {
                detectDragGestures(
                    onDragStart = { offset -> selectedIndex = nearestIndex(points, offset.x, size.width.toFloat()) },
                    onDrag = { change, _ -> selectedIndex = nearestIndex(points, change.position.x, size.width.toFloat()) }
                )
            }
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

        val index = selectedIndex
        if (index != null && index in points.indices) {
            val point = points[index]
            val px = xToPx(point.x)
            val py = yToPx(point.y)

            // Línea vertical punteada + punto resaltado sobre la curva.
            drawLine(
                color = lineColor.copy(alpha = 0.6f),
                start = Offset(px, 0f),
                end = Offset(px, size.height),
                strokeWidth = 1.5f,
                pathEffect = dashEffect
            )
            drawCircle(color = lineColor, radius = 6f, center = Offset(px, py))
            drawCircle(color = Color.White, radius = 3f, center = Offset(px, py))

            val (valueText, timeText) = tooltipLabel(point)
            drawTooltip(px, py, timeText, valueText, lineColor)
        }
    }
}

private fun nearestIndex(points: List<ChartPoint>, touchX: Float, canvasWidth: Float): Int {
    if (points.isEmpty()) return 0
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }.coerceAtLeast(minX + 1f)
    val targetX = minX + (touchX / canvasWidth) * (maxX - minX)
    return points.indices.minBy { kotlin.math.abs(points[it].x - targetX) }
}

/** Dibujado con el Canvas nativo de Android (Paint/drawText) porque
 * Compose Canvas no trae texto — es la única forma de lograr la burbuja
 * "hora + valor" sin agregar una librería de gráficos de terceros. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTooltip(
    anchorX: Float,
    anchorY: Float,
    timeText: String,
    valueText: String,
    accentColor: Color
) {
    val nativeCanvas = drawContext.canvas.nativeCanvas
    val density = 2.75f // aprox. px por dp en pantallas típicas; solo afecta tamaños de texto/paddings del tooltip

    val valuePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 14f * density
        isFakeBoldText = true
        isAntiAlias = true
    }
    val timePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.DKGRAY
        textSize = 11f * density
        isAntiAlias = true
    }

    val padding = 8f * density
    val lineGap = 4f * density
    val timeWidth = timePaint.measureText(timeText)
    val valueWidth = valuePaint.measureText(valueText)
    val boxWidth = maxOf(timeWidth, valueWidth) + padding * 2
    val boxHeight = (timePaint.textSize + valuePaint.textSize) + lineGap + padding * 2

    // Si no cabe a la derecha del punto, se ancla a la izquierda para no
    // salirse del gráfico (igual que el tooltip web de Felicity).
    val fitsRight = anchorX + 10f * density + boxWidth <= size.width
    val boxLeft = if (fitsRight) anchorX + 10f * density else anchorX - 10f * density - boxWidth
    val boxTop = (anchorY - boxHeight - 10f * density).coerceAtLeast(4f)

    val rectPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        isAntiAlias = true
        setShadowLayer(6f, 0f, 2f, android.graphics.Color.argb(60, 0, 0, 0))
    }
    val borderPaint = android.graphics.Paint().apply {
        color = accentColor.toArgb()
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    val rect = android.graphics.RectF(boxLeft, boxTop, boxLeft + boxWidth, boxTop + boxHeight)
    nativeCanvas.drawRoundRect(rect, 8f * density, 8f * density, rectPaint)
    nativeCanvas.drawRoundRect(rect, 8f * density, 8f * density, borderPaint)

    nativeCanvas.drawText(timeText, boxLeft + padding, boxTop + padding + timePaint.textSize, timePaint)
    nativeCanvas.drawText(
        valueText,
        boxLeft + padding,
        boxTop + padding + timePaint.textSize + lineGap + valuePaint.textSize * 0.85f,
        valuePaint
    )
}
