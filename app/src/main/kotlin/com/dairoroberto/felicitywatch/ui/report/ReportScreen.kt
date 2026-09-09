package com.dairoroberto.felicitywatch.ui.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.ui.components.ChartPoint
import com.dairoroberto.felicitywatch.ui.components.ChartSeries
import com.dairoroberto.felicitywatch.ui.components.ChartZoomState
import com.dairoroberto.felicitywatch.ui.components.HorizontalBarEntry
import com.dairoroberto.felicitywatch.ui.components.HorizontalBarList
import com.dairoroberto.felicitywatch.ui.components.HourlyRange
import com.dairoroberto.felicitywatch.ui.components.HourlyRangeBarList
import com.dairoroberto.felicitywatch.ui.components.LineAreaChart
import com.dairoroberto.felicitywatch.ui.components.MultiLineChart
import com.dairoroberto.felicitywatch.ui.components.NiceAxis
import com.dairoroberto.felicitywatch.ui.components.niceAxis
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val ELECTRICAL_TABS = listOf("PV", "Batería", "FV/Carga/Descarga", "Corriente", "Generación", "Consumo", "Consumo nocturno", "Voltaje", "Excedente")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(viewModel: ReportViewModel = hiltViewModel()) {
    val dateRange by viewModel.dateRange.collectAsState()
    val readings by viewModel.readings.collectAsState()
    val liveGridState by viewModel.liveGridState.collectAsState()
    val allReadingsInRetention by viewModel.allReadingsInRetention.collectAsState()
    val nightWindowStartHour by viewModel.nightWindowStartHour.collectAsState()
    val nightWindowEndHour by viewModel.nightWindowEndHour.collectAsState()
    val lowVoltageThreshold by viewModel.lowVoltageThreshold.collectAsState()
    val colors = LocalFelicityColors.current

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showPeriodMenu by remember { mutableStateOf(false) }
    var showCustomPickers by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    // Igual que en el Panel: "hace X" depende del reloj, no solo de los
    // datos — sin este tick, el texto de última lectura queda congelado.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            now = Instant.now()
        }
    }

    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale("es", "ES"))

    if (showStartPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dateRange.start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val newStart = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        viewModel.setCustomRange(newStart, dateRange.end)
                    }
                    showStartPicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = state) }
    }

    if (showEndPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dateRange.end.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val newEnd = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        viewModel.setCustomRange(dateRange.start, newEnd)
                    }
                    showEndPicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = state) }
    }

    // La pestaña "Corriente" (índice 3 de Eléctrico) usa su propio feed
    // continuo con scroll infinito, independiente del selector de fecha de
    // arriba — un corte que cruza la medianoche de un rango de "un solo
    // día" quedaba cortado artificialmente en dos, dando horas mal
    // contadas. El filtro se sigue mostrando en Corriente por consistencia
    // visual con las demás pestañas, pero elegir una fecha ahí no afecta
    // el feed (que siempre muestra todo el historial disponible).
    val isCorrienteTab = selectedTab == 3

    Column(Modifier.fillMaxSize()) {
        run {
            val isToday = dateRange.start == dateRange.end && dateRange.start == LocalDate.now()
            val isYesterday = dateRange.start == dateRange.end && dateRange.start == LocalDate.now().minusDays(1)
            val isLast7 = dateRange.start == LocalDate.now().minusDays(6) && dateRange.end == LocalDate.now()
            val isLast30 = dateRange.start == LocalDate.now().minusDays(29) && dateRange.end == LocalDate.now()
            val periodLabel = when {
                isToday -> "Hoy"
                isYesterday -> "Ayer"
                isLast7 -> "7 días"
                isLast30 -> "30 días"
                else -> "Personalizado"
            }
            val rangeLabel = if (dateRange.start == dateRange.end) {
                dateFormatter.format(dateRange.start)
            } else {
                "${dateFormatter.format(dateRange.start)} — ${dateFormatter.format(dateRange.end)}"
            }

            // Filtro compacto: fila de flechas + fecha (perfectamente
            // centrada, mismo ancho reservado a ambos lados) y el chip de
            // periodo en su propia fila debajo — antes competían por
            // espacio en la misma fila, lo que descentraba la fecha
            // respecto al ancho total de la pantalla.
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.shiftRange(forward = false) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Periodo anterior", modifier = Modifier.size(20.dp))
                    }
                    Text(
                        rangeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = colors.textHi,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(onClick = { viewModel.shiftRange(forward = true) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Periodo siguiente", modifier = Modifier.size(20.dp))
                    }
                }

                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(colors.accent.copy(alpha = 0.14f))
                            .clickable { showPeriodMenu = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            periodLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.accent
                        )
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.padding(start = 2.dp).size(16.dp)
                        )
                    }
                    DropdownMenu(expanded = showPeriodMenu, onDismissRequest = { showPeriodMenu = false }) {
                        DropdownMenuItem(text = { Text("Hoy") }, onClick = {
                            viewModel.setToday(); showPeriodMenu = false
                        })
                        DropdownMenuItem(text = { Text("Ayer") }, onClick = {
                            viewModel.setYesterday(); showPeriodMenu = false
                        })
                        DropdownMenuItem(text = { Text("7 días") }, onClick = {
                            viewModel.setLast7Days(); showPeriodMenu = false
                        })
                        DropdownMenuItem(text = { Text("30 días") }, onClick = {
                            viewModel.setLast30Days(); showPeriodMenu = false
                        })
                        DropdownMenuItem(text = { Text("Personalizado") }, onClick = {
                            showPeriodMenu = false
                            showCustomPickers = true
                        })
                    }
                }
            }

            if (showCustomPickers) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f)) {
                        Text(dateFormatter.format(dateRange.start))
                    }
                    Text("—", modifier = Modifier.padding(top = 12.dp))
                    OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.weight(1f)) {
                        Text(dateFormatter.format(dateRange.end))
                    }
                }
            }
        }

        // ScrollableTabRow (no TabRow fijo) porque con nombres largos como
        // "FV/Carga/Descarga" un ancho fijo comprime el texto y lo hace
        // saltar de línea — el scroll horizontal evita ese problema sin
        // tener que acortar los nombres.
        androidx.compose.material3.ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = colors.surface2,
            edgePadding = 12.dp
        ) {
            ELECTRICAL_TABS.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            title,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                )
            }
        }

        if (isCorrienteTab) {
            // Feed propio con LazyColumn (scroll infinito por día) — no
            // puede vivir dentro del Column().verticalScroll() genérico de
            // las demás pestañas (un LazyColumn anidado en un scroll
            // normal rompe la medición de alto).
            GridContinuousFeed(
                colors = colors,
                now = now,
                liveGridState = liveGridState,
                allReadingsInRetention = allReadingsInRetention,
                targetDate = dateRange.start
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                when (selectedTab) {
                    0 -> PvGenerationCard(readings, dateRange, colors, now)
                    1 -> BatterySocCard(readings, dateRange, colors)
                    2 -> PvChargeDischargeCard(readings, dateRange, colors)
                    4 -> DailyGenerationReportCard(colors, allReadingsInRetention)
                    5 -> GridPoweredConsumptionCard(readings, dateRange, colors)
                    6 -> NightConsumptionReportCard(
                        readings = readings,
                        allReadingsInRetention = allReadingsInRetention,
                        startHour = nightWindowStartHour,
                        endHour = nightWindowEndHour,
                        onStartHourChange = { viewModel.setNightWindowStartHour(it) },
                        onEndHourChange = { viewModel.setNightWindowEndHour(it) },
                        colors = colors
                    )
                    7 -> VoltageReportCard(readings, dateRange, colors, now, lowVoltageThreshold)
                    8 -> SolarSurplusReportCard(readings, dateRange, colors, now)
                }
            }
        }
    }
}

