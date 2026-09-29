package com.dairoroberto.felicitywatch.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dairoroberto.felicitywatch.data.repository.DeviceRoleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Envío del ID de transferencia desde un cliente con el periodo free vencido,
 * y consulta de si el master ya decidió.
 */
@HiltViewModel
class LicenseTransferViewModel @Inject constructor(
    private val deviceRoleRepository: DeviceRoleRepository
) : ViewModel() {

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    /**
     * Declara el comprobante. [onDone] corre solo si el envío llegó a
     * Supabase: la pantalla debe seguir mostrando el formulario si falló, o el
     * usuario creería que su transferencia está en revisión cuando no lo está.
     */
    fun submit(reference: String, onDone: () -> Unit) {
        val trimmed = reference.trim()
        if (trimmed.isBlank()) {
            _errorMessage.value = "Escribe el ID de la transferencia"
            return
        }
        if (_isSubmitting.value) return

        viewModelScope.launch {
            _isSubmitting.value = true
            _errorMessage.value = null
            try {
                if (deviceRoleRepository.submitTransferReference(trimmed)) {
                    onDone()
                } else {
                    _errorMessage.value = "No se pudo enviar. Revisa tu conexión e inténtalo de nuevo."
                }
            } catch (e: Exception) {
                _errorMessage.value = "No se pudo enviar. Revisa tu conexión e inténtalo de nuevo."
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    /** Re-consulta el estado: el master pudo haber decidido mientras esta
     * pantalla estaba abierta. */
    fun refresh(onDone: () -> Unit) {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                onDone()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
