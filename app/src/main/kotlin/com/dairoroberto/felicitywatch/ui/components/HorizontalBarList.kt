package com.dairoroberto.felicitywatch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Una fila de la lista: etiqueta, valor y proporción de la barra. */
data class HorizontalBarEntry(
    val label: String,
    val value: Float,
    /** Resalta la fila (ej. el mejor día del mes). */
    val highlighted: Boolean = false
)

/**
 * Lista de barras HORIZONTALES — una fila por entrada, con su etiqueta
 * siempre visible a la izquierda y el valor a la derecha.
 *
 * Resuelve el problema de un gráfico de barras vertical cuando hay muchas
 * entradas (ej. los 31 días de un mes): en horizontal solo caben ~6
 * etiquetas en el eje X, así que el usuario no puede saber a qué día
 * corresponde cada barra. Aquí cada fila lleva su propia etiqueta, y la
 * lista simplemente crece hacia abajo.
 */
@Composable
fun HorizontalBarList(
    entries: List<HorizontalBarEntry>,
    barColor: Color,
    trackColor: Color,
    labelColor: Color,
    valueColor: Color,
    modifier: Modifier = Modifier,
    labelWidth: androidx.compose.ui.unit.Dp = 34.dp,
    valueFormatter: (Float) -> String = { "%.1f".format(it) },
    /** Máximo del eje forzado — útil para comparar dos listas entre sí. */
    maxValueOverride: Float? = null,
    /** Segundo segmento apilado A CONTINUACIÓN de [HorizontalBarEntry.value]
     * en la misma barra (ej. consumo "sin corriente" después del "con
     * corriente") — mismo índice que [entries]. El máximo del eje se
     * calcula sobre primario+secundario para que la barra completa quepa. */
    secondaryValues: List<Float>? = null,
    secondaryColor: Color = barColor
) {
    if (entries.isEmpty()) return
    val totals = entries.mapIndexed { i, e -> e.value + (secondaryValues?.getOrNull(i) ?: 0f) }
    val maxValue = (maxValueOverride ?: totals.maxOrNull() ?: 0f).coerceAtLeast(0.01f)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        entries.forEachIndexed { index, entry ->
            val secondary = secondaryValues?.getOrNull(index) ?: 0f
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    entry.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (entry.highlighted) FontWeight.Bold else FontWeight.Normal,
                    color = labelColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.width(labelWidth)
                )
                // Pista de fondo + barra proporcional. Se usan pesos
                // (weight) en vez de fillMaxWidth(fracción) para el segundo
                // segmento: fillMaxWidth mide contra el ancho TOTAL
                // disponible en cada hijo del Row, no contra lo que sobra
                // tras el primer segmento, así que dos fillMaxWidth
                // consecutivos no se apilan proporcionalmente. Con weight,
                // Compose reparte el ancho exactamente según la proporción
                // de cada valor frente al máximo del eje.
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(trackColor)
                ) {
                    val remaining = (maxValue - entry.value - secondary).coerceAtLeast(0f)
                    if (entry.value > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(entry.value.coerceAtLeast(maxValue * 0.008f))
                                .fillMaxHeight()
                                .background(if (entry.highlighted) barColor else barColor.copy(alpha = 0.8f))
                        )
                    }
                    if (secondary > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(secondary.coerceAtLeast(maxValue * 0.008f))
                                .fillMaxHeight()
                                .background(secondaryColor.copy(alpha = if (entry.highlighted) 1f else 0.8f))
                        )
                    }
                    if (remaining > 0f) {
                        Spacer(modifier = Modifier.weight(remaining))
                    }
                }
                Text(
                    valueFormatter(entry.value + secondary),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (entry.highlighted) FontWeight.Bold else FontWeight.Normal,
                    color = valueColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 8.dp).width(58.dp)
                )
            }
        }
    }
}
