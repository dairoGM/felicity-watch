package com.dairoroberto.felicitywatch.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Fila de la tabla `power_readings` en Supabase — mismos campos que
 * [com.dairoroberto.felicitywatch.data.local.PowerReadingEntity], más
 * [deviceId] para que varias instalaciones (o una reinstalación) puedan
 * compartir la misma base sin pisarse.
 */
data class SupabasePowerReadingDto(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("timestamp_epoch_millis") val timestampEpochMillis: Long,
    @SerializedName("pv_power_watts") val pvPowerWatts: Int?,
    @SerializedName("grid_power_watts") val gridPowerWatts: Int?,
    @SerializedName("soc_percent") val socPercent: Int?,
    @SerializedName("load_power_watts") val loadPowerWatts: Int?,
    @SerializedName("battery_power_watts") val batteryPowerWatts: Int?,
    @SerializedName("pv_energy_today_kwh") val pvEnergyTodayKwh: Double?,
    @SerializedName("load_energy_today_kwh") val loadEnergyTodayKwh: Double?,
    @SerializedName("grid_voltage") val gridVoltage: Double?,
    @SerializedName("output_voltage") val outputVoltage: Double?,
    /** Voltaje/corriente instantáneos y capacidad del banco de baterías —
     * necesarios para que la master reconstruya Autonomía y Excedente Solar
     * en el detalle de un cliente exactamente igual que su propio Panel (ver
     * migración 002_power_readings_battery_detail.sql). null en filas
     * subidas antes de esta migración o si el equipo no reporta el dato. */
    @SerializedName("battery_voltage") val batteryVoltage: Double? = null,
    @SerializedName("battery_current") val batteryCurrent: Double? = null,
    @SerializedName("battery_capacity_ah") val batteryCapacityAh: Double? = null
)
