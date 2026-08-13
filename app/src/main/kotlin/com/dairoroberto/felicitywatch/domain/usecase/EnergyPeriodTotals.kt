package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.data.local.PowerReadingEntity
import java.time.Instant
import java.time.ZoneId

/** Totales de energía PV generada para Hoy / últimos 7 días / últimos 30
 * días — las tres ventanas quedan dentro de lo que el historial local
 * puede calcular con precisión real (se retiene solo 30 días, ver
 * [com.dairoroberto.felicitywatch.data.repository.PowerHistoryRepository]),
 * sin aproximar "mes" o "año" con datos que no existen. */
data class EnergyPeriodTotals(
    val todayKwh: Double,
    val last7DaysKwh: Double,
    val last30DaysKwh: Double
)

/**
 * Mismo criterio que [com.dairoroberto.felicitywatch.ui.report.DailyGenerationReportCard]:
 * el inversor acumula internamente y resetea "energía del día" a
 * medianoche, así que el total de cada día es el ÚLTIMO valor leído ese
 * día (no una suma de lecturas, que inflaría el número).
 */
fun computeEnergyPeriodTotals(readings: List<PowerReadingEntity>, now: Instant): EnergyPeriodTotals {
    val zone = ZoneId.systemDefault()
    val today = now.atZone(zone).toLocalDate()

    val dailyTotals = readings
        .filter { it.pvEnergyTodayKwh != null }
        .groupBy { Instant.ofEpochMilli(it.timestampEpochMillis).atZone(zone).toLocalDate() }
        .mapValues { (_, dayReadings) -> dayReadings.maxBy { it.timestampEpochMillis }.pvEnergyTodayKwh!! }

    val todayKwh = dailyTotals[today] ?: 0.0
    val last7DaysKwh = dailyTotals.filterKeys { !it.isBefore(today.minusDays(6)) }.values.sum()
    val last30DaysKwh = dailyTotals.filterKeys { !it.isBefore(today.minusDays(29)) }.values.sum()

    return EnergyPeriodTotals(todayKwh = todayKwh, last7DaysKwh = last7DaysKwh, last30DaysKwh = last30DaysKwh)
}
