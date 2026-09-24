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
    @SerializedName("output_voltage") val outputVoltage: Double?
)
