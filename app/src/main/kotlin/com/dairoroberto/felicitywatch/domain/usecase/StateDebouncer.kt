package com.dairoroberto.felicitywatch.domain.usecase

import java.time.Duration
import java.time.Instant

/**
 * Generic time-based debounce state machine, ported from the reference
 * pseudocode (guía sección 5). A new observed state is only confirmed
 * (and returned) once it has remained stable for [debounceSeconds]
 * consecutive seconds without reverting to a different candidate value.
 *
 * Anti-duplicate guarantee: once a state is confirmed, it will not be
 * returned again until the observed value changes to something else and
 * stabilizes for [debounceSeconds] again.
 */
class StateDebouncer<T>(
    private val debounceSeconds: Int,
    initialConfirmedState: T
) {
    private var candidateState: T? = null
    private var candidateSince: Instant? = null
    private var confirmedState: T = initialConfirmedState

    fun currentConfirmedState(): T = confirmedState

    fun onObservation(observed: T, now: Instant): T? {
        if (observed != candidateState) {
            candidateState = observed
            candidateSince = now
            return null
        }

        val since = candidateSince ?: now
        val elapsed = Duration.between(since, now).seconds
        if (elapsed >= debounceSeconds && observed != confirmedState) {
            confirmedState = observed
            return observed
        }
        return null
    }
}
