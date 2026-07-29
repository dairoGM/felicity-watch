package com.dairoroberto.felicitywatch.domain.model

import java.time.Instant

data class InverterReading(
    val timestamp: Instant,
    val serialNumber: String,
    val gridPowerWatts: Int?,
    val pvPowerWatts: Int?,
    val loadPowerWatts: Int?,
    /** Hora que el propio equipo reportó ("dataTimeStr" del snapshot) — si
     * viene muy vieja, es señal de que el equipo está desconectado (ej. por
     * un corte de luz que le quita WiFi al collector), no un bug de la app. */
    val deviceReportedAt: Instant? = null
)

data class BatteryReading(
    val timestamp: Instant,
    val serialNumber: String,
    val socPercent: Int?,
    val voltage: Double?,
    val current: Double?,
    val healthPercent: Int?,
    val deviceReportedAt: Instant? = null
)

enum class GridState { ONLINE, OFFLINE, UNKNOWN }
