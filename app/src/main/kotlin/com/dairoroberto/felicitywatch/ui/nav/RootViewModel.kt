package com.dairoroberto.felicitywatch.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dairoroberto.felicitywatch.data.local.AppPreferences
import com.dairoroberto.felicitywatch.data.local.CredentialsStore
import com.dairoroberto.felicitywatch.domain.usecase.DeviceAccessDecision
import com.dairoroberto.felicitywatch.domain.model.LicenseState
import com.dairoroberto.felicitywatch.domain.usecase.EvaluateDeviceApprovalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado de acceso de este dispositivo, evaluado una vez al arrancar. */
sealed class DeviceAccessState {
    data object Checking : DeviceAccessState()
    data object Allowed : DeviceAccessState()

    /** Falta la aprobación del dispositivo: debe canjear un código. */
    data object Blocked : DeviceAccessState()

    /** Venció el periodo free: debe declarar el ID de su transferencia. */
    data class TransferRequired(
        val license: LicenseState,
        val freePeriodDays: Int
    ) : DeviceAccessState()

    /** Agotó los intentos de validación; solo el master lo desbloquea. */
    data class LicenseBlocked(val license: LicenseState) : DeviceAccessState()
}

@HiltViewModel
class RootViewModel @Inject constructor(
    private val credentialsStore: CredentialsStore,
    private val evaluateDeviceApprovalUseCase: EvaluateDeviceApprovalUseCase,
    private val appPreferences: AppPreferences,
    private val stateHolder: com.dairoroberto.felicitywatch.service.MonitoringStateHolder
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

        // El flag de arriba solo cubre la REVOCACIÓN del dispositivo. El
        // vencimiento de la licencia no escribe ningún flag, así que el ciclo
        // de monitoreo emite esta señal al detectarlo y aquí se re-evalúa: es
        // lo que hace que el cliente pase solo a la pantalla de transferencia,
        // sin tener que cerrar y reabrir la app.
        //
        // Se re-evalúa en vez de fijar un estado concreto porque la señal no
        // dice CUÁL es el estado nuevo (pedir transferencia, o bloqueado por
        // intentos agotados); eso lo resuelve EvaluateDeviceApprovalUseCase.
        viewModelScope.launch {
            stateHolder.accessRevalidationTick
                .drop(1) // el valor inicial no es un evento
                .collect {
                    if (appPreferences.isMasterDevice.first()) return@collect
                    checkDeviceAccess(showCheckingState = false)
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
            _deviceAccessState.value = when (val decision = evaluateDeviceApprovalUseCase.evaluate()) {
                DeviceAccessDecision.Allowed -> DeviceAccessState.Allowed
                DeviceAccessDecision.Blocked -> DeviceAccessState.Blocked
                is DeviceAccessDecision.TransferRequired ->
                    DeviceAccessState.TransferRequired(decision.license, decision.freePeriodDays)
                is DeviceAccessDecision.LicenseBlocked ->
                    DeviceAccessState.LicenseBlocked(decision.license)
            }
        }
    }
}
