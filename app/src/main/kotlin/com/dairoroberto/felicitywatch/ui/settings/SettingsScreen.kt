package com.dairoroberto.felicitywatch.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.data.local.AppPreferences
import com.dairoroberto.felicitywatch.ui.alerts.AlertsViewModel
import com.dairoroberto.felicitywatch.ui.alerts.alertRuleItems
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
private val SETTINGS_TABS = listOf("Cuenta", "Alertas", "Sistema", "Diagnóstico")

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
    val isLoadingDeviceList by viewModel.isLoadingDeviceList.collectAsState()
    val alertsViewModel: AlertsViewModel = hiltViewModel()
    val alertRules by alertsViewModel.rules.collectAsState()
    var batteryExcluded by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showFactoryResetConfirm by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            coroutineScope.launch { snackbarHostState.showSnackbar(message) }
        }
    }
    LaunchedEffect(Unit) {
        alertsViewModel.messages.collect { message ->
            coroutineScope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    // Pide el permiso POST_NOTIFICATIONS (Android 13+) con el modal nativo del
    // sistema antes de disparar la prueba del canal push, en vez de fallar en
    // silencio y obligar al usuario a ir manualmente a Ajustes del sistema —
    // así lo hacen la mayoría de apps al pedir permisos por primera vez.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.testPushChannel()
    }
    val requestPushPermissionThenTest: () -> Unit = {
        val alreadyGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            viewModel.testPushChannel()
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
        Column(Modifier.fillMaxSize().padding(scaffoldPadding)) {
            val colors = LocalFelicityColors.current
            TabRow(selectedTabIndex = selectedTab, containerColor = colors.surface2) {
                SETTINGS_TABS.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().imePadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (selectedTab) {
                    0 -> accountTab(
                        formState = formState,
                        viewModel = viewModel,
                        onShowLogoutConfirm = { showLogoutConfirm = true }
                    )
                    1 -> alertsTab(
                        isTestingConnection = isTestingConnection,
                        viewModel = viewModel,
                        alertsViewModel = alertsViewModel,
                        alertRules = alertRules,
                        onTestPushChannel = requestPushPermissionThenTest
                    )
                    2 -> systemTab(
                        pollingIntervalSeconds = pollingIntervalSeconds,
                        serviceRunning = serviceRunning,
                        darkModeEnabled = darkModeEnabled,
                        onToggleDarkMode = onToggleDarkMode,
                        batteryExcluded = batteryExcluded,
                        onRequestBatteryExclusion = {
                            requestIgnoreBatteryOptimizations(context)
                            batteryExcluded = isIgnoringBatteryOptimizations(context)
                        },
                        viewModel = viewModel
                    )
                    3 -> diagnosticsTab(
                        lastInverterRawJson = lastInverterRawJson,
                        lastBatteryRawJson = lastBatteryRawJson,
                        isLoadingDeviceList = isLoadingDeviceList,
                        viewModel = viewModel,
                        onShowFactoryResetConfirm = { showFactoryResetConfirm = true }
                    )
                }
            }
        }
    }
}

private fun LazyListScope.accountTab(
    formState: SettingsUiState,
    viewModel: SettingsViewModel,
    onShowLogoutConfirm: () -> Unit
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
        SectionCard(title = "Zona de riesgo") {
            ActionButton(
                text = "Cerrar sesión",
                icon = Icons.Default.Logout,
                outlined = true,
                onClick = onShowLogoutConfirm
            )
        }
    }
}

private fun LazyListScope.alertsTab(
    isTestingConnection: Boolean,
    viewModel: SettingsViewModel,
    alertsViewModel: AlertsViewModel,
    alertRules: List<com.dairoroberto.felicitywatch.data.local.AlertRuleEntity>,
    onTestPushChannel: () -> Unit
) {
    item {
        Text(
            "REGLAS DE ALERTA",
            style = MaterialTheme.typography.labelSmall,
            color = LocalFelicityColors.current.textLow,
            modifier = Modifier.padding(start = 2.dp)
        )
    }

    alertRuleItems(alertRules, alertsViewModel)

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
                onTest = onTestPushChannel
            )
            ChannelTestRow(
                icon = Icons.Default.Chat,
                label = "WhatsApp",
                onTest = { viewModel.testWhatsappChannel() }
            )
        }
    }
}

