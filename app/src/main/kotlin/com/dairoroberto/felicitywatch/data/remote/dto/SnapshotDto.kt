package com.dairoroberto.felicitywatch.data.remote.dto

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/**
 * El snapshot real de Felicity trae decenas de campos no documentados y
 * varía por firmware/generación de dispositivo (ver coordinator.py de
 * felicityAPI). Se deserializa como JsonElement crudo (no JsonObject
 * directo): igual que en el login, se confirmó en producción que "data"
 * a veces llega como string plano (ej. en respuestas de error) en vez de
 * objeto, y forzar JsonObject ahí tumbaba el ciclo de lectura completo con
 * una excepción de Gson. [dataObject] solo expone el objeto si de verdad
 * lo es; si no, el mapper simplemente no encuentra campos y todo queda null.
 */
data class SnapshotResponse(
    @SerializedName("code") val code: Int?,
    @SerializedName("data") val data: JsonElement?
) {
    val dataObject: JsonObject?
        get() = data?.takeIf { it.isJsonObject }?.asJsonObject
}
