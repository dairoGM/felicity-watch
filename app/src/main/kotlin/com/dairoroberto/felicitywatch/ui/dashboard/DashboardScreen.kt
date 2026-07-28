package com.dairoroberto.felicitywatch.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ConnectionStatusCard(state) }
        item { GridHeroCard(state) }
        item { MetricsRow(state) }
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

@Composable
private fun ConnectionStatusCard(state: DashboardUiState) {
    val healthy = state.connectionHealthy
    val colors = LocalFelicityColors.current

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (healthy) colors.surface2 else colors.dangerBg
        ),
        shape = RoundedCornerShape(11.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (healthy) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (healthy) colors.green else MaterialTheme.colorScheme.error
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
                        "Última lectura hace ${secondsAgo(state.lastSuccessfulReadingAt)} s",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMid
                    )
                }
            }
        }
    }
}

private fun secondsAgo(instant: Instant): Long = Duration.between(instant, Instant.now()).seconds.coerceAtLeast(0)

@Composable
private fun GridHeroCard(state: DashboardUiState) {
    val colors = LocalFelicityColors.current
    val online = state.liveGridState == GridState.ONLINE
    val unknown = state.liveGridState == GridState.UNKNOWN

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (online) colors.greenDim else colors.surface2
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (online) colors.green else MaterialTheme.colorScheme.error)
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
                fontFamily = com.dairoroberto.felicitywatch.ui.theme.SpaceGroteskFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                lastChangeLabel(state.lastGridChangeAt),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMid,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private fun lastChangeLabel(lastChangeAt: Instant?): String {
    if (lastChangeAt == null) return "Todavía no se confirmó ningún cambio"
    val elapsed = Duration.between(lastChangeAt, Instant.now())
    val hours = elapsed.toHours()
    val minutes = elapsed.toMinutes() % 60
    return when {
        hours > 0 -> "Último cambio confirmado hace ${hours} h ${minutes} min"
        minutes > 0 -> "Último cambio confirmado hace ${minutes} min"
        else -> "Último cambio confirmado hace instantes"
    }
}

@Composable
private fun MetricsRow(state: DashboardUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        MetricCard(
            modifier = Modifier.weight(1f),
            label = "GENERACIÓN PV",
            valueText = state.inverter?.pvPowerWatts?.toString() ?: "—",
            unit = "W"
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            label = "BATERÍA",
            valueText = state.battery?.socPercent?.toString() ?: "—",
            unit = "%"
        )
    }
}

@Composable
private fun MetricCard(modifier: Modifier = Modifier, label: String, valueText: String, unit: String) {
    val colors = LocalFelicityColors.current
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(11.dp)
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
        return timeFormatter.format(event.triggeredAt.atZone(java.time.ZoneId.systemDefault()))
    }

    data class Row4(val label: String, val configured: Boolean, val lastFired: String)

    val rows = listOf(
        Row4("Voz del teléfono", state.voiceConfigured, timeFor(event?.voiceSent == true)),
        Row4("Notificación push", state.pushConfigured, timeFor(event?.pushSent == true)),
        Row4("WhatsApp", state.whatsappConfigured, timeFor(event?.whatsappSent == true))
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Card(colors = CardDefaults.cardColors(containerColor = colors.surface2), shape = RoundedCornerShape(9.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (row.configured) colors.green else colors.textLow)
                        )
                        Text("  ${row.label}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            if (row.configured) "Configurado" else "Sin configurar",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (row.configured) colors.green else colors.textLow
                        )
                        Text(row.lastFired, style = MaterialTheme.typography.labelSmall, color = colors.textLow)
                    }
                }
            }
        }
    }
}