@Composable
private fun PvGenerationCard(
    readings: List<com.dairoroberto.felicitywatch.data.local.PowerReadingEntity>,
    dateRange: DateRange,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors,
    now: Instant
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "GENERACIÓN FOTOVOLTAICA",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow
            )

            // epoch millis no cabe con precisión en Float (13 dígitos vs
            // ~7 significativos): se resta el mínimo antes de convertir
            // para no perder resolución de tiempo entre puntos.
            val filteredReadings = readings.filter { it.pvPowerWatts != null }
            val startOfDay = dateRange.start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDay = dateRange.end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
            val points = filteredReadings
                .map { ChartPoint((it.timestampEpochMillis - startOfDay).toFloat(), it.pvPowerWatts!!.toFloat()) }

            if (points.size < 2) {
                Text(
                    "No hay suficiente historial registrado en este periodo.\nEl historial se acumula localmente mientras la app monitorea.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid,
                    modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)
                )
            } else {
                val yAxis = niceAxis(0f, points.maxOf { it.y }.coerceAtLeast(1f))
                // Formato 12h ("01:00pm") consistente con el resto de reportes.
                val tooltipTimeFormatter = DateTimeFormatter.ofPattern("hh:mm").withLocale(Locale("es", "ES"))
                val zoomState = remember(points) { ChartZoomState() }
                // El eje arranca/termina donde realmente hay generación (>0W),
                // no a las 00:00/23:59 fijas — de lo contrario más de medio
                // gráfico es una línea plana en 0 durante la noche, sin
                // generación real que mostrar. Se deja un margen de 30 min a
                // cada lado para que la curva no arranque pegada al borde.
                val generatingPoints = points.filter { it.y > 0f }
                val marginMillis = 30 * 60 * 1000f
                val (baseMinX, baseMaxX) = if (generatingPoints.isNotEmpty()) {
                    val min = (generatingPoints.minOf { it.x } - marginMillis).coerceAtLeast(0f)
                    val max = (generatingPoints.maxOf { it.x } + marginMillis).coerceAtMost((endOfDay - startOfDay).toFloat())
                    min to max
                } else {
                    0f to (endOfDay - startOfDay).toFloat()
                }
                Box(modifier = Modifier.padding(top = 10.dp)) {
                    LineAreaChart(
                        points = points,
                        lineColor = colors.accent,
                        gridColor = colors.hairline,
                        minY = yAxis.min,
                        maxYOverride = yAxis.max,
                        minXOverride = baseMinX,
                        maxXOverride = baseMaxX,
                        yAxis = yAxis,
                        yUnit = "W",
                        textColor = colors.textLow,
                        zoomState = zoomState,
                        tooltipLabel = { point ->
                            val watts = point.y.toInt()
                            val valueText = if (watts >= 1000) {
                                String.format(Locale("es", "ES"), "PV: %.2f kW", watts / 1000.0)
                            } else {
                                "PV: $watts W"
                            }
                            val time = format12Hour(
                                tooltipTimeFormatter,
                                Instant.ofEpochMilli(startOfDay + point.x.toLong()).atZone(ZoneId.systemDefault())
                            )
                            valueText to time
                        }
                    )
                }
                ChartXAxis(
                    zoomState = zoomState,
                    baseMinX = baseMinX,
                    baseMaxX = baseMaxX,
                    originEpochMillis = startOfDay,
                    colors = colors
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Máximo: ${points.maxOf { it.y }.toInt()} W",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow
                    )
                    Text(
                        "Promedio: ${(points.sumOf { it.y.toDouble() } / points.size).toInt()} W",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow
                    )
                    Text(
                        "${points.size} lecturas",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow
                    )
                }
                val lastReadingMillis = filteredReadings.maxOf { it.timestampEpochMillis }
                val lastReadingInstant = Instant.ofEpochMilli(lastReadingMillis)
                val secondsAgo = Duration.between(lastReadingInstant, now).seconds.coerceAtLeast(0)
                val lastReadingTimeFormatter = DateTimeFormatter.ofPattern("hh:mm").withLocale(Locale("es", "ES"))
                Text(
                    "Última lectura: ${format12Hour(lastReadingTimeFormatter, lastReadingInstant.atZone(ZoneId.systemDefault()))} (hace ${secondsAgo}s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow,
                    modifier = Modifier.padding(top = 10.dp)
                )

                peakGenerationWindow(filteredReadings, ZoneId.systemDefault())?.let { window ->
                    PeakGenerationCard(window = window, colors = colors)
                }
            }
        }
    }
}

/** Ventana horaria de mayor generación solar promedio: hora de inicio, hora
 * de fin (exclusiva) y el promedio de PV en ese tramo. */
private data class PeakGenerationWindow(
    val startHour: Int,
    val endHour: Int,
    val averageWatts: Double
)

/**
 * Encuentra el tramo de horas CONSECUTIVAS con mayor generación solar
 * promedio del período — no la hora suelta más alta, sino una ventana (ej.
 * "11am-2pm") que es lo que de verdad sirve para decidir cuándo usar los
 * equipos de mayor consumo.
 *
 * Se agrupa por HORA DEL DÍA (0..23), promediando todas las lecturas de esa
 * hora sin importar a qué día del rango pertenecen — así en un rango de
 * varios días la ventana resultante es la más representativa del período
 * completo, no la de un solo día suelto.
 *
 * El tamaño de la ventana ([WINDOW_HOURS]) se fija en 3 horas: es un
 * intervalo que comunica algo accionable ("enciende la lavadora entre estas
 * horas"), mientras que una sola hora es demasiado estrecho para planear
 * nada y un rango de más horas deja de ser "el pico" para ser "el día".
 */
private fun peakGenerationWindow(
    readings: List<com.dairoroberto.felicitywatch.data.local.PowerReadingEntity>,
    zone: ZoneId
): PeakGenerationWindow? {
    val byHour = readings
        .mapNotNull { r -> r.pvPowerWatts?.let { watts -> Instant.ofEpochMilli(r.timestampEpochMillis).atZone(zone).hour to watts } }
        .groupBy({ it.first }, { it.second })

    if (byHour.isEmpty()) return null

    val hourlyAverage = (0..23).map { hour -> byHour[hour]?.average() ?: 0.0 }
    // Sin generación real en ningún tramo (ej. rango sin ninguna lectura de
    // día) no tiene sentido reportar una "ventana pico" de puros ceros.
    if (hourlyAverage.all { it <= 0.0 }) return null

    val windowSize = WINDOW_HOURS.coerceAtMost(24)
    var bestStart = 0
    var bestAverage = -1.0
    for (start in 0..(24 - windowSize)) {
        val windowAverage = hourlyAverage.subList(start, start + windowSize).average()
        if (windowAverage > bestAverage) {
            bestAverage = windowAverage
            bestStart = start
        }
    }

    return PeakGenerationWindow(
        startHour = bestStart,
        endHour = bestStart + windowSize,
        averageWatts = bestAverage
    )
}

private const val WINDOW_HOURS = 3

/**
 * Card de la hora pico de generación — deliberadamente distinto de los
 * números en fila de arriba (Máximo/Promedio/Lecturas): esto es la
 * respuesta a una pregunta concreta ("¿cuándo me conviene usar los equipos
 * grandes?"), así que se presenta como una recomendación con su propio
 * espacio, no como un dato más en la lista.
 */
