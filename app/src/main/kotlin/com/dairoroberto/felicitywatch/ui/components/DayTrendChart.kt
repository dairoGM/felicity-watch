package com.dairoroberto.felicitywatch.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** Un punto de la serie: hora del dia y valor. */
data class TrendPoint(
    /**
     * Hora del dia como fraccion: 13.5 = 1:30 pm. Se espacian los puntos por
     * tiempo REAL y no por indice, de modo que un hueco en las lecturas (el
     * servicio detenido, sin conexion) se vea como un hueco en el eje en vez
     * de quedar comprimido y aparentar continuidad.
     */
    val hourOfDay: Float,
    val value: Float
)

/**
 * Grafica de evolucion de una magnitud a lo largo del dia.
 *
 * Acabado pensado para que se vea como el grafico de un producto comercial y
 * no como una linea de depuracion:
 *
 * - Relleno con degradado de varias paradas y un piso solido tenue: da
 *   volumen sin ensuciar la rejilla.
 * - La linea lleva un halo suave debajo, lo que la separa visualmente del
 *   area y evita el aspecto plano de un solo trazo.
 * - Rejilla punteada muy tenue + eje base solido: la referencia se lee sin
 *   competir con los datos.
 * - Marcador del punto maximo del dia, que es la cifra que el usuario busca.
 * - Al tocar, aparece un tooltip con puntero, valor y hora.
 *
 * Sobre la curva: se usa Bezier cubica con control a media distancia
 * horizontal en vez de una spline libre. Una spline libre genera sobretiros,
 * y aqui un sobretiro dibujaria potencia negativa en un valle donde nunca la
 * hubo.
 */
