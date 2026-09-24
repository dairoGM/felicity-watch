package com.dairoroberto.felicitywatch.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dairoroberto.felicitywatch.data.local.AppPreferences
import com.dairoroberto.felicitywatch.data.local.CredentialsStore
import com.dairoroberto.felicitywatch.data.remote.RawResponseRecorder
import com.dairoroberto.felicitywatch.data.repository.AlertEventRepository
import com.dairoroberto.felicitywatch.data.repository.AlertRuleRepository
import com.dairoroberto.felicitywatch.data.repository.FelicityRepository
import com.dairoroberto.felicitywatch.data.repository.MigrationProgress
import com.dairoroberto.felicitywatch.data.repository.SupabaseSyncRepository
import com.dairoroberto.felicitywatch.domain.usecase.RunMonitoringCycleUseCase
import com.dairoroberto.felicitywatch.domain.usecase.describeMonitoringError
import com.dairoroberto.felicitywatch.notification.LowVoltageAlertPlayer
import com.dairoroberto.felicitywatch.notification.NotificationChannels
import com.dairoroberto.felicitywatch.notification.PushNotifier
import com.dairoroberto.felicitywatch.notification.VoiceAlertPlayer
import com.dairoroberto.felicitywatch.notification.WhatsappAlertSender
import com.dairoroberto.felicitywatch.service.MonitoringServiceController
import com.dairoroberto.felicitywatch.service.MonitoringStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val fsolarUsername: String = "",
    val fsolarPassword: String = "",
    val whatsappPhone: String = "",
    val callMeBotApiKey: String = ""
)