@Composable
private fun PeakGenerationCard(
    window: PeakGenerationWindow,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
) {
    val hourFormatter = remember { DateTimeFormatter.ofPattern("h:mm a").withLocale(Locale("es", "ES")) }
    val rangeLabel = "${formatClockHour(window.startHour, hourFormatter)} – " +
        formatClockHour(window.endHour % 24, hourFormatter)
    val averageLabel = if (window.averageWatts >= 1000) {
        String.format(Locale("es", "ES"), "%.2f kW", window.averageWatts / 1000.0)
    } else {
        "${window.averageWatts.toInt()} W"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(colors.pvAccent.copy(alpha = 0.16f), colors.pvAccent.copy(alpha = 0.05f))
                )
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.pvAccent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.WbSunny,
                contentDescription = null,
                tint = colors.pvAccent,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                "HORA PICO DE GENERACIÓN",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textLow
            )
            Text(
                rangeLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = colors.textHi,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                "Promedio de $averageLabel en este tramo — el mejor momento para usar equipos de mayor consumo.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMid,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** hora entera (0..23) -> "11:00 am", reutilizando el formato ya usado en el
 * resto de la app. Se construye un ZonedDateTime arbitrario del día actual
 * solo para poder aplicar el DateTimeFormatter sobre una hora en punto. */
private fun formatClockHour(hour: Int, formatter: DateTimeFormatter): String {
    val zoned = LocalDate.now().atTime(hour, 0).atZone(ZoneId.systemDefault())
    return formatter.format(zoned).lowercase()
}

/**
 * Evolución del voltaje por hora, para el día (o rango) elegido en el filtro
 * de arriba — mismo patrón que "PV" (día por hora, zoom, tooltip), pero con
 * dos series que nunca coinciden en el tiempo: el voltaje de la RED cuando
 * hay corriente, y el de SALIDA del inversor hacia la casa cuando no la hay.
 *
 * Son dos magnitudes de la misma naturaleza (AC, 110/120V) y se leen en el
 * mismo eje — se muestran como series distintas, no una sola línea
 * combinada, para que el usuario vea de un vistazo cuál fuente alimentaba
 * la casa en cada tramo del día.
 */
@Composable
private fun VoltageReportCard(
    readings: List<com.dairoroberto.felicitywatch.data.local.PowerReadingEntity>,
    dateRange: DateRange,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors,
    now: Instant,
    lowVoltageThreshold: Int
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = null,
                    tint = colors.textLow,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    "VOLTAJE POR HORA",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow,
                    modifier = Modifier.padding(start = 5.dp)
                )
            }

            val zone = ZoneId.systemDefault()
            // Se exige >= 1V, no solo != null: sin corriente de la calle el
            // inversor deja en la entrada AC fracciones de voltio (0,5-0,6V
            // medidos) en vez de un 0 limpio, y contarlas como lecturas
            // válidas metía esos residuos en el mínimo, en el promedio y en el
            // conteo de "lecturas bajo el umbral". Nada legítimo cae bajo 1V:
            // la vía que alimenta la casa está en la escala de 110/120V.
            val voltageReadings = readings.filter {
                (it.gridVoltage ?: 0.0) >= 1.0 || (it.outputVoltage ?: 0.0) >= 1.0
            }

            if (voltageReadings.size < 2) {
                Text(
                    "No hay suficiente historial de voltaje registrado en este periodo.\n" +
                        "El historial se acumula localmente mientras la app monitorea.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid,
                    modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)
                )
            } else {
                val lastReadingMillis = voltageReadings.maxOf { it.timestampEpochMillis }
                val lastReadingInstant = Instant.ofEpochMilli(lastReadingMillis)
                val currentHour = lastReadingInstant.atZone(zone).hour

                // Resumen PRIMERO, no al final: es lo que responde de un
                // vistazo "¿cómo estuvo el voltaje hoy?", antes de bajar al
                // detalle hora por hora. Mismo patrón que ya usa el modal de
                // detalle del Panel (MetricDetailDialog).
                // La vía se elige por el estado de red de cada lectura
                // (gridPower < 1W = sin corriente), el mismo criterio del card
                // del Panel — no con un elvis entre ambos campos, que se
                // quedaba con el residuo de la entrada AC inactiva y hundía el
                // mínimo a 0,5V.
                val allValues = voltageReadings.mapNotNull { r ->
                    val online = (r.gridPowerWatts ?: 0) >= 1
                    (if (online) r.gridVoltage else r.outputVoltage)?.takeIf { it >= 1.0 }
                }
                // minOrNull, no min(): `voltageReadings` filtra por "trae algún
                // voltaje en alguna vía", pero `allValues` exige además que sea
                // la vía ACTIVA de esa lectura, así que puede quedar vacía
                // (p.ej. lecturas donde solo vino el residuo de la vía
                // inactiva). Con min() eso era un NoSuchElementException.
                val minValue = allValues.minOrNull()
                if (minValue != null) {
                    VoltageSummaryStrip(
                        minV = minValue,
                        avgV = allValues.average(),
                        maxV = allValues.max(),
                        colors = colors
                    )
                }

                // Aviso agregado: cuantas horas del periodo registraron al
                // menos una lectura por debajo del umbral — responde de un
                // vistazo "¿hubo caidas de voltaje?" antes de bajar a ver
                // hora por hora cual.
                val lowHoursCount = allValues.count { it < lowVoltageThreshold }
                if (lowHoursCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            "$lowHoursCount lectura${if (lowHoursCount == 1) "" else "s"} por debajo de ${lowVoltageThreshold}V en este periodo",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 7.dp)
                        )
                    }
                }

                val secondsAgo = Duration.between(lastReadingInstant, now).seconds.coerceAtLeast(0)
                val lastReadingTimeFormatter = DateTimeFormatter.ofPattern("hh:mm").withLocale(Locale("es", "ES"))
                Text(
                    "Última lectura: ${format12Hour(lastReadingTimeFormatter, lastReadingInstant.atZone(zone))} (hace ${secondsAgo}s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow,
                    modifier = Modifier.padding(top = 10.dp)
                )

                // Se agrupa por la HORA DEL DÍA (0..23) de cada lectura, sin
                // importar a qué día del rango pertenece: si el filtro cubre
                // varios días, cada fila resume esa hora combinando todos
                // los días del rango. Para un solo día (el uso normal desde
                // el filtro de fecha) cada fila es esa hora exacta de ese día.
                //
                // Orden DESCENDENTE desde la hora de la última lectura: lo
                // más reciente es lo que interesa primero, igual que
                // cualquier bitácora — no tiene sentido obligar a bajar 23
                // filas para ver "ahora mismo".
                // takeIf { >= 1 } por lo mismo: el residuo que el inversor deja
                // en la vía inactiva no es una medición, y sin filtrarlo el
                // mínimo de cada hora salía en 0,5 V.
                val gridByHour = hourlyRanges(voltageReadings, zone) { it.gridVoltage?.takeIf { v -> v >= 1.0 } }
                val outputByHour = hourlyRanges(voltageReadings, zone) { it.outputVoltage?.takeIf { v -> v >= 1.0 } }
                val hourOrder = (0..23).sortedByDescending { hour ->
                    if (hour <= currentHour) hour + 24 else hour
                }
                val gridOrdered = hourOrder.map { gridByHour[it] }
                val outputOrdered = hourOrder.map { outputByHour[it] }

                val hasGrid = gridOrdered.any { it != null }
                val hasOutput = outputOrdered.any { it != null }

                if (hasGrid) {
                    VoltageSeriesHeader(label = "Red — con corriente", color = colors.accent)
                    HourlyRangeBarList(
                        entries = gridOrdered,
                        barColor = colors.accent,
                        trackColor = colors.hairline.copy(alpha = 0.4f),
                        labelColor = colors.textLow,
                        valueColor = colors.textHi,
                        valueFormatter = { "%.0f".format(it) },
                        lowThreshold = lowVoltageThreshold.toFloat(),
                        lowColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (hasOutput) {
                    VoltageSeriesHeader(
                        label = "Salida del inversor — sin corriente",
                        color = colors.chargeAccent,
                        modifier = Modifier.padding(top = if (hasGrid) 20.dp else 0.dp)
                    )
                    HourlyRangeBarList(
                        entries = outputOrdered,
                        barColor = colors.chargeAccent,
                        trackColor = colors.hairline.copy(alpha = 0.4f),
                        labelColor = colors.textLow,
                        valueColor = colors.textHi,
                        valueFormatter = { "%.0f".format(it) },
                        lowThreshold = lowVoltageThreshold.toFloat(),
                        lowColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

/** Encabezado de una de las dos series (Red / Salida del inversor): un
 * punto del color de la serie + etiqueta, más discreto que un bloque de
 * texto en mayúsculas y en negrita como antes. */
@Composable
private fun VoltageSeriesHeader(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(top = 14.dp)
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(7.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(color)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(start = 7.dp)
        )
    }
}

/**
 * Tira de resumen (Mínimo / Promedio / Máximo) con separadores verticales
 * — mismo lenguaje visual que ya usa el modal de detalle del Panel, para
 * que un usuario que abrió ambos reconozca el patrón.
 */
@Composable
private fun VoltageSummaryStrip(
    minV: Double,
    avgV: Double,
    maxV: Double,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
) {
    val stats = listOf(
        "MÍNIMO" to minV,
        "PROMEDIO" to avgV,
        "MÁXIMO" to maxV
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        stats.forEachIndexed { index, (label, value) ->
            if (index > 0) {
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .padding(horizontal = 12.dp)
                        .size(width = 1.dp, height = 26.dp)
                        .background(colors.hairline)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textLow
                )
                Text(
                    String.format(Locale("es", "ES"), "%.1f V", value),
                    fontFamily = com.dairoroberto.felicitywatch.ui.theme.JetBrainsMonoFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = colors.textHi,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

/**
 * Excedente solar acumulado hora por hora del día elegido en el filtro de
 * arriba: 24 filas fijas (00h..23h), cada una con el kWh que sobró de
 * generación sobre consumo en esa hora, y el total del día debajo.
 *
 * Usa [SolarSurplusEstimator], que integra la potencia NETA (PV − consumo)
 * en el tiempo — no resta dos totales de energía del día, que perdería la
 * variación minuto a minuto. Solo se acumulan los tramos con excedente
 * positivo; un tramo de déficit no resta, simplemente no aporta.
 */
@Composable
private fun SolarSurplusReportCard(
    readings: List<com.dairoroberto.felicitywatch.data.local.PowerReadingEntity>,
    dateRange: DateRange,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors,
    now: Instant
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "EXCEDENTE SOLAR POR HORA",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow
            )

            val zone = ZoneId.systemDefault()
            val usableReadings = readings.filter { it.pvPowerWatts != null && it.loadPowerWatts != null }

            if (usableReadings.size < 2) {
                Text(
                    "No hay suficiente historial registrado en este periodo.\n" +
                        "El historial se acumula localmente mientras la app monitorea.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid,
                    modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)
                )
            } else {
                val hourlyKwh = com.dairoroberto.felicitywatch.domain.usecase.SolarSurplusEstimator
                    .hourlySurplus(readings, zone)

                // El día es "hoy" si el filtro selecciona un único día y ese
                // día es el actual — ahí las horas futuras no pueden tener
                // datos y se atenúan en vez de mostrarse como 0.0kWh (que se
                // leería como "no hubo excedente" en vez de "no ha llegado").
                val today = java.time.LocalDate.now(zone)
                val isToday = dateRange.start == dateRange.end && dateRange.start == today
                val currentHour = now.atZone(zone).hour

                val total = if (isToday) {
                    hourlyKwh.filterIndexed { hour, _ -> hour <= currentHour }.sum()
                } else {
                    hourlyKwh.sum()
                }

                // Total PRIMERO, no al final: responde de un vistazo "¿cuánto
                // sobró hoy?" antes de bajar al detalle hora por hora — mismo
                // criterio que el resumen del reporte de Voltaje.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "TOTAL DEL DÍA",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.textHi
                    )
                    Text(
                        String.format(Locale("es", "ES"), "%.2f kWh", total),
                        fontFamily = com.dairoroberto.felicitywatch.ui.theme.JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.pvAccent
                    )
                }
                if (isToday) {
                    Text(
                        "Acumulado hasta la hora actual — las horas que faltan del día se irán sumando.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(top = 14.dp, bottom = 14.dp), color = colors.hairline)

                // Orden DESCENDENTE desde la hora actual: lo más reciente
                // primero, igual que el reporte de Voltaje — no tiene
                // sentido bajar 23 filas para ver la hora en curso.
                val hourOrder = (0..23).sortedByDescending { hour ->
                    if (hour <= currentHour) hour + 24 else hour
                }
                val maxKwh = hourlyKwh.max()

                val entries = hourOrder.map { hour ->
                    val isFuture = isToday && hour > currentHour
                    HorizontalBarEntry(
                        label = "%02dh".format(hour),
                        value = if (isFuture) 0f else hourlyKwh[hour].toFloat(),
                        highlighted = !isFuture && hourlyKwh[hour] == maxKwh
                    )
                }

                HorizontalBarList(
                    entries = entries,
                    barColor = colors.pvAccent,
                    trackColor = colors.hairline.copy(alpha = 0.4f),
                    labelColor = colors.textMid,
                    valueColor = colors.textHi,
                    valueFormatter = { "%.2f kWh".format(Locale("es", "ES"), it) },
                    // Se fuerza el mismo techo de eje para las 24 filas — sin
                    // esto, cada llamada a HorizontalBarList normalizaría por
                    // separado y las barras dejarían de ser comparables entre
                    // sí en el nuevo orden.
                    maxValueOverride = maxKwh.toFloat().coerceAtLeast(0.01f)
                )
            }
        }
    }
}

/**
 * Agrupa las lecturas por hora del día (0..23) y calcula mín/promedio/máx de
 * [selector] para cada una. Devuelve una lista de 24 posiciones (índice =
 * hora); `null` en las horas sin ninguna lectura de esa magnitud.
 */
private fun hourlyRanges(
    readings: List<com.dairoroberto.felicitywatch.data.local.PowerReadingEntity>,
    zone: ZoneId,
    selector: (com.dairoroberto.felicitywatch.data.local.PowerReadingEntity) -> Double?
): List<HourlyRange?> {
    val byHour = readings
        .mapNotNull { reading -> selector(reading)?.let { value -> reading to value } }
        .groupBy { (reading, _) -> Instant.ofEpochMilli(reading.timestampEpochMillis).atZone(zone).hour }

    return (0..23).map { hour ->
        val values = byHour[hour]?.map { (_, value) -> value.toFloat() } ?: return@map null
        if (values.isEmpty()) return@map null
        HourlyRange(
            hour = hour,
            min = values.min(),
            average = values.average().toFloat(),
            max = values.max()
        )
    }
}

@Composable
private fun BatterySocCard(
    readings: List<com.dairoroberto.felicitywatch.data.local.PowerReadingEntity>,
    dateRange: DateRange,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "NIVEL DE BATERÍA (SOC)",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow
            )

            val filteredReadings = readings.filter { it.socPercent != null }
            val startOfDay = dateRange.start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDay = dateRange.end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
            val points = filteredReadings
                .map { ChartPoint((it.timestampEpochMillis - startOfDay).toFloat(), it.socPercent!!.toFloat()) }

            if (points.size < 2) {
                Text(
                    "No hay suficiente historial registrado en este periodo.\nEl historial se acumula localmente mientras la app monitorea.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid,
                    modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)
                )
            } else {
                // Formato 12h ("01:00pm") consistente con el resto de reportes.
                val tooltipTimeFormatter = DateTimeFormatter.ofPattern("hh:mm").withLocale(Locale("es", "ES"))
                val zoomState = remember(points) { ChartZoomState() }
                val baseMinX = 0f
                val baseMaxX = (endOfDay - startOfDay).toFloat()
                Box(modifier = Modifier.padding(top = 10.dp)) {
                    val yAxis = NiceAxis(0f, 100f, 20f, 6)
                    LineAreaChart(
                        points = points,
                        lineColor = MaterialTheme.colorScheme.secondary,
                        gradientColors = listOf(colors.green, colors.error),
                        gridColor = colors.hairline,
                        maxYOverride = 100f,
                        minXOverride = baseMinX,
                        maxXOverride = baseMaxX,
                        yAxis = yAxis,
                        yUnit = "%",
                        textColor = colors.textLow,
                        zoomState = zoomState,
                        tooltipLabel = { point ->
                            val time = format12Hour(
                                tooltipTimeFormatter,
                                Instant.ofEpochMilli(startOfDay + point.x.toLong()).atZone(ZoneId.systemDefault())
                            )
                            "SOC: ${point.y.toInt()} %" to time
                        }
                    )
                }
                ChartXAxis(
                    zoomState = zoomState,
                    baseMinX = baseMinX,
                    baseMaxX = baseMaxX,
                    originEpochMillis = startOfDay,
                    colors = colors
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Máximo: ${points.maxOf { it.y }.toInt()} %",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow
                    )
                    Text(
                        "Mínimo: ${points.minOf { it.y }.toInt()} %",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow
                    )
                    Text(
                        "${points.size} lecturas",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow
                    )
                }
            }
        }
    }
}

