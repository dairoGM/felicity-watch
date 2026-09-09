package com.dairoroberto.felicitywatch.domain.model

import java.time.Instant

/**
 * Lectura de un string (rama) de paneles solares — V/I/P individuales, tal
 * como los muestra la propia pantalla física del inversor (pantalla
 * "Solar": V1/I1/P1, V2/I2/P2...).
 *
 * El inversor puede tener varios strings (el JSON de Felicity trae hasta
 * pv10Volt/pv10InCurr/pv10Power); solo se listan los que de verdad reportan
 * datos, así que [index] no es necesariamente consecutivo desde 1 si algún
 * string intermedio no viene en la respuesta.
 */
data class PvStringReading(
    val index: Int,
    val voltage: Double?,
    val currentAmps: Double?,
    val powerWatts: Int?
)

/**
 * Lectura de una fase de la red AC de entrada — V/P/frecuencia y el CT
 * asociado, tal como los muestra la pantalla física del inversor bajo "Red"
 * (L1/L2, P1/P2, CT1/CT2).
 */
data class GridPhaseReading(
    val index: Int,
    val voltage: Double?,
    val powerWatts: Int?,
    val frequencyHz: Double?,
    val ctPowerWatts: Int?
)

/**
 * Lectura de una fase de la salida hacia la carga de respaldo — V/P, tal
 * como los muestra la pantalla física del inversor bajo "Carga".
 */
data class LoadPhaseReading(
    val index: Int,
    val voltage: Double?,
    val powerWatts: Int?
)

data class InverterReading(
    val timestamp: Instant,
    val serialNumber: String,
    val gridPowerWatts: Int?,
    val pvPowerWatts: Int?,
    val loadPowerWatts: Int?,
    /** Energía del día en kWh — confirmado contra un snapshot real
     * (ePvToday="5.9" coincidía con "Energía Generada por Día" de la app
     * oficial). El resto sigue el mismo patrón de nombres ("...Today"). */
    val pvEnergyTodayKwh: Double? = null,
    val gridFeedEnergyTodayKwh: Double? = null,
    val gridInputEnergyTodayKwh: Double? = null,
    val loadEnergyTodayKwh: Double? = null,
    /** Voltaje de la red AC de entrada, en voltios. Puede venir null: no
     * todos los modelos lo reportan, y la API no es consistente en el nombre
     * del campo. La UI no lo muestra cuando falta. */
    val gridVoltage: Double? = null,
    /** Voltaje de SALIDA del inversor hacia la carga de respaldo (la casa),
     * en voltios AC (110/120V, misma escala que [gridVoltage]) — no el
     * voltaje del banco de baterías, que es DC y de otra magnitud (48V
     * nominal). Es lo que realmente alimenta los tomacorrientes cuando no
     * hay corriente de red. Puede venir null por el mismo motivo que
     * [gridVoltage]. */
    val outputVoltage: Double? = null,
    /** Hora que el propio equipo reportó ("dataTimeStr" del snapshot) — si
     * viene muy vieja, es señal de que el equipo está desconectado (ej. por
     * un corte de luz que le quita WiFi al collector), no un bug de la app. */
    val deviceReportedAt: Instant? = null,
    /** Dirección física de la planta ("plantAddress" del snapshot) — la
     * trae Felicity solo si el usuario la configuró desde su web; puede
     * venir null. Cuando falta, la UI cae a la dirección manual guardada
     * en Ajustes en vez de dejar el campo en blanco. */
    val plantAddress: String? = null,
    /** Detalle por string de paneles (V/I/P), la misma vista que muestra la
     * pantalla física del inversor bajo "Solar". Vacía si el snapshot no
     * trae ningún campo pvNVolt/pvNInCurr/pvNPower. */
    val pvStrings: List<PvStringReading> = emptyList(),
    /** Energía generada de por vida, en kWh ("totalEnergy"/"ePvTotal" del
     * snapshot — confirmados iguales entre sí y contra el "Total" que
     * muestra la pantalla física del inversor). Distinto de
     * [pvEnergyTodayKwh], que es solo la del día y se resetea a diario. */
    val pvEnergyTotalKwh: Double? = null,
    /** Detalle por fase de la red AC de entrada (L1/L2), la misma vista que
     * muestra la pantalla física del inversor bajo "Red". Vacía si el
     * snapshot no trae ningún campo acRInVolt/acSInVolt. */
    val gridPhases: List<GridPhaseReading> = emptyList(),
    /** Energía vendida (inyectada a la red) — "Vender" en la pantalla física.
     * Confirmado contra un snapshot real: eGridFeedToday="0.1" y
     * eGridFeedTotal="0.3" coinciden con "Hoy: 0.1kWh"/"Total: 0.3kWh". */
    val gridFeedEnergyTotalKwh: Double? = null,
    /** Energía comprada (tomada de la red) — "Comprar" en la pantalla física.
     * Confirmado contra un snapshot real: hotJson.eGridInToday="17.8" y
     * hotJson.gridInTotal="633.6" coinciden con "Hoy: 17.7kWh"/"Total:
     * 633.5kWh" (redondeo de la pantalla). */
    val gridInputEnergyTotalKwh: Double? = null,
    /** Detalle por fase de la salida hacia la carga de respaldo (L1/L2), la
     * misma vista que muestra la pantalla física del inversor bajo "Carga". */
    val loadPhases: List<LoadPhaseReading> = emptyList(),
    /** Energía total consumida de por vida ("eLoadTotal" del snapshot —
     * confirmado contra un snapshot real, 962.7 coincidiendo con "Total:
     * 962.5kWh" de la pantalla física bajo "Carga"). */
    val loadEnergyTotalKwh: Double? = null
)

data class BatteryReading(
    val timestamp: Instant,
    val serialNumber: String,
    val socPercent: Int?,
    val voltage: Double?,
    val current: Double?,
    val healthPercent: Int?,
    /** Capacidad real del banco en Ah, reportada por el propio equipo
     * (battCapacity) — ya no es configurable a mano en Ajustes. */
    val capacityAh: Double? = null,
    val chargeCurrentLimitA: Double? = null,
    val dischargeCurrentLimitA: Double? = null,
    val chargeVoltageLimitV: Double? = null,
    val dischargeVoltageLimitV: Double? = null,
    val batteryType: String? = null,
    val remainingEnergyKwh: Double? = null,
    val deviceReportedAt: Instant? = null,
    /** Potencia instantánea de la batería en vatios ("bmsPower" del snapshot),
     * con signo: positiva = cargando, negativa = descargando — misma
     * convención que muestra la pantalla física del inversor bajo
     * "Batería" ("Potencia"). */
    val powerWatts: Double? = null,
    /** Temperatura del banco de baterías en °C ("tempMax"/"tempMin" del
     * snapshot, iguales en la práctica — confirmado contra un snapshot real
     * donde ambos ="32" coincidía con "Temp: 32.0C" de la pantalla física). */
    val temperatureCelsius: Double? = null
)

enum class GridState { ONLINE, OFFLINE, UNKNOWN }
