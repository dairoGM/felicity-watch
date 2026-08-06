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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.domain.model.GridState
import com.dairoroberto.felicitywatch.ui.components.ProgressRing
import com.dairoroberto.felicitywatch.ui.theme.JetBrainsMonoFamily
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors
import com.dairoroberto.felicitywatch.ui.theme.SpaceGroteskFamily
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Jerarquía visual pensada para lo que el cliente necesita ver primero:
 * 1) Estado de la red (hero) — lo más crítico.
 * 2) Métricas en vivo (PV/Batería/Consumo) — el pulso del sistema.
 * 3) Autonomía/Tiempo de carga con anillos de progreso — solo sin corriente,
 *    lado a lado, más memorable que un número suelto.
 * 4) Reloj + estado de conexión con Felicity comparten una sola fila
 *    compacta — es contexto/diagnóstico, no algo que el cliente mire a
 *    diario, así que no necesita dos tarjetas grandes separadas.
 * 5) Canales de aviso como chips compactos — es configuración de fondo.
 */
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

    // Sin esto, tras terminar el onboarding el Panel se quedaba mostrando
    // "Esperando primera lectura…" hasta el próximo ciclo del servicio en
    // segundo plano (hasta 30s+) sin que el usuario supiera que podía
    // deslizar hacia abajo para forzarla — se dispara sola una vez al
    // entrar si todavía no hay ningún estado de red conocido.
    LaunchedEffect(state.liveGridState) {
        if (state.liveGridState == GridState.UNKNOWN) {
            viewModel.refreshNow()
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
            item { ClockAndConnectionRow(state, now) }
            item { GridHeroCard(state, now) }
            item { MetricsRow(state, now) }
            item { PvSurplusCard(state) }
            // Autonomía/Carga se muestran siempre, con o sin corriente de
            // red: con red, la batería siempre está cargando (el inversor
            // carga desde la red mientras SOC<100%), así que "Carga completa"
            // tiene sentido incluso online. Sin red, "Carga completa" refleja
            // el excedente solar si lo hay, o el proceso de DESCARGA si el
            // consumo supera al PV (mismo anillo, misma card, sin dejar un
            // "—" sin explicación).
            item { BatteryProjectionRow(state) }
            item { ChannelsRow(state) }
        }
    }
}

@Composable
private fun ClockAndConnectionRow(state: DashboardUiState, now: Instant) {
    val colors = LocalFelicityColors.current
    val healthy = state.connectionHealthy
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm:ss") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM").withLocale(Locale("es", "ES")) }

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1.4f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (healthy) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (healthy) colors.green else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Column(Modifier.padding(start = 8.dp)) {
                    Text(
                        if (healthy) "Conectado" else "Sin conexión",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textHi,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        connectionSubtitle(state, now),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMid,
                        maxLines = 1
                    )
                }
            }
            Box(
                Modifier
                    .width(1.dp)
                    .height(30.dp)
                    .background(colors.hairline)
            )
            Column(
                modifier = Modifier.weight(1f).padding(start = 14.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    timeFormatter.format(now.atZone(ZoneId.systemDefault())),
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 20.sp
                )
                Text(
                    dateFormatter.format(now.atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMid
                )
            }
        }
    }
}

private fun connectionSubtitle(state: DashboardUiState, now: Instant): String {
    if (!state.connectionHealthy) return state.lastError ?: "Desliza para reintentar"
    val lastReadingAt = state.lastSuccessfulReadingAt ?: return "—"
    return readingTimestampLabel(lastReadingAt, now)
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
                    color = colors.textHi,
                    fontWeight = FontWeight.Bold
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
        val loadPower = state.inverter?.loadPowerWatts
        val soc = state.battery?.socPercent
        // Con corriente de red, el inversor siempre carga la batería mientras
        // no esté al 100% (confirmado por el usuario) — la flecha va en verde
        // hacia arriba en ese caso sin importar PV vs consumo. Sin red,
        // cargando (verde, arriba) si la generación PV supera el consumo de
        // la casa (el excedente va a la batería); descargando (rojo, abajo)
        // en el caso contrario. Nunca se usa el signo de current/voltage de
        // la batería para esto (no siempre confiables/presentes).
        val chargingIndicator: Boolean? = when {
            soc != null && soc >= 100 -> null
            state.liveGridState == GridState.ONLINE && soc != null -> true
            pvPower != null && loadPower != null -> pvPower > loadPower
            else -> null
        }
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
            now = now,
            chargingIndicator = chargingIndicator
        )
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
 * Compara PV generado contra consumo de la casa con una barra doble, y
 * traduce la diferencia al lenguaje que le importa al usuario: cuánto de
 * ese excedente va a la batería (si no está llena) o cuánto déficit debe
 * cubrir la batería/red. No usa datos nuevos — pvPowerWatts y
 * loadPowerWatts ya existen en InverterReading — solo les da una lectura
 * visual dedicada en vez de dejar que el usuario reste dos tarjetas
 * separadas mentalmente.
 */
