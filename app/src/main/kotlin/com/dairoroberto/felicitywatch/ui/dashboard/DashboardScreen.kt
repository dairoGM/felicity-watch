package com.dairoroberto.felicitywatch.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
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
    val pollingIntervalSeconds by viewModel.pollingIntervalSeconds.collectAsState()

    // Tick cada segundo: alimenta tanto el reloj en vivo del Panel como los
    // textos "hace X min", que de lo contrario quedarían congelados hasta
    // la próxima lectura real aunque el tiempo transcurrido sí cambie.
    // Metrica cuyo detalle se esta mostrando; null = ningun modal abierto.
    var metricDetail by remember { mutableStateOf<MetricDetail?>(null) }

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

    metricDetail?.let { detail ->
        MetricDetailDialog(
            detail = detail,
            readings = state.allReadingsLast30Days,
            onDismiss = { metricDetail = null }
        )
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
            item { ClockAndConnectionRow(state, now, pollingIntervalSeconds) }
            item { GridHeroCard(state, now) }
            item { MetricsRow(state, now) { detail -> metricDetail = detail } }
            // Autonomía (anillo) y Excedente Solar (dos barras PV/Consumo)
            // lado a lado — el segundo reemplaza al antiguo anillo de "Carga
            // completa/Descarga", que duplicaba las mismas horas que ya
            // muestra Autonomía en el escenario sin red/sin excedente. El
            // card standalone de excedente que existía antes (PvSurplusCard)
            // se retiró para no repetir la misma información dos veces.
            item { BatteryProjectionRow(state) }
            item { ChannelsRow(state) }
        }
    }
}

@Composable
private fun ClockAndConnectionRow(
    state: DashboardUiState,
    now: Instant,
    pollingIntervalSeconds: Int
) {
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
                    val lastReadingAt = state.lastSuccessfulReadingAt
                    if (state.connectionHealthy && lastReadingAt != null) {
                        Text(
                            "Última lectura ${exactReadingTime(lastReadingAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMid,
                            maxLines = 1
                        )
                    } else {
                        Text(
                            connectionSubtitle(state, now),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMid,
                            maxLines = 1
                        )
                    }
                    // Antigüedad del dato en sí, distinta de la de la
                    // lectura: hace visible el defasaje de la nube.
                    dataAgeLabel(state, now)?.let { label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textLow,
                            maxLines = 1
                        )
                    }
                }
            }

            // Anilla con la cuenta regresiva a la próxima lectura. Ocupa un
            // ancho fijo, así el resto del card no se mueve al cambiar de
            // "27" a "9".
            val lastReadingAt = state.lastSuccessfulReadingAt
            val remaining = if (healthy) {
                secondsUntilNextReading(lastReadingAt, now, pollingIntervalSeconds)
            } else null
            if (remaining != null) {
                NextReadingRing(
                    remainingSeconds = remaining,
                    intervalSeconds = pollingIntervalSeconds,
                    modifier = Modifier.padding(end = 12.dp)
                )
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

/**
 * Antigüedad del DATO, que no es lo mismo que la antigüedad de la lectura.
 *
 * "Última lectura" dice cuándo la app le preguntó a Felicity; esto dice de
 * qué momento es el dato que Felicity respondió ("dataTimeStr" del
 * snapshot). Entre los dos está el defasaje real: el inversor sube sus
 * datos a la nube cada cierto tiempo, así que incluso una lectura recién
 * hecha puede traer un valor de varios minutos antes. Sin este número el
 * defasaje es invisible y un consumo viejo parece actual.
 */
private fun dataAgeLabel(state: DashboardUiState, now: Instant): String? {
    val reportedAt = state.inverter?.deviceReportedAt ?: return null
    val secondsAgo = Duration.between(reportedAt, now).seconds
    // Un dato "del futuro" solo puede venir de un desfase de reloj entre el
    // inversor y el teléfono; mostrar "hace -40s" confundiría más que ayudar.
    if (secondsAgo < 0) return null

    val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale("es", "ES"))
    val time = formatter.format(reportedAt.atZone(ZoneId.systemDefault()))
    val age = when {
        secondsAgo < 60 -> "hace ${secondsAgo}s"
        secondsAgo < 3600 -> "hace ${secondsAgo / 60}min"
        else -> "hace ${secondsAgo / 3600}h ${(secondsAgo % 3600) / 60}min"
    }
    return "Dato del inversor: $time ($age)"
}

private fun readingTimestampLabel(instant: Instant, now: Instant): String {
    val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale("es", "ES"))
    val time = formatter.format(instant.atZone(ZoneId.systemDefault()))
    val secondsAgo = Duration.between(instant, now).seconds.coerceAtLeast(0)
    return "$time (hace ${secondsAgo}s)"
}