@Composable
fun DayTrendChart(
    points: List<TrendPoint>,
    lineColor: Color,
    gridColor: Color,
    labelColor: Color,
    tooltipBackground: Color,
    /** Fondo de la tarjeta que contiene la grafica — se usa para el borde
     * del punto marcador, que asi parece recortado sobre el area. */
    surfaceColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 210.dp,
    /** Sufijo de unidad para el tooltip ("W", "%"). */
    unit: String = "",
    /** Fuerza el techo del eje Y. Necesario para porcentajes, donde la escala
     * debe ser 0..100 aunque los datos solo lleguen a 60. */
    maxValueOverride: Float? = null,
    valueFormatter: (Float) -> String = { it.roundToInt().toString() }
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Punto que el usuario esta inspeccionando; null = ninguno.
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    // Barrido de entrada: la linea se traza de izquierda a derecha al abrir,
    // con curva de aceleracion para que no se sienta mecanico.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(points.size) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
        )
    }

    if (points.size < 2) {
        Box(modifier = modifier.fillMaxWidth().height(height))
        return
    }

    val sorted = remember(points) { points.sortedBy { it.hourOfDay } }

    // Techo redondeado a un numero limpio, para que las etiquetas del eje
    // sean 1500 / 1125 / 750 y no 1347 / 1010 / 673.
    val yMax = maxValueOverride ?: niceCeiling(sorted.maxOf { it.value })
    val ySpan = yMax.takeIf { it > 0.01f } ?: 1f

    val xStart = sorted.first().hourOfDay
    val xEnd = sorted.last().hourOfDay
    val xSpan = (xEnd - xStart).takeIf { it > 0.01f } ?: 1f

    // Indice del maximo: se marca aparte porque es el dato que el usuario
    // busca de un vistazo ("cuanto llegue a generar hoy").
    val peakIndex = remember(sorted) {
        sorted.indices.maxByOrNull { sorted[it].value } ?: 0
    }

    val labelStyle = TextStyle(
        fontSize = 9.sp,
        color = labelColor,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp
    )
    val tooltipStyle = TextStyle(fontSize = 11.sp, color = labelColor, fontWeight = FontWeight.Bold)

    // Se MIDE el ancho de la etiqueta mas larga en vez de asumir un margen
    // fijo, que se rompe con valores de 4 o 5 digitos.
    val yLabelWidth = with(density) {
        textMeasurer.measure(valueFormatter(yMax), labelStyle).size.width.toDp()
    }
    val xLabelHeight = with(density) {
        textMeasurer.measure("00am", labelStyle).size.height.toDp()
    }
    val leftPadPx = with(density) { yLabelWidth.toPx() + 14.dp.toPx() }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(sorted) {
                    detectTapGestures { offset ->
                        selectedIndex = nearestIndex(
                            offset.x, size.width.toFloat(), leftPadPx, sorted, xStart, xSpan
                        )
                    }
                }
                .pointerInput(sorted) {
                    detectDragGestures { change, _ ->
                        selectedIndex = nearestIndex(
                            change.position.x, size.width.toFloat(), leftPadPx, sorted, xStart, xSpan
                        )
                    }
                }
        ) {
            val bottomPad = with(density) { xLabelHeight.toPx() + 8.dp.toPx() }
            // Espacio arriba para que el tooltip y el marcador de pico no
            // queden pegados al borde de la tarjeta.
            val topPad = with(density) { 26.dp.toPx() }
            val plotWidth = size.width - leftPadPx
            val plotHeight = size.height - bottomPad - topPad
            if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

            val baseY = topPad + plotHeight

            fun xFor(hour: Float) = leftPadPx + ((hour - xStart) / xSpan) * plotWidth
            fun yFor(value: Float) = baseY - (value / ySpan) * plotHeight

            // ---------- Rejilla y etiquetas del eje Y ----------
            val gridLines = 4
            for (i in 0..gridLines) {
                val value = ySpan * i / gridLines
                val y = yFor(value)
                val isBase = i == 0
                drawLine(
                    color = if (isBase) gridColor else gridColor.copy(alpha = 0.5f),
                    start = Offset(leftPadPx, y),
                    end = Offset(size.width, y),
                    strokeWidth = with(density) { if (isBase) 1.2.dp.toPx() else 1f },
                    // El eje base va solido y el resto punteado: da un suelo
                    // firme a la grafica sin llenarla de lineas duras.
                    pathEffect = if (isBase) null
                    else PathEffect.dashPathEffect(floatArrayOf(3f, 7f))
                )
                val layout = textMeasurer.measure(valueFormatter(value), labelStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        leftPadPx - with(density) { 7.dp.toPx() } - layout.size.width,
                        y - layout.size.height / 2f
                    )
                )
            }

            drawHourLabels(
                textMeasurer = textMeasurer,
                labelStyle = labelStyle,
                xStart = xStart,
                xEnd = xEnd,
                xFor = ::xFor,
                baselineY = baseY + with(density) { 7.dp.toPx() },
                plotRight = size.width
            )

            // ---------- Serie ----------
            val linePath = Path()
            val areaPath = Path()
            buildSmoothPaths(
                points = sorted,
                xFor = ::xFor,
                yFor = ::yFor,
                baseY = baseY,
                linePath = linePath,
                areaPath = areaPath
            )

            // El barrido se hace RECORTANDO el area de dibujo, no cortando la
            // lista de puntos: asi la curva no se re-suaviza en cada frame y
            // el crecimiento es perfectamente continuo.
            val revealWidth = leftPadPx + plotWidth * progress.value
            clipRect(left = 0f, top = 0f, right = revealWidth, bottom = size.height) {

                // Degradado de varias paradas: intenso junto a la linea,
                // apagandose antes de llegar al eje para no tapar la rejilla.
                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to lineColor.copy(alpha = 0.42f),
                            0.35f to lineColor.copy(alpha = 0.20f),
                            0.75f to lineColor.copy(alpha = 0.05f),
                            1.0f to Color.Transparent
                        ),
                        startY = topPad,
                        endY = baseY
                    )
                )

                // Halo bajo la linea: la separa del area y le da cuerpo. Es
                // lo que diferencia un trazo plano de uno con acabado.
                drawPath(
                    path = linePath,
                    color = lineColor.copy(alpha = 0.22f),
                    style = Stroke(width = with(density) { 6.dp.toPx() }, cap = StrokeCap.Round)
                )
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(width = with(density) { 2.4.dp.toPx() }, cap = StrokeCap.Round)
                )
            }

            // ---------- Marcador del maximo del dia ----------
            // Solo cuando el pico no es el ultimo punto: si coinciden, el
            // marcador de "ahora" ya lo cubre y dos circulos encimados se
            // ven como un error.
            if (progress.value > 0.98f && peakIndex != sorted.lastIndex && sorted.size > 3) {
                val peak = sorted[peakIndex]
                val px = xFor(peak.hourOfDay)
                val py = yFor(peak.value)
                drawCircle(surfaceColor, with(density) { 4.5.dp.toPx() }, Offset(px, py))
                drawCircle(
                    color = lineColor,
                    radius = with(density) { 4.5.dp.toPx() },
                    center = Offset(px, py),
                    style = Stroke(width = with(density) { 1.8.dp.toPx() })
                )

                // Etiqueta del pico, solo si hay espacio: mas vale omitirla
                // que dibujarla encima del borde.
                val peakLabel = "máx " + valueFormatter(peak.value)
                val layout = textMeasurer.measure(peakLabel, labelStyle)
                val labelX = (px - layout.size.width / 2f)
                    .coerceIn(leftPadPx, (size.width - layout.size.width).coerceAtLeast(leftPadPx))
                val labelY = py - layout.size.height - with(density) { 9.dp.toPx() }
                if (labelY > 0f) {
                    drawText(textLayoutResult = layout, topLeft = Offset(labelX, labelY))
                }
            }

            // ---------- Punto "ahora" ----------
            val last = sorted.last()
            val lastCenter = Offset(xFor(last.hourOfDay), yFor(last.value))
            // Dos halos concentricos: sugiere un indicador vivo, como el
            // punto de estado del Panel.
            drawCircle(lineColor.copy(alpha = 0.14f), with(density) { 11.dp.toPx() }, lastCenter)
            drawCircle(lineColor.copy(alpha = 0.28f), with(density) { 7.dp.toPx() }, lastCenter)
            drawCircle(surfaceColor, with(density) { 4.2.dp.toPx() }, lastCenter)
            drawCircle(lineColor, with(density) { 3.dp.toPx() }, lastCenter)

            // ---------- Inspeccion ----------
            selectedIndex?.let { index ->
                val point = sorted.getOrNull(index) ?: return@let
                val px = xFor(point.hourOfDay)
                val py = yFor(point.value)

                // Guia vertical punteada: marca el instante sin tapar la curva.
                drawLine(
                    color = lineColor.copy(alpha = 0.45f),
                    start = Offset(px, topPad),
                    end = Offset(px, baseY),
                    strokeWidth = with(density) { 1.dp.toPx() },
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                )
                drawCircle(surfaceColor, with(density) { 5.5.dp.toPx() }, Offset(px, py))
                drawCircle(lineColor, with(density) { 4.dp.toPx() }, Offset(px, py))

                val label = formatHour(point.hourOfDay) + "   " + valueFormatter(point.value) + unit
                val layout = textMeasurer.measure(label, tooltipStyle)
                val boxPad = with(density) { 8.dp.toPx() }
                val boxWidth = layout.size.width + boxPad * 2
                val boxHeight = layout.size.height + boxPad
                // Se mantiene dentro del area de dibujo: junto al borde
                // derecho se corre a la izquierda en vez de recortarse.
                val maxLeft = (size.width - boxWidth).coerceAtLeast(leftPadPx)
                val boxLeft = (px - boxWidth / 2f).coerceIn(leftPadPx, maxLeft)
                val boxTop = 0f

                drawRoundRect(
                    color = tooltipBackground,
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(boxWidth, boxHeight),
                    cornerRadius = CornerRadius(with(density) { 7.dp.toPx() })
                )
                // Borde del color de la serie: ata el tooltip a la linea que
                // esta describiendo.
                drawRoundRect(
                    color = lineColor.copy(alpha = 0.45f),
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(boxWidth, boxHeight),
                    cornerRadius = CornerRadius(with(density) { 7.dp.toPx() }),
                    style = Stroke(width = with(density) { 1.dp.toPx() })
                )
                // Puntero triangular hacia el punto: deja claro a que momento
                // corresponde el valor, sobre todo cuando la caja se corrio.
                val tipSize = with(density) { 5.dp.toPx() }
                val tipX = px.coerceIn(boxLeft + tipSize * 2, boxLeft + boxWidth - tipSize * 2)
                val pointer = Path().apply {
                    moveTo(tipX - tipSize, boxTop + boxHeight)
                    lineTo(tipX + tipSize, boxTop + boxHeight)
                    lineTo(tipX, boxTop + boxHeight + tipSize)
                    close()
                }
                drawPath(pointer, tooltipBackground)

                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(boxLeft + boxPad, boxTop + boxPad / 2f)
                )
            }
        }
    }
}

