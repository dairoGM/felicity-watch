package com.dairoroberto.felicitywatch.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.domain.model.GridState
import com.dairoroberto.felicitywatch.ui.theme.JetBrainsMonoFamily
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors
import com.dairoroberto.felicitywatch.ui.theme.SpaceGroteskFamily
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    // Tick cada segundo: alimenta tanto el reloj en vivo del Panel como los
    // textos "hace X min", que de lo contrario quedarían congelados hasta
    // la próxima lectura real aunque el tiempo transcurrido sí cambie.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            now = Instant.now()
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshNow() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { ClockCard(now) }
            item { ConnectionStatusCard(state, now) }
            item { GridHeroCard(state, now) }
            item { MetricsRow(state, now) }
            // Solo mostrar autonomía cuando no hay corriente de red
            if (state.liveGridState == GridState.OFFLINE) {
                item { BatteryRuntimeCard(state) }
            }
            item {
                Text(
                    "CANALES DE AVISO",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalFelicityColors.current.textLow,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
            item { ChannelsList(state) }
        }
    }
}

@Composable
private fun ClockCard(now: Instant) {
    val colors = LocalFelicityColors.current
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm:ss") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE d 'de' MMMM").withLocale(Locale("es", "ES")) }

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                timeFormatter.format(now.atZone(ZoneId.systemDefault())),
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 32.sp
            )
            Text(
                dateFormatter.format(now.atZone(ZoneId.systemDefault()))
                    .replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMid,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** Barra de acento a la izquierda en vez de rellenar toda la tarjeta de color — más sobrio. */
@Composable
private fun AccentBar(color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .width(4.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}

@Composable
private fun ConnectionStatusCard(state: DashboardUiState, now: Instant) {
    val healthy = state.connectionHealthy
    val colors = LocalFelicityColors.current

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccentBar(if (healthy) colors.green else MaterialTheme.colorScheme.error)
            Icon(
                if (healthy) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (healthy) colors.green else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 12.dp)
            )
            Column(Modifier.padding(start = 10.dp)) {
                Text(
                    if (healthy) "Conectado con Felicity" else "Sin comunicación con Felicity",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (!healthy && state.lastError != null) {
                    Text(
                        state.lastError,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMid
                    )
                } else if (healthy && state.lastSuccessfulReadingAt != null) {
                    Text(
                        "Última lectura: ${readingTimestampLabel(state.lastSuccessfulReadingAt, now)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMid
                    )
                } else if (!healthy) {
                    Text(
                        "Desliza hacia abajo para reintentar",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMid
                    )
                }
            }
        }
    }
}

private fun readingTimestampLabel(instant: Instant, now: Instant): String {
    val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale("es", "ES"))
    val time = formatter.format(instant.atZone(ZoneId.systemDefault()))
    val secondsAgo = Duration.between(instant, now).seconds.coerceAtLeast(0)
    return "$time (hace ${secondsAgo}s)"
}

@Composable
private fun GridHeroCard(state: DashboardUiState, now: Instant) {
    val colors = LocalFelicityColors.current
    val online = state.liveGridState == GridState.ONLINE
    val unknown = state.liveGridState == GridState.UNKNOWN
    val accent = if (unknown) colors.textLow else if (online) colors.green else MaterialTheme.colorScheme.error

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Text(
                    "  ESTADO DE LA RED",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMid
                )
            }
            Text(
                when {
                    unknown -> "Esperando primera lectura…"
                    online -> "Con corriente eléctrica"
                    else -> "Sin corriente eléctrica"
                },
                fontFamily = SpaceGroteskFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 26.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                lastChangeLabel(state.lastGridChangeAt, now),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMid,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private fun lastChangeLabel(lastChangeAt: Instant?, now: Instant): String {
    if (lastChangeAt == null) return "Todavía no se confirmó ningún cambio"
    val elapsed = Duration.between(lastChangeAt, now)
    val hours = elapsed.toHours()
    val minutes = elapsed.toMinutes() % 60
    return when {
        hours > 0 -> "Último cambio confirmado hace ${hours} h ${minutes} min"
        minutes > 0 -> "Último cambio confirmado hace ${minutes} min"
        else -> "Último cambio confirmado hace instantes"
    }
}

@Composable
private fun MetricsRow(state: DashboardUiState, now: Instant) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        val pvPower = state.inverter?.pvPowerWatts
        MetricCard(
            modifier = Modifier.weight(1f),
            label = "GENERACIÓN PV",
            valueText = pvPower?.let { formatPowerValue(it) } ?: "—",
            unit = pvPower?.let { formatPowerUnit(it) } ?: "W",
            errorReason = missingValueReason(
                readingExists = state.inverter != null,
                fieldPresent = pvPower != null,
                readingError = state.inverterError
            ),
            lastReadingAt = state.lastSuccessfulReadingAt,
            now = now
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            label = "BATERÍA",
            valueText = state.battery?.socPercent?.toString() ?: "—",
            unit = "%",
            errorReason = missingValueReason(
                readingExists = state.battery != null,
                fieldPresent = state.battery?.socPercent != null,
                readingError = state.batteryError
            ),
            lastReadingAt = state.lastSuccessfulReadingAt,
            now = now
        )
        val loadPower = state.inverter?.loadPowerWatts
        MetricCard(
            modifier = Modifier.weight(1f),
            label = "CONSUMO",
            valueText = loadPower?.let { formatPowerValue(it) } ?: "—",
            unit = loadPower?.let { formatPowerUnit(it) } ?: "W",
            errorReason = missingValueReason(
                readingExists = state.inverter != null,
                fieldPresent = loadPower != null,
                readingError = state.inverterError
            ),
            lastReadingAt = state.lastSuccessfulReadingAt,
            now = now
        )
    }
}

