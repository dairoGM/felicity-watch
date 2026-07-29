package com.dairoroberto.felicitywatch.data.remote

import com.dairoroberto.felicitywatch.domain.model.BatteryReading
import com.dairoroberto.felicitywatch.domain.model.InverterReading
import com.google.gson.JsonObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Puerto directo del helper `_first()` de coordinator.py (felicityAPI):
 * toma el primer valor no nulo/vacío/"unknown"/"unavailable"/"null" entre
 * varios nombres de campo candidatos, porque el mismo dato viene bajo
 * distintas claves según la generación de firmware.
 */
object FelicitySnapshotMapper {

    private val BLANK_TOKENS = setOf("unknown", "unavailable", "null")
    private val DEVICE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

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
        firstNonBlank(data, *keys)?.toDoubleOrNull()?.let { Math.round(it).toInt() }

    private fun firstDouble(data: JsonObject, vararg keys: String): Double? =
        firstNonBlank(data, *keys)?.toDoubleOrNull()

    /** [pvTotalPower]/[pvNPower] llegan en kW (ej. "1.5" = 1500W), a diferencia
     * de los campos de red/carga que ya vienen en W — se confirmó en vivo
     * contra el servidor real (1.5 reportado por el equipo = 1.50kW reales). */
    private fun firstKilowattsAsWatts(data: JsonObject, vararg keys: String): Int? =
        firstNonBlank(data, *keys)?.toDoubleOrNull()?.let { Math.round(it * 1000).toInt() }

    private fun deviceReportedAt(data: JsonObject): Instant? {
        val raw = firstNonBlank(data, "dataTimeStr") ?: return null
        return try {
            LocalDateTime.parse(raw, DEVICE_TIME_FORMATTER).atZone(ZoneId.systemDefault()).toInstant()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    fun toInverterReading(serialNumber: String, data: JsonObject, now: Instant): InverterReading {
        val gridPower = firstInt(
            data,
            "acTtlInPower", "acTtlInpower", "totalAcTtlInPower", "ctPower", "ctAcTtlInPower"
        )
        val pvPower = firstKilowattsAsWatts(data, "pvTotalPower", "pvPower", "pv1Power")
        val loadPower = firstInt(data, "totalConsumPower", "ctPower", "meterPower")

        return InverterReading(
            timestamp = now,
            serialNumber = serialNumber,
            gridPowerWatts = gridPower,
            pvPowerWatts = pvPower,
            loadPowerWatts = loadPower,
            deviceReportedAt = deviceReportedAt(data)
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
            healthPercent = health,
            deviceReportedAt = deviceReportedAt(data)
        )
    }
}