@Composable
private fun PvSurplusCard(state: DashboardUiState) {
    val colors = LocalFelicityColors.current
    val pv = state.inverter?.pvPowerWatts
    val load = state.inverter?.loadPowerWatts
    val soc = state.battery?.socPercent
    val batteryFull = soc != null && soc >= 100

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "EXCEDENTE SOLAR",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textHi,
                fontWeight = FontWeight.Bold
            )

            if (pv == null || load == null) {
                Text(
                    "Sin datos suficientes de PV/consumo en este ciclo",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textLow,
                    modifier = Modifier.padding(top = 8.dp)
                )
                return@Column
            }

            val maxScale = maxOf(pv, load, 1)
            Column(Modifier.padding(top = 12.dp)) {
                SurplusBarRow(label = "PV", value = pv, maxScale = maxScale, color = colors.accent)
                SurplusBarRow(
                    label = "Consumo",
                    value = load,
                    maxScale = maxScale,
                    color = colors.textLow,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            val surplus = pv - load
            val (message, messageColor) = when {
                surplus > 0 && batteryFull -> "Batería llena · +${formatPowerValue(surplus)} ${formatPowerUnit(surplus)} sin usar" to colors.textMid
                surplus > 0 -> "+${formatPowerValue(surplus)} ${formatPowerUnit(surplus)} disponibles para cargar la batería" to colors.green
                surplus < 0 -> "${formatPowerValue(-surplus)} ${formatPowerUnit(-surplus)} de déficit · lo cubre batería/red" to MaterialTheme.colorScheme.error
                else -> "PV y consumo equilibrados" to colors.textMid
            }
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = messageColor,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun SurplusBarRow(
    label: String,
    value: Int,
    maxScale: Int,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalFelicityColors.current
    val fraction = (value.toFloat() / maxScale).coerceIn(0f, 1f)

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMid,
            modifier = Modifier.width(64.dp)
        )
        Box(
            Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(colors.hairline)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(color)
            )
        }
        Text(
            "${formatPowerValue(value)} ${formatPowerUnit(value)}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textHi,
            modifier = Modifier.padding(start = 8.dp).width(64.dp),
            textAlign = TextAlign.End
        )
    }
}

/** Autonomía y tiempo de carga lado a lado, cada uno con un anillo de
 * progreso — más memorable que dos tarjetas apiladas de solo texto. */
@Composable
private fun BatteryProjectionRow(state: DashboardUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        BatteryRuntimeRing(state, modifier = Modifier.weight(1f))
        BatteryChargingRing(state, modifier = Modifier.weight(1f))
    }
}

/**
 * Fórmula: (capacidadAh × voltaje × SOC% / 100) / consumoW = horas. Solo
 * tiene sentido SIN corriente de red: con red presente, la casa la abastece
 * la red (no la batería) y de hecho la batería está cargando — mostrar una
 * cuenta regresiva de horas ahí sería engañoso, como si se estuviera
 * gastando algo que en realidad está protegido/en reserva. Con red, el
 * anillo pasa a un estado "Protegida" en vez de calcular una autonomía
 * hipotética.
 * El anillo muestra SOC% (no las horas) — es la variable de la que depende
 * directamente cuánto durará, y da una referencia visual consistente con
 * el anillo de carga (que también usa SOC como progreso).
 */
