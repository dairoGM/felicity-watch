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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.dairoroberto.felicitywatch.ui.theme.Green
import com.dairoroberto.felicitywatch.ui.theme.GreenDim
import com.dairoroberto.felicitywatch.ui.theme.JetBrainsMonoFamily
import com.dairoroberto.felicitywatch.ui.theme.PanelSurface2
import com.dairoroberto.felicitywatch.ui.theme.TextLow
import com.dairoroberto.felicitywatch.ui.theme.TextMid
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { GridHeroCard(state) }
        item { MetricsRow(state) }
        item {
            Text(
                "CANALES DE AVISO",
                style = MaterialTheme.typography.labelSmall,
                color = TextLow,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
        item { ChannelsList(state) }
    }
}

@Composable
private fun GridHeroCard(state: DashboardUiState) {
    val online = state.gridState == GridState.ONLINE
    Card(
        colors = CardDefaults.cardColors(containerColor = if (online) GreenDim else PanelSurface2),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(if (online) Green else MaterialTheme.colorScheme.error)
                )
                Text(
                    "  ESTADO DE LA RED",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMid
                )
            }
            Text(
                if (online) "Con corriente eléctrica" else "Sin corriente eléctrica",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                lastChangeLabel(state.lastGridChangeAt),
                style = MaterialTheme.typography.bodySmall,
                color = TextMid,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

private fun lastChangeLabel(lastChangeAt: Instant?): String {
    if (lastChangeAt == null) return "Sin datos todavía"
    val elapsed = Duration.between(lastChangeAt, Instant.now())
    val hours = elapsed.toHours()
    val minutes = elapsed.toMinutes() % 60
    return when {
        hours > 0 -> "Último cambio hace ${hours} h ${minutes} min"
        minutes > 0 -> "Último cambio hace ${minutes} min"
        else -> "Último cambio hace instantes"
    }
}

@Composable
private fun MetricsRow(state: DashboardUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        MetricCard(
            modifier = Modifier.weight(1f),
            label = "PV",
            valueText = "${state.inverter?.pvPowerWatts ?: "—"}",
            unit = "W"
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            label = "BATERÍA",
            valueText = "${state.battery?.socPercent ?: "—"}",
            unit = "%"
        )
    }
}

@Composable
private fun MetricCard(modifier: Modifier = Modifier, label: String, valueText: String, unit: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = PanelSurface2),
        shape = RoundedCornerShape(11.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextLow)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    valueText,
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp
                )
                Text(" $unit", style = MaterialTheme.typography.bodySmall, color = TextMid)
            }
        }
    }
}

@Composable
private fun ChannelsList(state: DashboardUiState) {
    val event = state.latestEvent
    val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale("es", "ES"))

    fun timeFor(sent: Boolean): String {
        if (event == null || !sent) return "—"
        return timeFormatter.format(event.triggeredAt.atZone(java.time.ZoneId.systemDefault()))
    }

    val rows = listOf(
        Triple("Voz del teléfono", event?.voiceSent == true, timeFor(event?.voiceSent == true)),
        Triple("Notificación push", event?.pushSent == true, timeFor(event?.pushSent == true)),
        Triple("WhatsApp", event?.whatsappSent == true, timeFor(event?.whatsappSent == true))
    )

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        rows.forEach { (label, active, time) ->
            Card(colors = CardDefaults.cardColors(containerColor = PanelSurface2), shape = RoundedCornerShape(9.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 11.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (active) Green else TextLow)
                        )
                        Text("  $label", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(time, style = MaterialTheme.typography.labelSmall, color = TextLow)
                }
            }
        }
    }
}
