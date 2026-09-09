package com.dairoroberto.felicitywatch.ui.alerts

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.data.local.AlertRuleEntity
import com.dairoroberto.felicitywatch.domain.model.AlertRuleType
import com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors
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
        val pvWindowStartHour by viewModel.pvAlertWindowStartHour.collectAsState()
        val pvWindowEndHour by viewModel.pvAlertWindowEndHour.collectAsState()
        AlertRuleCard(
            rule = rule,
            onToggleEnabled = { viewModel.toggleEnabled(rule) },
            onThresholdChange = { viewModel.updateThreshold(rule, it) },
            onDebounceChange = { viewModel.updateDebounceSeconds(rule, it) },
            onMessageChange = { viewModel.updateMessage(rule, it) },
            onToggleVoice = { viewModel.toggleVoiceChannel(rule) },
            onTogglePush = { viewModel.togglePushChannel(rule) },
            onToggleWhatsapp = { viewModel.toggleWhatsappChannel(rule) },
            onTestRule = { viewModel.testRule(rule) },
            pvWindowStartHour = pvWindowStartHour,
            pvWindowEndHour = pvWindowEndHour,
            onPvWindowStartHourChange = { viewModel.setPvAlertWindowStartHour(it) },
            onPvWindowEndHourChange = { viewModel.setPvAlertWindowEndHour(it) }
        )
    }
}

private fun titleFor(type: AlertRuleType): String = when (type) {
    AlertRuleType.GRID_OFFLINE -> "Corte de red"
    AlertRuleType.GRID_ONLINE -> "Volvió la red"
    AlertRuleType.BATTERY_SOC_LOW -> "Batería baja"
    AlertRuleType.BATTERY_SOC_HIGH -> "Batería llena"
    AlertRuleType.LOAD_HIGH -> "Consumo alto"
    AlertRuleType.BATTERY_AUTONOMY_LOW -> "Autonomía baja"
    AlertRuleType.PV_GENERATION_LOST -> "Generación PV perdida"
}

private fun subtitleFor(type: AlertRuleType): String = when (type) {
    AlertRuleType.GRID_OFFLINE -> "Se dispara cuando la potencia de red cae por debajo del umbral"
    AlertRuleType.GRID_ONLINE -> "Se dispara cuando la potencia de red vuelve a superar el umbral"
    AlertRuleType.BATTERY_SOC_LOW -> "Se dispara cuando la carga baja del umbral"
    AlertRuleType.BATTERY_SOC_HIGH -> "Se dispara cuando la carga supera el umbral"
    AlertRuleType.LOAD_HIGH -> "Se dispara cuando el consumo de la casa supera el umbral (el inversor es de 8kW)"
    AlertRuleType.BATTERY_AUTONOMY_LOW -> "Se dispara cuando el anillo de Autonomía del Panel se pone en rojo (sin corriente de red)"
    AlertRuleType.PV_GENERATION_LOST -> "Se dispara si la generación solar cae por debajo del umbral, solo en el horario de sol configurado abajo — posible falla del inversor o los paneles"
}

private fun thresholdUnitFor(type: AlertRuleType): String = when (type) {
    AlertRuleType.GRID_OFFLINE, AlertRuleType.GRID_ONLINE -> "W"
    AlertRuleType.BATTERY_SOC_LOW, AlertRuleType.BATTERY_SOC_HIGH -> "%"
    AlertRuleType.LOAD_HIGH -> "W"
    AlertRuleType.BATTERY_AUTONOMY_LOW -> "horas"
    AlertRuleType.PV_GENERATION_LOST -> "W"
}

private fun iconFor(type: AlertRuleType): ImageVector = when (type) {
    AlertRuleType.GRID_OFFLINE -> Icons.Default.FlashOff
    AlertRuleType.GRID_ONLINE -> Icons.Default.FlashOn
    AlertRuleType.BATTERY_SOC_LOW -> Icons.Default.BatteryAlert
    AlertRuleType.BATTERY_SOC_HIGH -> Icons.Default.BatteryFull
    AlertRuleType.LOAD_HIGH -> Icons.Default.ElectricBolt
    AlertRuleType.BATTERY_AUTONOMY_LOW -> Icons.Default.HourglassBottom
    AlertRuleType.PV_GENERATION_LOST -> Icons.Default.WbSunny
}

