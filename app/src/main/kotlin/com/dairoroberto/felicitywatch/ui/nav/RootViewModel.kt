package com.dairoroberto.felicitywatch.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dairoroberto.felicitywatch.data.local.CredentialsStore
import com.dairoroberto.felicitywatch.domain.usecase.DeviceAccessDecision
import com.dairoroberto.felicitywatch.domain.usecase.EvaluateDeviceApprovalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado de acceso de este dispositivo, evaluado una vez al arrancar. */
sealed class DeviceAccessState {
    data object Checking : DeviceAccessState()
    data object Allowed : DeviceAccessState()
    data object Blocked : DeviceAccessState()
}

@HiltViewModel
class RootViewModel @Inject constructor(
    private val credentialsStore: CredentialsStore,
    private val evaluateDeviceApprovalUseCase: EvaluateDeviceApprovalUseCase
) : ViewModel() {

    private val _onboardingCompleted = MutableStateFlow(credentialsStore.hasFsolarCredentials())
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted

    private val _deviceAccessState = MutableStateFlow<DeviceAccessState>(DeviceAccessState.Checking)
    val deviceAccessState: StateFlow<DeviceAccessState> = _deviceAccessState

    fun completeOnboarding() {
        _onboardingCompleted.value = true
        checkDeviceAccess()
    }

    /** Usado por cierre de sesión / restablecimiento de fábrica en Ajustes. */
    fun resetOnboarding() {
        _onboardingCompleted.value = false
    }

    init {
        if (_onboardingCompleted.value) checkDeviceAccess()
    }

    fun checkDeviceAccess() {
        viewModelScope.launch {
            _deviceAccessState.value = DeviceAccessState.Checking
            _deviceAccessState.value = when (evaluateDeviceApprovalUseCase.evaluate()) {
                DeviceAccessDecision.Allowed -> DeviceAccessState.Allowed
                DeviceAccessDecision.Blocked -> DeviceAccessState.Blocked
            }
        }
    }
}
