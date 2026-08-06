package com.dairoroberto.felicitywatch.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.data.local.PushNotificationEntity
import com.dairoroberto.felicitywatch.domain.model.AlertRuleType
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun NotificationsScreen(viewModel: NotificationsViewModel = hiltViewModel()) {
    val notifications by viewModel.notifications.collectAsState()
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var notificationPendingDelete by remember { mutableStateOf<PushNotificationEntity?>(null) }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("¿Borrar todas las notificaciones?") },
            text = { Text("Se eliminarán todas las notificaciones push registradas en este teléfono. Esta acción no se puede deshacer.") },
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

    notificationPendingDelete?.let { notification ->
        AlertDialog(
            onDismissRequest = { notificationPendingDelete = null },
            title = { Text("¿Borrar esta notificación?") },
            text = { Text("${notification.title} — ${notification.body}") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(notification.id)
                    notificationPendingDelete = null
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { notificationPendingDelete = null }) { Text("Cancelar") }
            }
        )
    }

    if (notifications.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp)
        ) {
            Text(
                "No hay notificaciones registradas.",
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
        items(notifications, key = { it.id }) { notification ->
            NotificationCard(notification, onDelete = { notificationPendingDelete = notification })
        }
    }
}

/**
 * Fuente primaria: el tipo real de regla (ruleType), guardado desde que
 * existe ese campo — no depende de palabras exactas en el mensaje, así que
 * sigue funcionando aunque el usuario personalice el texto en Ajustes >
 * Alertas. Fallback a detectar por palabras clave solo para notificaciones
 * guardadas ANTES de que existiera ruleType (siempre null) o pushes de
 * prueba manuales ("Probar" en Ajustes, que tampoco llevan ruleType).
 */
private enum class GridNotificationTone { RESTORED, LOST, OTHER }

private fun gridToneFor(notification: PushNotificationEntity): GridNotificationTone {
    when (notification.ruleType) {
        AlertRuleType.GRID_ONLINE -> return GridNotificationTone.RESTORED
        AlertRuleType.GRID_OFFLINE -> return GridNotificationTone.LOST
        AlertRuleType.BATTERY_SOC_LOW, AlertRuleType.BATTERY_SOC_HIGH -> return GridNotificationTone.OTHER
        null -> Unit
    }

    val text = "${notification.title} ${notification.body}".lowercase()
    val mentionsGrid = "corriente" in text || "red eléctrica" in text || "electricidad" in text
    if (!mentionsGrid) return GridNotificationTone.OTHER
    val restoredKeywords = listOf("vuelto", "volvió", "volvio", "restablec", "recuper")
    val lostKeywords = listOf("perdido", "se fue", "corte", "sin corriente", "falló", "fallo")
    return when {
        restoredKeywords.any { it in text } -> GridNotificationTone.RESTORED
        lostKeywords.any { it in text } -> GridNotificationTone.LOST
        else -> GridNotificationTone.OTHER
    }
}

@Composable
private fun NotificationCard(notification: PushNotificationEntity, onDelete: () -> Unit) {
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
    val colors = LocalFelicityColors.current
    val tone = gridToneFor(notification)
    val accentColor = when (tone) {
        GridNotificationTone.RESTORED -> colors.green
        GridNotificationTone.LOST -> colors.error
        GridNotificationTone.OTHER -> null
    }

    Card(colors = CardDefaults.cardColors(containerColor = colors.surface2), shape = RoundedCornerShape(11.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            if (accentColor != null) {
                Box(
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 11.dp, bottomStart = 11.dp))
                        .background(accentColor)
                )
            }
            Column(Modifier.padding(13.dp).weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(notification.title, style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatter.format(notification.receivedAt.atZone(ZoneId.systemDefault())),
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
                Text(notification.body, style = MaterialTheme.typography.bodyMedium, color = colors.textMid, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
