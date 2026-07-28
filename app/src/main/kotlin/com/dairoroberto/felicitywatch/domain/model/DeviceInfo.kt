package com.dairoroberto.felicitywatch.domain.model

enum class DeviceRole { INVERTER, BATTERY, OTHER }

data class DeviceInfo(
    val serialNumber: String,
    val role: DeviceRole,
    val model: String?,
    val alias: String?,
    val status: String?,
    val plantName: String?
)
