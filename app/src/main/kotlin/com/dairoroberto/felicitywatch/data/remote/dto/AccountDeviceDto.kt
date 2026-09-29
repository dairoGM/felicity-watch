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

/**
 * Payload del upsert de "aprobar/reclamar" (claimMaster, redeemPairingPin) —
 * a propósito NO incluye `display_name`. El upsert usa
 * `Prefer: resolution=merge-duplicates`, que en un re-canje (dispositivo ya
 * conocido, quizás renombrado y luego revocado) hace un UPDATE con
 * exactamente las columnas de este body; como Gson serializa nulls
 * (NetworkModule), enviar `display_name` aquí lo borraría con `null` en cada
 * aprobación, perdiendo el nombre que el master ya le había puesto.
 */
data class AccountDeviceApprovalDto(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("role") val role: String,
    @SerializedName("approved_at") val approvedAt: String? = null,
    @SerializedName("revoked") val revoked: Boolean = false
)

/** Lectura parcial usada solo para consultar el rol/estado de un device_id. */
data class AccountDeviceStatusDto(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("role") val role: String,
    @SerializedName("display_name") val displayName: String?,
    @SerializedName("approved_at") val approvedAt: String?,
    @SerializedName("last_seen_at") val lastSeenAt: String?,
    @SerializedName("revoked") val revoked: Boolean,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("location_updated_at") val locationUpdatedAt: String? = null,
    // Licenciamiento (migración 001_licensing.sql). Todos nullable con default
    // para que una fila creada antes de esa migración siga deserializando.
    @SerializedName("license_status") val licenseStatus: String? = null,
    @SerializedName("free_started_at") val freeStartedAt: String? = null,
    @SerializedName("transfer_reference") val transferReference: String? = null,
    @SerializedName("transfer_submitted_at") val transferSubmittedAt: String? = null,
    @SerializedName("license_decided_at") val licenseDecidedAt: String? = null,
    @SerializedName("rejected_attempts") val rejectedAttempts: Int? = null,
    @SerializedName("license_reject_reason") val licenseRejectReason: String? = null
)

/** Fila de `account_settings` — configuración de la cuenta que el master
 * edita y los clientes solo leen. */
data class AccountSettingsDto(
    @SerializedName("id") val id: String = "default",
    @SerializedName("free_period_days") val freePeriodDays: Int
)
