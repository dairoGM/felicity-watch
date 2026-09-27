package com.dairoroberto.felicitywatch.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dairoroberto.felicitywatch.data.repository.DeviceRoleRepository
import com.dairoroberto.felicitywatch.domain.usecase.UpdateDeviceLocationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceBlockedViewModel @Inject constructor(
    private val deviceRoleRepository: DeviceRoleRepository,
    private val updateDeviceLocationUseCase: UpdateDeviceLocationUseCase
) : ViewModel() {

    private val _isRedeeming = MutableStateFlow(false)
    val isRedeeming: StateFlow<Boolean> = _isRedeeming

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    /** [onApproved] se llama solo si el PIN era válido — el caller (nivel de
     * navegación) decide qué hacer, típicamente re-evaluar el acceso del
     * dispositivo para salir de esta pantalla. */
    fun redeem(pin: String, onApproved: () -> Unit) {
        if (_isRedeeming.value) return
        viewModelScope.launch {
            _isRedeeming.value = true
            _errorMessage.value = null
            try {
                val approved = deviceRoleRepository.redeemPairingPin(pin)
                if (approved) {
                    onApproved()
                } else {
                    _errorMessage.value = "Código incorrecto, ya usado o expirado"
                }
            } catch (e: Exception) {
                _errorMessage.value = "No se pudo verificar: ${e.message}"
            } finally {
                _isRedeeming.value = false
            }
        }
    }

    /**
     * Captura y reporta la ubicación de inmediato, sin esperar al primer
     * ciclo del servicio de monitoreo — que en este momento (justo tras
     * canjear el código) todavía ni arrancó, ya que EvaluateDeviceApprovalUseCase
     * solo lo inicia una vez que la navegación sale de esta pantalla. Sin
     * este disparo manual, el permiso quedaba concedido pero la primera
     * ubicación real tardaba hasta que el servicio arrancara y corriera un
     * ciclo completo. Si este intento falla (permiso recién concedido, GPS
     * lento), no queda perdido: al no haber capturado con éxito todavía, no
     * hay throttle que lo bloquee, así que el primer ciclo del servicio lo
     * reintenta sin demora.
     */
    fun captureLocationNow() {
        viewModelScope.launch {
            updateDeviceLocationUseCase.run()
        }
    }
}
