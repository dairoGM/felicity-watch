package com.dairoroberto.felicitywatch.service

import com.dairoroberto.felicitywatch.domain.model.BatteryReading
import com.dairoroberto.felicitywatch.domain.model.GridState
import com.dairoroberto.felicitywatch.domain.model.InverterReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Duration
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

    /** Última hora reportada por el propio inversor, para detectar cuándo
     * publica un dato nuevo. */
    private var _lastDeviceReportedAt: Instant? = null

    /**
     * Cada cuántos segundos el inversor publica realmente un dato nuevo en
     * la nube, MEDIDO (null hasta tener dos publicaciones distintas).
     *
     * Es el techo de utilidad del intervalo de consulta: pedir más seguido
     * que esto devuelve el mismo dato repetido.
     */
    private val _inverterPublishIntervalSeconds = MutableStateFlow<Int?>(null)
    val inverterPublishIntervalSeconds: StateFlow<Int?> = _inverterPublishIntervalSeconds

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

    private val _lastInverterRawJson = MutableStateFlow<String?>(null)
    val lastInverterRawJson: StateFlow<String?> = _lastInverterRawJson

    private val _lastBatteryRawJson = MutableStateFlow<String?>(null)
    val lastBatteryRawJson: StateFlow<String?> = _lastBatteryRawJson

    fun updateReadings(
        inverter: InverterReading?,
        battery: BatteryReading?,
        now: Instant,
        inverterError: String? = null,
        batteryError: String? = null,
        inverterRawJson: String? = null,
        batteryRawJson: String? = null
    ) {
        // Cadencia REAL con que el inversor publica en la nube, medida en
        // vez de supuesta: se mira cuándo CAMBIA la hora que el propio
        // equipo reporta (dataTimeStr). Consultar más seguido que esto no
        // trae datos nuevos, solo gasta batería y datos móviles.
        val reportedAt = inverter?.deviceReportedAt
        if (reportedAt != null && reportedAt != _lastDeviceReportedAt) {
            val previous = _lastDeviceReportedAt
            if (previous != null) {
                val gapSeconds = Duration.between(previous, reportedAt).seconds
                // Se ignoran saltos absurdos (relojes desfasados, equipo que
                // estuvo desconectado y vuelve con un hueco de horas).
                if (gapSeconds in 1..MAX_PLAUSIBLE_PUBLISH_GAP_SECONDS) {
                    _inverterPublishIntervalSeconds.value = gapSeconds.toInt()
                }
            }
            _lastDeviceReportedAt = reportedAt
        }

        _inverterReading.value = inverter
        _batteryReading.value = battery
        _inverterError.value = inverterError
        _batteryError.value = batteryError
        if (inverterRawJson != null) _lastInverterRawJson.value = inverterRawJson
        if (batteryRawJson != null) _lastBatteryRawJson.value = batteryRawJson
        _lastErrorMessage.value = null
        _lastSuccessfulReadingAt.value = now
        _consecutiveFailures.value = 0

        // Dato AUSENTE ≠ SIN CORRIENTE: si el equipo no reportó la potencia
        // de red en este ciclo (la API omite campos de forma intermitente),
        // se CONSERVA el último estado conocido en vez de caer a OFFLINE.
        // Antes cualquier respuesta incompleta hacía que el Panel dijera
        // "Sin corriente eléctrica" aunque la corriente nunca se fue.
        val gridPower = inverter?.gridPowerWatts
        if (gridPower != null) {
            _liveGridState.value = if (gridPower < 1) GridState.OFFLINE else GridState.ONLINE
        }
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

    private companion object {
        /** Más de esto entre dos publicaciones no es la cadencia del equipo
         * sino un hueco (equipo apagado, sin WiFi por un corte). */
        const val MAX_PLAUSIBLE_PUBLISH_GAP_SECONDS = 900L
    }
}
