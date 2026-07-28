package com.dairoroberto.felicitywatch.ui.alerts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.data.local.AlertRuleEntity
import com.dairoroberto.felicitywatch.domain.model.AlertRuleType
import com.dairoroberto.felicitywatch.ui.theme.PanelSurface2
import com.dairoroberto.felicitywatch.ui.theme.Teal
import com.dairoroberto.felicitywatch.ui.theme.TealDim
import com.dairoroberto.felicitywatch.ui.theme.TextLow
import com.dairoroberto.felicitywatch.ui.theme.TextMid

@Composable
fun AlertsScreen(viewModel: AlertsViewModel = hiltViewModel()) {
    val rules by viewModel.rules.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(rules, key = { it.id }) { rule ->
            AlertRuleCard(
                rule = rule,
                onToggleEnabled = { viewModel.toggleEnabled(rule) },
                onThresholdChange = { viewModel.updateThreshold(rule, it) },
                onMessageChange = { viewModel.updateMessage(rule, it) },
                onToggleVoice = { viewModel.toggleVoiceChannel(rule) },
                onTogglePush = { viewModel.togglePushChannel(rule) },
                onToggleWhatsapp = { viewModel.toggleWhatsappChannel(rule) }
            )
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
private fun AlertRuleCard(
    rule: AlertRuleEntity,
    onToggleEnabled: () -> Unit,
    onThresholdChange: (Double?) -> Unit,
    onMessageChange: (String) -> Unit,
    onToggleVoice: () -> Unit,
    onTogglePush: () -> Unit,
    onToggleWhatsapp: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PanelSurface2),
        shape = RoundedCornerShape(11.dp)
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { onToggleEnabled() },
                    colors = SwitchDefaults.colors(checkedTrackColor = Teal),
                    modifier = Modifier.size(width = 40.dp, height = 24.dp)
                )
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(titleFor(rule.type), style = MaterialTheme.typography.titleSmall)
                    Text(
                        ruleDescription(rule),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMid
                    )
                }
                ChannelPill(label = "V", active = rule.channelVoiceEnabled, onClick = onToggleVoice)
                ChannelPill(label = "P", active = rule.channelPushEnabled, onClick = onTogglePush)
                ChannelPill(label = "W", active = rule.channelWhatsappEnabled, onClick = onToggleWhatsapp)
            }

            if (rule.thresholdValue != null) {
                TextField(
                    value = rule.thresholdValue.toInt().toString(),
                    onValueChange = { value -> onThresholdChange(value.toDoubleOrNull()) },
                    label = { Text("Umbral (%)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = TextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.background)
                )
            }

            TextField(
                value = rule.messageTemplate,
                onValueChange = onMessageChange,
                label = { Text("Mensaje") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = TextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.background)
            )
        }
    }
}

private fun ruleDescription(rule: AlertRuleEntity): String = when (rule.type) {
    AlertRuleType.GRID_OFFLINE -> "Red por debajo de 1 W · ${rule.debounceSeconds} s"
    AlertRuleType.GRID_ONLINE -> "Red por encima de 1 W · ${rule.debounceSeconds} s"
    AlertRuleType.BATTERY_SOC_LOW -> "Carga igual o menor a ${rule.thresholdValue?.toInt() ?: 20}%"
    AlertRuleType.BATTERY_SOC_HIGH -> "Carga igual o mayor a ${rule.thresholdValue?.toInt() ?: 100}%"
}

@Composable
private fun ChannelPill(label: String, active: Boolean, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (active) TealDim else MaterialTheme.colorScheme.background),
        shape = RoundedCornerShape(5.dp),
        modifier = Modifier
            .size(24.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) Teal else TextLow,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 5.dp)
            )
        }
    }
}
