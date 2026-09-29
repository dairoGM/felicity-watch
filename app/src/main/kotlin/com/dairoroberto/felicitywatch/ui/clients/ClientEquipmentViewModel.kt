package com.dairoroberto.felicitywatch.ui.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dairoroberto.felicitywatch.data.repository.EquipmentRepository
import com.dairoroberto.felicitywatch.domain.usecase.describeMonitoringError
import com.dairoroberto.felicitywatch.ui.devices.DevicesUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Equipos (inversor/batería) de UN cliente puntual para la master — mismo
 * DevicesUiState que ya consume DevicesContent (el cuerpo visual real de la
 * pantalla Equipos), reconstruido desde el snapshot que ese cliente ya
 * sincroniza a Supabase (ver EquipmentSyncUseCase), sin necesitar sus
 * credenciales de FSolar. Sin lecturas en vivo de inverter/battery (esas
 * solo existen en el propio teléfono del cliente): los cards de Potencia
 * FV/SOC dentro de Equipos quedan en "—" aquí — el detalle de esas métricas
 * ya está en la pestaña Panel, con datos reales del historial sincronizado.
 */
@HiltViewModel
class ClientEquipmentViewModel @Inject constructor(
    private val equipmentRepository: EquipmentRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DevicesUiState(loading = true))
    val state: StateFlow<DevicesUiState> = _state

    fun load(deviceId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val devices = equipmentRepository.fetchEquipmentForDevice(deviceId)
                _state.value = _state.value.copy(
                    devices = devices.orEmpty(),
                    loading = false,
                    error = if (devices == null) {
                        "Este dispositivo todavía no reportó sus equipos. Debe tener la " +
                            "\"Sincronización en la nube\" activada en su propio Ajustes > Sistema."
                    } else null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = describeMonitoringError(e))
            }
        }
    }
}
