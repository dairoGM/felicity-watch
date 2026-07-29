package com.dairoroberto.felicitywatch.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.ui.components.ApiKeyField
import com.dairoroberto.felicitywatch.ui.components.ElegantSnackbar
import com.dairoroberto.felicitywatch.ui.components.EmailField
import com.dairoroberto.felicitywatch.ui.components.PasswordField
import com.dairoroberto.felicitywatch.ui.components.PhoneField
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Alto uniforme para todos los botones de acción de esta pantalla. */
private val ACTION_BUTTON_HEIGHT = 48.dp
private val SECTION_CONTENT_SPACING = 12.dp

private fun buildTimestampLabel(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale("es", "ES"))
    return formatter.format(Date(com.dairoroberto.felicitywatch.BuildConfig.BUILD_TIMESTAMP_MILLIS))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    darkModeEnabled: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    onLoggedOut: () -> Unit
) {
    val context = LocalContext.current
    val formState by viewModel.formState.collectAsState()
    val serviceRunning by viewModel.serviceRunning.collectAsState()
    val isTestingConnection by viewModel.isTestingConnection.collectAsState()
    val pollingIntervalSeconds by viewModel.pollingIntervalSeconds.collectAsState()
    val lastInverterRawJson by viewModel.lastInverterRawJson.collectAsState()
    val lastBatteryRawJson by viewModel.lastBatteryRawJson.collectAsState()
    var batteryExcluded by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showFactoryResetConfirm by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            coroutineScope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Cerrar sesión") },
            text = { Text("Se borrarán tus credenciales de FSolar y WhatsApp de este teléfono. Podrás volver a iniciar sesión cuando quieras.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    viewModel.logout(onLoggedOut)
                }) { Text("Cerrar sesión") }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancelar") } }
        )
    }

    if (showFactoryResetConfirm) {
        AlertDialog(
            onDismissRequest = { showFactoryResetConfirm = false },
            title = { Text("Restablecer valores de fábrica") },
            text = { Text("Se borrarán credenciales, reglas de alerta personalizadas e historial. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    showFactoryResetConfirm = false
                    viewModel.resetToFactoryDefaults(onLoggedOut)
                }) { Text("Restablecer") }
            },
            dismissButton = { TextButton(onClick = { showFactoryResetConfirm = false }) { Text("Cancelar") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { ElegantSnackbar(it) } }
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(scaffoldPadding)
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SectionCard(title = "Cuenta FSolar") {
                    EmailField(value = formState.fsolarUsername, onValueChange = viewModel::onUsernameChange)
                    PasswordField(
                        value = formState.fsolarPassword,
                        onValueChange = viewModel::onPasswordChange,
                        modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                    )
                    ActionButton(
                        text = "Guardar credenciales",
                        onClick = { viewModel.saveFsolarCredentials() },
                        modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                    )
                }
            }

            item {
                SectionCard(title = "WhatsApp (CallMeBot)") {
                    PhoneField(value = formState.whatsappPhone, onValueChange = viewModel::onWhatsappPhoneChange)
                    ApiKeyField(
                        value = formState.callMeBotApiKey,
                        onValueChange = viewModel::onApiKeyChange,
                        label = "API key de CallMeBot",
                        modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                    )
                    ActionButton(
                        text = "Guardar WhatsApp",
                        onClick = { viewModel.saveWhatsappConfig() },
                        modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                    )
                }
            }

            item {
                SectionCard(title = "Conexión con Felicity") {
                    Text(
                        "Verifica el acceso a tu cuenta FSolar y trae la primera lectura de PV y batería para el Panel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalFelicityColors.current.textMid
                    )
                    ActionButton(
                        text = if (isTestingConnection) "Probando…" else "Probar conexión / primera lectura",
                        icon = if (isTestingConnection) null else Icons.Default.CloudSync,
                        loading = isTestingConnection,
                        onClick = { viewModel.testConnection() },
                        enabled = !isTestingConnection,
                        modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                    )
                }
            }

            item {
                SectionCard(title = "Canales de aviso") {
                    Text(
                        "Verifica que cada canal funcione antes de confiar en él.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalFelicityColors.current.textMid
                    )
                    ChannelTestRow(
                        icon = Icons.Default.RecordVoiceOver,
                        label = "Voz del teléfono",
                        onTest = { viewModel.testVoiceChannel() }
                    )
                    ChannelTestRow(
                        icon = Icons.Default.Notifications,
                        label = "Notificación push",
                        onTest = { viewModel.testPushChannel() }
                    )
                    ChannelTestRow(
                        icon = Icons.Default.Chat,
                        label = "WhatsApp",
                        onTest = { viewModel.testWhatsappChannel() }
                    )
                }
            }

            item {
                SectionCard(title = "Optimización de batería") {
                    Text(
                        if (batteryExcluded) "Excluida — el sistema no debería matar el servicio."
                        else "No excluida — MIUI y fabricantes similares pueden matar el servicio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (batteryExcluded) LocalFelicityColors.current.green else LocalFelicityColors.current.textMid
                    )
                    ActionButton(
                        text = "Solicitar exclusión",
                        onClick = {
                            requestIgnoreBatteryOptimizations(context)
                            batteryExcluded = isIgnoringBatteryOptimizations(context)
                        },
                        modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                    )
                }
            }

            item {
                SectionCard(title = "Frecuencia de consulta") {
                    Text(
                        "Cada cuánto se consulta a Felicity para saber si se fue/llegó la corriente o cambió la generación PV. Un intervalo más corto detecta cambios más rápido pero consume más batería y datos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalFelicityColors.current.textMid
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = SECTION_CONTENT_SPACING),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 30, 60, 120, 300).forEach { seconds ->
                            val label = if (seconds < 60) "${seconds}s" else "${seconds / 60}min"
                            FilterChip(
                                selected = pollingIntervalSeconds == seconds,
                                onClick = { viewModel.setPollingIntervalSeconds(seconds) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LocalFelicityColors.current.tealDim
                                )
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Servicio de vigilancia") {
                    Text(
                        if (serviceRunning) "Activo" else "Detenido",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (serviceRunning) LocalFelicityColors.current.green else LocalFelicityColors.current.textMid
                    )
                    ActionButton(
                        text = "Reiniciar servicio",
                        onClick = { viewModel.restartService() },
                        modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                    )
                }
            }

            item {
                SectionCard(title = "Apariencia") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(Icons.Default.DarkMode, contentDescription = null)
                            Text(
                                "Modo oscuro",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        Switch(checked = darkModeEnabled, onCheckedChange = onToggleDarkMode)
                    }
                }
            }

            item {
                SectionCard(title = "Diagnóstico") {
                    Text(
                        "Copia la última respuesta cruda que Felicity envió para cada equipo — útil para reportar un problema sin conectar el teléfono por USB.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalFelicityColors.current.textMid
                    )
                    ActionButton(
                        text = "Copiar respuesta del inversor",
                        outlined = true,
                        onClick = { viewModel.copyRawJsonToClipboard("inversor", lastInverterRawJson) },
                        modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                    )
                    ActionButton(
                        text = "Copiar respuesta de la batería",
                        outlined = true,
                        onClick = { viewModel.copyRawJsonToClipboard("batería", lastBatteryRawJson) },
                        modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                    )
                }
            }

            item {
                SectionCard(title = "Acerca de") {
                    Text(
                        "Felicity Watch ${com.dairoroberto.felicitywatch.BuildConfig.VERSION_NAME} (build ${com.dairoroberto.felicitywatch.BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Compilado: ${buildTimestampLabel()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalFelicityColors.current.textMid,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            item {
                SectionCard(title = "Zona de riesgo") {
                    ActionButton(
                        text = "Cerrar sesión",
                        icon = Icons.Default.Logout,
                        outlined = true,
                        onClick = { showLogoutConfirm = true }
                    )
                    ActionButton(
                        text = "Restablecer valores de fábrica",
                        icon = Icons.Default.DeleteForever,
                        outlined = true,
                        contentColor = MaterialTheme.colorScheme.error,
                        onClick = { showFactoryResetConfirm = true },
                        modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                    )
                }
            }
        }
    }
}

