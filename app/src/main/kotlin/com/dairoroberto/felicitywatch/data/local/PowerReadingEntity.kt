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
    val socPercent: Int?,
    val loadPowerWatts: Int? = null,
    /** PV - consumo de la casa: positivo = cargando batería, negativo = descargando. */
    val batteryPowerWatts: Int? = null,
    /** Contadores de energía del DÍA reportados por el propio inversor
     * (ePvToday/eLoadToday) — se resetean a 0 cada medianoche en el equipo,
     * así que el reporte diario toma el ÚLTIMO valor leído en cada día, no
     * una suma (el equipo ya acumula internamente). */
    val pvEnergyTodayKwh: Double? = null,
    val loadEnergyTodayKwh: Double? = null,
    /** Voltaje de la red AC de entrada, en voltios — presente solo en las
     * lecturas tomadas CON corriente. Alimenta el reporte de Voltaje. */
    val gridVoltage: Double? = null,
    /** Voltaje de salida del inversor hacia la casa, en voltios — presente
     * solo en las lecturas tomadas SIN corriente (la red no existe, así que
     * lo que alimenta la casa es esta salida del inversor). Misma escala
     * AC que [gridVoltage] (110/120V), no confundir con el voltaje DC del
     * banco de baterías (48V nominal). */
    val outputVoltage: Double? = null
)
