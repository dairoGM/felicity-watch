package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.domain.model.ComparisonOperator
import java.time.Instant

/**
 * Same time-debounce pattern as [GridStateDebouncer], applied to a SOC
 * threshold crossing so a value oscillating right around the threshold
 * (e.g. 19% / 21% / 19%) does not repeat the alert.
 */
class SocThresholdDebouncer(
    debounceSeconds: Int,
    private val threshold: Double,
    private val operator: ComparisonOperator
) {
    private val debouncer = StateDebouncer(debounceSeconds, false)

    fun onNewReading(socPercent: Int?, now: Instant): Boolean? {
        if (socPercent == null) return null
        val observed = when (operator) {
            ComparisonOperator.GTE -> socPercent >= threshold
            ComparisonOperator.LTE -> socPercent <= threshold
        }
        return debouncer.onObservation(observed, now)?.takeIf { it }
    }
}
