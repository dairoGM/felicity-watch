package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.domain.model.ComparisonOperator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class SocThresholdDebouncerTest {

    private val base: Instant = Instant.parse("2024-01-01T00:00:00Z")

    @Test
    fun `oscillation around threshold does not repeat the alert`() {
        val debouncer = SocThresholdDebouncer(debounceSeconds = 60, threshold = 20.0, operator = ComparisonOperator.LTE)
        var now = base

        // Oscila 19 / 21 / 19 antes de estabilizarse por debajo del umbral.
        assertNull(debouncer.onNewReading(19, now))
        now = now.plusSeconds(10)
        assertNull(debouncer.onNewReading(21, now))
        now = now.plusSeconds(10)
        assertNull(debouncer.onNewReading(19, now))

        now = now.plusSeconds(65)
        val firstTrigger = debouncer.onNewReading(19, now)
        assertEquals(true, firstTrigger)

        // Se mantiene bajo el umbral en las siguientes lecturas: no debe repetirse.
        now = now.plusSeconds(30)
        assertNull(debouncer.onNewReading(18, now))
        now = now.plusSeconds(30)
        assertNull(debouncer.onNewReading(17, now))
    }
}