@Composable
private fun PvChargeDischargeCard(
    readings: List<com.dairoroberto.felicitywatch.data.local.PowerReadingEntity>,
    dateRange: DateRange,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "FV, CARGA Y DESCARGA",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow
            )

            val pvReadings = readings.filter { it.pvPowerWatts != null }
            val batteryReadings = readings.filter { it.batteryPowerWatts != null }
                .sortedBy { it.timestampEpochMillis }
            val startOfDay = dateRange.start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDay = dateRange.end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

            val pvPoints = pvReadings
                .map { ChartPoint((it.timestampEpochMillis - startOfDay).toFloat(), it.pvPowerWatts!!.toFloat()) }
            // Se mantiene el orden temporal completo (con huecos en NaN
            // donde la batería está en el signo contrario) en vez de
            // filtrar puntos — filtrar rompe el orden temporal del path
            // y dibuja diagonales largas conectando tramos lejanos cada
            // vez que la batería alterna entre cargar y descargar.
            val chargePoints = batteryReadings.map {
                val watts = it.batteryPowerWatts!!
                ChartPoint((it.timestampEpochMillis - startOfDay).toFloat(), if (watts > 0) watts.toFloat() else Float.NaN)
            }
            val dischargePoints = batteryReadings.map {
                val watts = it.batteryPowerWatts!!
                ChartPoint((it.timestampEpochMillis - startOfDay).toFloat(), if (watts < 0) watts.toFloat() else Float.NaN)
            }

            if (pvPoints.size < 2 && batteryReadings.size < 2) {
                Text(
                    "No hay suficiente historial registrado en este periodo.\nEl historial se acumula localmente mientras la app monitorea.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid,
                    modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)
                )
            } else {
                val series = listOf(
                    ChartSeries("FV", colors.accent, fill = false, points = pvPoints),
                    ChartSeries("Carga", colors.chargeAccent, fill = true, points = chargePoints),
                    ChartSeries("Descarga", MaterialTheme.colorScheme.secondary, fill = true, points = dischargePoints)
                )
                val allPoints = (pvPoints + chargePoints + dischargePoints).filterNot { it.y.isNaN() }
                val rawMinY = allPoints.minOf { it.y }.coerceAtMost(0f)
                val rawMaxY = allPoints.maxOf { it.y }.coerceAtLeast(rawMinY + 1f)
                val yAxis = niceAxis(rawMinY, rawMaxY)
                // Formato 12h ("01:00pm") consistente con el resto de reportes.
                val tooltipTimeFormatter = DateTimeFormatter.ofPattern("hh:mm").withLocale(Locale("es", "ES"))
                val zoomState = remember(pvPoints, batteryReadings) { ChartZoomState() }
                val baseMinX = 0f
                val baseMaxX = (endOfDay - startOfDay).toFloat()
                Box(modifier = Modifier.padding(top = 10.dp)) {
                    MultiLineChart(
                        series = series,
                        gridColor = colors.hairline,
                        minYOverride = yAxis.min,
                        maxYOverride = yAxis.max,
                        minXOverride = baseMinX,
                        maxXOverride = baseMaxX,
                        yAxis = yAxis,
                        yUnit = "W",
                        textColor = colors.textLow,
                        zoomState = zoomState,
                        tooltipLabel = { x, values ->
                            val time = format12Hour(
                                tooltipTimeFormatter,
                                Instant.ofEpochMilli(startOfDay + x.toLong()).atZone(ZoneId.systemDefault())
                            )
                            val lines = values.map { (s, value) -> "${s.name}: ${value.toInt()} W" }
                            time to lines
                        }
                    )
                }
                ChartXAxis(
                    zoomState = zoomState,
                    baseMinX = baseMinX,
                    baseMaxX = baseMaxX,
                    originEpochMillis = startOfDay,
                    colors = colors
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    series.forEach { s ->
                        Text(
                            "● ${s.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = s.color
                        )
                    }
                }
            }
        }
    }
}

