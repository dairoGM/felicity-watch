package com.dairoroberto.felicitywatch.service

import com.dairoroberto.felicitywatch.domain.model.BatteryReading
import com.dairoroberto.felicitywatch.domain.model.GridState
import com.dairoroberto.felicitywatch.domain.model.InverterReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puente en memoria entre el Foreground Service (productor) y la UI
 * Compose (consumidor) para reflejar el estado en vivo del panel general
 * sin depender de que la Activity esté abierta cuando ocurre una lectura.
 */
@Singleton
class MonitoringStateHolder @Inject constructor() {
    private val _inverterReading = MutableStateFlow<InverterReading?>(null)
    val inverterReading: StateFlow<InverterReading?> = _inverterReading

    private val _batteryReading = MutableStateFlow<BatteryReading?>(null)
    val batteryReading: StateFlow<BatteryReading?> = _batteryReading

    private val _gridState = MutableStateFlow(GridState.UNKNOWN)
    val gridState: StateFlow<GridState> = _gridState

    private val _lastGridChangeAt = MutableStateFlow<Instant?>(null)
    val lastGridChangeAt: StateFlow<Instant?> = _lastGridChangeAt

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage

    fun updateReadings(inverter: InverterReading?, battery: BatteryReading?) {
        _inverterReading.value = inverter
        _batteryReading.value = battery
        _lastErrorMessage.value = null
    }

    fun updateConfirmedGridState(state: GridState, changedAt: Instant) {
        _gridState.value = state
        _lastGridChangeAt.value = changedAt
    }

    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }

    fun setError(message: String?) {
        _lastErrorMessage.value = message
    }
}
