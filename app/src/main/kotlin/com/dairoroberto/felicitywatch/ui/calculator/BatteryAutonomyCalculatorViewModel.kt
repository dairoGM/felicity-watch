package com.dairoroberto.felicitywatch.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dairoroberto.felicitywatch.service.MonitoringStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class BatteryAutonomyUiState(
    val liveSocPercent: Int? = null,
    val liveLoadWatts: Int? = null,
    val capacityAh: Double? = null,
    val voltage: Double? = null
)

@HiltViewModel
class BatteryAutonomyCalculatorViewModel @Inject constructor(
    stateHolder: MonitoringStateHolder
) : ViewModel() {

    val uiState: StateFlow<BatteryAutonomyUiState> = combine(
        stateHolder.batteryReading,
        stateHolder.inverterReading
    ) { battery, inverter ->
        BatteryAutonomyUiState(
            liveSocPercent = battery?.socPercent,
            liveLoadWatts = inverter?.loadPowerWatts,
            capacityAh = battery?.capacityAh,
            voltage = battery?.voltage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BatteryAutonomyUiState())
}
