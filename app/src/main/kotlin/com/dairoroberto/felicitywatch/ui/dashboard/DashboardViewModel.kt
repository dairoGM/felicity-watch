package com.dairoroberto.felicitywatch.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dairoroberto.felicitywatch.data.local.AlertEventEntity
import com.dairoroberto.felicitywatch.data.repository.AlertEventRepository
import com.dairoroberto.felicitywatch.domain.model.BatteryReading
import com.dairoroberto.felicitywatch.domain.model.GridState
import com.dairoroberto.felicitywatch.domain.model.InverterReading
import com.dairoroberto.felicitywatch.service.MonitoringStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

data class DashboardUiState(
    val gridState: GridState = GridState.UNKNOWN,
    val lastGridChangeAt: Instant? = null,
    val inverter: InverterReading? = null,
    val battery: BatteryReading? = null,
    val serviceRunning: Boolean = false,
    val lastError: String? = null,
    val latestEvent: AlertEventEntity? = null
)

private data class ReadingsSnapshot(
    val gridState: GridState,
    val lastGridChangeAt: Instant?,
    val inverter: InverterReading?,
    val battery: BatteryReading?
)

private data class StatusSnapshot(
    val serviceRunning: Boolean,
    val lastError: String?,
    val latestEvent: AlertEventEntity?
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    stateHolder: MonitoringStateHolder,
    alertEventRepository: AlertEventRepository
) : ViewModel() {

    private val readingsFlow = combine(
        stateHolder.gridState,
        stateHolder.lastGridChangeAt,
        stateHolder.inverterReading,
        stateHolder.batteryReading
    ) { gridState, lastGridChangeAt, inverter, battery ->
        ReadingsSnapshot(gridState, lastGridChangeAt, inverter, battery)
    }

    private val statusFlow = combine(
        stateHolder.serviceRunning,
        stateHolder.lastErrorMessage,
        alertEventRepository.observeEvents()
    ) { running, error, events ->
        StatusSnapshot(running, error, events.firstOrNull())
    }

    val uiState: StateFlow<DashboardUiState> = combine(readingsFlow, statusFlow) { readings, status ->
        DashboardUiState(
            gridState = readings.gridState,
            lastGridChangeAt = readings.lastGridChangeAt,
            inverter = readings.inverter,
            battery = readings.battery,
            serviceRunning = status.serviceRunning,
            lastError = status.lastError,
            latestEvent = status.latestEvent
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())
}