private enum class GridEventFilter { ALL, OUTAGES, RESTORATIONS }

/** Formato 12h ("01:00pm" en vez de "13:00") — el patrón "a" de
 * DateTimeFormatter da "PM" en mayúsculas con espacio ("01:00 PM"), así que
 * se arma a mano en minúsculas y sin espacio. */
internal fun format12Hour(hourFormatter: DateTimeFormatter, zoned: java.time.ZonedDateTime): String {
    val suffix = if (zoned.hour < 12) "am" else "pm"
    return "${hourFormatter.format(zoned)}$suffix"
}

/** "4h 11min" o "35min" — duración de un tramo entre dos nodos del feed. */
private fun formatSegmentDuration(durationMillis: Long): String {
    val totalMinutes = durationMillis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}min" else "${minutes}min"
}

/** Un nodo de evento en el feed continuo — un punto de cambio de estado
 * real, o el ancla "Hora Actual". */
private data class FeedNode(
    val epochMillis: Long,
    val label: String,
    val isOutage: Boolean,
    val isRestoration: Boolean,
    val isNow: Boolean,
    val online: Boolean
)

/** Entrada de feed unificada: o un nodo de evento, o un separador de día
 * (se inserta uno cada vez que el día del nodo cambia respecto al
 * anterior, más reciente primero) — un solo modelo para que el LazyColumn
 * y el cálculo de índice de scroll usen exactamente los mismos índices,
 * sin desincronizarse entre sí. */
private sealed class FeedEntry {
    data class NowEntry(val node: FeedNode) : FeedEntry()
    data class EventEntry(val node: FeedNode) : FeedEntry()
    data class DaySeparator(val date: LocalDate) : FeedEntry()
}

/**
 * Feed continuo de corriente eléctrica: en vez de recortar por un rango de
 * fecha (lo que antes partía artificialmente un corte que cruza la
 * medianoche en dos pedazos, dando horas mal contadas), construye los
 * segmentos sobre TODO el historial disponible (30 días) y los presenta
 * como una lista con scroll infinito, más reciente arriba. Un header de
 * fecha "sticky" indica en qué día está el contenido visible y cambia
 * solo con el scroll. El filtro de fecha de arriba SÍ controla esta
 * pestaña: [targetDate] hace scroll automático hasta el estado vigente a
 * las 00:00 de ese día (no filtra ni recorta datos, solo navega el feed).
 */
