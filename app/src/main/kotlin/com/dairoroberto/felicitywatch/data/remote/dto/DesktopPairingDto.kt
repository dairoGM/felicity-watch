package com.dairoroberto.felicitywatch.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Fila de la tabla `desktop_pairings` — un PIN de un solo uso generado por
 * el dispositivo master, para aprobar una app de escritorio ([targetPlatform]
 * = "desktop") o un celular cliente ([targetPlatform] = "android"). */
data class DesktopPairingDto(
    @SerializedName("pin") val pin: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("expires_at") val expiresAt: String,
    @SerializedName("target_platform") val targetPlatform: String = "desktop"
)
