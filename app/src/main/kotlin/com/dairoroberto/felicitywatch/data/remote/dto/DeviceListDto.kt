package com.dairoroberto.felicitywatch.data.remote.dto

import com.google.gson.annotations.SerializedName

data class DeviceListResponse(
    @SerializedName("code") val code: Int?,
    @SerializedName("data") val data: DeviceListData?
)

data class DeviceListData(
    @SerializedName("dataList") val dataList: List<DeviceDto>?
)

data class DeviceDto(
    @SerializedName("deviceSn") val deviceSn: String?,
    @SerializedName("deviceType") val deviceType: String?,
    @SerializedName("deviceModel") val deviceModel: String?,
    @SerializedName("subType") val subType: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("alias") val alias: String?,
    @SerializedName("plantName") val plantName: String?
) {
    companion object {
        const val TYPE_INVERTER = "OC"
        const val TYPE_BATTERY = "BP"
    }
}
