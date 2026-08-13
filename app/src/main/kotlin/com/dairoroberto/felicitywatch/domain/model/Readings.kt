package com.dairoroberto.felicitywatch.domain.model

import java.time.Instant

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
    /** Hora que el propio equipo reportó ("dataTimeStr" del snapshot) — si
     * viene muy vieja, es señal de que el equipo está desconectado (ej. por
     * un corte de luz que le quita WiFi al collector), no un bug de la app. */
    val deviceReportedAt: Instant? = null,
    /** Dirección física de la planta ("plantAddress" del snapshot) — la
     * trae Felicity solo si el usuario la configuró desde su web; puede
     * venir null. Cuando falta, la UI cae a la dirección manual guardada
     * en Ajustes en vez de dejar el campo en blanco. */
    val plantAddress: String? = null
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
    val deviceReportedAt: Instant? = null
)

enum class GridState { ONLINE, OFFLINE, UNKNOWN }
