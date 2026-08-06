package com.dairoroberto.felicitywatch.ui.alerts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RecordVoiceOver
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.data.local.AlertRuleEntity
import com.dairoroberto.felicitywatch.domain.model.AlertRuleType
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors

@Composable
fun AlertsScreen(viewModel: AlertsViewModel = hiltViewModel()) {
    val rules by viewModel.rules.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        alertRuleItems(rules, viewModel)
    }
}

/** Extraído como items de LazyListScope (no una pantalla propia con su
 * propio LazyColumn) para poder incrustarlo dentro de otra lista que ya
 * tiene scroll — ej. la pestaña "Alertas" de Ajustes — sin anidar dos
 * LazyColumn (Compose no soporta scroll anidado del mismo eje). */
fun LazyListScope.alertRuleItems(rules: List<AlertRuleEntity>, viewModel: AlertsViewModel) {
    items(rules, key = { it.id }) { rule ->
        AlertRuleCard(
            rule = rule,
            onToggleEnabled = { viewModel.toggleEnabled(rule) },
            onThresholdChange = { viewModel.updateThreshold(rule, it) },
            onDebounceChange = { viewModel.updateDebounceSeconds(rule, it) },
            onMessageChange = { viewModel.updateMessage(rule, it) },
            onToggleVoice = { viewModel.toggleVoiceChannel(rule) },
            onTogglePush = { viewModel.togglePushChannel(rule) },
            onToggleWhatsapp = { viewModel.toggleWhatsappChannel(rule) }
        )
    }
}

private fun titleFor(type: AlertRuleType): String = when (type) {
    AlertRuleType.GRID_OFFLINE -> "Corte de red"
    AlertRuleType.GRID_ONLINE -> "Volvió la red"
    AlertRuleType.BATTERY_SOC_LOW -> "Batería baja"
    AlertRuleType.BATTERY_SOC_HIGH -> "Batería llena"
}

private fun subtitleFor(type: AlertRuleType): String = when (type) {
    AlertRuleType.GRID_OFFLINE -> "Se dispara cuando la potencia de red cae por debajo del umbral"
    AlertRuleType.GRID_ONLINE -> "Se dispara cuando la potencia de red vuelve a superar el umbral"
    AlertRuleType.BATTERY_SOC_LOW -> "Se dispara cuando la carga baja del umbral"
    AlertRuleType.BATTERY_SOC_HIGH -> "Se dispara cuando la carga supera el umbral"
}

private fun thresholdUnitFor(type: AlertRuleType): String = when (type) {
    AlertRuleType.GRID_OFFLINE, AlertRuleType.GRID_ONLINE -> "W"
    AlertRuleType.BATTERY_SOC_LOW, AlertRuleType.BATTERY_SOC_HIGH -> "%"
}

@Composable
private fun AlertRuleCard(
    rule: AlertRuleEntity,
    onToggleEnabled: () -> Unit,
    onThresholdChange: (Double?) -> Unit,
    onDebounceChange: (Int) -> Unit,
    onMessageChange: (String) -> Unit,
    onToggleVoice: () -> Unit,
    onTogglePush: () -> Unit,
    onToggleWhatsapp: () -> Unit
) {
    val colors = LocalFelicityColors.current
    val unit = thresholdUnitFor(rule.type)

    // Estado local desacoplado de "rule": si el TextField lee su valor
    // directo de "rule.messageTemplate" (que viene de un Flow de Room),
    // cada tecla dispara una escritura a la BD que reemite un nuevo valor
    // y sobreescribe el campo a mitad de edición — se pierden letras y el
    // borrado (backspace) queda roto al escribir rápido. Solo se
    // re-siembra desde "rule" cuando cambia de tarjeta (rule.id), nunca en
    // cada recomposición por el eco de la propia escritura.
    var localMessage by remember(rule.id) { mutableStateOf(rule.messageTemplate) }
    var localThreshold by remember(rule.id) { mutableStateOf((rule.thresholdValue?.toInt() ?: 0).toString()) }
    var localDebounce by remember(rule.id) { mutableStateOf(rule.debounceSeconds.toString()) }

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
                        subtitleFor(rule.type),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMid,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { onToggleEnabled() },
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.accent)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = localThreshold,
                    onValueChange = { value ->
                        localThreshold = value
                        onThresholdChange(value.toDoubleOrNull())
                    },
                    label = { Text("Umbral ($unit)") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = localDebounce,
                    onValueChange = { value ->
                        localDebounce = value
                        value.toIntOrNull()?.let(onDebounceChange)
                    },
                    label = { Text("Espera (s)") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
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

            OutlinedTextField(
                value = localMessage,
                onValueChange = { value ->
                    localMessage = value
                    onMessageChange(value)
                },
                label = { Text("Mensaje") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            )
        }
    }
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
                tint = if (active) colors.accent else colors.textLow,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) colors.accent else colors.textLow,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
