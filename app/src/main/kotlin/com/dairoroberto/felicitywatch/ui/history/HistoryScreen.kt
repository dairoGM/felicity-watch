package com.dairoroberto.felicitywatch.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.data.local.AlertEventEntity
import com.dairoroberto.felicitywatch.domain.model.AlertRuleType
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val events by viewModel.events.collectAsState()

    if (events.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp)
        ) {
            Text(
                "Todavía no se ha disparado ninguna alerta.",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalFelicityColors.current.textMid
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(events, key = { it.id }) { event -> HistoryEventCard(event) }
    }
}

private fun titleFor(type: AlertRuleType): String = when (type) {
    AlertRuleType.GRID_OFFLINE -> "Corte de red"
    AlertRuleType.GRID_ONLINE -> "Volvió la red"
    AlertRuleType.BATTERY_SOC_LOW -> "Batería baja"
    AlertRuleType.BATTERY_SOC_HIGH -> "Batería llena"
}

@Composable
private fun HistoryEventCard(event: AlertEventEntity) {
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(Locale("es", "ES"))
    val colors = LocalFelicityColors.current

    Card(colors = CardDefaults.cardColors(containerColor = colors.surface2), shape = RoundedCornerShape(11.dp)) {
        Column(Modifier.padding(13.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(titleFor(event.ruleType), style = MaterialTheme.typography.titleSmall)
                Text(
                    formatter.format(event.triggeredAt.atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow
                )
            }
            Text(event.message, style = MaterialTheme.typography.bodyMedium, color = colors.textMid, modifier = Modifier.padding(top = 4.dp))

            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ChannelStatus("Voz", event.voiceSent)
                ChannelStatus("Push", event.pushSent)
                ChannelStatus("WhatsApp", event.whatsappSent)
            }

            if (event.whatsappError != null) {
                Text(
                    "WhatsApp: ${event.whatsappError}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ChannelStatus(label: String, success: Boolean) {
    val colors = LocalFelicityColors.current
    Row {
        Text(if (success) "✓" else "✗", color = if (success) colors.green else MaterialTheme.colorScheme.error)
        Text(" $label", style = MaterialTheme.typography.labelSmall, color = colors.textMid)
    }
}