@Composable
private fun GridContinuousFeed(
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors,
    now: Instant,
    liveGridState: com.dairoroberto.felicitywatch.domain.model.GridState,
    allReadingsInRetention: List<com.dairoroberto.felicitywatch.data.local.PowerReadingEntity>,
    targetDate: LocalDate
) {
    val zone = ZoneId.systemDefault()

    // Fecha futura elegida en el filtro de arriba: no hay (ni puede haber)
    // registros de corriente todavía, así que se oculta el feed en vez de
    // mostrar datos de hoy que confundirían al usuario.
    if (targetDate.isAfter(LocalDate.now(zone))) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                "No hay registros para una fecha futura.\nElige hoy o una fecha anterior.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMid,
                modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)
            )
        }
        return
    }

    val segments = remember(allReadingsInRetention, now) {
        com.dairoroberto.felicitywatch.domain.usecase.buildGridSegments(allReadingsInRetention, now.toEpochMilli())
    }

    if (segments.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                "No hay suficiente historial registrado todavía.\nEl historial se acumula localmente mientras la app monitorea.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMid,
                modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)
            )
        }
        return
    }

    val currentlyOnline = liveGridState == com.dairoroberto.felicitywatch.domain.model.GridState.ONLINE
    val lastSegment = segments.last()
    // "Lleva X" sale del ÚLTIMO segmento real (mismo dato que arma la
    // línea de tiempo de abajo) — nunca puede discrepar entre ambos como
    // pasaba antes, porque ya no hay dos cálculos separados.
    val elapsedText = if (lastSegment.online == currentlyOnline) {
        val elapsed = Duration.between(Instant.ofEpochMilli(lastSegment.startEpochMillis), now)
        val hours = elapsed.toHours()
        val minutes = elapsed.toMinutes() % 60
        if (hours > 0) "${hours}h ${minutes}min" else "${minutes}min"
    } else {
        "—"
    }

    var filter by remember { mutableStateOf(GridEventFilter.ALL) }

    // Nodos de evento en orden DESCENDENTE (más reciente primero) — cada
    // uno es un punto de cambio de estado real, más un nodo "Hora Actual"
    // al principio si ahora cae dentro del último segmento.
    val allNodes = remember(segments) {
        val nodes = mutableListOf<FeedNode>()
        for (i in segments.indices) {
            val segment = segments[i]
            if (i == 0) continue // el primer segmento no es un "cambio", es el inicio del historial disponible
            nodes += FeedNode(
                epochMillis = segment.startEpochMillis,
                label = if (segment.online) "Restablecido" else "Corte de Energía",
                isOutage = !segment.online,
                isRestoration = segment.online,
                isNow = false,
                online = segment.online
            )
        }
        nodes.reversed()
    }

    val visibleNodes = remember(allNodes, filter) {
        allNodes.filter { node ->
            when (filter) {
                GridEventFilter.ALL -> true
                GridEventFilter.OUTAGES -> node.isOutage
                GridEventFilter.RESTORATIONS -> node.isRestoration
            }
        }
    }

    val feedEntries = remember(visibleNodes, now) {
        val entries = mutableListOf<FeedEntry>()
        val nowNode = FeedNode(now.toEpochMilli(), "Hora Actual", isOutage = false, isRestoration = false, isNow = true, online = false)
        entries += FeedEntry.NowEntry(nowNode)
        var lastDate = now.atZone(zone).toLocalDate()
        visibleNodes.forEach { node ->
            val nodeDate = Instant.ofEpochMilli(node.epochMillis).atZone(zone).toLocalDate()
            if (nodeDate != lastDate) {
                entries += FeedEntry.DaySeparator(nodeDate)
                lastDate = nodeDate
            }
            entries += FeedEntry.EventEntry(node)
        }
        entries
    }

    // Duración de cada tramo: epochMillis del nodo menos el del SIGUIENTE
    // evento/hora-actual en la lista (saltando separadores de día) — así
    // "9:38am → 1:49pm" muestra "duró 4h 11min" sobre el conector que los
    // une, sin importar si un separador de día se interpone entre ambos.
    val durationByIndex = remember(feedEntries) {
        val result = mutableMapOf<Int, Long>()
        for (i in feedEntries.indices) {
            val currentMillis = when (val entry = feedEntries[i]) {
                is FeedEntry.NowEntry -> entry.node.epochMillis
                is FeedEntry.EventEntry -> entry.node.epochMillis
                is FeedEntry.DaySeparator -> null
            } ?: continue
            val nextMillis = (i + 1 until feedEntries.size)
                .asSequence()
                .mapNotNull { j ->
                    when (val entry = feedEntries[j]) {
                        is FeedEntry.EventEntry -> entry.node.epochMillis
                        is FeedEntry.NowEntry -> entry.node.epochMillis
                        is FeedEntry.DaySeparator -> null
                    }
                }
                .firstOrNull()
            if (nextMillis != null) {
                result[i] = currentMillis - nextMillis
            }
        }
        result
    }

    val listState = rememberLazyListState()

    // Índice de LazyColumn: item(header)=0, luego items(feedEntries) desde 1.
    val entriesStartIndex = 1

    // El filtro de fecha de arriba SÍ controla este feed: al elegir un día,
    // hace scroll hasta el estado vigente a las 00:00 de esa fecha — busca
    // el SEPARADOR de ese día (si existe) para que quede justo al tope de
    // la pantalla; si no hay separador (el día no tuvo ningún cambio de
    // estado), busca el primer evento ya ocurrido para esa fecha.
    LaunchedEffect(targetDate, feedEntries) {
        val today = LocalDate.now(zone)
        if (targetDate.isAfter(today)) {
            return@LaunchedEffect // fecha futura: no hay nada que mostrar, ver más abajo
        }
        if (targetDate == today) {
            listState.animateScrollToItem(0)
            return@LaunchedEffect
        }
        val separatorIndex = feedEntries.indexOfFirst { it is FeedEntry.DaySeparator && it.date == targetDate }
        val targetMillis = targetDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val eventIndex = feedEntries.indexOfFirst { it is FeedEntry.EventEntry && it.node.epochMillis <= targetMillis }
        val targetIndex = when {
            separatorIndex >= 0 -> separatorIndex
            eventIndex >= 0 -> eventIndex
            else -> feedEntries.lastIndex // no hay ningún evento tan viejo: el historial no alcanza esa fecha
        }
        listState.animateScrollToItem((targetIndex + entriesStartIndex).coerceAtLeast(0))
    }

    // Fecha visible arriba de la lista — se recalcula con el scroll real
    // (primer item visible), no con la fecha "actual" fija, así que al
    // desplazarse a eventos de ayer/anteayer el indicador cambia solo.
    val visibleDate by remember {
        derivedStateOf {
            val firstVisible = listState.firstVisibleItemIndex
            val entryIndex = firstVisible - entriesStartIndex
            val entry = feedEntries.getOrNull(entryIndex)
            val millis = when (entry) {
                is FeedEntry.NowEntry -> now.toEpochMilli()
                is FeedEntry.EventEntry -> entry.node.epochMillis
                is FeedEntry.DaySeparator -> entry.date.atStartOfDay(zone).toInstant().toEpochMilli()
                null -> now.toEpochMilli()
            }
            Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        }
    }
    val dayFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM").withLocale(Locale("es", "ES"))
    val hourFormatter = DateTimeFormatter.ofPattern("hh:mm").withLocale(Locale("es", "ES"))
    // Fecha corta a la derecha de cada nodo — así al hacer scroll el
    // usuario ve a qué día pertenece cada evento sin depender solo del
    // header superior (útil cuando dos eventos de días distintos quedan
    // visibles a la vez, cerca del límite entre uno y otro).
    val nodeDateFormatter = DateTimeFormatter.ofPattern("d MMM").withLocale(Locale("es", "ES"))

    Column(Modifier.fillMaxSize()) {
        // Header de fecha "sticky" visual (no LazyColumn stickyHeader real
        // porque la fecha depende del nodo, no de un grupo fijo por
        // sección) — se actualiza solo mirando qué hay visible en pantalla.
        // Más notable que un label pequeño: tarjeta con acento de color y
        // tipografía grande, para que el usuario ubique de un vistazo en
        // qué día está mientras hace scroll.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.accent.copy(alpha = 0.12f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                visibleDate.format(dayFormatter).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.accent,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        HorizontalDivider(color = colors.hairline)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Column(Modifier.padding(bottom = 12.dp)) {
                    Text("CORRIENTE ELÉCTRICA", style = MaterialTheme.typography.labelSmall, color = colors.textLow)
                    // Tarjetas más pequeñas que antes (una fila delgada, no
                    // dos tarjetas grandes cuadradas) — la línea de tiempo
                    // de abajo pasa a ser el elemento con más protagonismo
                    // visual de la pantalla.
                    GridStatusHeaderCompact(
                        currentlyOnline = currentlyOnline,
                        elapsedText = elapsedText,
                        colors = colors,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    GridFilterPills(filter = filter, onFilterChange = { filter = it }, colors = colors, modifier = Modifier.padding(top = 12.dp))
                }
            }

            itemsIndexed(
                feedEntries,
                key = { _, entry ->
                    when (entry) {
                        is FeedEntry.NowEntry -> "now"
                        is FeedEntry.EventEntry -> entry.node.epochMillis
                        is FeedEntry.DaySeparator -> "sep-${entry.date}"
                    }
                }
            ) { index, entry ->
                val isLastEntry = index == feedEntries.lastIndex
                val durationMillis = durationByIndex[index]
                when (entry) {
                    is FeedEntry.NowEntry -> FeedNodeRow(
                        time = format12Hour(hourFormatter, now.atZone(zone)),
                        date = nodeDateFormatter.format(now.atZone(zone)),
                        label = "Hora Actual",
                        isNow = true,
                        online = currentlyOnline,
                        isLast = isLastEntry,
                        durationMillis = durationMillis,
                        colors = colors
                    )
                    is FeedEntry.EventEntry -> {
                        val node = entry.node
                        val nodeZoned = Instant.ofEpochMilli(node.epochMillis).atZone(zone)
                        FeedNodeRow(
                            time = format12Hour(hourFormatter, nodeZoned),
                            date = nodeDateFormatter.format(nodeZoned),
                            label = node.label,
                            isNow = false,
                            online = node.online,
                            isLast = isLastEntry,
                            durationMillis = durationMillis,
                            colors = colors
                        )
                    }
                    is FeedEntry.DaySeparator -> DaySeparatorRow(
                        date = entry.date,
                        colors = colors
                    )
                }
            }

            item {
                Text(
                    "Inicio del historial disponible (30 días).",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

/** Tarjetas de estado compactas (fila delgada, no dos cuadros grandes) —
 * el usuario pidió que ocupen menos espacio para que la línea de tiempo de
 * abajo tenga más protagonismo. */
@Composable
private fun GridStatusHeaderCompact(
    currentlyOnline: Boolean,
    elapsedText: String,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors,
    modifier: Modifier = Modifier
) {
    val accent = if (currentlyOnline) colors.green else colors.error
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(accent, CircleShape)
        )
        Text(
            if (currentlyOnline) "Con Corriente" else "Sin Corriente",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = accent,
            modifier = Modifier.padding(start = 8.dp).weight(1f)
        )
        Text(
            "Lleva $elapsedText",
            style = MaterialTheme.typography.labelSmall,
            color = accent.copy(alpha = 0.85f)
        )
    }
}

