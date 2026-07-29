package com.dairoroberto.felicitywatch.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Serie de tiempo local de PV/red/batería, acumulada por el propio
 * dispositivo en cada ciclo de polling — no depende del endpoint de
 * historial de Felicity (documentado como inestable/con 12 variantes de
 * payload en la referencia). Alimenta el gráfico de "Generación".
 */
@Entity(tableName = "power_readings")
data class PowerReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampEpochMillis: Long,
    val pvPowerWatts: Int?,
    val gridPowerWatts: Int?,
    val socPercent: Int?
)
