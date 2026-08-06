package com.dairoroberto.felicitywatch.domain.model

enum class DeviceRole { INVERTER, BATTERY, OTHER }

data class DeviceInfo(
    val serialNumber: String,
    val role: DeviceRole,
    val model: String?,
    val alias: String?,
    val status: String?,
    val plantName: String?,
    val plantId: String?,
    val ownerName: String?,
    val countryName: String?,
    /** Potencia nominal en kW (del inversor) — la más cercana a "Capacidad
     * Instalada" disponible en este endpoint; no es exactamente el mismo
     * dato que la tabla web (que trae "8kWP" de un endpoint de detalle de
     * planta al que esta app no llama). */
    val ratedPowerKw: Double?
)

/** Agrupador de dispositivos por plantId — la web de Felicity muestra la
 * planta como la fila principal (con foto/cubierta, capacidad, tipo,
 * propietario, fecha de instalación) y los equipos colgando de ella. */
data class PlantInfo(
    val plantId: String,
    val plantName: String,
    val ownerName: String?,
    val countryName: String?,
    val ratedPowerKw: Double?,
    val devices: List<DeviceInfo>
)