/**
 * Cuenta regresiva a la próxima lectura, como anilla que se vacía.
 *
 * Va en un tamaño FIJO y no como texto en línea: el texto "Próxima en 27s"
 * cambia de ancho al bajar a "9s", y eso desplazaba el resto del card en cada
 * segundo. Un círculo de lado fijo mantiene el layout quieto, y el número
 * queda centrado dentro sin empujar nada.
 */
@Composable
private fun NextReadingRing(
    remainingSeconds: Int,
    intervalSeconds: Int,
    modifier: Modifier = Modifier
) {
    val colors = LocalFelicityColors.current
    val reading = remainingSeconds <= 0
    val accent = if (reading) colors.green else colors.accent

    // Fracción que queda por transcurrir. Se anima para que el arco no salte
    // de un segundo al siguiente.
    val target = if (intervalSeconds > 0) {
        (remainingSeconds.toFloat() / intervalSeconds).coerceIn(0f, 1f)
    } else 0f
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 400),
        label = "nextReadingProgress"
    )

    Box(modifier = modifier.size(RING_SIZE), contentAlignment = Alignment.Center) {
        // Pista de fondo: deja ver el círculo completo aunque quede poco arco.
        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.fillMaxSize(),
            color = colors.hairline.copy(alpha = 0.45f),
            strokeWidth = RING_STROKE,
            trackColor = Color.Transparent
        )
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = accent,
            strokeWidth = RING_STROKE,
            trackColor = Color.Transparent
        )
        if (reading) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Leyendo ahora",
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
        } else {
            Text(
                remainingSeconds.toString(),
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = accent
            )
        }
    }
}

private val RING_SIZE = 40.dp
private val RING_STROKE = 3.dp

/** Hora exacta de la última lectura, con segundos. */
private fun exactReadingTime(instant: Instant): String {
    val formatter = DateTimeFormatter.ofPattern("hh:mm:ss a").withLocale(Locale("es", "ES"))
    return formatter.format(instant.atZone(ZoneId.systemDefault()))
}

/**
 * Segundos que faltan para la próxima lectura del servicio.
 *
 * Es una ESTIMACIÓN: el servicio duerme el intervalo configurado entre
 * lecturas, así que el momento de la próxima se deduce de la última más el
 * intervalo. Si una lectura tarda o falla, el conteo llega a 0 y se queda
 * ahí hasta que entre la siguiente — por eso la UI muestra "ahora…" en vez
 * de números negativos.
 */
private fun secondsUntilNextReading(
    lastReadingAt: Instant?,
    now: Instant,
    intervalSeconds: Int
): Int? {
    if (lastReadingAt == null) return null
    val elapsed = Duration.between(lastReadingAt, now).seconds
    if (elapsed < 0) return null
    return (intervalSeconds - elapsed).coerceAtLeast(0L).toInt()
}

