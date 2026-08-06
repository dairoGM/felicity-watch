package com.dairoroberto.felicitywatch.data.remote.dto

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.google.gson.Gson

/**
 * "data" se deserializa crudo por la misma razón que en LoginResponse: en
 * respuestas de error la nube de Felicity puede devolver un string en vez
 * de un objeto, y eso no debe tumbar el parseo de toda la respuesta.
 */
data class DeviceListResponse(
    @SerializedName("code") val code: Int?,
    @SerializedName("data") val data: JsonElement?
) {
    fun extractDeviceList(): List<DeviceDto> {
        val data = this.data ?: return emptyList()
        if (!data.isJsonObject) return emptyList()

        val dataListElement = data.asJsonObject.get("dataList") ?: return emptyList()
        if (!dataListElement.isJsonArray) return emptyList()

        val type = object : TypeToken<List<DeviceDto>>() {}.type
        return GSON.fromJson(dataListElement, type)
    }

    private companion object {
        val GSON = Gson()
    }
}

data class DeviceDto(
    @SerializedName("deviceSn") val deviceSn: String?,
    @SerializedName("deviceType") val deviceType: String?,
    @SerializedName("deviceModel") val deviceModel: String?,
    @SerializedName("subType") val subType: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("alias") val alias: String?,
    @SerializedName("plantName") val plantName: String?,
    /** Campos de "planta" confirmados contra un snapshot real de
     * list_device_all_type — no hay capacidad instalada/tipo de
     * planta/fecha de instalación en este endpoint, solo lo de abajo. */
    @SerializedName("plantId") val plantId: String?,
    @SerializedName("realName") val ownerName: String?,
    @SerializedName("countryName") val countryName: String?,
    @SerializedName("ratedPower") val ratedPowerKw: String?
) {
    companion object {
        const val TYPE_INVERTER = "OC"
        const val TYPE_BATTERY = "BP"
    }
}