@Composable
private fun BatteryRuntimeRing(state: DashboardUiState, modifier: Modifier = Modifier) {
    val colors = LocalFelicityColors.current
    val soc = state.battery?.socPercent
    val loadWatts = state.inverter?.loadPowerWatts
    val capacityAh = state.battery?.capacityAh
    val voltage = state.battery?.voltage
    val onGrid = state.liveGridState == GridState.ONLINE

    val runtimeHours: Double? = if (!onGrid && soc != null && loadWatts != null && loadWatts > 0 &&
        capacityAh != null && capacityAh > 0 && voltage != null && voltage > 0
    ) {
        val availableWh = capacityAh * voltage * (soc / 100.0)
        availableWh / loadWatts
    } else null

    val protected = onGrid && soc != null

    val ringColor = when {
        protected -> colors.green
        runtimeHours == null -> colors.textLow
        runtimeHours > 5 -> colors.green
        runtimeHours > 2 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }

    ProjectionCard(
        label = "AUTONOMÍA",
        ringColor = if (protected || runtimeHours != null) ringColor else colors.textLow,
        progress = (soc ?: 0) / 100f,
        subtitle = when {
            protected -> "Con corriente — no se descarga"
            runtimeHours == null -> "Sin consumo que estimar"
            else -> null
        },
        modifier = modifier
    ) {
        if (protected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Batería protegida",
                tint = colors.green,
                modifier = Modifier.size(22.dp)
            )
        } else if (runtimeHours != null) {
            val hours = runtimeHours.toLong()
            val minutes = ((runtimeHours - hours) * 60).toLong()
            Text(
                if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m",
                fontFamily = SpaceGroteskFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = ringColor
            )
        } else {
            Text("—", fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = colors.textLow)
        }
    }
}

/**
 * Este anillo cubre 3 estados posibles de la batería, priorizados en este
 * orden (confirmado con el usuario que con red la batería SIEMPRE carga
 * mientras SOC<100%, sin depender del sol):
 * 1) Llena (SOC>=100%) — no hay nada que proyectar.
 * 2) Con corriente de red: la red carga la batería activamente. No hay una
 *    tasa de carga confiable que reportar (current/voltage del BMS no son
 *    confiables — mismo motivo por el que MetricsRow ya evita usarlos), así
 *    que se muestra el proceso de carga en curso sin ETA numérico.
 * 3) Sin corriente de red:
 *    a) Con excedente solar (PV > consumo): tiempo de carga completa =
 *       energía faltante (Wh) / excedente (W). (comportamiento original)
 *    b) Sin excedente (consumo >= PV): la batería se está DESCARGANDO — el
 *       mismo anillo pasa a mostrar autonomía restante en vez de un "—" sin
 *       explicación, formula igual a BatteryRuntimeRing.
 */
