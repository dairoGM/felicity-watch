package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.domain.model.ComparisonOperator
import java.time.Instant

/**
 * Mismo patrón de debounce por tiempo que [SocThresholdDebouncer], pero para
 * un valor Double (ej. horas de autonomía) en vez de un porcentaje entero.
 */
class DoubleThresholdDebouncer(
    debounceSeconds: Int,
    private val threshold: Double,
    private val operator: ComparisonOperator
) {
    private val debouncer = StateDebouncer(debounceSeconds, false)

    fun onNewReading(value: Double?, now: Instant): Boolean? {
        if (value == null) return null
        val observed = when (operator) {
            ComparisonOperator.GTE -> value >= threshold
            ComparisonOperator.LTE -> value <= threshold
        }
        return debouncer.onObservation(observed, now)?.takeIf { it }
    }
}
