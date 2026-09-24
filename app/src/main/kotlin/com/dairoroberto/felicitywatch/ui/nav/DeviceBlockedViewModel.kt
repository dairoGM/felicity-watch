package com.dairoroberto.felicitywatch.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dairoroberto.felicitywatch.data.repository.DeviceRoleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceBlockedViewModel @Inject constructor(
    private val deviceRoleRepository: DeviceRoleRepository
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
}
