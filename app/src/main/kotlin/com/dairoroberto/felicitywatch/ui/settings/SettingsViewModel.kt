package com.dairoroberto.felicitywatch.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dairoroberto.felicitywatch.data.local.AppPreferences
import com.dairoroberto.felicitywatch.data.local.CredentialsStore
import com.dairoroberto.felicitywatch.data.repository.AlertEventRepository
import com.dairoroberto.felicitywatch.data.repository.AlertRuleRepository
import com.dairoroberto.felicitywatch.data.repository.FelicityRepository
import com.dairoroberto.felicitywatch.domain.usecase.RunMonitoringCycleUseCase
import com.dairoroberto.felicitywatch.domain.usecase.describeMonitoringError
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
import kotlinx.coroutines.flow.StateFlow
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
    private val pushNotifier: PushNotifier,
    private val whatsappSender: WhatsappAlertSender,
    private val runMonitoringCycleUseCase: RunMonitoringCycleUseCase,
    stateHolder: MonitoringStateHolder,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val serviceRunning: StateFlow<Boolean> = stateHolder.serviceRunning

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

    private fun emit(message: String) {
        _messages.tryEmit(message)
    }

    companion object {
        private const val TEST_PUSH_NOTIFICATION_ID = 9001
    }
}
