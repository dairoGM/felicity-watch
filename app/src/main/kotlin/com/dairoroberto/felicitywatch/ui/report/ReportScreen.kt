package com.dairoroberto.felicitywatch.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.ui.components.BarChartEntry
import com.dairoroberto.felicitywatch.ui.components.ChartPoint
import com.dairoroberto.felicitywatch.ui.components.ChartSeries
import com.dairoroberto.felicitywatch.ui.components.ChartZoomState
import com.dairoroberto.felicitywatch.ui.components.DailyBarChart
import com.dairoroberto.felicitywatch.ui.components.GridSegment
import com.dairoroberto.felicitywatch.ui.components.GridTimelineChart
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
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val REPORT_TABS = listOf("PV", "Batería", "FV/Carga/Descarga", "Corriente", "Generación", "Consumo")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(viewModel: ReportViewModel = hiltViewModel()) {
    val dateRange by viewModel.dateRange.collectAsState()
    val readings by viewModel.readings.collectAsState()
    val liveGridState by viewModel.liveGridState.collectAsState()
    val lastGridChangeAt by viewModel.lastGridChangeAt.collectAsState()
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

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
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

            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface2),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("PERIODO", style = MaterialTheme.typography.labelSmall, color = colors.textLow)

                        Box {
                            OutlinedButton(onClick = { showPeriodMenu = true }) {
                                Text(periodLabel)
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
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

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.shiftRange(forward = false) }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Periodo anterior")
                        }
                        Text(
                            rangeLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(onClick = { viewModel.shiftRange(forward = true) }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Periodo siguiente")
                        }
                    }

                    if (showCustomPickers) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
            }
        }

        // ScrollableTabRow (no TabRow fijo) porque con 5 pestañas y nombres
        // largos como "FV/Carga/Descarga" un ancho fijo comprime el texto y
        // lo hace saltar de línea — el scroll horizontal evita ese problema
        // sin tener que acortar los nombres.
        androidx.compose.material3.ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = colors.surface2,
            edgePadding = 12.dp
        ) {
            REPORT_TABS.forEachIndexed { index, title ->
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
                3 -> GridTimelineCard(readings, dateRange, colors, now, liveGridState, lastGridChangeAt)
                4 -> DailyGenerationReportCard(readings, dateRange, colors)
                5 -> GridPoweredConsumptionCard(readings, colors)
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
                val tooltipTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale("es", "ES"))
                val zoomState = remember(points) { ChartZoomState() }
                val baseMinX = 0f
                val baseMaxX = (endOfDay - startOfDay).toFloat()
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
                            val time = tooltipTimeFormatter.format(
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
                val lastReadingTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale("es", "ES"))
                Text(
                    "Última lectura: ${lastReadingTimeFormatter.format(lastReadingInstant.atZone(ZoneId.systemDefault()))} (hace ${secondsAgo}s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
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
                val tooltipTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale("es", "ES"))
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
                            val time = tooltipTimeFormatter.format(
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
                val tooltipTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale("es", "ES"))
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
                            val time = tooltipTimeFormatter.format(
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

@Composable
private fun GridTimelineCard(
    readings: List<com.dairoroberto.felicitywatch.data.local.PowerReadingEntity>,
    dateRange: DateRange,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors,
    now: Instant,
    liveGridState: com.dairoroberto.felicitywatch.domain.model.GridState,
    lastGridChangeAt: Instant?
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "CORRIENTE ELÉCTRICA",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow
            )

            val gridReadings = readings.filter { it.gridPowerWatts != null }
                .sortedBy { it.timestampEpochMillis }

            if (gridReadings.size < 2) {
                Text(
                    "No hay suficiente historial registrado en este periodo.\nEl historial se acumula localmente mientras la app monitorea.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid,
                    modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)
                )
            } else {
                val zone = ZoneId.systemDefault()
                val rangeStartMillis = dateRange.start.atStartOfDay(zone).toInstant().toEpochMilli()
                val rangeEndMillis = dateRange.end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

                // Cada lectura representa el estado hasta la siguiente
                // lectura (o hasta ahora, para la última); se agrupan
                // lecturas consecutivas con el mismo estado en un tramo.
                val rawSegments = gridReadings.mapIndexed { index, readingItem ->
                    val online = (readingItem.gridPowerWatts ?: 0) >= 1
                    val endMillis = if (index < gridReadings.size - 1) {
                        gridReadings[index + 1].timestampEpochMillis
                    } else {
                        now.toEpochMilli().coerceAtMost(rangeEndMillis)
                    }
                    GridSegment(readingItem.timestampEpochMillis, endMillis, online)
                }
                val segments = mutableListOf<GridSegment>()
                rawSegments.forEach { segment ->
                    val last = segments.lastOrNull()
                    if (last != null && last.online == segment.online) {
                        segments[segments.size - 1] = last.copy(endEpochMillis = segment.endEpochMillis)
                    } else {
                        segments += segment
                    }
                }

                val isSingleDay = dateRange.start == dateRange.end
                val tooltipTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale("es", "ES"))

                val allGridReadings = readings.filter { it.gridPowerWatts != null }.sortedBy { it.timestampEpochMillis }

                // Misma fuente que "Estado de la red" del Panel (guía sección
                // 5): estado en vivo sin debounce + lastGridChangeAt del
                // debouncer ya confirmado — antes esta tarjeta recalculaba su
                // propio "hace cuánto" recorriendo el historial crudo, sin
                // aplicar el debounce, y por eso el número no coincidía con
                // el del Panel (ej. Panel decía 1h20min, aquí decía 3h20min).
                val currentlyOnline = liveGridState == com.dairoroberto.felicitywatch.domain.model.GridState.ONLINE

                // Fallback cuando nunca hubo un cambio de red CONFIRMADO
                // (lastGridChangeAt queda null para siempre si la corriente
                // no ha cambiado de estado desde que arrancó el servicio, o
                // si la app se reinició recientemente) — se busca en el
                // historial local la lectura más antigua que ya tenga el
                // mismo estado que ahora, recorriendo hacia atrás desde el
                // final hasta el primer cambio real de signo.
                val fallbackChangeAt: Instant? = if (lastGridChangeAt == null && allGridReadings.isNotEmpty()) {
                    var changeMillis = allGridReadings.last().timestampEpochMillis
                    for (i in allGridReadings.indices.reversed()) {
                        val online = (allGridReadings[i].gridPowerWatts ?: 0) >= 1
                        if (online == currentlyOnline) {
                            changeMillis = allGridReadings[i].timestampEpochMillis
                        } else {
                            break
                        }
                    }
                    Instant.ofEpochMilli(changeMillis)
                } else null
                val effectiveChangeAt = lastGridChangeAt ?: fallbackChangeAt

                val elapsedText = if (effectiveChangeAt != null) {
                    val elapsedSinceChange = Duration.between(effectiveChangeAt, now)
                    val elapsedHours = elapsedSinceChange.toHours()
                    val elapsedMinutes = elapsedSinceChange.toMinutes() % 60
                    if (elapsedHours > 0) "${elapsedHours}h ${elapsedMinutes}min" else "${elapsedMinutes}min"
                } else {
                    "—"
                }

                if (segments.isNotEmpty() && lastGridChangeAt != null) {
                    val changeMillis = lastGridChangeAt.toEpochMilli()
                    segments.removeAll { it.startEpochMillis >= changeMillis }
                    val last = segments.lastOrNull()
                    if (last != null && last.endEpochMillis > changeMillis) {
                        segments[segments.size - 1] = last.copy(endEpochMillis = changeMillis)
                    }
                    if (changeMillis <= now.toEpochMilli()) {
                        val newLast = segments.lastOrNull()
                        if (newLast != null && newLast.online == currentlyOnline) {
                            segments[segments.size - 1] = newLast.copy(endEpochMillis = now.toEpochMilli())
                        } else {
                            segments += GridSegment(changeMillis, now.toEpochMilli(), currentlyOnline)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .size(12.dp)
                            .background(
                                color = if (currentlyOnline) colors.green else colors.error,
                                shape = CircleShape
                            )
                    )
                    Column {
                        Text(
                            if (currentlyOnline) "Con corriente" else "Sin corriente",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (currentlyOnline) colors.green else colors.error
                        )
                        Text(
                            "Lleva $elapsedText ${if (currentlyOnline) "con corriente" else "sin corriente"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textLow
                        )
                    }
                }

                GridTimelineChart(
                    segments = segments,
                    onlineColor = colors.green,
                    offlineColor = colors.error,
                    gridColor = colors.hairline,
                    minEpochMillisOverride = rangeStartMillis,
                    maxEpochMillisOverride = rangeEndMillis,
                    nowEpochMillis = now.toEpochMilli(),
                    tooltipLabel = { segment ->
                        val startText = tooltipTimeFormatter.format(
                            Instant.ofEpochMilli(segment.startEpochMillis).atZone(zone)
                        )
                        val endText = tooltipTimeFormatter.format(
                            Instant.ofEpochMilli(segment.endEpochMillis).atZone(zone)
                        )
                        val durationMinutes = Duration.between(
                            Instant.ofEpochMilli(segment.startEpochMillis),
                            Instant.ofEpochMilli(segment.endEpochMillis)
                        ).toMinutes()
                        val statusText = if (segment.online) "Con corriente" else "Sin corriente"
                        "$statusText ($durationMinutes min)" to "$startText — $endText"
                    }
                )

                // Marcas de hora en punto (0, 2, 4...22) cuando el
                // periodo es un solo día — igual al boceto pedido — o
                // marcas por día cuando el periodo abarca varios días.
                if (isSingleDay) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (hour in 0..24 step 4) {
                            Text(
                                hour.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textLow
                            )
                        }
                    }
                } else {
                    val dayFormatter = DateTimeFormatter.ofPattern("d MMM").withLocale(Locale("es", "ES"))
                    val totalDays = (daysBetween(dateRange.start, dateRange.end) + 1).coerceAtLeast(1)
                    val axisLabelCount = minOf(totalDays, 6L).toInt()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (i in 0 until axisLabelCount) {
                            val fraction = if (axisLabelCount == 1) 0f else i.toFloat() / (axisLabelCount - 1)
                            val dayOffset = (fraction * (totalDays - 1)).toLong()
                            Text(
                                dayFormatter.format(dateRange.start.plusDays(dayOffset)),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textLow
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("● ", color = colors.green, style = MaterialTheme.typography.bodyMedium)
                        Text("Con corriente", style = MaterialTheme.typography.labelSmall, color = colors.textLow)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("● ", color = colors.error, style = MaterialTheme.typography.bodyMedium)
                        Text("Sin corriente", style = MaterialTheme.typography.labelSmall, color = colors.textLow)
                    }
                }

                val totalMillis = (rangeEndMillis - rangeStartMillis).coerceAtLeast(1L)
                val offlineMillis = segments.filter { !it.online }
                    .sumOf { it.endEpochMillis - it.startEpochMillis }
                val offlineMinutes = offlineMillis / 60_000
                val onlineMinutes = (totalMillis - offlineMillis) / 60_000
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Con corriente: ${onlineMinutes / 60}h ${onlineMinutes % 60}min",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow
                    )
                    Text(
                        "Sin corriente: ${offlineMinutes / 60}h ${offlineMinutes % 60}min",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow
                    )
                }

                // Resumen fijo de últimas 24h (independiente del
                // periodo elegido arriba) — reconstruye tramos con
                // todas las lecturas de grid disponibles, no solo las
                // del rango de fecha seleccionado.
                val last24hStartMillis = now.toEpochMilli() - Duration.ofHours(24).toMillis()
                val last24hReadings = allGridReadings.filter { it.timestampEpochMillis >= last24hStartMillis }
                if (last24hReadings.size >= 2) {
                    val last24hSegments = mutableListOf<GridSegment>()
                    last24hReadings.forEachIndexed { index, readingItem ->
                        val online = (readingItem.gridPowerWatts ?: 0) >= 1
                        val endMillis = if (index < last24hReadings.size - 1) {
                            last24hReadings[index + 1].timestampEpochMillis
                        } else {
                            now.toEpochMilli()
                        }
                        val last = last24hSegments.lastOrNull()
                        if (last != null && last.online == online) {
                            last24hSegments[last24hSegments.size - 1] = last.copy(endEpochMillis = endMillis)
                        } else {
                            last24hSegments += GridSegment(readingItem.timestampEpochMillis, endMillis, online)
                        }
                    }
                    val last24hOfflineMinutes = last24hSegments.filter { !it.online }
                        .sumOf { it.endEpochMillis - it.startEpochMillis } / 60_000
                    val last24hOnlineMinutes = (Duration.ofHours(24).toMinutes() - last24hOfflineMinutes)
                        .coerceAtLeast(0)

                    Text(
                        "ÚLTIMAS 24 HORAS",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow,
                        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Con corriente: ${last24hOnlineMinutes / 60}h ${last24hOnlineMinutes % 60}min",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textLow
                        )
                        Text(
                            "Sin corriente: ${last24hOfflineMinutes / 60}h ${last24hOfflineMinutes % 60}min",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textLow
                        )
                    }
                }
            }
        }
    }
}

