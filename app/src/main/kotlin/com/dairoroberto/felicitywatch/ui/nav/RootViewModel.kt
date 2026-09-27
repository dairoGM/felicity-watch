package com.dairoroberto.felicitywatch.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dairoroberto.felicitywatch.data.local.AppPreferences
import com.dairoroberto.felicitywatch.data.local.CredentialsStore
import com.dairoroberto.felicitywatch.domain.usecase.DeviceAccessDecision
import com.dairoroberto.felicitywatch.domain.usecase.EvaluateDeviceApprovalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
    private val evaluateDeviceApprovalUseCase: EvaluateDeviceApprovalUseCase,
    private val appPreferences: AppPreferences
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

        // El ciclo de monitoreo (RunMonitoringCycleUseCase) revalida la
        // aprobación en CADA lectura, con la misma cadencia que consulta a
        // Felicity — no solo al reabrir la app (ON_RESUME). Al detectar una
        // revocación/eliminación, escribe clientApprovalConfirmed=false de
        // inmediato; observar ese flag aquí saca a un cliente ya revocado de
        // la pantalla en uso sin esperar a que la vuelva a abrir.
        //
        // clientApprovalConfirmed nunca se toca para la master (su default
        // en DataStore es false) — sin el chequeo de isMasterDevice, el
        // primer valor del flow bloquearía al master apenas arranca.
        viewModelScope.launch {
            appPreferences.clientApprovalConfirmed.collect { confirmed ->
                if (confirmed) return@collect
                if (appPreferences.isMasterDevice.first()) return@collect
                if (_deviceAccessState.value == DeviceAccessState.Allowed) {
                    _deviceAccessState.value = DeviceAccessState.Blocked
                }
            }
        }
    }

    /**
     * [showCheckingState] se omite en las revalidaciones de fondo (cada
     * ON_RESUME, ver FelicityWatchNavHost) para no tapar la pantalla con un
     * spinner cada vez que el usuario vuelve a la app — solo se usa en el
     * chequeo inicial y tras canjear un código, donde sí hay una pantalla de
     * espera dedicada.
     */
    fun checkDeviceAccess(showCheckingState: Boolean = true) {
        viewModelScope.launch {
            if (showCheckingState) _deviceAccessState.value = DeviceAccessState.Checking
            _deviceAccessState.value = when (evaluateDeviceApprovalUseCase.evaluate()) {
                DeviceAccessDecision.Allowed -> DeviceAccessState.Allowed
                DeviceAccessDecision.Blocked -> DeviceAccessState.Blocked
            }
        }
    }
}
