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
 *
 * Distingue dos nociones de "estado de red" a propósito:
 * - [liveGridState]: lo que dice la última lectura cruda, sin debounce —
 *   es lo que se muestra en el Panel para que nunca contradiga la realidad.
 * - [confirmedGridState] / [lastGridChangeAt]: el estado ya debounced que
 *   efectivamente disparó una alerta (guía sección 5), usado solo para el
 *   texto "Último cambio hace X".
 */
@Singleton
class MonitoringStateHolder @Inject constructor() {
    private val _inverterReading = MutableStateFlow<InverterReading?>(null)
    val inverterReading: StateFlow<InverterReading?> = _inverterReading

    private val _batteryReading = MutableStateFlow<BatteryReading?>(null)
    val batteryReading: StateFlow<BatteryReading?> = _batteryReading

    private val _liveGridState = MutableStateFlow(GridState.UNKNOWN)
    val liveGridState: StateFlow<GridState> = _liveGridState

    private val _confirmedGridState = MutableStateFlow(GridState.UNKNOWN)
    val confirmedGridState: StateFlow<GridState> = _confirmedGridState

    private val _lastGridChangeAt = MutableStateFlow<Instant?>(null)
    val lastGridChangeAt: StateFlow<Instant?> = _lastGridChangeAt

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage

    private val _lastSuccessfulReadingAt = MutableStateFlow<Instant?>(null)
    val lastSuccessfulReadingAt: StateFlow<Instant?> = _lastSuccessfulReadingAt

    private val _consecutiveFailures = MutableStateFlow(0)
    val consecutiveFailures: StateFlow<Int> = _consecutiveFailures

    private val _inverterError = MutableStateFlow<String?>(null)
    val inverterError: StateFlow<String?> = _inverterError

    private val _batteryError = MutableStateFlow<String?>(null)
    val batteryError: StateFlow<String?> = _batteryError

    fun updateReadings(
        inverter: InverterReading?,
        battery: BatteryReading?,
        now: Instant,
        inverterError: String? = null,
        batteryError: String? = null
    ) {
        _inverterReading.value = inverter
        _batteryReading.value = battery
        _inverterError.value = inverterError
        _batteryError.value = batteryError
        _lastErrorMessage.value = null
        _lastSuccessfulReadingAt.value = now
        _consecutiveFailures.value = 0

        val gridPower = inverter?.gridPowerWatts
        _liveGridState.value = if (gridPower == null || gridPower < 1) GridState.OFFLINE else GridState.ONLINE
    }

    fun updateConfirmedGridState(state: GridState, changedAt: Instant) {
        _confirmedGridState.value = state
        _lastGridChangeAt.value = changedAt
    }

    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }

    /**
     * Registra un fallo de lectura, sea del ciclo automático del servicio o
     * de una lectura manual (Panel/Ajustes) — cualquier llamador incrementa
     * el mismo contador compartido, que [updateReadings] resetea a 0 en el
     * próximo éxito, venga de donde venga.
     */
    fun reportFailure(message: String?) {
        _lastErrorMessage.value = message
        _consecutiveFailures.value = _consecutiveFailures.value + 1
    }
}
