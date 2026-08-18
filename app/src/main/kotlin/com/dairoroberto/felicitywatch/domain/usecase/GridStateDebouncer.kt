package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.domain.model.GridState
import java.time.Instant

/**
 * Debounce de estado de red (guía sección 5): una potencia por debajo de
 * [wattThreshold] (editable en Alertas, 1 W por defecto) se considera
 * OFFLINE, pero solo se confirma si se mantiene estable durante el tiempo
 * de debounce — el sensor nunca reporta un 0 limpio y fluctúa unos
 * segundos tras un cambio real.
 *
 * Dato AUSENTE ≠ SIN CORRIENTE: cuando [gridPowerWatts] llega null (la API
 * de Felicity omite campos de forma intermitente, o la respuesta viene
 * incompleta) se emite UNKNOWN en vez de OFFLINE. Antes se traducía a
 * OFFLINE y unas cuantas respuestas incompletas seguidas disparaban una
 * alerta de "se fue la corriente" que nunca ocurrió.
 *
 * UNKNOWN además NO reinicia el candidato en curso: si venían 3 lecturas
 * OFFLINE reales y la cuarta llega sin el campo, el conteo del debounce
 * continúa donde estaba en vez de empezar de cero, para no retrasar
 * indefinidamente una alerta legítima cuando la red va y viene.
 */
class GridStateDebouncer(debounceSeconds: Int, private val wattThreshold: Int = 1) {
    private val debouncer = StateDebouncer(debounceSeconds, GridState.UNKNOWN)

    fun onNewReading(gridPowerWatts: Int?, now: Instant): GridState? {
        if (gridPowerWatts == null) return null
        val observed = if (gridPowerWatts < wattThreshold) GridState.OFFLINE else GridState.ONLINE
        return debouncer.onObservation(observed, now)
    }

    fun currentConfirmedState(): GridState = debouncer.currentConfirmedState()
}