/**
 * Tarjeta de autonomía de batería — solo visible cuando no hay corriente eléctrica.
 * Capacidad (Ah) y voltaje vienen del propio equipo (totalEmsCapacity/emsVoltage
 * del API de Felicity), ya no se configuran a mano en Ajustes.
 * Fórmula: (capacidadAh × voltaje × SOC% / 100) / consumoW = horas
 * Ejemplo: (350 Ah × 48 V × 75 / 100) / 1250 W ≈ 10.1 h
 */
@Composable
private fun BatteryRuntimeCard(state: DashboardUiState) {
    val colors = LocalFelicityColors.current
    val soc = state.battery?.socPercent
    val loadWatts = state.inverter?.loadPowerWatts
    val capacityAh = state.battery?.capacityAh
    val voltage = state.battery?.voltage

    // Energía disponible (Wh) = capacidad (Ah) × voltaje (V) × SOC% / 100.
    // Sin voltaje no hay forma de convertir Ah a Wh, así que sin ese dato no
    // se muestra un número (mejor "—" que una hora inventada).
    val runtimeHours: Double? = if (soc != null && loadWatts != null && loadWatts > 0 &&
        capacityAh != null && capacityAh > 0 && voltage != null && voltage > 0
    ) {
        val availableWh = capacityAh * voltage * (soc / 100.0)
        availableWh / loadWatts
    } else null

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(colors.green.copy(alpha = 0.7f))
                )
                Text(
                    "  AUTONOMÍA DE BATERÍA",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMid
                )
            }
            if (runtimeHours != null) {
                val hours = runtimeHours.toLong()
                val minutes = ((runtimeHours - hours) * 60).toLong()
                val runtimeText = when {
                    hours > 0 -> "${hours}h ${minutes}min"
                    else -> "${minutes}min"
                }
                val runtimeColor = when {
                    runtimeHours > 5 -> colors.green
                    runtimeHours > 2 -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.error
                }
                Text(
                    runtimeText,
                    fontFamily = SpaceGroteskFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 28.sp,
                    color = runtimeColor,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Capacidad: ${capacityAh?.toInt()} Ah",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow
                    )
                    Text(
                        "SOC: ${soc}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow
                    )
                    Text(
                        "Consumo: ${loadWatts} W",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow
                    )
                }
            } else {
                Text(
                    "Sin datos suficientes para calcular la autonomía",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
}

private fun formatPowerValue(watts: Int): String =
    if (watts >= 1000) String.format(Locale("es", "ES"), "%.2f", watts / 1000.0) else watts.toString()

private fun formatPowerUnit(watts: Int): String = if (watts >= 1000) "kW" else "W"

/**
 * Distingue por qué una métrica muestra "—": si nunca se pudo leer el
 * dispositivo (error real), o si la lectura tuvo éxito pero Felicity no
 * trajo ese campo puntual en este ciclo (típico cuando el equipo está
 * desconectado por un corte de luz — no es un bug de la app).
 */
private fun missingValueReason(readingExists: Boolean, fieldPresent: Boolean, readingError: String?): String? {
    if (fieldPresent) return null
    if (!readingExists) return readingError ?: "No se pudo leer el equipo"
    return "El equipo no reportó este dato en el último ciclo"
}

/** Misma etiqueta que "Última lectura" del primer card (hora exacta + hace
 * Xs), pero con la hora que el EQUIPO reportó — distinta de cuándo la app
 * consultó; si es vieja, el equipo está desconectado (ej. corte de luz le
 * quita WiFi al collector), no un bug de la app. */
private fun lastReadingLabel(lastReadingAt: Instant?, now: Instant): String? {
    if (lastReadingAt == null) return null
    return "Última lectura: ${readingTimestampLabel(lastReadingAt, now)}"
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    label: String,
    valueText: String,
    unit: String,
    errorReason: String? = null,
    lastReadingAt: Instant? = null,
    now: Instant = Instant.now()
) {
    val colors = LocalFelicityColors.current
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textLow)
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    valueText,
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 26.sp
                )
                Text(" $unit", style = MaterialTheme.typography.bodyMedium, color = colors.textMid)
            }
            if (errorReason != null) {
                Text(
                    errorReason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            lastReadingLabel(lastReadingAt, now)?.let { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ChannelsList(state: DashboardUiState) {
    val colors = LocalFelicityColors.current
    val event = state.latestEvent
    val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale("es", "ES"))

    fun timeFor(sent: Boolean): String {
        if (event == null || !sent) return "—"
        return timeFormatter.format(event.triggeredAt.atZone(ZoneId.systemDefault()))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ChannelStatusIcon(
                icon = androidx.compose.material.icons.Icons.Default.Phone,
                label = "Voz",
                configured = state.voiceConfigured,
                lastFired = timeFor(event?.voiceSent == true)
            )
            ChannelStatusIcon(
                icon = androidx.compose.material.icons.Icons.Default.Notifications,
                label = "Push",
                configured = state.pushConfigured,
                lastFired = timeFor(event?.pushSent == true)
            )
            ChannelStatusIcon(
                icon = androidx.compose.material.icons.Icons.Default.Chat,
                label = "WhatsApp",
                configured = state.whatsappConfigured,
                lastFired = timeFor(event?.whatsappSent == true)
            )
        }
    }
}

@Composable
private fun ChannelStatusIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    configured: Boolean,
    lastFired: String
) {
    val colors = LocalFelicityColors.current
    val accent = if (configured) colors.green else colors.textLow

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                icon,
                contentDescription = label,
                tint = accent,
                modifier = Modifier
                    .size(32.dp)
                    .padding(4.dp)
            )
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            lastFired,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textLow,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