/**
 * Botón de acción estándar de Ajustes: mismo alto y ancho completo en
 * todas las tarjetas, para que la pantalla se vea consistente en vez de
 * cada botón con su propio tamaño.
 */
@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    outlined: Boolean = false,
    loading: Boolean = false,
    contentColor: androidx.compose.ui.graphics.Color? = null
) {
    val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(text, modifier = Modifier.padding(start = 8.dp))
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text, modifier = Modifier.padding(start = 8.dp))
            } else {
                Text(text)
            }
        }
    }

    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .fillMaxWidth()
                .height(ACTION_BUTTON_HEIGHT),
            colors = if (contentColor != null) {
                ButtonDefaults.outlinedButtonColors(contentColor = contentColor)
            } else {
                ButtonDefaults.outlinedButtonColors()
            },
            content = content
        )
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .fillMaxWidth()
                .height(ACTION_BUTTON_HEIGHT),
            content = content
        )
    }
}

@Composable
private fun ChannelTestRow(icon: ImageVector, label: String, onTest: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SECTION_CONTENT_SPACING),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = LocalFelicityColors.current.textMid)
            Text("  $label", style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onTest, modifier = Modifier.height(ACTION_BUTTON_HEIGHT)) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
            Text("Probar")
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LocalFelicityColors.current.surface2),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Column(Modifier.padding(top = SECTION_CONTENT_SPACING)) { content() }
        }
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
}
