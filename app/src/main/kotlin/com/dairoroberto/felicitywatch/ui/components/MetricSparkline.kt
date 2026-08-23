package com.dairoroberto.felicitywatch.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Curva compacta de la tendencia del día, para los cards del Panel.
 *
 * Es deliberadamente distinta de [DayTrendChart] (el del modal): sin ejes,
 * sin rejilla, sin etiquetas ni interacción. Aquí el objetivo no es leer
 * valores — para eso está el modal al tocar el card — sino ver de un vistazo
 * la FORMA del día: si la generación viene subiendo, si el consumo tuvo un
 * pico, si la batería lleva rato plana.
 *
 * Comparte el lenguaje visual del modal (misma curva suavizada, misma área
 * con degradado, mismo punto final destacado) para que al abrir el detalle se
 * reconozca como la versión grande de lo mismo.
 */
@Composable
fun MetricSparkline(
    /** Valores en orden cronológico. Se espacian de forma uniforme: en un
     * espacio tan pequeño la posición temporal exacta no es legible, y usar
     * el índice evita que un hueco en las lecturas deje la curva vacía. */
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 26.dp,
    /** Fuerza el techo. Para porcentajes conviene 100 fijo, así la altura de
     * la curva significa lo mismo entre aperturas del Panel. */
    maxValueOverride: Float? = null
) {
    if (values.size < 2) {
        // Se reserva el espacio igual: si el card apareciera sin la curva, la
        // fila de tres cards quedaría descuadrada mientras se acumulan las
        // primeras lecturas del día.
        Box(modifier = modifier.fillMaxWidth().height(height))
        return
    }

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val maxValue = maxValueOverride ?: values.max()
        // El piso es cero y no el mínimo de la serie: con piso variable, una
        // curva plana en 300 W se vería idéntica a una plana en 1500 W.
        val span = maxValue.takeIf { it > 0.01f } ?: 1f

        // Margen arriba y abajo para que la curva no toque los bordes del
        // Canvas, donde el trazo quedaría cortado a la mitad.
        val padY = size.height * 0.14f
        val usable = size.height - padY * 2

        val stepX = size.width / (values.size - 1)
        fun xFor(index: Int) = stepX * index
        fun yFor(value: Float) = padY + usable - (value / span).coerceIn(0f, 1f) * usable

        val linePath = Path()
        val areaPath = Path()

        var previousX = xFor(0)
        var previousY = yFor(values[0])
        linePath.moveTo(previousX, previousY)
        areaPath.moveTo(previousX, size.height)
        areaPath.lineTo(previousX, previousY)

        for (i in 1 until values.size) {
            val currentX = xFor(i)
            val currentY = yFor(values[i])
            val midX = (previousX + currentX) / 2f
            // Misma Bézier con control a media distancia que el gráfico
            // grande: suaviza sin sobretiros que dibujarían valores negativos.
            linePath.cubicTo(midX, previousY, midX, currentY, currentX, currentY)
            areaPath.cubicTo(midX, previousY, midX, currentY, currentX, currentY)
            previousX = currentX
            previousY = currentY
        }

        areaPath.lineTo(previousX, size.height)
        areaPath.close()

        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.30f), Color.Transparent),
                startY = padY,
                endY = size.height
            )
        )
        drawPath(
            path = linePath,
            color = color,
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        )

        // Punto final: ancla la curva al valor grande que muestra el card.
        drawCircle(color, radius = 2.2.dp.toPx(), center = Offset(previousX, previousY))
    }
}
