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

    /** Descarta lo que el equipo reporta en una vía AC inactiva. No basta con
     * exigir `> 0`: sin corriente de la calle la entrada AC no queda en 0
     * limpio, queda con un residuo de fracciones de voltio (0,5-0,6V medidos
     * en vivo). Cualquier vía que realmente esté alimentando la casa está en
     * la escala de 110/120V, así que por debajo de 1V no hay medición que
     * guardar — es ruido, y guardarlo hacía que las gráficas lo dibujaran
     * como una caída de voltaje que nunca ocurrió. */
    private fun plausibleAcVoltageOrNull(value: Double?): Double? =
        if (value != null && value >= 1.0) value else null

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
        // Voltaje de la red (AC de entrada). Se prueban varios nombres porque
        // la API no es consistente entre modelos; si ninguno viene, el campo
        // queda null y la UI simplemente no muestra el voltaje en vez de
        // inventar un valor.
        //
        // Se filtra el residuo: SIN corriente de la calle el inversor sigue
        // reportando acRInVolt, con fracciones de voltio (0,5-0,6V medidos en
        // vivo) en vez de un 0 limpio. Eso no es "la red está a 0,6V", es "no
        // hay red que medir". Guardarlo hacía que los consumidores que
        // resuelven `gridVoltage ?: outputVoltage` se quedaran con ese residuo
        // (no es null, y un filtro `> 0` tampoco lo descarta) y dibujaran
        // caídas de voltaje que nunca ocurrieron, mientras la casa seguía
        // alimentada por el inversor a 119V. Un voltaje ausente es null.
        val gridVoltage = plausibleAcVoltageOrNull(
            firstDouble(
                data,
                "acRInVolt", "acVoltR", "acRVolt", "gridVoltage", "acInVolt", "vGrid", "acVolt"
            )
        )
        // Voltaje de SALIDA del inversor hacia la carga de respaldo (la casa),
        // no el del banco de baterías. Se necesita por separado: sin corriente
        // de red, el "voltaje de la calle" no existe, y lo que realmente
        // alimenta los tomacorrientes es esta salida AC del inversor — un
        // valor en la misma escala que gridVoltage (110/120V), muy distinto
        // del voltaje DC del banco de baterías (48V nominal, ~55V observado),
        // que es una magnitud física distinta y no comparable.
        // Mismo criterio que [gridVoltage]: un valor por debajo de 1V es la
        // vía inactiva, no una salida moribunda.
        val outputVoltage = plausibleAcVoltageOrNull(
            firstDouble(
                data,
                "acROutVolt", "acOutVolt", "acRoOutVolt", "outVolt", "vOut", "acOutputVolt"
            )
        )
        val plantAddress = firstNonBlank(data, "plantAddress")

        // Detalle por string (V/I/P), la misma vista "Solar" de la pantalla
        // física del inversor. El string 1 rompe el patrón numerado del resto
        // (pvVolt/pvInCurr/pvPower, sin el "1"); del 2 en adelante sí siguen
        // pvNVolt/pvNInCurr/pvNPower — confirmado contra un snapshot real
        // hasta pv4 (pv5..pv10 existen en la API pero no vienen en este
        // inversor). Un string se incluye solo si trae AL MENOS un campo no
        // nulo — omitir strings vacíos evita listar 10 filas en "—" cuando el
        // inversor solo tiene 2 ramas conectadas.
        val pvStrings = (1..10).mapNotNull { index ->
            val voltKey = if (index == 1) "pvVolt" else "pv${index}Volt"
            val currKey = if (index == 1) "pvInCurr" else "pv${index}InCurr"
            val powerKey = if (index == 1) "pvPower" else "pv${index}Power"

            val voltage = firstDouble(data, voltKey)
            val current = firstDouble(data, currKey)
            val power = firstKilowattsAsWatts(data, powerKey)

            if (voltage == null && current == null && power == null) return@mapNotNull null
            com.dairoroberto.felicitywatch.domain.model.PvStringReading(
                index = index,
                voltage = voltage,
                currentAmps = current,
                powerWatts = power
            )
        }

        // Energía de por vida — "totalEnergy" y "ePvTotal" coinciden en el
        // snapshot real (447.2 ambos) y corresponden al "Total: 447.2kWh" de
        // la pantalla física del inversor.
        val pvEnergyTotal = firstDouble(data, "totalEnergy", "ePvTotal")

        // Detalle por fase de la red AC de entrada (pantalla física "Red":
        // L1/L2, P1/P2, CT1/CT2, F). Confirmado contra un snapshot real:
        // acRInVolt/acSInVolt ~ L1/L2, acRInPower/acSInPower (en kW) ~ P1/P2,
        // acRInFreq/acSInFreq ~ F, ctPowerL1/ctPowerL2 ~ CT1/CT2. El inversor
        // de este usuario es monofásico dividido (2 fases, R/S); T viene
        // siempre null.
        val gridPhases = (1..3).mapNotNull { index ->
            val phaseLetter = when (index) { 1 -> "R"; 2 -> "S"; else -> "T" }
            val voltage = firstDouble(data, "ac${phaseLetter}InVolt")
            val power = firstKilowattsAsWatts(data, "ac${phaseLetter}InPower")
            val freq = firstDouble(data, "ac${phaseLetter}InFreq")
            val ctPower = firstInt(data, "ctPowerL$index")
            if (voltage == null && power == null && freq == null && ctPower == null) return@mapNotNull null
            com.dairoroberto.felicitywatch.domain.model.GridPhaseReading(
                index = index,
                voltage = voltage,
                powerWatts = power,
                frequencyHz = freq,
                ctPowerWatts = ctPower
            )
        }
        // "Vender" en la pantalla física — energía inyectada a la red.
        // Confirmado contra un snapshot real: eGridFeedToday="0.1" y
        // eGridFeedTotal="0.3" coinciden exacto con "Hoy: 0.1kWh"/"Total:
        // 0.3kWh".
        val gridFeedEnergyTotal = firstDouble(data, "eGridFeedTotal")

        // Detalle por fase de la salida hacia la carga de respaldo (pantalla
        // física "Carga"). Confirmado: acROutVolt/acSOutVolt ~ L1/L2,
        // acROutPower/acSOutPower (en kW) ~ potencia mostrada por fase.
        val loadPhases = (1..3).mapNotNull { index ->
            val phaseLetter = when (index) { 1 -> "R"; 2 -> "S"; else -> "T" }
            val voltage = firstDouble(data, "ac${phaseLetter}OutVolt")
            val power = firstKilowattsAsWatts(data, "ac${phaseLetter}OutPower")
            if (voltage == null && power == null) return@mapNotNull null
            com.dairoroberto.felicitywatch.domain.model.LoadPhaseReading(
                index = index,
                voltage = voltage,
                powerWatts = power
            )
        }
        // Energía total consumida de por vida — "eLoadTotal" confirmado
        // contra un snapshot real (962.7 ~ "Total: 962.5kWh" de pantalla).
        val loadEnergyTotal = firstDouble(data, "eLoadTotal")

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
            deviceReportedAt = deviceReportedAt(data),
            gridVoltage = gridVoltage,
            outputVoltage = outputVoltage,
            plantAddress = plantAddress,
            pvStrings = pvStrings,
            pvEnergyTotalKwh = pvEnergyTotal,
            gridPhases = gridPhases,
            gridFeedEnergyTotalKwh = gridFeedEnergyTotal,
            loadPhases = loadPhases,
            loadEnergyTotalKwh = loadEnergyTotal
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
        // Potencia instantánea de la batería, con signo (positiva=carga,
        // negativa=descarga) — misma convención que "Potencia" en la
        // pantalla física del inversor bajo "Batería".
        val powerWatts = firstDouble(data, "bmsPower")
        // tempMax/tempMin vienen iguales en la práctica (confirmado contra
        // un snapshot real, ambos "32" = "Temp: 32.0C" en pantalla).
        val temperatureCelsius = firstDouble(data, "tempMax", "tempMin")

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
            deviceReportedAt = deviceReportedAt(data),
            powerWatts = powerWatts,
            temperatureCelsius = temperatureCelsius
        )
    }
}
