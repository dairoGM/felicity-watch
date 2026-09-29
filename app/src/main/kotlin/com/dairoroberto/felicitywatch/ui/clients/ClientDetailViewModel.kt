package com.dairoroberto.felicitywatch.ui.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dairoroberto.felicitywatch.data.local.PowerReadingEntity
import com.dairoroberto.felicitywatch.data.remote.dto.SupabasePowerReadingDto
import com.dairoroberto.felicitywatch.data.repository.SupabaseSyncRepository
import com.dairoroberto.felicitywatch.domain.model.BatteryReading
import com.dairoroberto.felicitywatch.domain.model.GridState
import com.dairoroberto.felicitywatch.domain.model.InverterReading
import com.dairoroberto.felicitywatch.ui.dashboard.DashboardUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Estado de la pantalla de detalle de un cliente — Loading/Error/NoData
 * envuelven a [DashboardUiState] (ver Loaded), que es EXACTAMENTE lo que ya
 * consume DashboardContent (el mismo cuerpo visual del Panel del propio
 * cliente): sin necesitar sus credenciales de FSolar, se reconstruye a
 * partir de las lecturas que el propio cliente ya sube a Supabase con su
 * device_id (requiere que ese cliente tenga la sincronización activada —
 * forzada para cualquier cliente, ver PowerHistoryRepository.record).
 */
sealed class ClientDetailState {
    data object Loading : ClientDetailState()
    data object NoData : ClientDetailState()
    data class Loaded(val uiState: DashboardUiState) : ClientDetailState()
    data class Error(val message: String) : ClientDetailState()
}

@HiltViewModel
class ClientDetailViewModel @Inject constructor(
    private val supabaseSyncRepository: SupabaseSyncRepository
) : ViewModel() {

    private val _state = MutableStateFlow<ClientDetailState>(ClientDetailState.Loading)
    val state: StateFlow<ClientDetailState> = _state

    fun load(deviceId: String) {
        _state.value = ClientDetailState.Loading
        viewModelScope.launch {
            try {
                // Mismo límite que el historial del propio Panel
                // (allReadingsInRetention no tiene tope explícito ahí porque
                // lee de Room directo; aquí sí hace falta uno porque es una
                // consulta de red) — 500 alcanza para varias horas incluso
                // con polling agresivo, suficiente para los sparklines de
                // "hoy" y el tramo de red vigente.
                val readings = supabaseSyncRepository.fetchReadingsForDevice(deviceId, limit = 500)
                if (readings.isEmpty()) {
                    _state.value = ClientDetailState.NoData
                    return@launch
                }

                val entities = readings.map { it.toEntity() }
                val last = entities.last()
                val online = (last.gridPowerWatts ?: 0) >= 1

                _state.value = ClientDetailState.Loaded(
                    DashboardUiState(
                        liveGridState = if (last.gridPowerWatts != null) {
                            if (online) GridState.ONLINE else GridState.OFFLINE
                        } else {
                            GridState.UNKNOWN
                        },
                        confirmedGridState = if (last.gridPowerWatts != null) {
                            if (online) GridState.ONLINE else GridState.OFFLINE
                        } else {
                            GridState.UNKNOWN
                        },
                        inverter = last.toInverterReading(deviceId),
                        battery = last.toBatteryReading(deviceId),
                        // connectionHealthy se deriva de consecutiveFailures==0
                        // && lastError==null — un cliente remoto no tiene un
                        // "fallo de conexión" propio que reportar aquí, solo
                        // datos ya subidos, así que siempre se muestra sano.
                        consecutiveFailures = 0,
                        lastError = null,
                        lastSuccessfulReadingAt = Instant.ofEpochMilli(last.timestampEpochMillis),
                        allReadingsInRetention = entities
                    )
                )
            } catch (e: Exception) {
                _state.value = ClientDetailState.Error(e.message ?: "No se pudo consultar")
            }
        }
    }
}

private fun SupabasePowerReadingDto.toEntity() = PowerReadingEntity(
    timestampEpochMillis = timestampEpochMillis,
    pvPowerWatts = pvPowerWatts,
    gridPowerWatts = gridPowerWatts,
    socPercent = socPercent,
    loadPowerWatts = loadPowerWatts,
    batteryPowerWatts = batteryPowerWatts,
    pvEnergyTodayKwh = pvEnergyTodayKwh,
    loadEnergyTodayKwh = loadEnergyTodayKwh,
    gridVoltage = gridVoltage,
    outputVoltage = outputVoltage,
    batteryVoltage = batteryVoltage,
    batteryCurrent = batteryCurrent,
    batteryCapacityAh = batteryCapacityAh
)

private fun PowerReadingEntity.toInverterReading(deviceId: String) = InverterReading(
    timestamp = Instant.ofEpochMilli(timestampEpochMillis),
    serialNumber = deviceId,
    gridPowerWatts = gridPowerWatts,
    pvPowerWatts = pvPowerWatts,
    loadPowerWatts = loadPowerWatts,
    pvEnergyTodayKwh = pvEnergyTodayKwh,
    loadEnergyTodayKwh = loadEnergyTodayKwh,
    gridVoltage = gridVoltage,
    outputVoltage = outputVoltage,
    deviceReportedAt = Instant.ofEpochMilli(timestampEpochMillis)
)

private fun PowerReadingEntity.toBatteryReading(deviceId: String) = BatteryReading(
    timestamp = Instant.ofEpochMilli(timestampEpochMillis),
    serialNumber = deviceId,
    socPercent = socPercent,
    voltage = batteryVoltage,
    current = batteryCurrent,
    healthPercent = null,
    capacityAh = batteryCapacityAh,
    deviceReportedAt = Instant.ofEpochMilli(timestampEpochMillis),
    powerWatts = batteryPowerWatts?.toDouble()
)
