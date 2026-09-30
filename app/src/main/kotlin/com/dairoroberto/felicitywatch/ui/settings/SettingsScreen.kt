package com.dairoroberto.felicitywatch.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.data.local.AppPreferences
import com.dairoroberto.felicitywatch.data.repository.MigrationProgress
import com.dairoroberto.felicitywatch.domain.model.LicenseStatus
import com.dairoroberto.felicitywatch.domain.model.MAX_REJECTED_ATTEMPTS
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
private val BASE_SETTINGS_TABS = listOf("Cuenta", "Alertas", "Sistema", "Diagnóstico")

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
    val isMasterDevice by viewModel.isMasterDevice.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Si el rol cambia mientras Ajustes está abierto (ej. se reclama el rol
    // de master en esta misma sesión), la pestaña "Dispositivos" aparece o
    // desaparece y desplaza los índices — se vuelve a "Cuenta" para no dejar
    // seleccionado un índice que ahora apunta a otro contenido.
    LaunchedEffect(isMasterDevice) { selectedTab = 0 }

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

    // Ubicación para el mapa de dispositivos de la master (guía) — este
    // dispositivo puede ser cliente o master, así que cualquiera necesita el
    // permiso para reportar su posición.
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasLocationPermission = results.values.any { it }
    }
    val requestLocationPermission: () -> Unit = {
        locationPermissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    // ACCESS_BACKGROUND_LOCATION no se puede pedir en el mismo diálogo que
    // el permiso foreground (Android 11+ lo ignora si viene junto) — hay
    // que mandar al usuario a Ajustes > Apps > Felicity Watch > Permisos >
    // Ubicación > "Permitir todo el tiempo". Sin esto, LocationTracker solo
    // puede obtener la posición mientras la app está abierta en pantalla,
    // nunca durante el ciclo de monitoreo en segundo plano.
    var hasBackgroundLocationPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    // El permiso concedido no basta: si el usuario tiene apagado el
    // servicio de ubicación de Android (GPS/red), LocationTracker jamás
    // consigue una posición y el ciclo de monitoreo lo ignora en silencio
    // (best-effort). Se re-chequea al volver a la pantalla porque el
    // usuario puede prender/apagar esto desde el panel rápido de Android,
    // no solo desde el botón de abajo.
    var isLocationServiceEnabled by remember { mutableStateOf(isLocationServiceEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isLocationServiceEnabled = isLocationServiceEnabled(context)
                hasBackgroundLocationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    // La gestión de clientes se movió a su propia pantalla (Más > Clientes,
    // ver ClientsScreen) — ya no es una pestaña más de Ajustes.
    val settingsTabs = BASE_SETTINGS_TABS

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { ElegantSnackbar(it) } }
    ) { scaffoldPadding ->
        Column(Modifier.fillMaxSize().padding(scaffoldPadding)) {
            val colors = LocalFelicityColors.current
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = colors.surface2,
                edgePadding = 12.dp
            ) {
                settingsTabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().imePadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (settingsTabs.getOrNull(selectedTab)) {
                    "Cuenta" -> accountTab(
                        formState = formState,
                        viewModel = viewModel,
                        onShowLogoutConfirm = { showLogoutConfirm = true }
                    )
                    "Alertas" -> alertsTab(
                        isTestingConnection = isTestingConnection,
                        viewModel = viewModel,
                        alertsViewModel = alertsViewModel,
                        alertRules = alertRules,
                        onTestPushChannel = requestPushPermissionThenTest
                    )
                    "Sistema" -> systemTab(
                        pollingIntervalSeconds = pollingIntervalSeconds,
                        serviceRunning = serviceRunning,
                        darkModeEnabled = darkModeEnabled,
                        onToggleDarkMode = onToggleDarkMode,
                        batteryExcluded = batteryExcluded,
                        onRequestBatteryExclusion = {
                            requestIgnoreBatteryOptimizations(context)
                            batteryExcluded = isIgnoringBatteryOptimizations(context)
                        },
                        hasLocationPermission = hasLocationPermission,
                        onRequestLocationPermission = requestLocationPermission,
                        isLocationServiceEnabled = isLocationServiceEnabled,
                        onOpenLocationSettings = {
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        },
                        hasBackgroundLocationPermission = hasBackgroundLocationPermission,
                        onOpenAppLocationSettings = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.fromParts("package", context.packageName, null))
                            )
                        },
                        viewModel = viewModel
                    )
                    "Diagnóstico" -> diagnosticsTab(
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
        val colors = LocalFelicityColors.current
        val isMaster by viewModel.isMasterDevice.collectAsState()
        val ownLicense by viewModel.ownLicense.collectAsState()
        LaunchedEffect(Unit) { viewModel.loadOwnLicense() }

        // Solo para clientes: la master no tiene licencia que mostrar. Sin
        // esta tarjeta, tras la aprobación el cliente solo recupera el
        // acceso, sin ninguna confirmación de que su pago quedó validado.
        if (!isMaster) {
            val license = ownLicense
            if (license != null && license.status == LicenseStatus.APPROVED) {
                val dateFormatter = remember {
                    java.time.format.DateTimeFormatter
                        .ofPattern("d 'de' MMMM 'de' yyyy", java.util.Locale("es", "ES"))
                        .withZone(java.time.ZoneId.systemDefault())
                }
                SectionCard(title = "Licencia") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = colors.green,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(
                                "Validada · acceso indefinido",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = colors.textHi
                            )
                            license.decidedAt?.let {
                                Text(
                                    "Desde el ${dateFormatter.format(it)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textLow
                                )
                            }
                        }
                    }
                }
            }
        }
    }

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
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    isLocationServiceEnabled: Boolean,
    onOpenLocationSettings: () -> Unit,
    hasBackgroundLocationPermission: Boolean,
    onOpenAppLocationSettings: () -> Unit,
    viewModel: SettingsViewModel
) {
    item {
        val colors = LocalFelicityColors.current
        val isMaster by viewModel.isMasterDevice.collectAsState()
        val masterInfo by viewModel.currentMasterInfo.collectAsState()

        SectionCard(title = "Rol de este dispositivo") {
            if (isMaster) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = colors.green)
                    Text(
                        "Este dispositivo es el principal",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = colors.green,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Text(
                    "Configura los códigos de acceso en la pestaña Dispositivos.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow,
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = colors.accent)
                    Text(
                        "Este dispositivo es cliente",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = colors.textHi,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Text(
                    "Consulta a Felicity con permiso del dispositivo principal" +
                        (masterInfo?.let { " (${it.displayName ?: it.deviceId.take(8)})" } ?: "") + ".",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }

    item {
        val colors = LocalFelicityColors.current
        // Un cliente reporta su ubicación de todas formas (forzado, ver
        // UpdateDeviceLocationUseCase) para que el master pueda ubicarlo en
        // el mapa — pero sin exponerle esta tarjeta explicativa: la idea es
        // que ese reporte sea transparente para el cliente, no algo que
        // pueda pausar ni sobre lo que necesite enterarse. El permiso en sí
        // se sigue pidiendo igual (Android exige el diálogo del sistema,
        // sin eso no hay ubicación posible) — solo se oculta la mención a
        // "ubicación"/"mapa" dentro de la propia app del cliente.
        val isMaster by viewModel.isMasterDevice.collectAsState()
        if (!isMaster) return@item

        SectionCard(title = "Ubicación") {
            Text(
                "Reporta la ubicación de este dispositivo para que el dispositivo principal " +
                    "pueda ubicarlo en un mapa (pestaña Dispositivos).",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMid
            )
            if (hasLocationPermission && isLocationServiceEnabled && !hasBackgroundLocationPermission) {
                Text(
                    "Falta permitir la ubicación \"todo el tiempo\": el ciclo de monitoreo corre en " +
                        "segundo plano, y sin ese permiso Android no deja obtener la posición cuando " +
                        "la app no está abierta en pantalla.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
                ActionButton(
                    text = "Abrir ajustes de permisos",
                    onClick = onOpenAppLocationSettings,
                    modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                )
            } else if (hasLocationPermission && isLocationServiceEnabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = colors.green)
                    Text(
                        "Permiso concedido",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = colors.green,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                val lastLocationError by viewModel.lastLocationError.collectAsState()
                lastLocationError?.let { error ->
                    Text(
                        "Último intento de reportar ubicación falló: $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            } else if (hasLocationPermission) {
                Text(
                    "El permiso está concedido, pero el servicio de ubicación de este teléfono está apagado. Actívalo para que se pueda reportar la posición.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
                ActionButton(
                    text = "Activar ubicación del sistema",
                    onClick = onOpenLocationSettings,
                    modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                )
            } else {
                ActionButton(
                    text = "Habilitar ubicación",
                    onClick = onRequestLocationPermission,
                    modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                )
            }
        }
    }

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

    item {
        val colors = LocalFelicityColors.current
        val isMaster by viewModel.isMasterDevice.collectAsState()
        // Forzada y transparente para un cliente (ver PowerHistoryRepository.
        // record): no hay switch ni botón de migrar que tocar, ni nada que
        // el cliente deba saber sobre esto — mismo criterio que la tarjeta
        // de Ubicación, que tampoco se le muestra.
        if (!isMaster) return@item

        val migrationDone by viewModel.supabaseMigrationDone.collectAsState()
        val syncEnabled by viewModel.supabaseSyncEnabled.collectAsState()
        val progress by viewModel.migrationProgress.collectAsState()

        SectionCard(title = "Sincronización en la nube") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudSync, contentDescription = null, tint = colors.accent)
                Text(
                    "Respalda tu historial en la nube (Supabase) para poder consultarlo desde otro " +
                        "dispositivo. El guardado local sigue funcionando igual, con o sin conexión.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            if (!migrationDone) {
                val inProgress = progress as? MigrationProgress.InProgress
                if (inProgress != null && inProgress.total > 0) {
                    Text(
                        "Migrando: ${inProgress.uploaded} / ${inProgress.total} lecturas",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMid,
                        modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                    )
                    LinearProgressIndicator(
                        progress = { inProgress.uploaded.toFloat() / inProgress.total.toFloat() },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
                val failure = progress as? MigrationProgress.Failed
                if (failure != null) {
                    Text(
                        "Falló la migración: ${failure.message}. Puedes intentarlo de nuevo.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                    )
                }
                ActionButton(
                    text = "Migrar historial a la nube",
                    icon = Icons.Default.CloudSync,
                    loading = progress is MigrationProgress.InProgress,
                    onClick = { viewModel.migrateToSupabase() },
                    modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = SECTION_CONTENT_SPACING)
                ) {
                    Text(
                        if (syncEnabled) "Sincronización activada" else "Sincronización desactivada",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (syncEnabled) colors.green else colors.textMid,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = syncEnabled,
                        onCheckedChange = { viewModel.setSupabaseSyncEnabled(it) }
                    )
                }
                Text(
                    "Historial ya migrado. Cada lectura nueva se guarda también en la nube" +
                        (if (syncEnabled) "." else ", pero está pausado."),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

}

/**
 * Pestaña "Clientes" — solo tiene sentido en la master (el ítem de la
 * TabRow ya se oculta por completo para un cliente, ver [SettingsScreen]):
 * marcar/confirmar el rol de este dispositivo, generar códigos de acceso
 * (celular cliente o escritorio) y administrar los dispositivos registrados.
 *
 * Tiene dos vistas, elegidas con [showListView]:
 * - Listado y gestión: quién está registrado, ver sus datos en detalle,
 *   renombrar/revocar/eliminar — lo que se consulta día a día.
 * - Configuración: rol de este dispositivo, período de prueba, generar
 *   código para un celular o PIN para escritorio — acciones puntuales.
 * Antes vivían todas mezcladas en una sola pestaña con el listado al final,
 * obligando a bajar por varias tarjetas de configuración para ver algo tan
 * básico como quién está registrado.
 */
internal fun LazyListScope.devicesTab(
    viewModel: SettingsViewModel,
    showListView: Boolean,
    onOpenClientDetail: (deviceId: String, displayName: String?) -> Unit
) {
    if (showListView) {
    item {
        val colors = LocalFelicityColors.current
        val isMaster by viewModel.isMasterDevice.collectAsState()
        if (!isMaster) return@item

        val devices by viewModel.accountDevices.collectAsState()
        val loadingDevices by viewModel.isLoadingDevices.collectAsState()
        var showMap by remember { mutableStateOf(false) }

        // Refresca sola mientras esta pestaña está abierta — antes solo
        // cargaba una vez al entrar, así que un cambio hecho desde otro
        // dispositivo (o revocar/aprobar en la misma sesión) no se veía
        // hasta salir del tab y volver a entrar.
        LaunchedEffect(Unit) {
            while (true) {
                viewModel.loadAccountDevices()
                kotlinx.coroutines.delay(15_000)
            }
        }

        if (showMap) {
            DeviceMapDialog(devices = devices, onDismiss = { showMap = false })
        }

        // El vencimiento del periodo free se calcula con los días vigentes, que
        // vive en el ViewModel — no en la fila de cada dispositivo.
        val devicesFreePeriodDays by viewModel.freePeriodDays.collectAsState()

        SectionCard(title = "Dispositivos registrados") {
            if (loadingDevices) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else if (devices.isEmpty()) {
                Text(
                    "Todavía no hay otros dispositivos registrados.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow
                )
            } else {
                val locatedCount = devices.count { it.latitude != null && it.longitude != null }
                ActionButton(
                    text = if (locatedCount > 0) "Ver mapa ($locatedCount)" else "Ver mapa (sin ubicaciones aún)",
                    outlined = true,
                    enabled = locatedCount > 0,
                    onClick = { showMap = true },
                    modifier = Modifier.padding(bottom = SECTION_CONTENT_SPACING)
                )
                devices.forEach { device ->
                    AccountDeviceRow(
                        device = device,
                        colors = colors,
                        freePeriodDays = devicesFreePeriodDays,
                        onRename = { name -> viewModel.renameDevice(device.deviceId, name) },
                        onRevoke = { viewModel.revokeDevice(device.deviceId) },
                        onDelete = { viewModel.deleteDevice(device.deviceId) },
                        onApproveTransfer = { viewModel.approveTransfer(device.deviceId) },
                        onRejectTransfer = { reason ->
                            viewModel.rejectTransfer(
                                device.deviceId,
                                device.license.rejectedAttempts,
                                reason
                            )
                        },
                        onUnblock = { viewModel.unblockDevice(device.deviceId) },
                        onStartTrial = { viewModel.startFreePeriodFor(device.deviceId) },
                        onOpenDetail = { onOpenClientDetail(device.deviceId, device.displayName) }
                    )
                }
            }
        }
    }
    } // showListView

    if (!showListView) {
    item {
        val colors = LocalFelicityColors.current
        val isMaster by viewModel.isMasterDevice.collectAsState()
        val masterInfo by viewModel.currentMasterInfo.collectAsState()
        val claiming by viewModel.isClaimingMaster.collectAsState()

        SectionCard(title = "Rol de este dispositivo") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudSync, contentDescription = null, tint = colors.accent)
                Text(
                    "Solo un dispositivo es el \"principal\": el único que consulta Felicity y " +
                        "sincroniza a la nube. Los demás requieren su aprobación para funcionar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = SECTION_CONTENT_SPACING)
            ) {
                Text(
                    if (isMaster) "Este dispositivo es el principal" else "Marcar como principal",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isMaster) colors.green else colors.textHi,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = isMaster,
                    enabled = !isMaster,
                    onCheckedChange = { if (it) viewModel.claimMasterRole() }
                )
            }

            if (claiming) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }

            if (!isMaster && masterInfo != null) {
                Text(
                    "Dispositivo principal actual: ${masterInfo?.displayName ?: masterInfo?.deviceId?.take(8)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }

    item {
        val colors = LocalFelicityColors.current
        val isMaster by viewModel.isMasterDevice.collectAsState()
        if (!isMaster) return@item

        val freeDays by viewModel.freePeriodDays.collectAsState()
        LaunchedEffect(Unit) { viewModel.loadFreePeriodDays() }

        SectionCard(title = "Periodo de prueba") {
            Text(
                "Días de acceso gratis que recibe un celular al canjear su primer código. " +
                    "Al terminar, deberá enviar el ID de su transferencia para que la valides.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMid
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = SECTION_CONTENT_SPACING)
            ) {
                Text(
                    "$freeDays ${if (freeDays == 1) "día" else "días"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { viewModel.setFreePeriodDays((freeDays - 1).coerceAtLeast(0)) },
                    enabled = freeDays > 0
                ) { Text("−") }
                TextButton(
                    onClick = { viewModel.setFreePeriodDays((freeDays + 1).coerceAtMost(365)) },
                    enabled = freeDays < 365
                ) { Text("+") }
            }
            Text(
                "Cambiarlo afecta también a los clientes que ya están en prueba: su " +
                    "vencimiento se recalcula sobre la fecha en que canjearon el código.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    item {
        val colors = LocalFelicityColors.current
        val isMaster by viewModel.isMasterDevice.collectAsState()
        if (!isMaster) return@item

        val generatingClientPin by viewModel.isGeneratingClientPin.collectAsState()
        val clientPin by viewModel.clientPairingPin.collectAsState()

        SectionCard(title = "Generar código para un celular") {
            Text(
                "Otros teléfonos con esta cuenta necesitan un código de acceso para funcionar. " +
                    "Genera uno y compártelo con ese dispositivo.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMid
            )

            if (clientPin != null) {
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                Text(
                    clientPin!!.pin,
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = com.dairoroberto.felicitywatch.ui.theme.JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = SECTION_CONTENT_SPACING)
                )
                PairingCountdown(
                    expiresAt = clientPin!!.expiresAt,
                    colors = colors,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
                // Al copiar se oculta el código y vuelve el botón de generar.
                // Ojo: si el portapapeles se pisa antes de pegarlo, el código
                // ya no se puede volver a ver y hay que generar otro (el
                // anterior sigue siendo válido en Supabase hasta que expire,
                // pero nadie lo conoce).
                ActionButton(
                    text = "Copiar código",
                    icon = Icons.Default.ContentCopy,
                    onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(clientPin!!.pin))
                        viewModel.clearClientPairingPin()
                    },
                    modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                )
                ActionButton(
                    text = "Generar otro código",
                    outlined = true,
                    onClick = { viewModel.generateClientPairingPin() },
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                ActionButton(
                    text = "Generar código para un celular",
                    icon = Icons.Default.CloudSync,
                    loading = generatingClientPin,
                    onClick = { viewModel.generateClientPairingPin() },
                    modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                )
            }
        }
    }

    item {
        val colors = LocalFelicityColors.current
        val isMaster by viewModel.isMasterDevice.collectAsState()
        if (!isMaster) return@item

        val migrationDone by viewModel.supabaseMigrationDone.collectAsState()
        val pin by viewModel.pairingPin.collectAsState()
        val generating by viewModel.isGeneratingPairingPin.collectAsState()

        SectionCard(title = "Generar PIN para la app de escritorio") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudSync, contentDescription = null, tint = colors.accent)
                Text(
                    "Genera un PIN de un solo uso para conectar la app de escritorio a tu historial " +
                        "en la nube.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            if (!migrationDone) {
                Text(
                    "Primero migra tu historial a la nube (pestaña Sistema) — sin eso la app de " +
                        "escritorio no tendría nada que mostrar.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow,
                    modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                )
            } else if (pin != null) {
                Text(
                    pin!!.pin,
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = com.dairoroberto.felicitywatch.ui.theme.JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = SECTION_CONTENT_SPACING)
                )
                PairingCountdown(
                    expiresAt = pin!!.expiresAt,
                    colors = colors,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
                ActionButton(
                    text = "Generar otro PIN",
                    outlined = true,
                    onClick = { viewModel.generateDesktopPairingPin() },
                    modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                )
            } else {
                ActionButton(
                    text = "Generar PIN para escritorio",
                    icon = Icons.Default.CloudSync,
                    loading = generating,
                    onClick = { viewModel.generateDesktopPairingPin() },
                    modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                )
            }
        }
    }
    } // !showListView

}

/** Cuenta regresiva "Expira en 1:47" hasta [expiresAt], se actualiza cada segundo. */
@Composable
private fun PairingCountdown(
    expiresAt: java.time.Instant,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors,
    modifier: Modifier = Modifier
) {
    var remainingSeconds by remember(expiresAt) {
        mutableStateOf(java.time.Duration.between(java.time.Instant.now(), expiresAt).seconds.coerceAtLeast(0))
    }

    LaunchedEffect(expiresAt) {
        while (remainingSeconds > 0) {
            kotlinx.coroutines.delay(1000)
            remainingSeconds = java.time.Duration.between(java.time.Instant.now(), expiresAt).seconds.coerceAtLeast(0)
        }
    }

    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    Text(
        if (remainingSeconds > 0) {
            "Expira en %d:%02d, o al usarlo una vez".format(minutes, seconds)
        } else {
            "Generando un código nuevo…"
        },
        style = MaterialTheme.typography.labelSmall,
        color = colors.textLow,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
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
            val lastEquipmentError by viewModel.lastEquipmentError.collectAsState()
            lastEquipmentError?.let { error ->
                Text(
                    "Último intento de sincronizar equipos falló: $error",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = SECTION_CONTENT_SPACING)
                )
            }
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

/** "hace 5min" / "hace 3h" / "ayer" / "12 sep" — misma idea que
 * dataAgeLabel() del Panel, pero con escala de días para "última actividad",
 * que puede ser mucho más vieja que un dato de inversor. */
private fun formatLastSeen(instant: java.time.Instant): String {
    val now = java.time.Instant.now()
    val secondsAgo = java.time.Duration.between(instant, now).seconds.coerceAtLeast(0)
    return when {
        secondsAgo < 60 -> "hace ${secondsAgo}s"
        secondsAgo < 3600 -> "hace ${secondsAgo / 60}min"
        secondsAgo < 86_400 -> "hace ${secondsAgo / 3600}h"
        secondsAgo < 172_800 -> "ayer"
        secondsAgo < 604_800 -> "hace ${secondsAgo / 86_400} días"
        else -> {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("d MMM").withLocale(Locale("es", "ES"))
            formatter.format(instant.atZone(java.time.ZoneId.systemDefault()))
        }
    }
}

@Composable
private fun AccountDeviceRow(
    device: com.dairoroberto.felicitywatch.data.repository.AccountDeviceInfo,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors,
    freePeriodDays: Int,
    onRename: (String) -> Unit,
    onRevoke: () -> Unit,
    onDelete: () -> Unit,
    onApproveTransfer: () -> Unit,
    onRejectTransfer: (String?) -> Unit,
    onUnblock: () -> Unit,
    onStartTrial: () -> Unit,
    onOpenDetail: () -> Unit
) {
    var editingName by remember(device.deviceId) { mutableStateOf(false) }
    var nameInput by remember(device.deviceId) { mutableStateOf(device.displayName ?: "") }
    var showRevokeConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectReason by remember(device.deviceId) { mutableStateOf("") }

    if (showRejectDialog) {
        val willBlock = device.license.rejectedAttempts + 1 >= MAX_REJECTED_ATTEMPTS
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text("¿Rechazar esta transferencia?") },
            text = {
                Column {
                    Text(
                        if (willBlock) {
                            "Este es el intento $MAX_REJECTED_ATTEMPTS: el cliente quedará " +
                                "BLOQUEADO y solo podrás desbloquearlo desde aquí."
                        } else {
                            "El cliente perderá el acceso y necesitará un código nuevo para " +
                                "volver a intentarlo. Le quedarán " +
                                "${MAX_REJECTED_ATTEMPTS - device.license.rejectedAttempts - 1} intentos."
                        }
                    )
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { rejectReason = it },
                        singleLine = true,
                        label = { Text("Motivo (opcional)") },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showRejectDialog = false
                    onRejectTransfer(rejectReason.ifBlank { null })
                    rejectReason = ""
                }) { Text("Rechazar", color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showRevokeConfirm) {
        AlertDialog(
            onDismissRequest = { showRevokeConfirm = false },
            title = { Text("¿Revocar este dispositivo?") },
            text = { Text("Dejará de poder monitorear hasta que generes un código nuevo para él.") },
            confirmButton = {
                TextButton(onClick = { showRevokeConfirm = false; onRevoke() }) { Text("Revocar") }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("¿Eliminar este registro?") },
            text = {
                Text(
                    "Se borra por completo, incluido el nombre. Si este teléfono vuelve a " +
                        "canjear un código, entrará como un dispositivo nuevo."
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Eliminar", color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(if (device.revoked) colors.error else colors.green, androidx.compose.foundation.shape.CircleShape)
            )
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                if (editingName) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        singleLine = true,
                        label = { Text("Nombre") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        device.displayName ?: "Dispositivo ${device.deviceId.take(8)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = colors.textHi
                    )
                    Text(
                        "${if (device.role == "master") "Principal" else "Cliente"}" +
                            (if (device.revoked) " · Revocado" else "") +
                            (device.lastSeenAt?.let { " · Visto ${formatLastSeen(it)}" } ?: " · Sin actividad registrada"),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow
                    )
                }
            }
            if (editingName) {
                TextButton(onClick = {
                    editingName = false
                    onRename(nameInput)
                }) { Text("Guardar") }
            } else {
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Más opciones", tint = colors.textLow)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (device.role != "master") {
                            DropdownMenuItem(
                                text = { Text("Ver detalle") },
                                onClick = { showMenu = false; onOpenDetail() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Renombrar") },
                            onClick = { showMenu = false; editingName = true }
                        )
                        if (device.role != "master" && !device.revoked) {
                            DropdownMenuItem(
                                text = { Text("Revocar", color = colors.error) },
                                onClick = { showMenu = false; showRevokeConfirm = true }
                            )
                        }
                        if (device.role != "master") {
                            DropdownMenuItem(
                                text = { Text("Eliminar", color = colors.error) },
                                onClick = { showMenu = false; showDeleteConfirm = true }
                            )
                        }
                    }
                }
            }
        }

        // Estado de licencia — solo para clientes: la master no tiene periodo
        // de prueba ni transferencia que validar.
        if (device.role != "master") {
            LicenseRow(
                license = device.license,
                freePeriodDays = freePeriodDays,
                colors = colors,
                onApprove = onApproveTransfer,
                onReject = { showRejectDialog = true },
                onUnblock = onUnblock,
                onStartTrial = onStartTrial
            )
        }
    }
}

/**
 * Estado de licencia de un cliente y las acciones del master sobre él.
 *
 * La fecha que se muestra depende del estado, porque es la que importa en cada
 * caso: en prueba, cuándo vence; pendiente, cuándo llegó la transferencia;
 * decidido, cuándo se decidió.
 */
@Composable
private fun LicenseRow(
    license: com.dairoroberto.felicitywatch.domain.model.LicenseState,
    freePeriodDays: Int,
    colors: com.dairoroberto.felicitywatch.ui.theme.FelicitySemanticColors,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onUnblock: () -> Unit,
    onStartTrial: () -> Unit
) {
    val dateFormatter = remember {
        java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, hh:mm a", java.util.Locale("es", "ES"))
            .withZone(java.time.ZoneId.systemDefault())
    }

    val (label, tint) = when (license.status) {
        LicenseStatus.NONE -> "Sin licencia iniciada" to colors.textLow
        LicenseStatus.FREE -> {
            val remaining = license.freeDaysRemaining(freePeriodDays)
            if (license.isFreeExpired(freePeriodDays)) {
                "Prueba vencida — esperando transferencia" to colors.error
            } else {
                "En prueba · $remaining ${if (remaining == 1) "día" else "días"} restantes" to colors.accent
            }
        }
        LicenseStatus.PENDING -> "Transferencia por validar" to colors.chargeAccent
        LicenseStatus.APPROVED -> "Validado · licencia indefinida" to colors.green
        LicenseStatus.REJECTED ->
            "Rechazado · intento ${license.rejectedAttempts} de $MAX_REJECTED_ATTEMPTS" to colors.error
        LicenseStatus.BLOCKED -> "BLOQUEADO · agotó los intentos" to colors.error
    }

    // La fecha relevante cambia con el estado; se elige una sola para no
    // llenar la fila de timestamps que el master tendría que interpretar.
    val dateLine = when (license.status) {
        LicenseStatus.FREE -> license.freeExpiresAt(freePeriodDays)?.let { "Vence el ${dateFormatter.format(it)}" }
        LicenseStatus.PENDING -> license.transferSubmittedAt?.let { "Enviada el ${dateFormatter.format(it)}" }
        LicenseStatus.APPROVED -> license.decidedAt?.let { "Validada el ${dateFormatter.format(it)}" }
        LicenseStatus.REJECTED, LicenseStatus.BLOCKED ->
            license.decidedAt?.let { "Rechazada el ${dateFormatter.format(it)}" }
        LicenseStatus.NONE -> null
    }

    Column(Modifier.fillMaxWidth().padding(start = 18.dp, top = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = tint)
        dateLine?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = colors.textLow)
        }
        license.transferReference?.takeIf { it.isNotBlank() }?.let {
            Text("ID: $it", style = MaterialTheme.typography.labelSmall, color = colors.textMid)
        }

        when (license.status) {
            // Validar/rechazar solo tiene sentido con una transferencia
            // esperando: fuera de ese estado no hay nada que decidir.
            LicenseStatus.PENDING -> Row(modifier = Modifier.padding(top = 4.dp)) {
                TextButton(onClick = onApprove) { Text("Aprobar", color = colors.green) }
                TextButton(onClick = onReject) { Text("Rechazar", color = colors.error) }
            }
            LicenseStatus.BLOCKED -> TextButton(
                onClick = onUnblock,
                modifier = Modifier.padding(top = 4.dp)
            ) { Text("Desbloquear", color = colors.accent) }
            // El periodo de prueba arranca solo al canjear el código; este
            // botón es la salida para un cliente cuyo canje no llegó a
            // iniciarlo (p. ej. canjeó antes de aplicar la migración), que si
            // no se quedaría sin licencia y sin forma de obtenerla.
            LicenseStatus.NONE -> TextButton(
                onClick = onStartTrial,
                modifier = Modifier.padding(top = 4.dp)
            ) { Text("Iniciar periodo de prueba", color = colors.accent) }
            else -> Unit
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

private fun isLocationServiceEnabled(context: Context): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
}
