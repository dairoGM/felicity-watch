package com.dairoroberto.felicitywatch.ui.onboarding

import androidx.lifecycle.ViewModel
import com.dairoroberto.felicitywatch.data.local.CredentialsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class OnboardingFormState(
    val username: String = "",
    val password: String = "",
    val whatsappPhone: String = "",
    val callMeBotApiKey: String = ""
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val credentialsStore: CredentialsStore
) : ViewModel() {

    private val _formState = MutableStateFlow(OnboardingFormState())
    val formState: StateFlow<OnboardingFormState> = _formState

    fun onUsernameChange(value: String) {
        _formState.value = _formState.value.copy(username = value)
    }

    fun onPasswordChange(value: String) {
        _formState.value = _formState.value.copy(password = value)
    }

    fun onWhatsappPhoneChange(value: String) {
        _formState.value = _formState.value.copy(whatsappPhone = value)
    }

    fun onApiKeyChange(value: String) {
        _formState.value = _formState.value.copy(callMeBotApiKey = value)
    }

    fun canProceedFromLogin(): Boolean =
        _formState.value.username.isNotBlank() && _formState.value.password.isNotBlank()

    fun saveCredentials() {
        val state = _formState.value
        credentialsStore.fsolarUsername = state.username
        credentialsStore.fsolarPassword = state.password
        if (state.whatsappPhone.isNotBlank() && state.callMeBotApiKey.isNotBlank()) {
            credentialsStore.whatsappPhone = state.whatsappPhone
            credentialsStore.callMeBotApiKey = state.callMeBotApiKey
        }
    }
}
