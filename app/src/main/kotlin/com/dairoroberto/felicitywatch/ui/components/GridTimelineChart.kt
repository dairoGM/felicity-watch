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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** Un tramo continuo con corriente presente (true) o ausente (false), entre dos instantes epoch millis. */
data class GridSegment(val startEpochMillis: Long, val endEpochMillis: Long, val online: Boolean)

/**
 * Línea de tiempo tipo "banda de estado": una sola franja redondeada
 * coloreada por tramo (verde=con corriente, rojo=sin corriente), pensada
 * para leerse de un vistazo sin necesidad de tocarla — las marcas de hora y
 * la lista de eventos concretos ("16:00 volvió") viven fuera, en
 * [ReportScreen], para poder usar texto de Compose real en vez de Canvas.
 */
@Composable
fun GridTimelineChart(
    segments: List<GridSegment>,
    onlineColor: Color,
    offlineColor: Color,
    gridColor: Color,
    modifier: Modifier = Modifier,
    minEpochMillisOverride: Long? = null,
    maxEpochMillisOverride: Long? = null,
    nowEpochMillis: Long? = null,
    nowMarkerColor: Color = Color.White,
    tooltipLabel: (GridSegment) -> Pair<String, String> = { "" to "" }
) {
    var selectedIndex by remember(segments) { mutableStateOf<Int?>(null) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(0f) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .pointerInput(segments, scale, offset) {
                detectTapGestures { tapOffset ->
                    val minX = minEpochMillisOverride ?: segments.firstOrNull()?.startEpochMillis ?: 0L
                    val maxX = (maxEpochMillisOverride ?: segments.lastOrNull()?.endEpochMillis ?: 0L).coerceAtLeast(minX + 1L)
                    val dataRange = (maxX - minX).toFloat()
                    val visibleRange = dataRange / scale
                    val offsetFraction = if (scale > 1f) -offset / (size.width * scale - size.width) else 0f
                    val currentMinX = minX + (offsetFraction * (dataRange - visibleRange)).toLong()
                    val currentMaxX = currentMinX + visibleRange.toLong()
                    
                    val touchDataX = currentMinX + ((tapOffset.x / size.width) * (currentMaxX - currentMinX)).toLong()
                    selectedIndex = segments.indices.minByOrNull { kotlin.math.abs((segments[it].startEpochMillis + segments[it].endEpochMillis) / 2L - touchDataX) }
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
        if (segments.isEmpty()) return@Canvas

        val baseMinX = minEpochMillisOverride ?: segments.first().startEpochMillis
        val baseMaxX = (maxEpochMillisOverride ?: segments.last().endEpochMillis).coerceAtLeast(baseMinX + 1L)
        val dataRange = (baseMaxX - baseMinX).toFloat()
        val visibleRange = dataRange / scale
        val offsetFraction = if (scale > 1f) -offset / (size.width * scale - size.width) else 0f
        val minX = baseMinX + (offsetFraction * (dataRange - visibleRange)).toLong()
        val maxX = minX + visibleRange.toLong()

        fun xToPx(x: Long) = ((x - minX).toFloat() / (maxX - minX).toFloat()) * size.width

        val barTop = size.height * 0.15f
        val barHeight = size.height * 0.7f
        val cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f)

        // Franja de fondo continua (evita que se vea "cortada" en los
        // bordes redondeados de cada tramo individual).
        drawRoundRect(
            color = offlineColor.copy(alpha = 0.25f),
            topLeft = Offset(0f, barTop),
            size = Size(size.width, barHeight),
            cornerRadius = cornerRadius
        )

        segments.forEach { segment ->
            val left = xToPx(segment.startEpochMillis).coerceIn(0f, size.width)
            val right = xToPx(segment.endEpochMillis).coerceIn(0f, size.width)
            if (right <= left) return@forEach
            drawRect(
                color = if (segment.online) onlineColor else offlineColor,
                topLeft = Offset(left, barTop),
                size = Size((right - left), barHeight)
            )
        }

        // Marcador de "ahora": una línea vertical + triángulo arriba, para
        // ubicar de un vistazo si la hora actual cae en un tramo con o sin
        // corriente, sin depender de tocar la gráfica.
        if (nowEpochMillis != null && nowEpochMillis in minX..maxX) {
            val nowX = xToPx(nowEpochMillis).coerceIn(0f, size.width)
            drawLine(
                color = nowMarkerColor,
                start = Offset(nowX, 0f),
                end = Offset(nowX, size.height),
                strokeWidth = 2f
            )
            val triangleSize = 6f
            val trianglePath = androidx.compose.ui.graphics.Path().apply {
                moveTo(nowX - triangleSize, 0f)
                lineTo(nowX + triangleSize, 0f)
                lineTo(nowX, triangleSize * 1.5f)
                close()
            }
            drawPath(path = trianglePath, color = nowMarkerColor)
        }

        val index = selectedIndex
        if (index != null && index in segments.indices) {
            val segment = segments[index]
            val left = xToPx(segment.startEpochMillis)
            val right = xToPx(segment.endEpochMillis)
            val centerX = ((left + right) / 2f).coerceIn(0f, size.width)

            if (centerX in 0f..size.width) {
                drawLine(
                    color = gridColor,
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, size.height),
                    strokeWidth = 1.5f
                )
    
                val (title, subtitle) = tooltipLabel(segment)
                drawTimelineTooltip(centerX, barTop, title, subtitle, if (segment.online) onlineColor else offlineColor, size.width)
            }
        }
    }
}

private fun nearestIndex(
    segments: List<GridSegment>,
    touchX: Float,
    canvasWidth: Float,
    minEpochMillisOverride: Long?,
    maxEpochMillisOverride: Long?
): Int {
    if (segments.isEmpty()) return 0
    val minX = minEpochMillisOverride ?: segments.first().startEpochMillis
    val maxX = (maxEpochMillisOverride ?: segments.last().endEpochMillis).coerceAtLeast(minX + 1L)
    val targetX = minX + ((touchX / canvasWidth) * (maxX - minX).toFloat()).toLong()
    return segments.indices.minBy { index ->
        val segment = segments[index]
        when {
            targetX < segment.startEpochMillis -> segment.startEpochMillis - targetX
            targetX > segment.endEpochMillis -> targetX - segment.endEpochMillis
            else -> 0L
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTimelineTooltip(
    anchorX: Float,
    anchorY: Float,
    title: String,
    subtitle: String,
    accentColor: Color,
    canvasWidth: Float
) {
    val nativeCanvas = drawContext.canvas.nativeCanvas
    val density = 2.75f

    val titlePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 13f * density
        isFakeBoldText = true
        isAntiAlias = true
    }
    val subtitlePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.DKGRAY
        textSize = 11f * density
        isAntiAlias = true
    }

    val padding = 8f * density
    val lineGap = 4f * density
    val titleWidth = titlePaint.measureText(title)
    val subtitleWidth = subtitlePaint.measureText(subtitle)
    val boxWidth = maxOf(titleWidth, subtitleWidth) + padding * 2
    val boxHeight = (titlePaint.textSize + subtitlePaint.textSize) + lineGap + padding * 2

    val fitsRight = anchorX + 10f * density + boxWidth <= canvasWidth
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

    nativeCanvas.drawText(title, boxLeft + padding, boxTop + padding + titlePaint.textSize, titlePaint)
    nativeCanvas.drawText(
        subtitle,
        boxLeft + padding,
        boxTop + padding + titlePaint.textSize + lineGap + subtitlePaint.textSize * 0.85f,
        subtitlePaint
    )
}
