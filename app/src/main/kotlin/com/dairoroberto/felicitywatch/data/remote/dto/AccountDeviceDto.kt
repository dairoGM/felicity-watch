package com.dairoroberto.felicitywatch.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Fila de la tabla `account_devices` — registro de control de acceso de una
 * instalación móvil (master o cliente) para esta cuenta. */
data class AccountDeviceDto(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("role") val role: String,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("approved_at") val approvedAt: String? = null,
    @SerializedName("last_seen_at") val lastSeenAt: String? = null,
    @SerializedName("revoked") val revoked: Boolean = false
)

/** Lectura parcial usada solo para consultar el rol/estado de un device_id. */
data class AccountDeviceStatusDto(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("role") val role: String,
    @SerializedName("display_name") val displayName: String?,
    @SerializedName("approved_at") val approvedAt: String?,
    @SerializedName("last_seen_at") val lastSeenAt: String?,
    @SerializedName("revoked") val revoked: Boolean
)
