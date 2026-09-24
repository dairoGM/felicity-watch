package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.data.local.PowerReadingEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Consumo y costo de un día, separado por si había corriente de red o no. */
data class DailyBilling(
    val date: LocalDate,
    /** kWh consumidos MIENTRAS había corriente de red — son los que se pagan. */
    val gridKwh: Double,
    /** kWh consumidos SIN corriente de red — cubiertos por batería/solar, y por
     * tanto ahorrados. */
    val offGridKwh: Double
) {
    val totalKwh: Double get() = gridKwh + offGridKwh
}

/**
 * Estimación de factura de un periodo.
 *
 * [gridCost] es lo que se pagaría por el consumo con red. [savedCost] es lo que
 * se HABRÍA pagado si ese consumo sin red también hubiera venido de la calle.
 *
 * Ambos se calculan con la tarifa progresiva sobre el TOTAL del periodo, no día
 * por día: la tarifa se aplica al acumulado del mes, así que sumar costos
 * diarios daría un número mucho menor (cada día caería en el primer tramo).
 */
data class BillingEstimate(
    val period: String,
    val gridKwh: Double,
    val offGridKwh: Double,
    val gridCost: Double,
    /**
     * Ahorro por el consumo cubierto sin red.
     *
     * Se calcula como el COSTO MARGINAL: lo que costaría la factura con todo el
     * consumo (red + sin red) menos lo que cuesta solo con el de red. No es
     * "aplicar la tarifa a los kWh ahorrados por separado", porque en una
     * tarifa progresiva esos kWh se habrían sumado ENCIMA de los ya
     * consumidos, cayendo en tramos más caros. Calcularlo aparte subestimaría
     * el ahorro, a veces por mucho.
     */
    val savedCost: Double,
    val daily: List<DailyBilling>
) {
    val totalKwh: Double get() = gridKwh + offGridKwh

    /** Costo total si NADA se hubiera cubierto con solar/batería. */
    val costWithoutSolar: Double get() = gridCost + savedCost

    /** Porcentaje del consumo cubierto sin red. */
    val offGridShare: Double
        get() = if (totalKwh > 0) offGridKwh / totalKwh else 0.0

    val bracketNumber: Int get() = ElectricityTariff.bracketNumber(gridKwh)
    val marginalRate: Double get() = ElectricityTariff.marginalRate(gridKwh)
    val kwhToNextBracket: Double? get() = ElectricityTariff.kwhToNextBracket(gridKwh)
}

/**
 * Convierte el historial de lecturas en consumo facturable por día.
 *
 * La atribución con/sin red usa [ConsumptionSplitCalculator], la MISMA lógica
 * que el Reporte de Consumo (delta del contador `loadEnergyTodayKwh` del
 * inversor, no integración de potencia) — así un mismo rango de fechas
 * siempre da el mismo número de "con corriente" en ambas pantallas.
 */
object BillingEstimator {

    fun estimate(
        readings: List<PowerReadingEntity>,
        period: String,
        zone: ZoneId = ZoneId.systemDefault()
    ): BillingEstimate {
        val daily = ConsumptionSplitCalculator.splitByDay(readings, zone)
            .toSortedMap()
            .map { (day, split) -> DailyBilling(date = day, gridKwh = split.gridKwh, offGridKwh = split.batteryKwh) }

        val gridKwh = daily.sumOf { it.gridKwh }
        val offGridKwh = daily.sumOf { it.offGridKwh }

        val gridCost = ElectricityTariff.cost(gridKwh)
        // Costo marginal del consumo sin red: cuánto MÁS costaría la factura si
        // esos kWh también hubieran venido de la calle.
        val savedCost = ElectricityTariff.cost(gridKwh + offGridKwh) - gridCost

        return BillingEstimate(
            period = period,
            gridKwh = gridKwh,
            offGridKwh = offGridKwh,
            gridCost = gridCost,
            savedCost = savedCost,
            daily = daily
        )
    }

    /** Filtra las lecturas de un rango de días naturales, ambos inclusive. */
    fun filterByDateRange(
        readings: List<PowerReadingEntity>,
        start: LocalDate,
        end: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<PowerReadingEntity> {
        val from = if (start <= end) start else end
        val to = if (start <= end) end else start
        val startMillis = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return readings.filter { it.timestampEpochMillis in startMillis until endMillis }
    }

    /** Primer día con lecturas en el historial, o null si está vacío. */
    fun earliestDate(
        readings: List<PowerReadingEntity>,
        zone: ZoneId = ZoneId.systemDefault()
    ): LocalDate? = readings.minOfOrNull { Instant.ofEpochMilli(it.timestampEpochMillis).atZone(zone).toLocalDate() }

    /**
     * Proyección a fin de mes del consumo con red.
     *
     * Solo aplica cuando el rango elegido es "del 1 al día de hoy" del mes en
     * curso: extrapola el promedio diario observado a los días que faltan.
     * Devuelve null fuera de ese caso o si no hay días suficientes para
     * promediar — con un solo día de datos la proyección sería pura invención.
     */
    fun projectMonthEnd(
        estimate: BillingEstimate,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        today: LocalDate = LocalDate.now()
    ): Double? {
        val month = YearMonth.from(today)
        if (rangeEnd != today) return null
        if (rangeStart != month.atDay(1)) return null

        val daysWithData = estimate.daily.count { it.gridKwh > 0 }
        if (daysWithData < 2) return null

        val daysInMonth = month.lengthOfMonth()
        val elapsed = today.dayOfMonth
        if (elapsed >= daysInMonth) return null

        val dailyAverage = estimate.gridKwh / elapsed
        return estimate.gridKwh + dailyAverage * (daysInMonth - elapsed)
    }
}
