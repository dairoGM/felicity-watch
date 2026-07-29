package com.dairoroberto.felicitywatch.data.remote

import com.dairoroberto.felicitywatch.data.remote.dto.SnapshotResponse
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant

/**
 * Usa una respuesta REAL capturada del servidor de Felicity (cuenta real,
 * ver conversación de soporte) para verificar que el mapeo de PV/SOC
 * funciona contra datos de producción, no solo contra fixtures inventados.
 */
class FelicitySnapshotMapperTest {

    @Test
    fun `real inverter snapshot maps pvPowerWatts and gridPowerWatts`() {
        val json = javaClass.classLoader!!.getResourceAsStream("real_inverter_snapshot.json")!!
            .bufferedReader().readText()

        val response = Gson().fromJson(json, SnapshotResponse::class.java)
        val dataObject = response.dataObject
        assertNotNull("dataObject no debería ser null", dataObject)

        val reading = FelicitySnapshotMapper.toInverterReading("120308004826040053", dataObject!!, Instant.now())

        assertNotNull("pvPowerWatts no debería ser null", reading.pvPowerWatts)
        assertNotNull("gridPowerWatts no debería ser null", reading.gridPowerWatts)
    }
}
