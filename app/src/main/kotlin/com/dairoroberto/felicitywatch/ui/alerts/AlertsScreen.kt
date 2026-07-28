package com.dairoroberto.felicitywatch.ui.alerts

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.data.local.AlertRuleEntity
import com.dairoroberto.felicitywatch.domain.model.AlertRuleType
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors
import com.dairoroberto.felicitywatch.ui.theme.Teal

@Composable
fun AlertsScreen(viewModel: AlertsViewModel = hiltViewModel()) {
    val rules by viewModel.rules.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
    val colors = LocalFelicityColors.current

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(titleFor(rule.type), style = MaterialTheme.typography.titleSmall)
                    Text(
                        ruleDescription(rule),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMid,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { onToggleEnabled() },
                    colors = SwitchDefaults.colors(checkedTrackColor = Teal)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = colors.hairline)

            Text(
                "CANALES",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ChannelChip(
                    icon = Icons.Default.RecordVoiceOver,
                    label = "Voz",
                    active = rule.channelVoiceEnabled,
                    onClick = onToggleVoice,
                    modifier = Modifier.weight(1f)
                )
                ChannelChip(
                    icon = Icons.Default.Notifications,
                    label = "Push",
                    active = rule.channelPushEnabled,
                    onClick = onTogglePush,
                    modifier = Modifier.weight(1f)
                )
                ChannelChip(
                    icon = Icons.Default.Chat,
                    label = "WhatsApp",
                    active = rule.channelWhatsappEnabled,
                    onClick = onToggleWhatsapp,
                    modifier = Modifier.weight(1f)
                )
            }

            if (rule.thresholdValue != null) {
                OutlinedTextField(
                    value = rule.thresholdValue.toInt().toString(),
                    onValueChange = { value -> onThresholdChange(value.toDoubleOrNull()) },
                    label = { Text("Umbral (%)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                )
            }

            OutlinedTextField(
                value = rule.messageTemplate,
                onValueChange = onMessageChange,
                label = { Text("Mensaje") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
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
private fun ChannelChip(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalFelicityColors.current

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (active) colors.tealDim else MaterialTheme.colorScheme.background
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (active) Teal else colors.textLow,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) Teal else colors.textLow,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
