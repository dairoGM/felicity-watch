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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var eventPendingDelete by remember { mutableStateOf<AlertEventEntity?>(null) }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("¿Borrar todo el historial?") },
            text = { Text("Se eliminarán todos los registros de alertas disparadas. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showClearAllConfirm = false
                }) { Text("Borrar todo") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    eventPendingDelete?.let { event ->
        AlertDialog(
            onDismissRequest = { eventPendingDelete = null },
            title = { Text("¿Borrar este registro?") },
            text = { Text(titleFor(event.ruleType) + " — " + event.message) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(event.id)
                    eventPendingDelete = null
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { eventPendingDelete = null }) { Text("Cancelar") }
            }
        )
    }

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
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { showClearAllConfirm = true }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Limpiar todo")
                }
            }
        }
        items(events, key = { it.id }) { event ->
            HistoryEventCard(event, onDelete = { eventPendingDelete = event })
        }
    }
}

private fun titleFor(type: AlertRuleType): String = when (type) {
    AlertRuleType.GRID_OFFLINE -> "Corte de red"
    AlertRuleType.GRID_ONLINE -> "Volvió la red"
    AlertRuleType.BATTERY_SOC_LOW -> "Batería baja"
    AlertRuleType.BATTERY_SOC_HIGH -> "Batería llena"
}

@Composable
private fun HistoryEventCard(event: AlertEventEntity, onDelete: () -> Unit) {
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(Locale("es", "ES"))
    val colors = LocalFelicityColors.current

    Card(colors = CardDefaults.cardColors(containerColor = colors.surface2), shape = RoundedCornerShape(11.dp)) {
        Column(Modifier.padding(13.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(titleFor(event.ruleType), style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatter.format(event.triggeredAt.atZone(ZoneId.systemDefault())),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow
                    )
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Borrar registro",
                            tint = colors.textLow
                        )
                    }
                }
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
