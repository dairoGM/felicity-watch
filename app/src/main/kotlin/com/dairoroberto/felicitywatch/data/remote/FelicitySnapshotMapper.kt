package com.dairoroberto.felicitywatch.data.remote

import com.dairoroberto.felicitywatch.domain.model.BatteryReading
import com.dairoroberto.felicitywatch.domain.model.InverterReading
import com.google.gson.JsonObject
import java.time.Instant

/**
 * Puerto directo del helper `_first()` de coordinator.py (felicityAPI):
 * toma el primer valor no nulo/vacío/"unknown"/"unavailable"/"null" entre
 * varios nombres de campo candidatos, porque el mismo dato viene bajo
 * distintas claves según la generación de firmware.
 */
object FelicitySnapshotMapper {

    private val BLANK_TOKENS = setOf("unknown", "unavailable", "null")

    private fun firstNonBlank(data: JsonObject, vararg keys: String): String? {
        for (key in keys) {
            val element = data.get(key) ?: continue
            if (element.isJsonNull) continue
            val raw = if (element.isJsonPrimitive) element.asString else element.toString()
            if (raw.isBlank() || raw.lowercase() in BLANK_TOKENS) continue
            return raw
        }
        return null
    }

    private fun firstInt(data: JsonObject, vararg keys: String): Int? =
        firstNonBlank(data, *keys)?.toDoubleOrNull()?.toInt()

    private fun firstDouble(data: JsonObject, vararg keys: String): Double? =
        firstNonBlank(data, *keys)?.toDoubleOrNull()

    fun toInverterReading(serialNumber: String, data: JsonObject, now: Instant): InverterReading {
        val gridPower = firstInt(
            data,
            "acTtlInPower", "acTtlInpower", "totalAcTtlInPower", "ctPower", "ctAcTtlInPower"
        )
        val pvPower = firstInt(data, "pvTotalPower", "pvPower", "pv1Power")
        val loadPower = firstInt(data, "totalConsumPower", "ctPower", "meterPower")

        return InverterReading(
            timestamp = now,
            serialNumber = serialNumber,
            gridPowerWatts = gridPower,
            pvPowerWatts = pvPower,
            loadPowerWatts = loadPower
        )
    }

    fun toBatteryReading(serialNumber: String, data: JsonObject, now: Instant): BatteryReading {
        val soc = firstInt(data, "emsSoc", "battSoc")
        val voltage = firstDouble(data, "emsVoltage", "battVolt")
        val current = firstDouble(data, "emsCurrent", "battCurr")
        val health = firstInt(data, "battSoh", "emsSoh")

        return BatteryReading(
            timestamp = now,
            serialNumber = serialNumber,
            socPercent = soc,
            voltage = voltage,
            current = current,
            healthPercent = health
        )
    }
}
