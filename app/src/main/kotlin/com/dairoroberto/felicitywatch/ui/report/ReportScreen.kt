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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.ui.components.BarChartEntry
import com.dairoroberto.felicitywatch.ui.components.ChartPoint
import com.dairoroberto.felicitywatch.ui.components.ChartSeries
import com.dairoroberto.felicitywatch.ui.components.ChartZoomState
import com.dairoroberto.felicitywatch.ui.components.DailyBarChart
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

private val ELECTRICAL_TABS = listOf("PV", "Batería", "FV/Carga/Descarga", "Corriente", "Generación", "Consumo")
private val IMPACT_TABS = listOf("Estadísticas", "Ambiental")
private val REPORT_GROUPS = listOf("Eléctrico", "Impacto")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(viewModel: ReportViewModel = hiltViewModel()) {
    val dateRange by viewModel.dateRange.collectAsState()
    val readings by viewModel.readings.collectAsState()
    val liveGridState by viewModel.liveGridState.collectAsState()
    val allReadingsLast30Days by viewModel.allReadingsLast30Days.collectAsState()
    val colors = LocalFelicityColors.current

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showPeriodMenu by remember { mutableStateOf(false) }
    var showCustomPickers by remember { mutableStateOf(false) }
    // Grupo de nivel superior (Eléctrico/Impacto) — separa las 6 pestañas
    // eléctricas ya existentes de las 2 nuevas de impacto (Estadísticas/
    // Ambiental) para no saturar una sola fila con 8 pestañas. selectedTab
    // se reinicia al cambiar de grupo para no quedar apuntando a un índice
    // que no existe en la fila del otro grupo (ej. índice 4 no existe en
    // Impacto, que solo tiene 2 pestañas).
    var selectedGroup by remember { mutableIntStateOf(0) }
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
    val isCorrienteTab = selectedGroup == 0 && selectedTab == 3

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

        // Selector de grupo (Eléctrico/Impacto) — TabRow fijo porque son
        // solo 2 categorías, no necesita scroll horizontal.
        TabRow(
            selectedTabIndex = selectedGroup,
            containerColor = colors.surface2
        ) {
            REPORT_GROUPS.forEachIndexed { index, title ->
                Tab(
                    selected = selectedGroup == index,
                    onClick = { selectedGroup = index; selectedTab = 0 },
                    text = { Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                )
            }
        }

        // ScrollableTabRow (no TabRow fijo) porque con nombres largos como
        // "FV/Carga/Descarga" un ancho fijo comprime el texto y lo hace
        // saltar de línea — el scroll horizontal evita ese problema sin
        // tener que acortar los nombres. Se usa para ambos grupos por
        // consistencia visual, aunque Impacto solo tenga 2 pestañas.
        val currentTabs = if (selectedGroup == 0) ELECTRICAL_TABS else IMPACT_TABS
        androidx.compose.material3.ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = colors.surface2,
            edgePadding = 12.dp
        ) {
            currentTabs.forEachIndexed { index, title ->
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
                allReadingsLast30Days = allReadingsLast30Days,
                targetDate = dateRange.start
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                if (selectedGroup == 0) {
                    when (selectedTab) {
                        0 -> PvGenerationCard(readings, dateRange, colors, now)
                        1 -> BatterySocCard(readings, dateRange, colors)
                        2 -> PvChargeDischargeCard(readings, dateRange, colors)
                        4 -> DailyGenerationReportCard(readings, dateRange, colors, allReadingsLast30Days)
                        5 -> GridPoweredConsumptionCard(readings, colors)
                    }
                } else {
                    when (selectedTab) {
                        0 -> EnergyStatisticsCard(readings, dateRange, colors)
                        1 -> EnvironmentalImpactCard(readings, dateRange, colors)
                    }
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
private fun format12Hour(hourFormatter: DateTimeFormatter, zoned: java.time.ZonedDateTime): String {
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
    allReadingsLast30Days: List<com.dairoroberto.felicitywatch.data.local.PowerReadingEntity>,
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

    val segments = remember(allReadingsLast30Days, now) {
        com.dairoroberto.felicitywatch.domain.usecase.buildGridSegments(allReadingsLast30Days, now.toEpochMilli())
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
 * Estadísticas de energía PV generada para el periodo seleccionado —
 * ahora responde al filtro de fecha (antes siempre mostraba Hoy/7/30
 * días fijos). Calcula el total del periodo y promedio diario con las
 * lecturas filtradas por el rango de fecha activo.
 */
@Composable
private fun EnergyStatisticsCard(
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
                "DATOS ESTADÍSTICOS",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow
            )

            val zone = ZoneId.systemDefault()
            val dailyTotals = readings
                .filter { it.pvEnergyTodayKwh != null }
                .groupBy { Instant.ofEpochMilli(it.timestampEpochMillis).atZone(zone).toLocalDate() }
                .mapValues { (_, dayReadings) -> dayReadings.maxBy { it.timestampEpochMillis }.pvEnergyTodayKwh!! }

            val totalKwh = dailyTotals.values.sum()
            val daysWithData = dailyTotals.size.coerceAtLeast(1)
            val averageDailyKwh = totalKwh / daysWithData
            val bestDay = dailyTotals.maxByOrNull { it.value }

            val dateFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM").withLocale(Locale("es", "ES"))
            val rangeLabel = if (dateRange.start == dateRange.end) {
                dateFormatter.format(dateRange.start)
            } else {
                "${dateFormatter.format(dateRange.start)} — ${dateFormatter.format(dateRange.end)}"
            }
            Text(
                rangeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMid,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GenerationStatTile(
                    label = "Total del periodo",
                    value = String.format(Locale("es", "ES"), "%.1f", totalKwh),
                    unit = "kWh",
                    color = colors.accent,
                    modifier = Modifier.weight(1f)
                )
                GenerationStatTile(
                    label = "Promedio diario",
                    value = String.format(Locale("es", "ES"), "%.1f", averageDailyKwh),
                    unit = "kWh",
                    color = colors.textMid,
                    modifier = Modifier.weight(1f)
                )
            }
            if (bestDay != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Mejor día: ${dateFormatter.format(bestDay.key)}",
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
        }
    }
}

/**
 * Impacto ambiental estimado a partir de la energía PV generada en el
 * periodo seleccionado — ahora responde al filtro de fecha (antes
 * siempre usaba solo el total de "hoy"). Felicity no reporta ningún dato
 * ambiental, así que se calcula localmente con factores estándar de
 * conversión (ver [com.dairoroberto.felicitywatch.domain.usecase.computeEnvironmentalImpact]).
 */
@Composable
private fun EnvironmentalImpactCard(
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
                "DATOS AMBIENTALES",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow
            )

            val zone = ZoneId.systemDefault()
            val dailyTotals = readings
                .filter { it.pvEnergyTodayKwh != null }
                .groupBy { Instant.ofEpochMilli(it.timestampEpochMillis).atZone(zone).toLocalDate() }
                .mapValues { (_, dayReadings) -> dayReadings.maxBy { it.timestampEpochMillis }.pvEnergyTodayKwh!! }
            val totalKwh = dailyTotals.values.sum()

            val dateFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM").withLocale(Locale("es", "ES"))
            val rangeLabel = if (dateRange.start == dateRange.end) {
                "Estimado con la energía generada el ${dateFormatter.format(dateRange.start)}"
            } else {
                "Estimado con la energía generada del ${dateFormatter.format(dateRange.start)} al ${dateFormatter.format(dateRange.end)}"
            }
            Text(
                rangeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMid,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )

            val impact = com.dairoroberto.felicitywatch.domain.usecase.computeEnvironmentalImpact(totalKwh)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GenerationStatTile(
                    label = "Carbón ahorrado",
                    value = String.format(Locale("es", "ES"), "%.1f", impact.coalSavedKg),
                    unit = "Kg",
                    color = colors.textMid,
                    modifier = Modifier.weight(1f)
                )
                GenerationStatTile(
                    label = "CO₂ reducido",
                    value = String.format(Locale("es", "ES"), "%.1f", impact.co2AvoidedKg),
                    unit = "Kg",
                    color = colors.green,
                    modifier = Modifier.weight(1f)
                )
                GenerationStatTile(
                    label = "Árboles equiv.",
                    value = String.format(Locale("es", "ES"), "%.2f", impact.treesEquivalent),
                    unit = "árbol",
                    color = colors.green,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private enum class GenerationViewMode { DAILY, MONTHLY }

/**
 * Reporte de generación fotovoltaica — con selector de vista Diaria/Mensual.
 * La vista diaria muestra barras por día del periodo seleccionado (como antes).
 * La vista mensual tiene su propio selector de mes/año con flechas ‹ › y
 * muestra una barra por cada día de ese mes, con promedio diario del mes.
 * Cada barra usa el ÚLTIMO valor de pvEnergyTodayKwh leído ese día (el
 * inversor ya acumula internamente y resetea a medianoche).
 */
@Composable
private fun DailyGenerationReportCard(
    readings: List<com.dairoroberto.felicitywatch.data.local.PowerReadingEntity>,
    dateRange: DateRange,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors,
    /** Historial completo (30 días) sin acotar por el filtro de fecha de
     * arriba — la vista Mensual tiene su propio selector de mes con flechas
     * ‹ ›, así que si usara [readings] (ya filtrado, ej. solo "Hoy") al
     * navegar a un mes anterior no habría ningún dato que mostrar. */
    allReadingsLast30Days: List<com.dairoroberto.felicitywatch.data.local.PowerReadingEntity>
) {
    var viewMode by remember { mutableStateOf(GenerationViewMode.DAILY) }
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

            // Selector Diaria / Mensual
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                SegmentedButton(
                    selected = viewMode == GenerationViewMode.DAILY,
                    onClick = { viewMode = GenerationViewMode.DAILY },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Diaria") }
                SegmentedButton(
                    selected = viewMode == GenerationViewMode.MONTHLY,
                    onClick = { viewMode = GenerationViewMode.MONTHLY },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Mensual") }
            }

            val zone = ZoneId.systemDefault()

            when (viewMode) {
                GenerationViewMode.DAILY -> {
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

                GenerationViewMode.MONTHLY -> {
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
                    val monthReadings = allReadingsLast30Days.filter {
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

                        // Barra por cada día del mes (incluye días sin datos con 0)
                        val dayFormatter = DateTimeFormatter.ofPattern("d").withLocale(Locale("es", "ES"))
                        val entries = (1..selectedMonth.lengthOfMonth()).map { day ->
                            val date = selectedMonth.atDay(day)
                            val kwh = dailyTotals[date] ?: 0.0
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

                        // Etiquetas de día cada ~5 días
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

