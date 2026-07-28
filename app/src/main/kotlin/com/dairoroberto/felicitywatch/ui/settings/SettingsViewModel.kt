package com.dairoroberto.felicitywatch.ui.settings

import androidx.lifecycle.ViewModel
import com.dairoroberto.felicitywatch.data.local.CredentialsStore
import com.dairoroberto.felicitywatch.service.MonitoringStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class SettingsUiState(
    val fsolarUsername: String = "",
    val fsolarPassword: String = "",
    val whatsappPhone: String = "",
    val callMeBotApiKey: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val credentialsStore: CredentialsStore,
    stateHolder: MonitoringStateHolder
) : ViewModel() {

    val serviceRunning: StateFlow<Boolean> = stateHolder.serviceRunning

    private val _formState = MutableStateFlow(
        SettingsUiState(
            fsolarUsername = credentialsStore.fsolarUsername.orEmpty(),
            fsolarPassword = credentialsStore.fsolarPassword.orEmpty(),
            whatsappPhone = credentialsStore.whatsappPhone.orEmpty(),
            callMeBotApiKey = credentialsStore.callMeBotApiKey.orEmpty()
        )
    )
    val formState: StateFlow<SettingsUiState> = _formState

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
        credentialsStore.fsolarUsername = _formState.value.fsolarUsername
        credentialsStore.fsolarPassword = _formState.value.fsolarPassword
    }

    fun saveWhatsappConfig() {
        credentialsStore.whatsappPhone = _formState.value.whatsappPhone
        credentialsStore.callMeBotApiKey = _formState.value.callMeBotApiKey
    }
}