/** Filtro pastilla (Ver Todo / Solo Cortes / Solo Restablecidos) que acota
 * qué nodos aparecen en el feed — todo el historial se sigue calculando
 * siempre, esto solo filtra la presentación. */
@Composable
private fun GridFilterPills(
    filter: GridEventFilter,
    onFilterChange: (GridEventFilter) -> Unit,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GridFilterPill("Ver Todo", filter == GridEventFilter.ALL, colors.accent) { onFilterChange(GridEventFilter.ALL) }
        GridFilterPill("Solo Cortes", filter == GridEventFilter.OUTAGES, colors.error) { onFilterChange(GridEventFilter.OUTAGES) }
        GridFilterPill("Solo Restablecidos", filter == GridEventFilter.RESTORATIONS, colors.green) { onFilterChange(GridEventFilter.RESTORATIONS) }
    }
}

@Composable
private fun GridFilterPill(
    label: String,
    selected: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val colors = LocalFelicityColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accentColor.copy(alpha = 0.16f) else colors.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accentColor else colors.textMid
        )
    }
}

/** Una fila del feed continuo: hora a la izquierda, nodo + conector
 * vertical al centro, evento a la derecha — mismo lenguaje visual que la
 * línea de tiempo anterior, pero como filas de LazyColumn en vez de una
 * Column fija, para soportar scroll infinito eficiente. */
@Composable
private fun FeedNodeRow(
    time: String,
    date: String,
    label: String,
    isNow: Boolean,
    online: Boolean,
    isLast: Boolean,
    /** Milisegundos entre este nodo y el siguiente evento en el feed (más
     * antiguo) — se muestra como chip flotante sobre el conector, ej.
     * "duró 4h 11min" entre "9:38am" y "1:49pm". Null si es el último
     * nodo visible (no hay "siguiente" con qué calcular una duración). */
    durationMillis: Long?,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
) {
    val nodeColor = when {
        isNow -> colors.accent
        online -> colors.green
        else -> colors.error
    }
    val connectorColor = if (online) colors.green else colors.error
    // "4h 11min" entre este nodo y el siguiente (más antiguo) — se muestra
    // a la derecha, debajo de la fecha (antes iba flotando sobre el
    // conector de 20dp de ancho, y el texto quedaba truncado a un solo
    // dígito por falta de espacio; aquí tiene todo el ancho que necesite).
    val durationLabel = durationMillis?.let { formatSegmentDuration(it) }

    // IntrinsicSize.Min fuerza a que el Row mida su alto según el
    // contenido más alto (la Column de la derecha, con label + subtítulo),
    // y fillMaxHeight() en la columna del conector toma exactamente esa
    // misma altura — así el conector siempre llega hasta el borde inferior
    // real de la fila, sin depender de un valor fijo ni de medir múltiples
    // filas del LazyColumn a la vez (cada fila es su propio item, y este
    // patrón solo necesita medirse a sí misma).
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Text(
            time,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.textMid,
            modifier = Modifier.width(46.dp).padding(top = 2.dp)
        )
        Column(
            modifier = Modifier.width(20.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(if (isNow) 14.dp else 11.dp)
                    .background(
                        color = if (isNow) colors.accent else colors.surface2,
                        shape = CircleShape
                    )
                    .border(2.dp, nodeColor, CircleShape)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .weight(1f)
                        .padding(vertical = 2.dp)
                        .background(connectorColor.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                )
            }
        }
        Row(
            modifier = Modifier
                .padding(start = 12.dp, bottom = if (isLast) 0.dp else 22.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isNow) FontWeight.Bold else FontWeight.Medium,
                    color = if (isNow) colors.accent else colors.textHi
                )
                if (isNow) {
                    Text(
                        if (online) "Con Corriente" else "Sin Corriente",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (online) colors.green else colors.error
                    )
                }
            }
            // Fecha corta (ej. "12 ago") arriba y duración del tramo
            // ("4h 11min") debajo — alineadas a la derecha, con ancho
            // libre para no truncarse.
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    date,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow,
                    maxLines = 1
                )
                if (durationLabel != null) {
                    Text(
                        durationLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textMid,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

/** Separador horizontal entre días distintos dentro del feed continuo —
 * una línea con la fecha en el centro, igual al patrón de "nuevo día" de
 * apps de mensajería, para que el cambio de día sea evidente sin depender
 * solo del header superior. */
@Composable
private fun DaySeparatorRow(
    date: LocalDate,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
) {
    val formatter = DateTimeFormatter.ofPattern("d 'de' MMMM").withLocale(Locale("es", "ES"))
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = colors.hairline)
        Text(
            formatter.format(date),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.textMid,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = colors.hairline)
    }
}

