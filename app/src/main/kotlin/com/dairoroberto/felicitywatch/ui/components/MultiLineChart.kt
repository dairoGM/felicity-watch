package com.dairoroberto.felicitywatch.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * [points] debe venir en orden temporal e incluir un [ChartPoint] con
 * y=NaN en los instantes donde la serie no aplica (ej. batería cargando en
 * el punto de "Descarga") — así el trazo se corta en ese hueco en vez de
 * dibujar una diagonal larga uniendo el último y el próximo tramo real.
 */
data class ChartSeries(val name: String, val color: Color, val fill: Boolean = false, val points: List<ChartPoint>)

/** Alto fijo del canvas — ver [com.dairoroberto.felicitywatch.ui.components.CHART_HEIGHT]. */
val MULTI_CHART_HEIGHT = 200.dp

/**
 * Varias series superpuestas en el mismo eje X/Y (ej. FV/Carga/Descarga de
 * la web de Felicity), con eje Y que puede cruzar cero para representar
 * descarga como valores negativos. Mismo patrón táctil que [LineAreaChart].
 */
@Composable
fun MultiLineChart(
    series: List<ChartSeries>,
    gridColor: Color,
    modifier: Modifier = Modifier,
    minXOverride: Float? = null,
    maxXOverride: Float? = null,
    minYOverride: Float? = null,
    maxYOverride: Float? = null,
    yAxis: NiceAxis? = null,
    yUnit: String = "",
    textColor: Color = Color.Gray,
    zoomState: ChartZoomState = remember { ChartZoomState() },
    tooltipLabel: (Float, List<Pair<ChartSeries, Float>>) -> Pair<String, List<String>> = { _, _ -> "" to emptyList() }
) {
    val allPoints = series.flatMap { it.points }.filterNot { it.y.isNaN() }
    var selectedX by remember(series) { mutableStateOf<Float?>(null) }
    var scale by zoomState::scale
    var offset by zoomState::offset

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(MULTI_CHART_HEIGHT)
            .clipToBounds()
            .pointerInput(series, scale, offset) {
                detectTapGestures { tapOffset ->
                    val baseMinX = minXOverride ?: allPoints.minOfOrNull { it.x } ?: 0f
                    val baseMaxX = (maxXOverride ?: allPoints.maxOfOrNull { it.x } ?: 0f).coerceAtLeast(baseMinX + 1f)
                    val dataRange = baseMaxX - baseMinX
                    val visibleRange = dataRange / scale
                    val offsetFraction = if (scale > 1f) -offset / (size.width * scale - size.width) else 0f
                    val currentMinX = baseMinX + offsetFraction * (dataRange - visibleRange)
                    val currentMaxX = currentMinX + visibleRange
                    
                    val touchDataX = currentMinX + (tapOffset.x / size.width) * (currentMaxX - currentMinX)
                    selectedX = allPoints.map { it.x }.minByOrNull { kotlin.math.abs(it - touchDataX) }
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val oldScale = scale
                    scale = (scale * zoom).coerceIn(1f, 24f)
                    val scaleFactor = scale / oldScale
                    offset = (offset - centroid.x) * scaleFactor + centroid.x + pan.x
                    val minOffset = size.width - size.width * scale
                    offset = offset.coerceIn(minOffset, 0f)
                }
            }
    ) {
        if (allPoints.size < 2) return@Canvas

        val baseMinX = minXOverride ?: allPoints.minOf { it.x }
        val baseMaxX = (maxXOverride ?: allPoints.maxOf { it.x }).coerceAtLeast(baseMinX + 1f)
        val dataRange = baseMaxX - baseMinX
        val visibleRange = dataRange / scale
        val offsetFraction = if (scale > 1f) -offset / (size.width * scale - size.width) else 0f
        val minX = baseMinX + offsetFraction * (dataRange - visibleRange)
        val maxX = minX + visibleRange

        val minY = (minYOverride ?: allPoints.minOf { it.y }.coerceAtMost(0f))
        val maxY = (maxYOverride ?: allPoints.maxOf { it.y }).coerceAtLeast(minY + 1f)

        fun xToPx(x: Float) = (x - minX) / (maxX - minX) * size.width
        fun yToPx(y: Float) = size.height - ((y - minY) / (maxY - minY) * size.height)

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

        // Línea sólida en y=0 cuando el eje cruza cero (separa carga de descarga).
        if (minY < 0f && maxY > 0f) {
            val zeroPy = yToPx(0f)
            drawLine(
                color = gridColor,
                start = Offset(0f, zeroPy),
                end = Offset(size.width, zeroPy),
                strokeWidth = 1.5f
            )
        }

        val zeroPy = yToPx(0f)
        series.forEach { s ->
            if (s.points.size < 2) return@forEach
            val path = androidx.compose.ui.graphics.Path()
            val fillPath = androidx.compose.ui.graphics.Path()
            var penDown = false
            
            s.points.forEach { point ->
                if (point.y.isNaN()) {
                    if (penDown && s.fill) {
                        fillPath.lineTo(xToPx(point.x), zeroPy)
                        fillPath.close()
                    }
                    penDown = false
                    return@forEach
                }
                val px = xToPx(point.x)
                val py = yToPx(point.y)
                if (!penDown) {
                    path.moveTo(px, py)
                    if (s.fill) {
                        fillPath.moveTo(px, zeroPy)
                        fillPath.lineTo(px, py)
                    }
                    penDown = true
                } else {
                    path.lineTo(px, py)
                    if (s.fill) fillPath.lineTo(px, py)
                }
            }
            if (penDown && s.fill) {
                val lastRealX = s.points.last { !it.y.isNaN() }.x
                fillPath.lineTo(xToPx(lastRealX), zeroPy)
                fillPath.close()
            }
            
            if (s.fill) {
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(s.color.copy(alpha = 0.35f), s.color.copy(alpha = 0.05f))
                    )
                )
            }
            drawPath(path = path, color = s.color, style = Stroke(width = 3f))
        }

        val targetX = selectedX
        if (targetX != null) {
            val px = xToPx(targetX)
            if (px in 0f..size.width) {
                drawLine(
                    color = gridColor,
                    start = Offset(px, 0f),
                    end = Offset(px, size.height),
                    strokeWidth = 1.5f,
                    pathEffect = dashEffect
                )

                val seriesValues = series.mapNotNull { s ->
                    val nearest = s.points.filterNot { it.y.isNaN() }.minByOrNull { kotlin.math.abs(it.x - targetX) }
                        ?: return@mapNotNull null
                    s to nearest.y
                }
                seriesValues.forEach { (s, value) ->
                    val py = yToPx(value)
                    drawCircle(color = s.color, radius = 6f, center = Offset(px, py))
                    drawCircle(color = Color.White, radius = 3f, center = Offset(px, py))
                }

                val (timeText, valuesList) = tooltipLabel(targetX, seriesValues)
                drawMultiTooltip(px, size.height / 2f, timeText, valuesList, size.width)
            }
        }
        
        if (yAxis != null) {
            val textPaint = android.graphics.Paint().apply {
                color = textColor.toArgb()
                textSize = 10f * density
                isAntiAlias = true
            }
            for (i in 0 until yAxis.labelCount) {
                val value = yAxis.max - yAxis.step * i
                val fraction = if (yAxis.max == yAxis.min) 0f else (yAxis.max - value) / (yAxis.max - yAxis.min)
                val py = size.height * fraction
                drawContext.canvas.nativeCanvas.drawText(
                    "${value.toInt()} $yUnit",
                    12f * density,
                    (py - 4f * density).coerceAtLeast(10f * density),
                    textPaint
                )
            }
        }
    }
}

