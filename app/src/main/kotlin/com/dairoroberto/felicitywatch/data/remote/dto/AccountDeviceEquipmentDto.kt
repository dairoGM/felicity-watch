package com.dairoroberto.felicitywatch.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Un equipo (inversor/batería) tal como lo trae Felicity — mismos campos
 * que [com.dairoroberto.felicitywatch.domain.model.DeviceInfo], serializado
 * para viajar dentro de `devices_json` en la tabla `account_device_equipment`.
 * Ver migración 003_account_device_equipment.sql. */
data class EquipmentDeviceDto(
    @SerializedName("serial_number") val serialNumber: String,
    /** "INVERTER" | "BATTERY" | "OTHER", igual que DeviceRole.name. */
    @SerializedName("role") val role: String,
    @SerializedName("model") val model: String?,
    @SerializedName("alias") val alias: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("plant_name") val plantName: String?,
    @SerializedName("plant_id") val plantId: String?,
    @SerializedName("owner_name") val ownerName: String?,
    @SerializedName("country_name") val countryName: String?,
    @SerializedName("rated_power_kw") val ratedPowerKw: Double?
)

/** Fila de `account_device_equipment` — un snapshot completo de los equipos
 * de un dispositivo puntual. */
data class AccountDeviceEquipmentDto(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("devices_json") val devices: List<EquipmentDeviceDto>,
    @SerializedName("updated_at") val updatedAt: String? = null
)
