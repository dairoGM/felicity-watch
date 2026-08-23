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
 * Cómo se mide el consumo: se INTEGRA la potencia en el tiempo. Cada lectura
 * aporta (potencia media con la lectura anterior) x (tiempo entre ambas), que es
 * la regla del trapecio. No se puede usar `loadEnergyTodayKwh` del inversor
 * para esto, aunque exista: ese contador es el total del día y no distingue si
 * el consumo ocurrió con corriente o sin ella, que es justo la separación que
 * necesitamos.
 *
 * La atribución con/sin red se hace por lectura, usando gridPowerWatts: si el
 * inversor reportaba potencia de red en ese momento, ese consumo se pagó.
 */
object BillingEstimator {

    /**
     * Huecos mayores a esto no se integran. Sin este corte, un teléfono que
     * estuvo 8 horas sin conexión generaría un tramo de 8 h a la potencia de la
     * última lectura, inventando un consumo enorme que nunca ocurrió.
     */
    private const val MAX_GAP_MINUTES = 15L

    fun estimate(
        readings: List<PowerReadingEntity>,
        period: String,
        zone: ZoneId = ZoneId.systemDefault()
    ): BillingEstimate {
        val daily = aggregateDaily(readings, zone)

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

    /** Integra la potencia en energía, agrupando por día natural. */
    private fun aggregateDaily(
        readings: List<PowerReadingEntity>,
        zone: ZoneId
    ): List<DailyBilling> {
        val usable = readings
            .filter { it.loadPowerWatts != null }
            .sortedBy { it.timestampEpochMillis }
        if (usable.size < 2) return emptyList()

        // Acumuladores por día: (kWh con red, kWh sin red).
        val gridByDay = mutableMapOf<LocalDate, Double>()
        val offGridByDay = mutableMapOf<LocalDate, Double>()

        for (i in 1 until usable.size) {
            val previous = usable[i - 1]
            val current = usable[i]

            val gapMillis = current.timestampEpochMillis - previous.timestampEpochMillis
            if (gapMillis <= 0) continue
            val gapMinutes = gapMillis / 60_000.0
            if (gapMinutes > MAX_GAP_MINUTES) continue

            val previousWatts = previous.loadPowerWatts ?: continue
            val currentWatts = current.loadPowerWatts ?: continue
            // Regla del trapecio: la potencia media del intervalo.
            val averageWatts = (previousWatts + currentWatts) / 2.0
            val kwh = averageWatts * (gapMinutes / 60.0) / 1000.0
            if (kwh <= 0) continue

            // ¿Había corriente durante este intervalo? Se toma el estado de la
            // lectura de CIERRE: es la que confirma qué pasó en el tramo. Un
            // gridPowerWatts null no se cuenta como "sin corriente" — puede ser
            // un campo ausente, y asumirlo inflaría el ahorro.
            val gridWatts = current.gridPowerWatts
            val day = Instant.ofEpochMilli(current.timestampEpochMillis)
                .atZone(zone)
                .toLocalDate()

            when {
                gridWatts == null -> {
                    // Sin dato de red: se atribuye al consumo con red, que es
                    // el supuesto conservador (no infla el ahorro reportado).
                    gridByDay[day] = (gridByDay[day] ?: 0.0) + kwh
                }
                gridWatts >= 1 -> gridByDay[day] = (gridByDay[day] ?: 0.0) + kwh
                else -> offGridByDay[day] = (offGridByDay[day] ?: 0.0) + kwh
            }
        }

        val days = (gridByDay.keys + offGridByDay.keys).sorted()
        return days.map { day ->
            DailyBilling(
                date = day,
                gridKwh = gridByDay[day] ?: 0.0,
                offGridKwh = offGridByDay[day] ?: 0.0
            )
        }
    }

    /** Meses presentes en el historial, del más reciente al más antiguo. */
    fun availableMonths(
        readings: List<PowerReadingEntity>,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<YearMonth> = readings
        .asSequence()
        .map { YearMonth.from(Instant.ofEpochMilli(it.timestampEpochMillis).atZone(zone)) }
        .distinct()
        .sortedDescending()
        .toList()

    /** Filtra las lecturas de un rango de meses, ambos inclusive. */
    fun filterByMonths(
        readings: List<PowerReadingEntity>,
        from: YearMonth,
        to: YearMonth,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<PowerReadingEntity> {
        val start = if (from <= to) from else to
        val end = if (from <= to) to else from
        return readings.filter {
            val month = YearMonth.from(Instant.ofEpochMilli(it.timestampEpochMillis).atZone(zone))
            month >= start && month <= end
        }
    }

    /**
     * Proyección a fin de mes del consumo con red.
     *
     * Solo aplica al mes en curso: extrapola el promedio diario observado a los
     * días que faltan. Devuelve null si el periodo no es el mes actual o si no
     * hay días suficientes para promediar — con un solo día de datos la
     * proyección sería pura invención.
     */
    fun projectMonthEnd(
        estimate: BillingEstimate,
        month: YearMonth,
        today: LocalDate = LocalDate.now()
    ): Double? {
        if (month != YearMonth.from(today)) return null
        val daysWithData = estimate.daily.count { it.gridKwh > 0 }
        if (daysWithData < 2) return null

        val daysInMonth = month.lengthOfMonth()
        val elapsed = today.dayOfMonth
        if (elapsed >= daysInMonth) return null

        val dailyAverage = estimate.gridKwh / elapsed
        return estimate.gridKwh + dailyAverage * (daysInMonth - elapsed)
    }
}
