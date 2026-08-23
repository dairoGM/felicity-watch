package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.data.local.PowerReadingEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Consumo integrado de UNA hora del calendario, separado por fuente. */
data class HourlyConsumption(
    val hourStartEpochMillis: Long,
    val gridKwh: Double,
    val batteryKwh: Double
) {
    val totalKwh: Double get() = gridKwh + batteryKwh
}

/** Consumo de una "ventana" (ej. noche 10pm-8am), identificada por la fecha
 * en la que EMPIEZA — para una ventana 22h→8h que arrancó el día 22, sus
 * horas de la madrugada del día 23 pertenecen igual a "la noche del 22". */
data class WindowConsumption(val startDate: LocalDate, val hours: List<HourlyConsumption>) {
    val totalKwh: Double get() = hours.sumOf { it.totalKwh }
    val gridKwh: Double get() = hours.sumOf { it.gridKwh }
    val batteryKwh: Double get() = hours.sumOf { it.batteryKwh }
}

data class WeekdayPrediction(val dayOfWeek: DayOfWeek, val averageKwh: Double, val sampleCount: Int)

/**
 * Integra el consumo por hora del calendario, separado por fuente (red vs
 * batería) — mismo criterio que el resto de reportes de consumo de la app:
 * se suma (potencia media del intervalo) × (tiempo), atribuyendo cada
 * intervalo a la hora de INICIO, y se descartan huecos que crucen la
 * medianoche del contador del inversor (delta negativo).
 */
fun computeHourlyConsumption(readings: List<PowerReadingEntity>, zone: ZoneId): List<HourlyConsumption> {
    val sorted = readings
        .filter { it.loadEnergyTodayKwh != null && it.gridPowerWatts != null }
        .sortedBy { it.timestampEpochMillis }

    fun slotStart(epochMillis: Long): Long {
        val zoned = Instant.ofEpochMilli(epochMillis).atZone(zone)
        return zoned.withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()
    }

    val gridResult = mutableMapOf<Long, Double>()
    val batteryResult = mutableMapOf<Long, Double>()
    for (i in 0 until sorted.size - 1) {
        val current = sorted[i]
        val next = sorted[i + 1]
        val delta = next.loadEnergyTodayKwh!! - current.loadEnergyTodayKwh!!
        if (delta <= 0) continue
        val online = (current.gridPowerWatts ?: 0) >= 1
        val slot = slotStart(current.timestampEpochMillis)
        if (online) {
            gridResult[slot] = (gridResult[slot] ?: 0.0) + delta
        } else {
            batteryResult[slot] = (batteryResult[slot] ?: 0.0) + delta
        }
    }

    val allSlots = (gridResult.keys + batteryResult.keys).toSortedSet()
    return allSlots.map { slot ->
        HourlyConsumption(
            hourStartEpochMillis = slot,
            gridKwh = gridResult[slot] ?: 0.0,
            batteryKwh = batteryResult[slot] ?: 0.0
        )
    }
}

/**
 * A qué ventana pertenece una hora dada, identificada por la fecha en que
 * ARRANCA la ventana — o null si esa hora cae fuera de la franja
 * configurada (ej. las horas del día, si la franja es la noche).
 *
 * [startHour] <= [endHour]: ventana normal dentro del mismo día (ej. 9→17).
 * [startHour] > [endHour]: cruza medianoche (ej. 22→8) — las horas desde
 * [startHour] pertenecen a la ventana que arranca ESE día; las horas antes
 * de [endHour] pertenecen a la ventana que arrancó el día ANTERIOR.
 */
fun windowStartDateFor(zoned: ZonedDateTime, startHour: Int, endHour: Int): LocalDate? {
    val hour = zoned.hour
    return if (startHour <= endHour) {
        if (hour in startHour until endHour) zoned.toLocalDate() else null
    } else {
        when {
            hour >= startHour -> zoned.toLocalDate()
            hour < endHour -> zoned.toLocalDate().minusDays(1)
            else -> null
        }
    }
}

/** Agrupa horas ya integradas en ventanas completas, más recientes primero. */
fun groupIntoWindows(
    hourly: List<HourlyConsumption>,
    startHour: Int,
    endHour: Int,
    zone: ZoneId
): List<WindowConsumption> {
    return hourly
        .mapNotNull { hour ->
            val zoned = Instant.ofEpochMilli(hour.hourStartEpochMillis).atZone(zone)
            val windowDate = windowStartDateFor(zoned, startHour, endHour) ?: return@mapNotNull null
            windowDate to hour
        }
        .groupBy({ it.first }, { it.second })
        .map { (date, hours) -> WindowConsumption(date, hours.sortedBy { it.hourStartEpochMillis }) }
        .sortedByDescending { it.startDate }
}

/**
 * Promedio histórico de consumo por día de la semana, sobre TODAS las
 * ventanas disponibles (no solo el periodo filtrado arriba en el Reporte):
 * cuantos más datos, más confiable el promedio — un rango corto (ej. "7
 * días") solo tendría una muestra por día de la semana.
 */
fun predictByWeekday(windows: List<WindowConsumption>): Map<DayOfWeek, WeekdayPrediction> {
    return windows
        .groupBy { it.startDate.dayOfWeek }
        .mapValues { (dayOfWeek, group) ->
            WeekdayPrediction(
                dayOfWeek = dayOfWeek,
                averageKwh = group.map { it.totalKwh }.average(),
                sampleCount = group.size
            )
        }
}