/** Punto mas cercano al x tocado, en coordenadas de pantalla. */
private fun nearestIndex(
    touchX: Float,
    canvasWidth: Float,
    leftPad: Float,
    points: List<TrendPoint>,
    xStart: Float,
    xSpan: Float
): Int? {
    val plotWidth = canvasWidth - leftPad
    if (plotWidth <= 0f) return null
    val fraction = ((touchX - leftPad) / plotWidth).coerceIn(0f, 1f)
    val targetHour = xStart + fraction * xSpan
    return points.indices.minByOrNull { abs(points[it].hourOfDay - targetHour) }
}

/**
 * Construye la curva suavizada y su area.
 *
 * Bezier cubica con puntos de control a media distancia horizontal entre cada
 * par de puntos: suaviza sin generar sobretiros. Eso importa porque un
 * sobretiro dibujaria potencia negativa en un valle donde nunca la hubo.
 */
private fun buildSmoothPaths(
    points: List<TrendPoint>,
    xFor: (Float) -> Float,
    yFor: (Float) -> Float,
    baseY: Float,
    linePath: Path,
    areaPath: Path
) {
    if (points.isEmpty()) return

    var previousX = xFor(points.first().hourOfDay)
    var previousY = yFor(points.first().value)
    linePath.moveTo(previousX, previousY)
    areaPath.moveTo(previousX, baseY)
    areaPath.lineTo(previousX, previousY)

    for (i in 1 until points.size) {
        val currentX = xFor(points[i].hourOfDay)
        val currentY = yFor(points[i].value)
        val midX = (previousX + currentX) / 2f
        linePath.cubicTo(midX, previousY, midX, currentY, currentX, currentY)
        areaPath.cubicTo(midX, previousY, midX, currentY, currentX, currentY)
        previousX = currentX
        previousY = currentY
    }

    areaPath.lineTo(previousX, baseY)
    areaPath.close()
}

