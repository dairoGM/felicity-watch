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

data class ChartPoint(val x: Float, val y: Float)

data class NiceAxis(val min: Float, val max: Float, val step: Float, val labelCount: Int)

fun niceAxisStep(range: Float, targetSteps: Int = 5): Float {
    if (range <= 0f) return 1f
    val rawStep = range / targetSteps
    val magnitude = Math.pow(10.0, Math.floor(Math.log10(rawStep.toDouble()))).toFloat()
    val normalized = rawStep / magnitude
    val niceNormalized = when {
        normalized <= 1.5f -> 1f
        normalized <= 2.5f -> 2f
        normalized <= 5f -> 5f
        else -> 10f
    }
    return niceNormalized * magnitude
}

fun niceAxis(dataMin: Float, dataMax: Float, targetSteps: Int = 5): NiceAxis {
    val range = (dataMax - dataMin).coerceAtLeast(0.01f)
    val step = niceAxisStep(range, targetSteps)
    val niceMin = kotlin.math.floor(dataMin / step) * step
    val niceMax = kotlin.math.ceil(dataMax / step) * step
    val labelCount = ((niceMax - niceMin) / step).let { kotlin.math.round(it).toInt() } + 1
    return NiceAxis(niceMin, niceMax, step, labelCount)
}

/** Estado de zoom/pan compartido por [LineAreaChart] y [MultiLineChart] —
 * se saca (hoist) al llamador para que ReportScreen pueda calcular el
 * rango X actualmente visible y dibujar un eje X de horas SINCRONIZADO con
 * el zoom, en vez de un eje fijo que ya no corresponde a lo que se ve tras
 * hacer pinch-zoom o pan. */
class ChartZoomState {
    var scale by mutableStateOf(1f)
    var offset by mutableStateOf(0f)

    /** Rango X visible actualmente, dado el rango completo de datos [baseMinX]..[baseMaxX]. */
    fun visibleRange(baseMinX: Float, baseMaxX: Float, canvasWidthPx: Float): Pair<Float, Float> {
        val dataRange = (baseMaxX - baseMinX).coerceAtLeast(0.01f)
        val visibleRange = dataRange / scale
        val offsetFraction = if (scale > 1f && canvasWidthPx > 0f) {
            -offset / (canvasWidthPx * scale - canvasWidthPx)
        } else 0f
        val minX = baseMinX + offsetFraction * (dataRange - visibleRange)
        return minX to (minX + visibleRange)
    }
}

/** Alto fijo del canvas de [LineAreaChart]/[MultiLineChart] — expuesto para
 * que las etiquetas superpuestas del eje Y (dibujadas fuera del Canvas con
 * texto de Compose) puedan igualar esta altura exacta y sus marcas queden
 * alineadas en px con las líneas de cuadrícula reales, no aproximadas. */
val CHART_HEIGHT = 180.dp

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
    gradientColors: List<Color>? = null,
    gridColor: Color,
    modifier: Modifier = Modifier,
    minY: Float = 0f,
    maxYOverride: Float? = null,
    minXOverride: Float? = null,
    maxXOverride: Float? = null,
    yAxis: NiceAxis? = null,
    yUnit: String = "",
    textColor: Color = Color.Gray,
    zoomState: ChartZoomState = remember { ChartZoomState() },
    tooltipLabel: (ChartPoint) -> Pair<String, String> = { it.y.toInt().toString() to "" }
) {
    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }
    var scale by zoomState::scale
    var offset by zoomState::offset

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .clipToBounds()
            .pointerInput(points, scale, offset) {
                detectTapGestures { tapOffset ->
                    val baseMinX = minXOverride ?: points.minOfOrNull { it.x } ?: 0f
                    val baseMaxX = (maxXOverride ?: points.maxOfOrNull { it.x } ?: 0f).coerceAtLeast(baseMinX + 1f)
                    val dataRange = baseMaxX - baseMinX
                    val visibleRange = dataRange / scale
                    val offsetFraction = if (scale > 1f) -offset / (size.width * scale - size.width) else 0f
                    val currentMinX = baseMinX + offsetFraction * (dataRange - visibleRange)
                    val currentMaxX = currentMinX + visibleRange
                    
                    val touchDataX = currentMinX + (tapOffset.x / size.width) * (currentMaxX - currentMinX)
                    selectedIndex = points.indices.minByOrNull { kotlin.math.abs(points[it].x - touchDataX) }
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
        if (points.size < 2) return@Canvas

        val maxY = (maxYOverride ?: points.maxOf { it.y }).coerceAtLeast(1f)
        val baseMinX = minXOverride ?: points.minOf { it.x }
        val baseMaxX = (maxXOverride ?: points.maxOf { it.x }).coerceAtLeast(baseMinX + 1f)
        val dataRange = baseMaxX - baseMinX
        val visibleRange = dataRange / scale
        val offsetFraction = if (scale > 1f) -offset / (size.width * scale - size.width) else 0f
        val minX = baseMinX + offsetFraction * (dataRange - visibleRange)
        val maxX = minX + visibleRange

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

        val actualFillBrush = if (gradientColors != null) {
            Brush.verticalGradient(
                colors = listOf(
                    gradientColors.first().copy(alpha = 0.4f),
                    gradientColors.last().copy(alpha = 0.05f)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.35f), lineColor.copy(alpha = 0.02f))
            )
        }
        drawPath(
            path = fillPath,
            brush = actualFillBrush
        )

        val actualLineBrush = if (gradientColors != null) {
            Brush.verticalGradient(colors = gradientColors)
        } else {
            androidx.compose.ui.graphics.SolidColor(lineColor)
        }
        drawPath(
            path = linePath,
            brush = actualLineBrush,
            style = Stroke(width = 3f)
        )

        val index = selectedIndex
        if (index != null && index in points.indices) {
            val point = points[index]
            val px = xToPx(point.x)
            val py = yToPx(point.y)

            if (px in 0f..size.width) {
                val activeColor = if (gradientColors != null) {
                    val fraction = (py / size.height).coerceIn(0f, 1f)
                    // Interpolación simple entre los dos colores del degradado
                    val c1 = gradientColors.first()
                    val c2 = gradientColors.last()
                    Color(
                        red = c1.red + (c2.red - c1.red) * fraction,
                        green = c1.green + (c2.green - c1.green) * fraction,
                        blue = c1.blue + (c2.blue - c1.blue) * fraction,
                        alpha = 1f
                    )
                } else {
                    lineColor
                }

                drawLine(
                    color = activeColor.copy(alpha = 0.6f),
                    start = Offset(px, 0f),
                    end = Offset(px, size.height),
                    strokeWidth = 1.5f,
                    pathEffect = dashEffect
                )
                drawCircle(color = activeColor, radius = 6f, center = Offset(px, py))
                drawCircle(color = Color.White, radius = 3f, center = Offset(px, py))
                val (valueText, timeText) = tooltipLabel(point)
                drawTooltip(px, py, timeText, valueText, activeColor)
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
                // y = 0 is top, y = height is bottom. gridLines are distributed evenly.
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
