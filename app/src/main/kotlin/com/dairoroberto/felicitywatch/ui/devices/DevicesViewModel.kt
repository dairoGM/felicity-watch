package com.dairoroberto.felicitywatch.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dairoroberto.felicitywatch.data.repository.FelicityRepository
import com.dairoroberto.felicitywatch.domain.model.BatteryReading
import com.dairoroberto.felicitywatch.domain.model.DeviceInfo
import com.dairoroberto.felicitywatch.domain.model.InverterReading
import com.dairoroberto.felicitywatch.domain.model.PlantInfo
import com.dairoroberto.felicitywatch.domain.usecase.describeMonitoringError
import com.dairoroberto.felicitywatch.service.MonitoringStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DevicesUiState(
    val devices: List<DeviceInfo> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val inverterReading: InverterReading? = null,
    val batteryReading: BatteryReading? = null
) {
    /** Agrupa por plantId (calcado de la web de Felicity: la planta es la
     * fila principal, los equipos cuelgan de ella) — dispositivos sin
     * plantId van a una planta "desconocida" en vez de perderse. */
    val plants: List<PlantInfo> get() = devices
        .groupBy { it.plantId ?: it.plantName ?: "unknown" }
        .map { (_, devicesInPlant) ->
            val first = devicesInPlant.first()
            PlantInfo(
                plantId = first.plantId ?: "unknown",
                plantName = first.plantName ?: "Planta sin nombre",
                ownerName = first.ownerName,
                countryName = first.countryName,
                ratedPowerKw = devicesInPlant.firstNotNullOfOrNull { it.ratedPowerKw },
                devices = devicesInPlant
            )
        }
}

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val felicityRepository: FelicityRepository,
    stateHolder: MonitoringStateHolder
) : ViewModel() {

    private val _devicesState = MutableStateFlow(DevicesUiState())

    val uiState: StateFlow<DevicesUiState> = combine(
        _devicesState,
        stateHolder.inverterReading,
        stateHolder.batteryReading
    ) { devicesState, inverter, battery ->
        devicesState.copy(inverterReading = inverter, batteryReading = battery)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DevicesUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _devicesState.value = _devicesState.value.copy(loading = true, error = null)
            try {
                val devices = felicityRepository.fetchDevices()
                _devicesState.value = _devicesState.value.copy(devices = devices, loading = false, error = null)
            } catch (e: Exception) {
                _devicesState.value = _devicesState.value.copy(loading = false, error = describeMonitoringError(e))
            }
        }
    }
}
