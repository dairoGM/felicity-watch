package com.dairoroberto.felicitywatch.ui.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dairoroberto.felicitywatch.data.local.PowerReadingEntity
import com.dairoroberto.felicitywatch.data.remote.dto.SupabasePowerReadingDto
import com.dairoroberto.felicitywatch.data.repository.SupabaseSyncRepository
import com.dairoroberto.felicitywatch.domain.usecase.buildGridSegments
import com.dairoroberto.felicitywatch.ui.components.GridSegment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** Estado consolidado de un cliente para la pantalla de detalle — sin
 * necesitar sus credenciales de FSolar: se arma a partir de las lecturas que
 * el propio cliente ya sube a Supabase con su device_id (requiere que ese
 * cliente tenga la sincronización activada; ver PowerHistoryRepository). */
sealed class ClientDetailState {
    data object Loading : ClientDetailState()
    data class NoData(val syncMaybeDisabled: Boolean) : ClientDetailState()
    data class Loaded(
        val pvPowerWatts: Int?,
        val loadPowerWatts: Int?,
        val socPercent: Int?,
        val online: Boolean,
        val gridStateSinceEpochMillis: Long?,
        val lastReadingAt: Instant
    ) : ClientDetailState()
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
                val readings = supabaseSyncRepository.fetchReadingsForDevice(deviceId)
                if (readings.isEmpty()) {
                    _state.value = ClientDetailState.NoData(syncMaybeDisabled = true)
                    return@launch
                }

                val last = readings.last()
                val now = Instant.now().toEpochMilli()
                val entities = readings.map { it.toEntity() }
                val segments = buildGridSegments(entities, now)
                val lastSegment = segments.lastOrNull()

                _state.value = ClientDetailState.Loaded(
                    pvPowerWatts = last.pvPowerWatts,
                    loadPowerWatts = last.loadPowerWatts,
                    socPercent = last.socPercent,
                    online = (last.gridPowerWatts ?: 0) >= 1,
                    gridStateSinceEpochMillis = lastSegment?.startEpochMillis,
                    lastReadingAt = Instant.ofEpochMilli(last.timestampEpochMillis)
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
    outputVoltage = outputVoltage
)
