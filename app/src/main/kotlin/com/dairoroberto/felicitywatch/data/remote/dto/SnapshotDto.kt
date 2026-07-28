package com.dairoroberto.felicitywatch.data.remote.dto

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

/**
 * El snapshot real de Felicity trae decenas de campos no documentados y
 * varía por firmware/generación de dispositivo (ver coordinator.py de
 * felicityAPI). Se deserializa como JsonObject crudo y se leen los campos
 * relevantes con fallback en cadena vía [FelicitySnapshotMapper], en vez
 * de fijar un data class estricto que rompería con campos inesperados.
 */
data class SnapshotResponse(
    @SerializedName("code") val code: Int?,
    @SerializedName("data") val data: JsonObject?
)
