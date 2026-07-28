package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.domain.model.GridState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * Cubre los criterios de aceptación de la guía sección 9 (#1 y #2):
 * fluctuaciones cortas no deben disparar nada, un cambio real y sostenido
 * dispara exactamente una vez, y lecturas repetidas en el mismo estado
 * confirmado no vuelven a disparar.
 */
class GridStateDebouncerTest {

    private val base: Instant = Instant.parse("2024-01-01T00:00:00Z")

    @Test
    fun `short fluctuations produce no trigger, sustained change triggers exactly once`() {
        val debouncer = GridStateDebouncer(debounceSeconds = 60)
        var now = base

        // Baseline: la red está confirmada ONLINE desde antes.
        assertNull(debouncer.onNewReading(100, now))
        now = now.plusSeconds(61)
        assertEquals(GridState.ONLINE, debouncer.onNewReading(100, now))

        val triggersDuringFluctuations = mutableListOf<GridState?>()

        // 3 fluctuaciones cortas (menos de 60s cada una): breve caída a null
        // seguida de recuperación, ninguna debe confirmar OFFLINE.
        repeat(3) {
            now = now.plusSeconds(5)
            triggersDuringFluctuations += debouncer.onNewReading(null, now)
            now = now.plusSeconds(5)
            triggersDuringFluctuations += debouncer.onNewReading(100, now)
        }

        assertEquals(
            "Ninguna fluctuación corta debe producir un disparo",
            emptyList<GridState?>(),
            triggersDuringFluctuations.filterNotNull()
        )

        // Cambio real y sostenido: corte que se mantiene más de 60s.
        now = now.plusSeconds(5)
        assertNull(debouncer.onNewReading(null, now))
        now = now.plusSeconds(65)
        val realTrigger = debouncer.onNewReading(null, now)

        assertEquals(GridState.OFFLINE, realTrigger)
    }

    @Test
    fun `ten consecutive readings in the same confirmed state trigger only once`() {
        val debouncer = GridStateDebouncer(debounceSeconds = 60)
        var now = base

        assertNull(debouncer.onNewReading(100, now))
        now = now.plusSeconds(61)
        assertEquals(GridState.ONLINE, debouncer.onNewReading(100, now))

        var triggerCount = 0
        repeat(10) {
            now = now.plusSeconds(30)
            if (debouncer.onNewReading(null, now) != null) triggerCount++
        }

        assertEquals(1, triggerCount)
        assertEquals(GridState.OFFLINE, debouncer.currentConfirmedState())
    }
}
