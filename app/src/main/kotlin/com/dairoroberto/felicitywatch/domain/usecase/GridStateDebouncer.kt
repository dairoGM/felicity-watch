package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.domain.model.GridState
import java.time.Instant

/**
 * Direct port of the reference pseudocode (guía sección 5). Grid power
 * arriving as null/unavailable, or below 1 W, is treated as OFFLINE —
 * the sensor never reports a clean 0 and fluctuates for a few seconds
 * after a real change, hence the time debounce instead of a plain threshold.
 */
class GridStateDebouncer(debounceSeconds: Int) {
    private val debouncer = StateDebouncer(debounceSeconds, GridState.UNKNOWN)

    fun onNewReading(gridPowerWatts: Int?, now: Instant): GridState? {
        val observed = if (gridPowerWatts == null || gridPowerWatts < 1) GridState.OFFLINE else GridState.ONLINE
        return debouncer.onObservation(observed, now)
    }

    fun currentConfirmedState(): GridState = debouncer.currentConfirmedState()
}
