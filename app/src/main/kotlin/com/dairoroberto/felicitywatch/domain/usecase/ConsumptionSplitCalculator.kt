package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.data.local.PowerReadingEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Consumo con/sin corriente de red dentro de un tramo (hora, día, o el periodo completo). */
data class ConsumptionSplit(val gridKwh: Double, val batteryKwh: Double) {
    val totalKwh: Double get() = gridKwh + batteryKwh
}

/**
 * Única fuente de verdad para separar el consumo entre "con corriente" y
 * "sin corriente" (batería/solar) — usada por el Reporte de Consumo y por
 * Factura y ahorro, para que el mismo rango de fechas dé siempre el mismo
 * número en ambas pantallas.
 *
 * Se mide por el DELTA del contador acumulado del inversor
 * (`loadEnergyTodayKwh`), no integrando potencia: el contador ya descuenta
 * los huecos de medición del propio inversor, y evita el doble cómputo que
 * daría integrar potencia con lecturas irregulares de la app.
 */
object ConsumptionSplitCalculator {

    /** Un delta de consumo ya atribuido a con/sin corriente, con la marca de tiempo de su lectura de inicio. */
    private data class AttributedDelta(val timestampEpochMillis: Long, val online: Boolean, val kwh: Double)

    private fun attributedDeltas(readings: List<PowerReadingEntity>): List<AttributedDelta> {
        val sorted = readings
            .filter { it.loadEnergyTodayKwh != null && it.gridPowerWatts != null }
            .sortedBy { it.timestampEpochMillis }

        val deltas = mutableListOf<AttributedDelta>()
        for (i in 0 until sorted.size - 1) {
            val current = sorted[i]
            val next = sorted[i + 1]
            val delta = next.loadEnergyTodayKwh!! - current.loadEnergyTodayKwh!!
            // Delta negativo = el contador del inversor se reinició (cruce de
            // medianoche); se descarta en vez de restar energía inexistente.
            if (delta <= 0) continue
            val online = (current.gridPowerWatts ?: 0) >= 1
            deltas += AttributedDelta(current.timestampEpochMillis, online, delta)
        }
        return deltas
    }

    /** Consumo con/sin corriente por día natural. */
    fun splitByDay(
        readings: List<PowerReadingEntity>,
        zone: ZoneId = ZoneId.systemDefault()
    ): Map<LocalDate, ConsumptionSplit> {
        val gridByDay = mutableMapOf<LocalDate, Double>()
        val batteryByDay = mutableMapOf<LocalDate, Double>()

        attributedDeltas(readings).forEach { d ->
            val day = Instant.ofEpochMilli(d.timestampEpochMillis).atZone(zone).toLocalDate()
            if (d.online) {
                gridByDay[day] = (gridByDay[day] ?: 0.0) + d.kwh
            } else {
                batteryByDay[day] = (batteryByDay[day] ?: 0.0) + d.kwh
            }
        }

        val days = gridByDay.keys + batteryByDay.keys
        return days.associateWith { day ->
            ConsumptionSplit(gridKwh = gridByDay[day] ?: 0.0, batteryKwh = batteryByDay[day] ?: 0.0)
        }
    }

    /** Consumo con/sin corriente por slot de hora natural (epoch-millis del inicio de cada hora). */
    fun splitByHour(
        readings: List<PowerReadingEntity>,
        zone: ZoneId = ZoneId.systemDefault()
    ): Map<Long, ConsumptionSplit> {
        fun slotStart(epochMillis: Long): Long =
            Instant.ofEpochMilli(epochMillis).atZone(zone).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()

        val gridBySlot = mutableMapOf<Long, Double>()
        val batteryBySlot = mutableMapOf<Long, Double>()

        attributedDeltas(readings).forEach { d ->
            val slot = slotStart(d.timestampEpochMillis)
            if (d.online) {
                gridBySlot[slot] = (gridBySlot[slot] ?: 0.0) + d.kwh
            } else {
                batteryBySlot[slot] = (batteryBySlot[slot] ?: 0.0) + d.kwh
            }
        }

        val slots = gridBySlot.keys + batteryBySlot.keys
        return slots.associateWith { slot ->
            ConsumptionSplit(gridKwh = gridBySlot[slot] ?: 0.0, batteryKwh = batteryBySlot[slot] ?: 0.0)
        }
    }
}