private fun daysBetween(start: LocalDate, end: LocalDate): Long =
    java.time.temporal.ChronoUnit.DAYS.between(start, end)

/**
 * Reporte de generación fotovoltaica por día — pensado para mostrarle al
 * cliente: resumen numérico (total, promedio, mejor día) arriba, gráfico de
 * barras abajo. Cada barra usa el ÚLTIMO valor de pvEnergyTodayKwh leído ese
 * día (el inversor ya acumula internamente y resetea a medianoche, así que
 * sumar lecturas del mismo día daría un total inflado).
 */
@Composable
private fun DailyGenerationReportCard(
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
                "GENERACIÓN FOTOVOLTAICA POR DÍA",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow
            )

            val zone = ZoneId.systemDefault()
            val dailyTotals = readings
                .filter { it.pvEnergyTodayKwh != null }
                .groupBy { Instant.ofEpochMilli(it.timestampEpochMillis).atZone(zone).toLocalDate() }
                .mapValues { (_, dayReadings) -> dayReadings.maxBy { it.timestampEpochMillis }.pvEnergyTodayKwh!! }
                .toSortedMap()

            if (dailyTotals.isEmpty()) {
                Text(
                    "No hay suficiente historial registrado en este periodo.\nEl historial se acumula localmente mientras la app monitorea.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid,
                    modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)
                )
            } else {
                val total = dailyTotals.values.sum()
                val average = total / dailyTotals.size
                val bestDay = dailyTotals.maxByOrNull { it.value }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GenerationStatTile(
                        label = "Total del periodo",
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
                if (bestDay != null) {
                    val bestDayFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM").withLocale(Locale("es", "ES"))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Mejor día: ${bestDayFormatter.format(bestDay.key)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textLow
                        )
                        Text(
                            String.format(Locale("es", "ES"), "%.1f kWh", bestDay.value),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.green
                        )
                    }
                }

                val dayFormatter = DateTimeFormatter.ofPattern("d MMM").withLocale(Locale("es", "ES"))
                val entries = dailyTotals.map { (date, kwh) ->
                    BarChartEntry(label = dayFormatter.format(date), value = kwh.toFloat())
                }
                DailyBarChart(
                    entries = entries,
                    barColor = colors.accent,
                    gridColor = colors.hairline,
                    textColor = colors.textLow,
                    valueFormatter = { "%.1f kWh".format(it) },
                    modifier = Modifier.padding(top = 16.dp)
                )

                // Etiquetas de fecha bajo el gráfico — máximo 6 para no
                // amontonar texto cuando el rango abarca muchos días.
                val labelStep = (entries.size / 6).coerceAtLeast(1)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    entries.forEachIndexed { index, entry ->
                        if (index % labelStep == 0) {
                            Text(
                                entry.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textLow
                            )
                        }
                    }
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
private fun ChartXAxis(
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
        val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale("es", "ES"))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (i in 0 until axisLabelCount) {
                val fraction = i.toFloat() / (axisLabelCount - 1)
                val targetX = minX + (maxX - minX) * fraction
                val epochMillis = originEpochMillis + targetX.toLong()
                Text(
                    timeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow
                )
            }
        }
    }
}