/**
 * Reporte de generación fotovoltaica — vista mensual única (el selector
 * Diaria/Mensual se quitó: la vista diaria era redundante con la pestaña
 * "PV", que ya muestra el detalle de cualquier día elegido en el filtro de
 * arriba). Tiene su propio selector de mes/año con flechas ‹ › y muestra
 * una barra por cada día de ese mes, con promedio diario del mes. Cada
 * barra usa el ÚLTIMO valor de pvEnergyTodayKwh leído ese día (el inversor
 * ya acumula internamente y resetea a medianoche).
 */
@Composable
private fun DailyGenerationReportCard(
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors,
    /** Historial completo (30 días) sin acotar por el filtro de fecha de
     * arriba — esta vista tiene su propio selector de mes con flechas
     * ‹ ›, independiente del filtro de periodo general. */
    allReadingsInRetention: List<com.dairoroberto.felicitywatch.data.local.PowerReadingEntity>
) {
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "GENERACIÓN FOTOVOLTAICA",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow
            )

            val zone = ZoneId.systemDefault()
            run {
                    // Selector de mes con flechas ‹ ›
                    val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy").withLocale(Locale("es", "ES"))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedMonth = selectedMonth.minusMonths(1) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Mes anterior", modifier = Modifier.size(20.dp))
                        }
                        Text(
                            monthFormatter.format(selectedMonth).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textHi,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        IconButton(onClick = { selectedMonth = selectedMonth.plusMonths(1) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Mes siguiente", modifier = Modifier.size(20.dp))
                        }
                    }

                    // Filtrar lecturas del mes seleccionado
                    val monthStart = selectedMonth.atDay(1)
                    val monthEnd = selectedMonth.atEndOfMonth()
                    val monthStartMillis = monthStart.atStartOfDay(zone).toInstant().toEpochMilli()
                    val monthEndMillis = monthEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

                    // Se filtra sobre el historial completo, no sobre
                    // readings (que ya viene acotado por el filtro de fecha
                    // de arriba) — así el selector de mes funciona de forma
                    // independiente, como espera el usuario.
                    val monthReadings = allReadingsInRetention.filter {
                        it.timestampEpochMillis in monthStartMillis..monthEndMillis
                    }

                    val dailyTotals = monthReadings
                        .filter { it.pvEnergyTodayKwh != null }
                        .groupBy { Instant.ofEpochMilli(it.timestampEpochMillis).atZone(zone).toLocalDate() }
                        .mapValues { (_, dayReadings) -> dayReadings.maxBy { it.timestampEpochMillis }.pvEnergyTodayKwh!! }
                        .toSortedMap()

                    if (dailyTotals.isEmpty()) {
                        Text(
                            "No hay datos registrados para este mes.\nEl historial se acumula localmente mientras la app monitorea.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMid,
                            modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)
                        )
                    } else {
                        val total = dailyTotals.values.sum()
                        val daysWithData = dailyTotals.size
                        val average = total / daysWithData
                        val bestDay = dailyTotals.maxByOrNull { it.value }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GenerationStatTile(
                                label = "Total del mes",
                                value = String.format(Locale("es", "ES"), "%.1f", total),
                                unit = "kWh",
                                color = colors.accent,
                                modifier = Modifier.weight(1f)
                            )
                            GenerationStatTile(
                                label = "Promedio diario",
                                value = String.format(Locale("es", "ES"), "%.1f", average),
                                unit = "kWh",
                                color = colors.textMid,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "$daysWithData días con datos",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textLow
                            )
                            if (bestDay != null) {
                                val bestDayFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM").withLocale(Locale("es", "ES"))
                                Text(
                                    "Mejor: ${bestDayFormatter.format(bestDay.key)} (${String.format(Locale("es", "ES"), "%.1f", bestDay.value)} kWh)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.green
                                )
                            }
                        }

                        // Lista de barras HORIZONTALES, una fila por cada día
                        // del mes — en vertical solo caben ~6 etiquetas en el
                        // eje X ("1 6 11 16"), así que era imposible saber a
                        // qué día correspondía cada barra. Aquí cada día
                        // lleva su número y su valor en kWh siempre visibles.
                        val dayFormatter = DateTimeFormatter.ofPattern("d MMM").withLocale(Locale("es", "ES"))
                        val barEntries = (1..selectedMonth.lengthOfMonth()).map { day ->
                            val date = selectedMonth.atDay(day)
                            val kwh = dailyTotals[date] ?: 0.0
                            HorizontalBarEntry(
                                label = day.toString(),
                                value = kwh.toFloat(),
                                highlighted = bestDay != null && date == bestDay.key
                            )
                        }
                        HorizontalBarList(
                            entries = barEntries,
                            barColor = colors.accent,
                            trackColor = colors.hairline.copy(alpha = 0.4f),
                            labelColor = colors.textMid,
                            valueColor = colors.textHi,
                            valueFormatter = { "%.1f kWh".format(it) },
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
            }
        }
    }
}

@Composable
internal fun GenerationStatTile(
    label: String,
    value: String,
    unit: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalFelicityColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textMid)
            Row(modifier = Modifier.padding(top = 4.dp)) {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
                Text(" $unit", style = MaterialTheme.typography.labelSmall, color = colors.textMid, modifier = Modifier.padding(start = 2.dp))
            }
        }
    }
}

/**
 * Eje X de horas sincronizado con el zoom/pan de [LineAreaChart]/[MultiLineChart]
 * — antes el eje se calculaba una sola vez sobre el rango completo de datos y
 * quedaba obsoleto en cuanto el usuario hacía zoom (el gráfico se veía
 * "recortado" sin ninguna referencia de qué horas mostraba). Usa el mismo
 * [ChartZoomState] hoisted que el gráfico para derivar el rango visible actual.
 */
@Composable
internal fun ChartXAxis(
    zoomState: ChartZoomState,
    baseMinX: Float,
    baseMaxX: Float,
    originEpochMillis: Long,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors,
    axisLabelCount: Int = 5
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
        val (minX, maxX) = zoomState.visibleRange(baseMinX, baseMaxX, widthPx)
        // Formato 12h consistente con la pestaña Corriente — usa el mismo
        // patrón "hh:mm" + sufijo am/pm en minúsculas sin espacio.
        val hourFormatter = DateTimeFormatter.ofPattern("hh:mm").withLocale(Locale("es", "ES"))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (i in 0 until axisLabelCount) {
                val fraction = i.toFloat() / (axisLabelCount - 1)
                val targetX = minX + (maxX - minX) * fraction
                val epochMillis = originEpochMillis + targetX.toLong()
                val zoned = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
                Text(
                    format12Hour(hourFormatter, zoned),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow
                )
            }
        }
    }
}

