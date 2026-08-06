package com.dairoroberto.felicitywatch.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

data class BarChartEntry(val label: String, val value: Float)

/**
 * Gráfico de barras simple para reportes por día/mes (ej. kWh generados)
 * — una barra por entrada, tocar una barra muestra su valor exacto.
 * Deliberadamente sin zoom/pan (a diferencia de LineAreaChart): estos
 * reportes tienen pocas barras (días de un mes, meses de un año) y no
 * necesitan esa interacción.
 */
@Composable
fun DailyBarChart(
    entries: List<BarChartEntry>,
    barColor: Color,
    gridColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    valueFormatter: (Float) -> String = { "%.1f".format(it) }
) {
    var selectedIndex by remember(entries) { mutableStateOf<Int?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(entries) {
                detectTapGestures { offset ->
                    if (entries.isEmpty()) return@detectTapGestures
                    val barWidth = size.width / entries.size
                    val index = (offset.x / barWidth).toInt().coerceIn(0, entries.size - 1)
                    selectedIndex = index
                }
            }
    ) {
        if (entries.isEmpty()) return@Canvas

        val maxValue = entries.maxOf { it.value }.coerceAtLeast(0.01f)
        val dashEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
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

        val slotWidth = size.width / entries.size
        val barWidth = slotWidth * 0.6f
        val barGap = (slotWidth - barWidth) / 2f

        entries.forEachIndexed { index, entry ->
            val barHeight = (entry.value / maxValue) * size.height * 0.85f
            val left = index * slotWidth + barGap
            val top = size.height - barHeight
            val isSelected = selectedIndex == index
            drawRoundRect(
                color = if (isSelected) barColor else barColor.copy(alpha = 0.75f),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            )
        }

        val index = selectedIndex
        if (index != null && index in entries.indices) {
            val entry = entries[index]
            val barHeight = (entry.value / maxValue) * size.height * 0.85f
            val centerX = index * slotWidth + slotWidth / 2f
            val top = size.height - barHeight
            drawTooltipAbove(centerX, top, entry.label, valueFormatter(entry.value), barColor, size.width)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTooltipAbove(
    anchorX: Float,
    anchorY: Float,
    title: String,
    value: String,
    accentColor: Color,
    canvasWidth: Float
) {
    val nativeCanvas = drawContext.canvas.nativeCanvas
    val density = 2.75f

    val titlePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.DKGRAY
        textSize = 11f * density
        isAntiAlias = true
    }
    val valuePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 13f * density
        isFakeBoldText = true
        isAntiAlias = true
    }

    val padding = 8f * density
    val lineGap = 3f * density
    val titleWidth = titlePaint.measureText(title)
    val valueWidth = valuePaint.measureText(value)
    val boxWidth = maxOf(titleWidth, valueWidth) + padding * 2
    val boxHeight = titlePaint.textSize + valuePaint.textSize + lineGap + padding * 2

    val halfWidth = boxWidth / 2f
    val boxLeft = (anchorX - halfWidth).coerceIn(4f, canvasWidth - boxWidth - 4f)
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
        value,
        boxLeft + padding,
        boxTop + padding + titlePaint.textSize + lineGap + valuePaint.textSize * 0.85f,
        valuePaint
    )
}