/** Color de acento por tipo de regla — cada tarjeta de Alertas queda
 * identificada de un vistazo por su franja lateral e icono, en vez de que
 * las 7 se vean iguales y solo se distingan leyendo el título. */
private fun accentColorFor(type: AlertRuleType, colors: FelicitySemanticColors): Color = when (type) {
    AlertRuleType.GRID_OFFLINE -> colors.error
    AlertRuleType.GRID_ONLINE -> colors.green
    AlertRuleType.BATTERY_SOC_LOW, AlertRuleType.BATTERY_SOC_HIGH, AlertRuleType.BATTERY_AUTONOMY_LOW -> colors.chargeAccent
    AlertRuleType.LOAD_HIGH -> colors.accent
    AlertRuleType.PV_GENERATION_LOST -> colors.pvAccent
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
    onToggleWhatsapp: () -> Unit,
    onTestRule: () -> Unit,
    pvWindowStartHour: Int,
    pvWindowEndHour: Int,
    onPvWindowStartHourChange: (Int) -> Unit,
    onPvWindowEndHourChange: (Int) -> Unit
) {
    val colors = LocalFelicityColors.current
    val unit = thresholdUnitFor(rule.type)
    val accentColor = accentColorFor(rule.type, colors)

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
        // Franja de acento a la izquierda + fila del cuerpo: cada tipo de
        // regla queda identificado por su color sin tener que leer el
        // título, igual patrón que ya usa Historial para corte/reconexión
        // de red — aquí se extiende a las 7 reglas para que las tarjetas
        // se diferencien entre sí de un vistazo, no solo por el texto.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .background(accentColor)
            )
            Column(Modifier.padding(16.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(iconFor(rule.type), contentDescription = null, tint = accentColor, modifier = Modifier.size(19.dp))
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        titleFor(rule.type),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textHi
                    )
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

            if (rule.type == AlertRuleType.PV_GENERATION_LOST) {
                PvWindowConfigRow(
                    startHour = pvWindowStartHour,
                    endHour = pvWindowEndHour,
                    onStartHourChange = onPvWindowStartHourChange,
                    onEndHourChange = onPvWindowEndHourChange,
                    colors = colors,
                    modifier = Modifier.padding(top = 14.dp)
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

            val playsIntenseTone = rule.type == AlertRuleType.LOAD_HIGH ||
                rule.type == AlertRuleType.BATTERY_AUTONOMY_LOW ||
                rule.type == AlertRuleType.PV_GENERATION_LOST
            if (playsIntenseTone) {
                Text(
                    "La voz suena primero un tono de aviso intenso y luego lee este mensaje.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            TextButton(
                onClick = onTestRule,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Probar esta alerta")
            }
            }
        }
    }
}

@Composable
private fun PvWindowConfigRow(
    startHour: Int,
    endHour: Int,
    onStartHourChange: (Int) -> Unit,
    onEndHourChange: (Int) -> Unit,
    colors: FelicitySemanticColors,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.hairline.copy(alpha = 0.25f))
            .padding(12.dp)
    ) {
        Text(
            "HORARIO DE SOL ESPERADO",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textLow
        )
        Text(
            "Fuera de este rango, 0W es normal (de noche) y no dispara nada.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textLow,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            PvHourStepper(label = "Desde", hour = startHour, onChange = onStartHourChange, colors = colors)
            PvHourStepper(label = "Hasta", hour = endHour, onChange = onEndHourChange, colors = colors)
        }
    }
}

@Composable
private fun PvHourStepper(
    label: String,
    hour: Int,
    onChange: (Int) -> Unit,
    colors: FelicitySemanticColors
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textLow)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            IconButton(onClick = { onChange((hour + 23) % 24) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Hora anterior", modifier = Modifier.size(20.dp))
            }
            Text(
                pvHourLabel(hour),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textHi,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            IconButton(onClick = { onChange((hour + 1) % 24) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Hora siguiente", modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun pvHourLabel(hour: Int): String {
    val ampm = if (hour < 12) "am" else "pm"
    val display = if (hour % 12 == 0) 12 else hour % 12
    return "%d%s".format(display, ampm)
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
