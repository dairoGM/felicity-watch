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
        // acTtlInpower/acTtlInPower llegan en kW igual que pvTotalPower (ej.
        // "0.14" = 140W) — con firstInt (sin conversión) un valor bajo como ese
        // redondeaba a 0W y el estado de red caía a OFFLINE aunque sí hubiera
        // corriente, contradiciendo lo que muestra la web de Felicity.
        val gridPower = firstKilowattsAsWatts(data, "acTtlInPower", "acTtlInpower", "totalAcTtlInPower")
            ?: firstInt(data, "ctPower", "ctAcTtlInPower")
        val pvPower = firstKilowattsAsWatts(data, "pvTotalPower", "pvPower", "pv1Power")
        // "Carga de Respaldo" en la web de Felicity = potencia de salida AC del
        // inversor hacia la carga de respaldo (acTotalOutActPower, en kW como
        // pvTotalPower), no el consumo medido por CT/medidor externo
        // (ctPower/meterPower/totalConsumPower vienen en 0 o null cuando no hay
        // medidor instalado) — confirmado contra un snapshot real donde
        // acTotalOutActPower="1.8" coincidía con acROutPower+acSOutPower≈1.81.
        val loadPower = firstKilowattsAsWatts(data, "acTotalOutActPower")
            ?: firstInt(data, "totalConsumPower", "ctPower", "meterPower")

        // Energía del día (kWh) — ePvToday="5.9" confirmado contra un
        // snapshot real coincidiendo con "Energía Generada por Día" de la
        // app oficial; el resto sigue el mismo patrón de nombres "...Today".
        val pvEnergyToday = firstDouble(data, "ePvToday")
        val gridFeedEnergyToday = firstDouble(data, "eGridFeedToday", "feedOutput")
        val gridInputEnergyToday = firstDouble(data, "eInvToday", "gridInput")
        val loadEnergyToday = firstDouble(data, "eLoadToday", "loadConsumption")

        return InverterReading(
            timestamp = now,
            serialNumber = serialNumber,
            gridPowerWatts = gridPower,
            pvPowerWatts = pvPower,
            loadPowerWatts = loadPower,
            pvEnergyTodayKwh = pvEnergyToday,
            gridFeedEnergyTodayKwh = gridFeedEnergyToday,
            gridInputEnergyTodayKwh = gridInputEnergyToday,
            loadEnergyTodayKwh = loadEnergyToday,
            deviceReportedAt = deviceReportedAt(data)
        )
    }

    fun toBatteryReading(serialNumber: String, data: JsonObject, now: Instant): BatteryReading {
        val soc = firstInt(data, "emsSoc", "battSoc")
        val voltage = firstDouble(data, "emsVoltage", "battVolt")
        val current = firstDouble(data, "emsCurrent", "battCurr")
        val health = firstInt(data, "battSoh", "emsSoh")
        // battCapacity="314" confirmado contra un snapshot real de una
        // batería de 16kWh nominal (314 Ah × 51.2V nominal ≈ 16.1kWh) —
        // totalEmsCapacity/emsCapacity traían un valor genérico distinto
        // (350/0) que no correspondía a la capacidad real del banco.
        val capacityAh = firstDouble(data, "battCapacity", "capacity")
        val chargeCurrentLimitA = firstDouble(data, "BMSLCCurr", "bmslccurr")
        val dischargeCurrentLimitA = firstDouble(data, "BMSLDCurr", "bmsldcurr")
        val chargeVoltageLimitV = firstDouble(data, "BMSLCVolt")
        val dischargeVoltageLimitV = firstDouble(data, "BMSLDVolt")
        val batteryType = when (firstNonBlank(data, "productTypeEnum")) {
            "LITHIUM_BATTERY_PACK" -> "Batería de litio"
            else -> null
        }
        val remainingEnergyKwh = firstDouble(data, "remainingBatteryEnergy1", "remainingBatteryEnergy")

        return BatteryReading(
            timestamp = now,
            serialNumber = serialNumber,
            socPercent = soc,
            voltage = voltage,
            current = current,
            healthPercent = health,
            capacityAh = capacityAh,
            chargeCurrentLimitA = chargeCurrentLimitA,
            dischargeCurrentLimitA = dischargeCurrentLimitA,
            chargeVoltageLimitV = chargeVoltageLimitV,
            dischargeVoltageLimitV = dischargeVoltageLimitV,
            batteryType = batteryType,
            remainingEnergyKwh = remainingEnergyKwh,
            deviceReportedAt = deviceReportedAt(data)
        )
    }
}