// FlowRow (chips del umbral) sigue marcada como experimental en Foundation,
// aunque su API es estable en la práctica desde hace varias versiones.
@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.systemTab(
    pollingIntervalSeconds: Int,
    serviceRunning: Boolean,
    darkModeEnabled: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    batteryExcluded: Boolean,
    onRequestBatteryExclusion: () -> Unit,
    viewModel: SettingsViewModel
) {
    item {
        val colors = LocalFelicityColors.current
        val publishInterval by viewModel.inverterPublishIntervalSeconds.collectAsState()

        // Intervalo escrito a mano. Igual que en el umbral de avisos, se
        // guarda solo al confirmar: así no se escribe en preferencias un
        // valor a medio teclear ("4" mientras se escribe "45").
        var customInterval by remember { mutableStateOf("") }
        var customIntervalMode by remember {
            mutableStateOf(pollingIntervalSeconds !in AppPreferences.POLLING_INTERVAL_PRESETS)
        }

        SectionCard(title = "Frecuencia de consulta") {
            Text(
                "Cada cuánto se consulta a Felicity para saber si se fue/llegó la corriente o cambió la generación PV. Un intervalo más corto detecta cambios más rápido pero consume más batería y datos.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMid
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SECTION_CONTENT_SPACING),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppPreferences.POLLING_INTERVAL_PRESETS.forEach { seconds ->
                    FilterChip(
                        selected = !customIntervalMode && pollingIntervalSeconds == seconds,
                        onClick = {
                            customIntervalMode = false
                            viewModel.setPollingIntervalSeconds(seconds)
                        },
                        label = { Text("${seconds}s") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.tealDim
                        )
                    )
                }
                FilterChip(
                    selected = customIntervalMode,
                    onClick = {
                        customIntervalMode = true
                        if (customInterval.isBlank()) customInterval = pollingIntervalSeconds.toString()
                    },
                    // El valor personalizado se muestra EN el chip: antes decia solo
                    // "Personalizado" y habia que abrirlo para saber cual era,
                    // asi que el usuario no veia su propia configuracion.
                    label = {
                        Text(
                            if (customIntervalMode) "Otro: ${pollingIntervalSeconds}s"
                            else "Otro…"
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.tealDim
                    )
                )
            }

            if (customIntervalMode) {
                val entered = customInterval.toIntOrNull()
                val outOfRange = entered != null && (
                    entered < AppPreferences.MIN_POLLING_INTERVAL_SECONDS ||
                        entered > AppPreferences.MAX_POLLING_INTERVAL_SECONDS
                    )
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    OutlinedTextField(
                        value = customInterval,
                        onValueChange = { input -> customInterval = input.filter { it.isDigit() }.take(4) },
                        label = { Text("Segundos") },
                        singleLine = true,
                        isError = outOfRange,
                        supportingText = if (outOfRange) {
                            {
                                Text(
                                    "Entre ${AppPreferences.MIN_POLLING_INTERVAL_SECONDS} y " +
                                        "${AppPreferences.MAX_POLLING_INTERVAL_SECONDS} s"
                                )
                            }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { entered?.let { viewModel.setPollingIntervalSeconds(it) } },
                        enabled = entered != null && !outOfRange,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                    ) { Text("Aplicar") }
                }
            }

            // La cadencia del inversor es el TECHO de utilidad: consultar más
            // seguido que eso devuelve el mismo dato repetido. Se muestra
            // medida y no supuesta, porque depende del equipo de cada casa.
            if (publishInterval != null) {
                val publish = publishInterval!!
                val wasteful = pollingIntervalSeconds < publish
                Text(
                    "Tu inversor publica un dato nuevo cada ~${publish}s (medido).",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMid,
                    modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                )
                if (wasteful) {
                    Text(
                        "Estás consultando cada ${pollingIntervalSeconds}s, más seguido de lo que el " +
                            "inversor publica: esas consultas extra devuelven el mismo dato y solo " +
                            "gastan batería y datos. Subirlo a ${publish}s o más no te haría perder " +
                            "información.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                Text(
                    "La cadencia real del inversor se muestra aquí después de un par de lecturas " +
                        "con datos nuevos.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow,
                    modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                )
            }
        }
    }

    item {
        val colors = LocalFelicityColors.current
        val alertsEnabled by viewModel.applianceAlertsEnabled.collectAsState()
        val threshold by viewModel.applianceAlertThresholdWatts.collectAsState()

        // Valor escrito a mano cuando el usuario elige "Personalizado". Se
        // guarda solo al confirmar y no en cada tecla, para no escribir en
        // preferencias con valores a medio teclear ("4" al empezar "450").
        var customThreshold by remember { mutableStateOf("") }
        var customMode by remember {
            mutableStateOf(threshold !in AppPreferences.APPLIANCE_ALERT_THRESHOLD_PRESETS)
        }

        SectionCard(title = "Aviso de equipos") {
            Text(
                "Envía una notificación cuando se detecta que un equipo de tu inventario se conectó " +
                    "o se desconectó, indicando cuál y el consumo detectado.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMid
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = SECTION_CONTENT_SPACING)
            ) {
                Text(
                    if (alertsEnabled) "Activado" else "Desactivado",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (alertsEnabled) colors.green else colors.textMid,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = alertsEnabled,
                    onCheckedChange = { viewModel.setApplianceAlertsEnabled(it) }
                )
            }

            if (alertsEnabled) {
                Text(
                    "Salto mínimo de consumo para avisar: $threshold W",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = colors.textHi,
                    modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                )
                Text(
                    "Súbelo si recibes avisos de más — el compresor de la nevera es la causa " +
                        "más común de avisos que no esperabas.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow
                )
                // FlowRow y no Row: los chips no caben en el ancho de un
                // teléfono y el último quedaba cortado a media palabra. Así
                // envuelven a la línea siguiente completos.
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppPreferences.APPLIANCE_ALERT_THRESHOLD_PRESETS.forEach { watts ->
                        FilterChip(
                            selected = !customMode && threshold == watts,
                            onClick = {
                                customMode = false
                                viewModel.setApplianceAlertThresholdWatts(watts)
                            },
                            label = { Text("${watts}W") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.tealDim
                            )
                        )
                    }
                    FilterChip(
                        selected = customMode,
                        onClick = {
                            customMode = true
                            if (customThreshold.isBlank()) customThreshold = threshold.toString()
                        },
                        label = {
                            Text(if (customMode) "Otro: ${threshold}W" else "Otro…")
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.tealDim
                        )
                    )
                }

                if (customMode) {
                    val entered = customThreshold.toIntOrNull()
                    val outOfRange = entered != null && (
                        entered < AppPreferences.MIN_APPLIANCE_ALERT_THRESHOLD_WATTS ||
                            entered > AppPreferences.MAX_APPLIANCE_ALERT_THRESHOLD_WATTS
                        )
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = customThreshold,
                            onValueChange = { input ->
                                customThreshold = input.filter { it.isDigit() }.take(5)
                            },
                            label = { Text("Watts") },
                            singleLine = true,
                            isError = outOfRange,
                            supportingText = if (outOfRange) {
                                {
                                    Text(
                                        "Entre ${AppPreferences.MIN_APPLIANCE_ALERT_THRESHOLD_WATTS} " +
                                            "y ${AppPreferences.MAX_APPLIANCE_ALERT_THRESHOLD_WATTS} W"
                                    )
                                }
                            } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                entered?.let { viewModel.setApplianceAlertThresholdWatts(it) }
                            },
                            enabled = entered != null && !outOfRange,
                            modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                        ) { Text("Aplicar") }
                    }
                }

                Text(
                    "El aviso llega cuando la app lee el dato, no en el instante exacto del " +
                        "encendido: depende de la frecuencia de consulta y de cuándo el inversor " +
                        "publica sus datos. Solo avisa de equipos registrados en tu inventario.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow,
                    modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                )
            }
        }
    }

    item {
        val colors = LocalFelicityColors.current
        val voltageAlertEnabled by viewModel.lowVoltageAlertEnabled.collectAsState()
        val voltageThreshold by viewModel.lowVoltageThreshold.collectAsState()

        var customVoltage by remember { mutableStateOf("") }
        var customVoltageMode by remember {
            mutableStateOf(voltageThreshold !in AppPreferences.LOW_VOLTAGE_THRESHOLD_PRESETS)
        }

        SectionCard(title = "Aviso de voltaje bajo") {
            Text(
                "Avisa cuando el voltaje cae por debajo del valor que fijes. Vigila el voltaje " +
                    "de la red cuando hay corriente, y el de la batería cuando no.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMid
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = SECTION_CONTENT_SPACING)
            ) {
                Text(
                    if (voltageAlertEnabled) "Activado" else "Desactivado",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (voltageAlertEnabled) colors.green else colors.textMid,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = voltageAlertEnabled,
                    onCheckedChange = { viewModel.setLowVoltageAlertEnabled(it) }
                )
            }

            if (voltageAlertEnabled) {
                Text(
                    "Avisar por debajo de: $voltageThreshold V",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = colors.textHi,
                    modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                )
                Text(
                    "Ajústalo al voltaje nominal de tu instalación. Un voltaje bajo no se nota " +
                        "porque la casa sigue encendida, pero fuerza motores y compresores.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppPreferences.LOW_VOLTAGE_THRESHOLD_PRESETS.forEach { volts ->
                        FilterChip(
                            selected = !customVoltageMode && voltageThreshold == volts,
                            onClick = {
                                customVoltageMode = false
                                viewModel.setLowVoltageThreshold(volts)
                            },
                            label = { Text("${volts}V") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.tealDim
                            )
                        )
                    }
                    FilterChip(
                        selected = customVoltageMode,
                        onClick = {
                            customVoltageMode = true
                            if (customVoltage.isBlank()) customVoltage = voltageThreshold.toString()
                        },
                        label = {
                            Text(if (customVoltageMode) "Otro: ${voltageThreshold}V" else "Otro…")
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.tealDim
                        )
                    )
                }

                if (customVoltageMode) {
                    val entered = customVoltage.toIntOrNull()
                    val outOfRange = entered != null && (
                        entered < AppPreferences.MIN_LOW_VOLTAGE_THRESHOLD ||
                            entered > AppPreferences.MAX_LOW_VOLTAGE_THRESHOLD
                        )
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = customVoltage,
                            onValueChange = { input -> customVoltage = input.filter { it.isDigit() }.take(3) },
                            label = { Text("Voltios") },
                            singleLine = true,
                            isError = outOfRange,
                            supportingText = if (outOfRange) {
                                {
                                    Text(
                                        "Entre ${AppPreferences.MIN_LOW_VOLTAGE_THRESHOLD} y " +
                                            "${AppPreferences.MAX_LOW_VOLTAGE_THRESHOLD} V"
                                    )
                                }
                            } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { entered?.let { viewModel.setLowVoltageThreshold(it) } },
                            enabled = entered != null && !outOfRange,
                            modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                        ) { Text("Aplicar") }
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.testLowVoltageAlert() },
                    modifier = Modifier.fillMaxWidth().padding(top = SECTION_CONTENT_SPACING)
                ) { Text("Probar el aviso") }

                Text(
                    "Llega una notificación, suenan tres pulsos graves y el teléfono vibra. En el " +
                        "Panel, la pastilla del " +
                        "voltaje se pone en rojo mientras siga bajo. Para no llenarte de avisos, " +
                        "solo se notifica al cruzar el umbral y con 10 minutos de espera entre " +
                        "avisos.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow,
                    modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                )
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
        SectionCard(title = "Optimización de batería") {
            Text(
                if (batteryExcluded) "Excluida — el sistema no debería matar el servicio."
                else "No excluida — MIUI y fabricantes similares pueden matar el servicio.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (batteryExcluded) LocalFelicityColors.current.green else LocalFelicityColors.current.textMid
            )
            ActionButton(
                text = "Solicitar exclusión",
                onClick = onRequestBatteryExclusion,
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
}

private fun LazyListScope.diagnosticsTab(
    lastInverterRawJson: String?,
    lastBatteryRawJson: String?,
    isLoadingDeviceList: Boolean,
    viewModel: SettingsViewModel,
    onShowFactoryResetConfirm: () -> Unit
) {
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
            ActionButton(
                text = if (isLoadingDeviceList) "Consultando…" else "Consultar dispositivos (planta)",
                outlined = true,
                onClick = { viewModel.refreshDeviceListForDiagnostics() },
                modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
            )
            ActionButton(
                text = "Copiar respuesta de dispositivos",
                outlined = true,
                onClick = { viewModel.copyDeviceListJsonToClipboard() },
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
                text = "Restablecer valores de fábrica",
                icon = Icons.Default.DeleteForever,
                outlined = true,
                contentColor = MaterialTheme.colorScheme.error,
                onClick = onShowFactoryResetConfirm
            )
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
