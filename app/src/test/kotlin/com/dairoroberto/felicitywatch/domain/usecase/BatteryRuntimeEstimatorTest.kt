package com.dairoroberto.felicitywatch.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cubre el estimador de autonomía, con foco en el caso que se veía en el
 * Panel: PV y consumo casi empatados producían cifras absurdas de autonomía.
 *
 * Los valores del banco (314Ah, 54V) son los reales del equipo del usuario,
 * para que las cifras de los asserts sean comprobables contra lo que muestra
 * la app.
 */
class BatteryRuntimeEstimatorTest {

    private val capacityAh = 314.0
    private val voltage = 54.0

    @Test
    fun `deficit marginal no reporta autonomia, reporta que el sol cubre el consumo`() {
        // El caso real del Panel: 700W de sol contra 710W de casa. La division
        // directa daba ~1687 horas (70 dias) sobre un deficit de 10W que es
        // puro ruido de medicion.
        val hours = estimateBatteryRuntimeHours(
            socPercent = 99,
            loadWatts = 710,
            capacityAh = capacityAh,
            voltage = voltage,
            pvWatts = 700
        )

        assertEquals(Double.POSITIVE_INFINITY, hours!!, 0.0)
    }

    @Test
    fun `pv que supera el consumo reporta que el sol cubre el consumo`() {
        val hours = estimateBatteryRuntimeHours(
            socPercent = 80,
            loadWatts = 500,
            capacityAh = capacityAh,
            voltage = voltage,
            pvWatts = 900
        )

        assertEquals(Double.POSITIVE_INFINITY, hours!!, 0.0)
    }

    @Test
    fun `deficit sostenido si calcula horas`() {
        // Sin sol y 700W de consumo: 314Ah x 54V x 1.00 = 16956Wh / 700W.
        val hours = estimateBatteryRuntimeHours(
            socPercent = 100,
            loadWatts = 700,
            capacityAh = capacityAh,
            voltage = voltage,
            pvWatts = 0
        )

        assertEquals(16956.0 / 700.0, hours!!, 0.01)
        // Un dato accionable, no 70 dias.
        assertTrue("La autonomia deberia estar en el rango de horas", hours < 48)
    }

    @Test
    fun `el umbral de deficit no descarta un deficit real`() {
        // 60W esta por encima del umbral de ruido: se calcula de verdad.
        val hours = estimateBatteryRuntimeHours(
            socPercent = 100,
            loadWatts = 760,
            capacityAh = capacityAh,
            voltage = voltage,
            pvWatts = 700
        )

        assertEquals(16956.0 / 60.0, hours!!, 0.01)
    }

    @Test
    fun `sin dato de pv todo el consumo recae en la bateria`() {
        val hours = estimateBatteryRuntimeHours(
            socPercent = 50,
            loadWatts = 400,
            capacityAh = capacityAh,
            voltage = voltage,
            pvWatts = null
        )

        assertEquals((16956.0 * 0.5) / 400.0, hours!!, 0.01)
    }

    @Test
    fun `datos faltantes o invalidos devuelven null`() {
        assertNull(estimateBatteryRuntimeHours(null, 400, capacityAh, voltage, 0))
        assertNull(estimateBatteryRuntimeHours(50, null, capacityAh, voltage, 0))
        assertNull(estimateBatteryRuntimeHours(50, 0, capacityAh, voltage, 0))
        assertNull(estimateBatteryRuntimeHours(50, 400, null, voltage, 0))
        assertNull(estimateBatteryRuntimeHours(50, 400, 0.0, voltage, 0))
        assertNull(estimateBatteryRuntimeHours(50, 400, capacityAh, null, 0))
        assertNull(estimateBatteryRuntimeHours(50, 400, capacityAh, 0.0, 0))
    }
}