/** Etiquetas de hora, espaciadas para que no se solapen. */
private fun DrawScope.drawHourLabels(
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    xStart: Float,
    xEnd: Float,
    xFor: (Float) -> Float,
    baselineY: Float,
    plotRight: Float
) {
    // Paso adaptativo: con pocas horas de datos se etiqueta cada hora; con el
    // dia completo, cada 4. El criterio es no pasar de unas 6 etiquetas.
    val span = xEnd - xStart
    val step = when {
        span <= 3f -> 1f
        span <= 8f -> 2f
        span <= 14f -> 3f
        else -> 4f
    }

    var hour = ceil(xStart / step) * step
    var lastRight = -Float.MAX_VALUE
    while (hour <= xEnd) {
        val layout = textMeasurer.measure(formatHourShort(hour), labelStyle)
        val x = xFor(hour) - layout.size.width / 2f
        // Solo se dibuja si no pisa la anterior ni se sale del area.
        if (x > lastRight + 10f && x + layout.size.width <= plotRight) {
            drawText(textLayoutResult = layout, topLeft = Offset(x, baselineY))
            lastRight = x + layout.size.width
        }
        hour += step
    }
}

/** 13.5 -> "1:30pm", para el tooltip. */
private fun formatHour(hourOfDay: Float): String {
    val totalMinutes = (hourOfDay * 60).roundToInt().coerceIn(0, 24 * 60)
    val hour24 = (totalMinutes / 60) % 24
    val minutes = totalMinutes % 60
    val suffix = if (hour24 < 12) "am" else "pm"
    val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
    return hour12.toString() + ":" + minutes.toString().padStart(2, '0') + suffix
}

/** 13.0 -> "1pm", para el eje X donde el espacio es escaso. */
private fun formatHourShort(hourOfDay: Float): String {
    val hour24 = hourOfDay.roundToInt().coerceIn(0, 24) % 24
    val suffix = if (hour24 < 12) "am" else "pm"
    val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
    return hour12.toString() + suffix
}

/** Techo limpio para el eje Y: 1347 -> 1500. */
private fun niceCeiling(value: Float): Float {
    if (value <= 0f) return 1f
    val magnitude = 10.0.pow(floor(log10(value.toDouble()))).toFloat()
    val normalized = value / magnitude
    val stepped = when {
        normalized <= 1f -> 1f
        normalized <= 1.5f -> 1.5f
        normalized <= 2f -> 2f
        normalized <= 3f -> 3f
        normalized <= 5f -> 5f
        normalized <= 7.5f -> 7.5f
        else -> 10f
    }
    return stepped * magnitude
}
