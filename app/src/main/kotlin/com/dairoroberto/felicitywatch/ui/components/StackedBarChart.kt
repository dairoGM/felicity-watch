package com.dairoroberto.felicitywatch.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** Una barra apilada: dos series que se suman en el mismo día. */
data class StackedEntry(
    val label: String,
    val lower: Float,
    val upper: Float
) {
    val total: Float get() = lower + upper
}

/**
 * Barras apiladas de dos series.
 *
 * Se usa apilado y no dos barras lado a lado porque las dos series son PARTES
 * DE UN MISMO TOTAL (el consumo del día, repartido entre lo que se pagó y lo
 * que se cubrió con solar/batería). Lado a lado sugeriría dos magnitudes
 * independientes y perdería la lectura más útil: la altura total es el consumo
 * del día, y la proporción de color dice cuánto se ahorró.
 *
 * Al tocar una barra se muestra su desglose exacto arriba.
 */
@Composable
fun StackedBarChart(
    entries: List<StackedEntry>,
    lowerColor: Color,
    upperColor: Color,
    gridColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    lowerName: String = "",
    upperName: String = "",
    valueFormatter: (Float) -> String = { "%.1f".format(it) }
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var selectedIndex by remember(entries) { mutableStateOf<Int?>(null) }

    if (entries.isEmpty()) {
        Column(modifier.fillMaxWidth().height(height)) {}
        return
    }

    val yMax = niceCeil(entries.maxOf { it.total })
    val labelStyle = TextStyle(fontSize = 9.sp, color = labelColor, fontWeight = FontWeight.Medium)
    val tipStyle = TextStyle(fontSize = 10.sp, color = labelColor, fontWeight = FontWeight.Bold)

    val yLabelWidth = with(density) {
        textMeasurer.measure(valueFormatter(yMax), labelStyle).size.width.toDp()
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(entries) {
                detectTapGestures { offset ->
                    val leftPad = with(density) { yLabelWidth.toPx() + 12.dp.toPx() }
                    val plotWidth = size.width - leftPad
                    if (plotWidth <= 0f) return@detectTapGestures
                    val slot = plotWidth / entries.size
                    val index = ((offset.x - leftPad) / slot).toInt()
                    selectedIndex = index.takeIf { it in entries.indices }
                }
            }
    ) {
        val leftPad = with(density) { yLabelWidth.toPx() + 12.dp.toPx() }
        val topPad = with(density) { 22.dp.toPx() }
        val bottomPad = with(density) { 18.dp.toPx() }
        val plotWidth = size.width - leftPad
        val plotHeight = size.height - topPad - bottomPad
        if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

        val baseY = topPad + plotHeight
        fun yFor(value: Float) = baseY - (value / yMax).coerceIn(0f, 1f) * plotHeight

        // Rejilla y etiquetas del eje Y
        for (i in 0..3) {
            val value = yMax * i / 3f
            val y = yFor(value)
            val isBase = i == 0
            drawLine(
                color = if (isBase) gridColor else gridColor.copy(alpha = 0.5f),
                start = Offset(leftPad, y),
                end = Offset(size.width, y),
                strokeWidth = with(density) { if (isBase) 1.2.dp.toPx() else 1f },
                pathEffect = if (isBase) null else PathEffect.dashPathEffect(floatArrayOf(3f, 7f))
            )
            val layout = textMeasurer.measure(valueFormatter(value), labelStyle)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    leftPad - with(density) { 6.dp.toPx() } - layout.size.width,
                    y - layout.size.height / 2f
                )
            )
        }

        val slot = plotWidth / entries.size
        // Ancho de barra con separación proporcional; con muchos días las
        // barras se adelgazan solas en vez de solaparse.
        val barWidth = (slot * 0.62f).coerceAtMost(with(density) { 26.dp.toPx() })
        val radius = CornerRadius(with(density) { 2.5.dp.toPx() })

        entries.forEachIndexed { index, entry ->
            val centerX = leftPad + slot * index + slot / 2f
            val left = centerX - barWidth / 2f
            val isSelected = selectedIndex == index

            // Parte inferior (lo pagado)
            if (entry.lower > 0f) {
                val top = yFor(entry.lower)
                drawRoundRect(
                    color = if (isSelected) lowerColor else lowerColor.copy(alpha = 0.85f),
                    topLeft = Offset(left, top),
                    size = Size(barWidth, baseY - top),
                    cornerRadius = radius
                )
            }
            // Parte superior (lo ahorrado), encima
            if (entry.upper > 0f) {
                val bottom = yFor(entry.lower)
                val top = yFor(entry.total)
                drawRoundRect(
                    color = if (isSelected) upperColor else upperColor.copy(alpha = 0.85f),
                    topLeft = Offset(left, top),
                    size = Size(barWidth, bottom - top),
                    cornerRadius = radius
                )
            }

            // Etiquetas del eje X, salteadas para que no se solapen.
            val step = when {
                entries.size <= 8 -> 1
                entries.size <= 16 -> 2
                entries.size <= 24 -> 3
                else -> 5
            }
            if (index % step == 0) {
                val layout = textMeasurer.measure(entry.label, labelStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        centerX - layout.size.width / 2f,
                        baseY + with(density) { 5.dp.toPx() }
                    )
                )
            }
        }

        // Desglose de la barra tocada, arriba del gráfico.
        selectedIndex?.let { index ->
            val entry = entries.getOrNull(index) ?: return@let
            val text = buildString {
                append(entry.label)
                append("  ")
                if (lowerName.isNotBlank()) {
                    append(lowerName)
                    append(" ")
                }
                append(valueFormatter(entry.lower))
                append("  ·  ")
                if (upperName.isNotBlank()) {
                    append(upperName)
                    append(" ")
                }
                append(valueFormatter(entry.upper))
            }
            val layout = textMeasurer.measure(text, tipStyle)
            val boxPad = with(density) { 6.dp.toPx() }
            val boxWidth = layout.size.width + boxPad * 2
            val centerX = leftPad + slot * index + slot / 2f
            val maxLeft = (size.width - boxWidth).coerceAtLeast(0f)
            val boxLeft = (centerX - boxWidth / 2f).coerceIn(0f, maxLeft)

            drawRoundRect(
                color = gridColor,
                topLeft = Offset(boxLeft, 0f),
                size = Size(boxWidth, layout.size.height + boxPad),
                cornerRadius = CornerRadius(with(density) { 5.dp.toPx() })
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(boxLeft + boxPad, boxPad / 2f)
            )
        }
    }
}

/** Techo limpio para el eje Y: 6.3 -> 8, 47 -> 50. */
private fun niceCeil(value: Float): Float {
    if (value <= 0f) return 1f
    val magnitude = 10.0.pow(floor(log10(value.toDouble()))).toFloat()
    val normalized = value / magnitude
    val stepped = when {
        normalized <= 1f -> 1f
        normalized <= 2f -> 2f
        normalized <= 4f -> 4f
        normalized <= 5f -> 5f
        normalized <= 8f -> 8f
        else -> 10f
    }
    return stepped * magnitude
}