@Composable
private fun GridHeroCard(state: DashboardUiState, now: Instant) {
    val colors = LocalFelicityColors.current
    val online = state.liveGridState == GridState.ONLINE
    val unknown = state.liveGridState == GridState.UNKNOWN
    val accent = if (unknown) colors.textLow else if (online) colors.green else MaterialTheme.colorScheme.error

    // Misma fuente que el Reporte (pestaña Corriente): se reconstruyen los
    // tramos reales desde el historial de 30 días, en vez de depender de
    // lastGridChangeAt (que solo se actualiza cuando una regla de alerta
    // se dispara y podía quedar desfasado) — así el Panel y el Reporte
    // nunca pueden mostrar un "lleva X tiempo" distinto entre sí.
    val segments = remember(state.allReadingsLast30Days, now) {
        com.dairoroberto.felicitywatch.domain.usecase.buildGridSegments(state.allReadingsLast30Days, now.toEpochMilli())
    }
    val lastSegment = segments.lastOrNull()
    val elapsedText = if (!unknown && lastSegment != null && lastSegment.online == online) {
        val elapsed = Duration.between(Instant.ofEpochMilli(lastSegment.startEpochMillis), now)
        val hours = elapsed.toHours()
        val minutes = elapsed.toMinutes() % 60
        if (hours > 0) "${hours}h ${minutes}min" else "${minutes}min"
    } else {
        null
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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
                // Badge de tiempo transcurrido — el indicador que pidió el
                // cliente ("3h 32min con corriente"), tratado como un
                // elemento visual propio (no un texto secundario gris) para
                // que destaque igual que el estado principal.
                if (elapsedText != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(accent.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            elapsedText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
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
            // Sin conexión con Felicity el estado mostrado es el ÚLTIMO
            // conocido, no una lectura actual — se aclara explícitamente en
            // vez de afirmar "sin corriente" (que el usuario leía como un
            // corte real cuando en realidad solo se cayó la comunicación).
            if (!state.connectionHealthy && !unknown) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = colors.textMid,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        "Sin conexión con Felicity · último estado conocido",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMid,
                        modifier = Modifier.padding(start = 5.dp)
                    )
                }
            } else {
                Text(
                    gridSinceLabel(lastSegment, online, unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/** "Desde las 09:15am" — hora exacta en que comenzó el tramo vigente,
 * complementa el badge de tiempo transcurrido con un ancla temporal
 * concreta (mismo criterio de formato 12h que usa el Reporte). */
private fun gridSinceLabel(
    lastSegment: com.dairoroberto.felicitywatch.ui.components.GridSegment?,
    online: Boolean,
    unknown: Boolean
): String {
    if (unknown || lastSegment == null || lastSegment.online != online) {
        return "Todavía no se confirmó ningún cambio"
    }
    val zoned = Instant.ofEpochMilli(lastSegment.startEpochMillis).atZone(ZoneId.systemDefault())
    val hourFormatter = DateTimeFormatter.ofPattern("hh:mm").withLocale(Locale("es", "ES"))
    val suffix = if (zoned.hour < 12) "am" else "pm"
    return "Desde las ${hourFormatter.format(zoned)}$suffix"
}

@Composable
private fun MetricsRow(
    state: DashboardUiState,
    now: Instant,
    onOpenDetail: (MetricDetail) -> Unit
) {
    // IntrinsicSize.Min + fillMaxHeight en cada card: la fila mide el alto
    // del contenido más alto y los tres cards lo adoptan, así ninguno queda
    // más corto que sus vecinos cuando solo uno trae dato extra (ej. el
    // estimado de carga en Batería).
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
    ) {
        val pvPower = state.inverter?.pvPowerWatts
        MetricCard(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            label = "GENERACIÓN PV",
            onClick = { onOpenDetail(MetricDetail.PV) },
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
        val colors = LocalFelicityColors.current
        val timeToFullText = solarTimeToFullChargeLabel(state)
        // Verde solo cuando hay un estimado real de llegar al 100%; los
        // avisos de excedente insuficiente van en tono neutro para no
        // leerse como una buena noticia.
        val timeToFullColor = if (timeToFullText?.endsWith("al 100%") == true) colors.green else colors.textMid
        MetricCard(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            label = "BATERÍA",
            onClick = { onOpenDetail(MetricDetail.BATTERY) },
            valueText = state.battery?.socPercent?.toString() ?: "—",
            unit = "%",
            errorReason = missingValueReason(
                readingExists = state.battery != null,
                fieldPresent = state.battery?.socPercent != null,
                readingError = state.batteryError
            ),
            lastReadingAt = state.lastSuccessfulReadingAt,
            now = now,
            chargingIndicator = chargingIndicator,
            highlightText = timeToFullText,
            highlightColor = timeToFullColor
        )
        MetricCard(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            label = "CONSUMO",
            onClick = { onOpenDetail(MetricDetail.LOAD) },
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
/**
 * Reemplaza al antiguo anillo de "Carga completa/Descarga" (que repetía las
 * mismas horas que ya muestra el anillo de Autonomía en el escenario sin
 * red/sin excedente) y al card standalone de excedente solar que existía
 * antes — misma información (PV vs consumo de la casa), en el mismo tamaño
 * de card que ProjectionCard para que la fila quede pareja, con dos barras
 * VERTICALES en vez de un anillo: más directo para comparar dos magnitudes
 * a la vez que una cuenta de horas.
 */
@Composable
private fun PvSurplusMiniCard(state: DashboardUiState, modifier: Modifier = Modifier) {
    val colors = LocalFelicityColors.current
    val pv = state.inverter?.pvPowerWatts
    val load = state.inverter?.loadPowerWatts

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
            if (pv == null || load == null) {
                Box(modifier = Modifier.height(104.dp), contentAlignment = Alignment.Center) {
                    Text("—", fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = colors.textLow)
                }
            } else {
                val maxScale = maxOf(pv, load, 1)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.height(104.dp)
                ) {
                    SurplusVerticalBar(label = "PV", value = pv, maxScale = maxScale, color = colors.green)
                    SurplusVerticalBar(label = "Consumo", value = load, maxScale = maxScale, color = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                "EXCEDENTE SOLAR",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textHi,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp)
            )
            val subtitle = if (pv != null && load != null) {
                val surplus = pv - load
                when {
                    surplus > 0 -> "+${formatPowerValue(surplus)} ${formatPowerUnit(surplus)} de excedente"
                    surplus < 0 -> "${formatPowerValue(-surplus)} ${formatPowerUnit(-surplus)} de déficit"
                    else -> "PV y consumo equilibrados"
                }
            } else {
                "Sin datos suficientes"
            }
            Text(
                subtitle,
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

@Composable
private fun SurplusVerticalBar(
    label: String,
    value: Int,
    maxScale: Int,
    color: androidx.compose.ui.graphics.Color
) {
    val colors = LocalFelicityColors.current
    val fraction = (value.toFloat() / maxScale).coerceIn(0.03f, 1f)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "${formatPowerValue(value)}${formatPowerUnit(value)}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textHi,
            fontWeight = FontWeight.Medium
        )
        Box(
            Modifier
                .padding(top = 4.dp)
                .width(28.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.hairline),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMid,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** Autonomía (anillo de progreso) y Excedente Solar (dos barras PV/Consumo)
 * lado a lado, mismo alto — más memorable que tarjetas apiladas de solo
 * texto, y evita repetir las mismas horas de autonomía dos veces con
 * distinto nombre (antes el segundo anillo mostraba "Descarga" con el
 * mismo número que ya muestra Autonomía). */
@Composable
private fun BatteryProjectionRow(state: DashboardUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        BatteryRuntimeRing(state, modifier = Modifier.weight(1f))
        PvSurplusMiniCard(state, modifier = Modifier.weight(1f))
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

/**
 * Tiempo estimado para que la batería llegue al 100% cargando SOLO con el
 * excedente solar (PV menos el consumo de la casa) — pensado para el
 * escenario sin corriente de red, donde el usuario necesita saber si le va
 * a alcanzar el sol del día para recargar.
 *
 * Fórmula: energía faltante (Wh) / excedente solar (W) = horas.
 * La energía faltante es capacidadAh × voltaje × (100 − SOC) / 100, misma
 * base que usa el anillo de Autonomía para el cálculo inverso.
 *
 * Devuelve null (no se muestra nada) cuando el estimado no aplica o no es
 * calculable: con corriente de red (la carga no depende del sol), batería
 * ya al 100%, sin excedente solar (el PV no cubre ni el consumo, así que
 * no está cargando), o si falta algún dato del equipo.
 */
private fun solarTimeToFullChargeLabel(state: DashboardUiState): String? {
    if (state.liveGridState != GridState.OFFLINE) return null

    val soc = state.battery?.socPercent ?: return null
    if (soc >= 100) return null

    val capacityAh = state.battery?.capacityAh ?: return null
    val voltage = state.battery?.voltage ?: return null
    if (capacityAh <= 0 || voltage <= 0) return null

    val pvWatts = state.inverter?.pvPowerWatts ?: return null
    val loadWatts = state.inverter?.loadPowerWatts ?: return null
    val surplusWatts = pvWatts - loadWatts
    // Sin excedente el consumo se está comiendo todo el PV: la batería no
    // carga (o se descarga). Se dice explícitamente en vez de ocultar el
    // dato, que dejaba al usuario sin saber si era un bug o un estado real.
    if (surplusWatts <= 0) return "Sin excedente"

    val missingWh = capacityAh * voltage * ((100 - soc) / 100.0)
    val hoursToFull = missingWh / surplusWatts

    // Con excedentes muy bajos el estimado se dispara a decenas de horas.
    // El número exacto ahí no significa nada (el sol se va mucho antes),
    // pero el HECHO de que el excedente no alcanza sí es información
    // valiosa — así que se comunica de forma cualitativa.
    if (hoursToFull > 24) return "Carga muy lenta"

    // Texto corto ("2h 39m", no "100% en 2h 39min"): el card es angosto y
    // con el texto largo se cortaba a media palabra. El ícono de reloj y
    // la flecha de "cargando" ya dan el contexto de qué representa.
    val totalMinutes = (hoursToFull * 60).toLong()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m al 100%" else "${minutes}m al 100%"
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
    chargingIndicator: Boolean? = null,
    /** Dato derivado destacado bajo el valor principal — ej. en Batería,
     * el tiempo estimado para llegar al 100% con la carga solar actual. */
    highlightText: String? = null,
    highlightColor: androidx.compose.ui.graphics.Color? = null,
    /** Abre el detalle de la metrica. null = card no interactivo. */
    onClick: (() -> Unit)? = null
) {
    val colors = LocalFelicityColors.current
    Card(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
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
            // Dato derivado destacado (ej. "100% en 2h 15min") — va justo
            // bajo el valor principal para que se lea como parte de la
            // métrica, no como una nota al pie. El alto se reserva SIEMPRE
            // (aunque no haya texto) para que los tres cards de la fila
            // midan exactamente lo mismo y no se descuadren cuando solo
            // uno tiene dato extra.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp).height(14.dp)
            ) {
                if (highlightText != null) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = highlightColor ?: colors.textMid,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        highlightText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = highlightColor ?: colors.textMid,
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 3.dp)
                    )
                }
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
                // 2 líneas fijas: el texto envuelve distinto según el ancho
                // disponible de cada card, y sin esto un card podía quedar
                // una línea más alto que sus vecinos.
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow,
                    maxLines = 2,
                    minLines = 2,
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
