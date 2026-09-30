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

/**
 * Payload del upsert — a propósito NO incluye `updated_at`: Gson serializa
 * nulls (ver NetworkModule), así que si este campo existiera con valor
 * null, el upsert mandaría `"updated_at": null` explícito en el body,
 * pisando el `default now()` de la columna con un NULL literal y violando
 * su `not null` (confirmado con un error real: HTTP 400, código 23502 "null
 * value in column updated_at violates not-null constraint"). Mismo patrón
 * de bug que ya se había resuelto antes para display_name en
 * AccountDeviceApprovalDto.
 */
data class AccountDeviceEquipmentUpsertDto(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("devices_json") val devices: List<EquipmentDeviceDto>
)

/** Fila de `account_device_equipment` tal como se lee — incluye
 * `updated_at`, útil para mostrar cuándo se sincronizó por última vez. */
data class AccountDeviceEquipmentDto(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("devices_json") val devices: List<EquipmentDeviceDto>,
    @SerializedName("updated_at") val updatedAt: String? = null
)
