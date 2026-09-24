package com.dairoroberto.felicitywatch.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Fila de la tabla `desktop_pairings` — un PIN de un solo uso que
 * autoriza a la app de escritorio a leer el historial de este [deviceId]. */
data class DesktopPairingDto(
    @SerializedName("pin") val pin: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("expires_at") val expiresAt: String
)