@Composable
private fun BatteryChargingRing(state: DashboardUiState, modifier: Modifier = Modifier) {
    val colors = LocalFelicityColors.current
    val soc = state.battery?.socPercent
    val pvWatts = state.inverter?.pvPowerWatts
    val loadWatts = state.inverter?.loadPowerWatts
    val capacityAh = state.battery?.capacityAh
    val voltage = state.battery?.voltage
    val onGrid = state.liveGridState == GridState.ONLINE
    val surplusWatts = if (pvWatts != null && loadWatts != null) pvWatts - loadWatts else null

    val hasCapacityData = capacityAh != null && capacityAh > 0 && voltage != null && voltage > 0

    val chargingHours: Double? = if (!onGrid && soc != null && soc < 100 && surplusWatts != null && surplusWatts > 0 && hasCapacityData) {
        val missingWh = capacityAh!! * voltage!! * ((100 - soc) / 100.0)
        missingWh / surplusWatts
    } else null

    val dischargingHours: Double? = if (!onGrid && soc != null && loadWatts != null && loadWatts > 0 &&
        (surplusWatts == null || surplusWatts <= 0) && hasCapacityData
    ) {
        val availableWh = capacityAh!! * voltage!! * (soc / 100.0)
        availableWh / loadWatts
    } else null

    val full = soc != null && soc >= 100
    val chargingFromGrid = !full && onGrid && soc != null

    val stalledReason: String? = when {
        full || chargingFromGrid || chargingHours != null || dischargingHours != null -> null
        surplusWatts != null && surplusWatts <= 0 && loadWatts == null -> "Sin consumo que estimar"
        !hasCapacityData -> "Sin dato suficiente"
        else -> null
    }

    val ringColor = when {
        full -> colors.green
        chargingFromGrid || chargingHours != null -> colors.accent
        dischargingHours != null -> MaterialTheme.colorScheme.error
        else -> colors.textLow
    }

    ProjectionCard(
        label = if (dischargingHours != null) "DESCARGA" else "CARGA COMPLETA",
        ringColor = ringColor,
        progress = (soc ?: 0) / 100f,
        subtitle = stalledReason,
        modifier = modifier
    ) {
        when {
            full -> Text(
                "Llena",
                fontFamily = SpaceGroteskFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = colors.green
            )
            chargingFromGrid -> ChargeDirectionContent(charging = true, color = colors.accent) {
                Text(
                    "Cargando",
                    fontFamily = SpaceGroteskFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = colors.accent
                )
            }
            chargingHours != null -> {
                val hours = chargingHours.toLong()
                val minutes = ((chargingHours - hours) * 60).toLong()
                ChargeDirectionContent(charging = true, color = colors.accent) {
                    Text(
                        if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m",
                        fontFamily = SpaceGroteskFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = colors.accent
                    )
                }
            }
            dischargingHours != null -> {
                val hours = dischargingHours.toLong()
                val minutes = ((dischargingHours - hours) * 60).toLong()
                ChargeDirectionContent(charging = false, color = MaterialTheme.colorScheme.error) {
                    Text(
                        if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m",
                        fontFamily = SpaceGroteskFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            else -> Text("—", fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = colors.textLow)
        }
    }
}

/** Flecha (creciendo/decreciendo) sobre el valor, DENTRO del propio anillo
 * — antes el anillo de "Carga completa"/"Descarga" solo distinguía el
 * estado por color y texto, sin el mismo indicador visual de flecha que ya
 * tiene la tarjeta de BATERÍA en MetricsRow. */
@Composable
private fun ChargeDirectionContent(
    charging: Boolean,
    color: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            if (charging) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
            contentDescription = if (charging) "Cargando" else "Descargando",
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        content()
    }
}

@Composable
private fun ProjectionCard(
    label: String,
    ringColor: androidx.compose.ui.graphics.Color,
    progress: Float,
    modifier: Modifier = Modifier,
    /** Motivo breve de por qué no hay una estimación activa ahora mismo (ej.
     * "Sin excedente solar ahora") — sin esto el usuario solo veía un "—"
     * congelado sin saber si es un bug o un estado esperado. */
    subtitle: String? = null,
    ringContent: @Composable () -> Unit
) {
    val colors = LocalFelicityColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProgressRing(
                progress = progress,
                color = ringColor,
                trackColor = colors.hairline,
                size = 104.dp,
                strokeWidth = 9.dp
            ) {
                ringContent()
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textHi,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp)
            )
            // Alto reservado siempre (2 líneas), tenga o no subtítulo, para
            // que esta card y la de al lado midan exactamente lo mismo — antes
            // la que sí traía un motivo (ej. "Sin excedente solar ahora") se
            // veía más alta que la otra, descuadrando la fila.
            Text(
                subtitle.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow,
                textAlign = TextAlign.Center,
                maxLines = 2,
                minLines = 2,
                modifier = Modifier.padding(top = 2.dp)
            )
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
    now: Instant = Instant.now(),
    /** true = cargando (PV > consumo), false = descargando, null = sin dato
     * suficiente para saberlo (no se muestra ninguna flecha). */
    chargingIndicator: Boolean? = null
) {
    val colors = LocalFelicityColors.current
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textHi,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
                    modifier = Modifier.weight(1f)
                )
                if (chargingIndicator != null) {
                    Icon(
                        if (chargingIndicator) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = if (chargingIndicator) "Cargando" else "Descargando",
                        tint = if (chargingIndicator) colors.green else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(top = 4.dp).fillMaxWidth()
            ) {
                Text(
                    valueText,
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 24.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    " $unit",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMid,
                    maxLines = 1
                )
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

/** Fila compacta de chips, en vez de una tarjeta grande con iconos de
 * 32dp — los canales son configuración de fondo, no algo que el cliente
 * revise a diario, así que no necesitan tanto espacio vertical. */
@Composable
private fun ChannelsRow(state: DashboardUiState) {
    val colors = LocalFelicityColors.current
    val event = state.latestEvent
    val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale("es", "ES"))

    fun timeFor(sent: Boolean): String {
        if (event == null || !sent) return "—"
        return timeFormatter.format(event.triggeredAt.atZone(ZoneId.systemDefault()))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ChannelChip(
            icon = Icons.Default.Phone,
            label = "Voz",
            configured = state.voiceConfigured,
            lastFired = timeFor(event?.voiceSent == true),
            modifier = Modifier.weight(1f)
        )
        ChannelChip(
            icon = Icons.Default.Notifications,
            label = "Push",
            configured = state.pushConfigured,
            lastFired = timeFor(event?.pushSent == true),
            modifier = Modifier.weight(1f)
        )
        ChannelChip(
            icon = Icons.Default.Chat,
            label = "WhatsApp",
            configured = state.whatsappConfigured,
            lastFired = timeFor(event?.whatsappSent == true),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ChannelChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    configured: Boolean,
    lastFired: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalFelicityColors.current
    val accent = if (configured) colors.green else colors.textLow

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.padding(start = 6.dp).size(16.dp))
            Column(Modifier.padding(start = 6.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textHi, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(lastFired, style = MaterialTheme.typography.labelSmall, color = colors.textLow, maxLines = 1)
            }
        }
    }
}
