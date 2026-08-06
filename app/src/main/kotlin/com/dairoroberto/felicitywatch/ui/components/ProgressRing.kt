package com.dairoroberto.felicitywatch.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Anillo de progreso circular tipo velocímetro (arco de 270°, abierto abajo)
 * — más memorable de un vistazo que un número solo, para métricas de
 * "cuánto falta/dura" como autonomía de batería o tiempo de carga.
 * [progress] en 0f..1f; el contenido central (typically un Text) se pasa
 * como [content] para no atar este componente a un formato de texto fijo.
 */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    strokeWidth: Dp = 8.dp,
    content: @Composable () -> Unit = {}
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            val startAngle = 135f
            val sweepAngle = 270f
            val inset = strokeWidth.toPx() / 2f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)

            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = stroke
            )
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = stroke
            )
        }
        content()
    }
}
