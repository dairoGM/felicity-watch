package com.dairoroberto.felicitywatch.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dairoroberto.felicitywatch.data.repository.FelicityRepository
import com.dairoroberto.felicitywatch.domain.model.DeviceInfo
import com.dairoroberto.felicitywatch.domain.usecase.describeMonitoringError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DevicesUiState(
    val devices: List<DeviceInfo> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val felicityRepository: FelicityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val devices = felicityRepository.fetchDevices()
                _uiState.value = DevicesUiState(devices = devices, loading = false, error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(loading = false, error = describeMonitoringError(e))
            }
        }
    }
}