private fun nearestX(points: List<ChartPoint>, touchX: Float, canvasWidth: Float): Float {
    if (points.isEmpty()) return 0f
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }.coerceAtLeast(minX + 1f)
    return minX + (touchX / canvasWidth) * (maxX - minX)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMultiTooltip(
    anchorX: Float,
    anchorY: Float,
    title: String,
    lines: List<String>,
    canvasWidth: Float
) {
    val nativeCanvas = drawContext.canvas.nativeCanvas
    val density = 2.75f

    val titlePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.DKGRAY
        textSize = 11f * density
        isAntiAlias = true
    }
    val linePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 13f * density
        isFakeBoldText = true
        isAntiAlias = true
    }

    val padding = 8f * density
    val lineGap = 3f * density
    val titleWidth = titlePaint.measureText(title)
    val maxLineWidth = lines.maxOfOrNull { linePaint.measureText(it) } ?: 0f
    val boxWidth = maxOf(titleWidth, maxLineWidth) + padding * 2
    val boxHeight = titlePaint.textSize + lines.size * (linePaint.textSize + lineGap) + padding * 2

    val fitsRight = anchorX + 10f * density + boxWidth <= canvasWidth
    val boxLeft = if (fitsRight) anchorX + 10f * density else anchorX - 10f * density - boxWidth
    val boxTop = (anchorY - boxHeight - 10f * density).coerceAtLeast(4f)

    val rectPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        isAntiAlias = true
        setShadowLayer(6f, 0f, 2f, android.graphics.Color.argb(60, 0, 0, 0))
    }

    val rect = android.graphics.RectF(boxLeft, boxTop, boxLeft + boxWidth, boxTop + boxHeight)
    nativeCanvas.drawRoundRect(rect, 8f * density, 8f * density, rectPaint)

    var textY = boxTop + padding + titlePaint.textSize
    nativeCanvas.drawText(title, boxLeft + padding, textY, titlePaint)
    lines.forEach { line ->
        textY += linePaint.textSize + lineGap
        nativeCanvas.drawText(line, boxLeft + padding, textY, linePaint)
    }
}