private const val TEST_MESSAGE = "Esto es una prueba de Felicity Watch"

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val credentialsStore: CredentialsStore,
    private val alertRuleRepository: AlertRuleRepository,
    private val alertEventRepository: AlertEventRepository,
    private val appPreferences: AppPreferences,
    private val felicityRepository: FelicityRepository,
    private val voicePlayer: VoiceAlertPlayer,
    private val lowVoltageAlertPlayer: LowVoltageAlertPlayer,
    private val pushNotifier: PushNotifier,
    private val whatsappSender: WhatsappAlertSender,
    private val runMonitoringCycleUseCase: RunMonitoringCycleUseCase,
    stateHolder: MonitoringStateHolder,
    private val rawResponseRecorder: RawResponseRecorder,
    private val supabaseSyncRepository: SupabaseSyncRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val serviceRunning: StateFlow<Boolean> = stateHolder.serviceRunning
    val lastInverterRawJson: StateFlow<String?> = stateHolder.lastInverterRawJson
    val lastBatteryRawJson: StateFlow<String?> = stateHolder.lastBatteryRawJson

    private val _isLoadingDeviceList = MutableStateFlow(false)
    val isLoadingDeviceList: StateFlow<Boolean> = _isLoadingDeviceList

    val pollingIntervalSeconds: StateFlow<Int> = appPreferences.pollingIntervalSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPreferences.DEFAULT_POLLING_INTERVAL_SECONDS)

    /** Cadencia real medida con que el inversor publica en la nube. */
    val inverterPublishIntervalSeconds: StateFlow<Int?> = stateHolder.inverterPublishIntervalSeconds

    val applianceAlertsEnabled: StateFlow<Boolean> = appPreferences.applianceAlertsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val applianceAlertThresholdWatts: StateFlow<Int> = appPreferences.applianceAlertThresholdWatts
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AppPreferences.DEFAULT_APPLIANCE_ALERT_THRESHOLD_WATTS
        )

    fun setApplianceAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setApplianceAlertsEnabled(enabled) }
    }

    fun setApplianceAlertThresholdWatts(watts: Int) {
        viewModelScope.launch {
            appPreferences.setApplianceAlertThresholdWatts(
                watts.coerceIn(
                    AppPreferences.MIN_APPLIANCE_ALERT_THRESHOLD_WATTS,
                    AppPreferences.MAX_APPLIANCE_ALERT_THRESHOLD_WATTS
                )
            )
        }
    }

    val lowVoltageAlertEnabled: StateFlow<Boolean> = appPreferences.lowVoltageAlertEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lowVoltageThreshold: StateFlow<Int> = appPreferences.lowVoltageThreshold
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AppPreferences.DEFAULT_LOW_VOLTAGE_THRESHOLD
        )

    fun setLowVoltageAlertEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setLowVoltageAlertEnabled(enabled) }
    }

    fun setLowVoltageThreshold(volts: Int) {
        viewModelScope.launch {
            appPreferences.setLowVoltageThreshold(
                volts.coerceIn(
                    AppPreferences.MIN_LOW_VOLTAGE_THRESHOLD,
                    AppPreferences.MAX_LOW_VOLTAGE_THRESHOLD
                )
            )
        }
    }

    /** Prueba el aviso COMPLETO — notificacion + sonido + vibracion — para que
     * el usuario vea exactamente lo que va a recibir, sin esperar una caida
     * real de voltaje. Antes solo tocaba el sonido, asi que no se podia
     * comprobar si las notificaciones estaban permitidas. */
    fun testLowVoltageAlert() {
        viewModelScope.launch {
            val threshold = appPreferences.lowVoltageThreshold.first()
            // Valor de ejemplo por debajo del umbral, para que el texto de la
            // notificacion se lea igual que en un aviso real.
            val sample = (threshold - 4).coerceAtLeast(1)
            val sent = pushNotifier.notifyAlert(
                title = "Voltaje bajo (prueba)",
                body = "El voltaje de la red cayo a $sample,0 V, por debajo de los $threshold V " +
                    "configurados. Un voltaje bajo puede danar motores y compresores.",
                notificationId = 6_002
            )
            lowVoltageAlertPlayer.play()
            emit(
                if (sent) "Aviso de prueba enviado"
                else "Sono la alerta, pero no se pudo mostrar la notificacion: revisa el permiso de notificaciones"
            )
        }
    }


    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection

    private val _formState = MutableStateFlow(
        SettingsUiState(
            fsolarUsername = credentialsStore.fsolarUsername.orEmpty(),
            fsolarPassword = credentialsStore.fsolarPassword.orEmpty(),
            whatsappPhone = credentialsStore.whatsappPhone.orEmpty(),
            callMeBotApiKey = credentialsStore.callMeBotApiKey.orEmpty()
        )
    )
    val formState: StateFlow<SettingsUiState> = _formState

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun onUsernameChange(value: String) {
        _formState.value = _formState.value.copy(fsolarUsername = value)
    }

    fun onPasswordChange(value: String) {
        _formState.value = _formState.value.copy(fsolarPassword = value)
    }

    fun onWhatsappPhoneChange(value: String) {
        _formState.value = _formState.value.copy(whatsappPhone = value)
    }

    fun onApiKeyChange(value: String) {
        _formState.value = _formState.value.copy(callMeBotApiKey = value)
    }

    fun saveFsolarCredentials() {
        // Recortar espacios accidentales (autocompletar/autocorrección del
        // teclado suele agregar uno al final) — una de las causas más
        // comunes de "contraseña incorrecta" cuando la cuenta sí es válida.
        credentialsStore.fsolarUsername = _formState.value.fsolarUsername.trim()
        credentialsStore.fsolarPassword = _formState.value.fsolarPassword.trim()
        felicityRepository.resetDeviceCache()
        emit("Credenciales de FSolar guardadas")
    }

    fun saveWhatsappConfig() {
        credentialsStore.whatsappPhone = _formState.value.whatsappPhone
        credentialsStore.callMeBotApiKey = _formState.value.callMeBotApiKey
        emit("Configuración de WhatsApp guardada")
    }

    fun setPollingIntervalSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(
            AppPreferences.MIN_POLLING_INTERVAL_SECONDS,
            AppPreferences.MAX_POLLING_INTERVAL_SECONDS
        )
        viewModelScope.launch {
            appPreferences.setPollingIntervalSeconds(clamped)
            val label = if (clamped < 60) {
                "${clamped}s"
            } else {
                val minutes = clamped / 60
                val remainder = clamped % 60
                if (remainder == 0) "${minutes}min" else "${minutes}min ${remainder}s"
            }
            emit("Frecuencia de consulta actualizada a $label")
        }
    }

    fun restartService() {
        MonitoringServiceController.start(context)
        emit("Servicio de vigilancia reiniciado")
    }

    /** "Probar conexión / realizar primera lectura", igual que un canal más. */
    fun testConnection() {
        if (_isTestingConnection.value) return
        viewModelScope.launch {
            _isTestingConnection.value = true
            try {
                runMonitoringCycleUseCase.run()
                emit("Lectura exitosa: ya se puede ver PV y batería en el Panel")
            } catch (e: Exception) {
                emit("Falló la lectura: ${describeMonitoringError(e)}")
            } finally {
                _isTestingConnection.value = false
            }
        }
    }

    fun testVoiceChannel() {
        viewModelScope.launch {
            val ok = voicePlayer.speak(TEST_MESSAGE)
            emit(if (ok) "Prueba de voz reproducida" else "No se pudo reproducir la voz (revisa el motor de TTS del teléfono)")
        }
    }

    fun testPushChannel() {
        val enabled = NotificationChannels.areNotificationsEnabled(context)
        if (!enabled) {
            emit("Las notificaciones están deshabilitadas para Felicity Watch en Ajustes del sistema")
            return
        }
        val ok = pushNotifier.notifyAlert(
            title = "Felicity Watch (prueba)",
            body = TEST_MESSAGE,
            notificationId = TEST_PUSH_NOTIFICATION_ID
        )
        emit(if (ok) "Notificación de prueba enviada" else "No se pudo enviar la notificación de prueba")
    }

    fun testWhatsappChannel() {
        if (!credentialsStore.hasWhatsappConfig()) {
            emit("Configura primero el número y la apiKey de CallMeBot")
            return
        }
        viewModelScope.launch {
            val result = whatsappSender.send(TEST_MESSAGE)
            emit(
                if (result.success) "Mensaje de prueba enviado por WhatsApp (puede tardar en llegar)"
                else "Falló el envío de WhatsApp: ${result.error}"
            )
        }
    }

    fun logout(onDone: () -> Unit) {
        MonitoringServiceController.stop(context)
        credentialsStore.clearAll()
        onDone()
        emit("Sesión cerrada")
    }

    fun resetToFactoryDefaults(onDone: () -> Unit) {
        viewModelScope.launch {
            MonitoringServiceController.stop(context)
            credentialsStore.clearAll()
            alertRuleRepository.resetToDefaults()
            alertEventRepository.clearAll()
            appPreferences.clearAll()
            felicityRepository.resetDeviceCache()
            onDone()
            emit("La app se restableció a los valores de fábrica")
        }
    }

    /** Dispara una consulta de dispositivos para que el interceptor capture
     * la respuesta cruda de list_device_all_type (RawResponseRecorder no
     * graba nada hasta que esta llamada de red ocurra al menos una vez). */
    fun refreshDeviceListForDiagnostics() {
        if (_isLoadingDeviceList.value) return
        viewModelScope.launch {
            _isLoadingDeviceList.value = true
            try {
                felicityRepository.fetchDevices()
                emit("Respuesta de dispositivos capturada, ya se puede copiar")
            } catch (e: Exception) {
                emit("Falló la consulta de dispositivos: ${describeMonitoringError(e)}")
            } finally {
                _isLoadingDeviceList.value = false
            }
        }
    }

    fun copyDeviceListJsonToClipboard() {
        copyRawJsonToClipboard("listado de dispositivos", rawResponseRecorder.lastDeviceListBody)
    }

    val supabaseMigrationDone: StateFlow<Boolean> = appPreferences.supabaseMigrationDone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val supabaseSyncEnabled: StateFlow<Boolean> = appPreferences.supabaseSyncEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _migrationProgress = MutableStateFlow<MigrationProgress>(MigrationProgress.Idle)
    val migrationProgress: StateFlow<MigrationProgress> = _migrationProgress

    /** Sube todo el historial local a Supabase. Se puede reintentar sin
     * duplicar filas si falla a la mitad (ver [SupabaseSyncRepository.migrateAll]). */
    fun migrateToSupabase() {
        if (_migrationProgress.value is MigrationProgress.InProgress) return
        viewModelScope.launch {
            _migrationProgress.value = MigrationProgress.InProgress(0, 0)
            try {
                supabaseSyncRepository.migrateAll { uploaded, total ->
                    _migrationProgress.value = MigrationProgress.InProgress(uploaded, total)
                }
                _migrationProgress.value = MigrationProgress.Success
                emit("Historial migrado a la nube. Los nuevos datos se guardarán también ahí.")
            } catch (e: Exception) {
                _migrationProgress.value = MigrationProgress.Failed(describeMonitoringError(e))
                emit("Falló la migración: ${describeMonitoringError(e)}")
            }
        }
    }

    fun setSupabaseSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setSupabaseSyncEnabled(enabled)
            emit(if (enabled) "Sincronización con la nube activada" else "Sincronización con la nube desactivada")
        }
    }

    /** Diagnóstico sin USB: copia la última respuesta cruda de Felicity para pegarla donde haga falta. */
    fun copyRawJsonToClipboard(label: String, json: String?) {
        if (json.isNullOrBlank()) {
            emit("Todavía no hay una respuesta de $label registrada")
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, json))
        emit("Respuesta de $label copiada al portapapeles")
    }

    private fun emit(message: String) {
        _messages.tryEmit(message)
    }

    companion object {
        private const val TEST_PUSH_NOTIFICATION_ID = 9001
    }
}
