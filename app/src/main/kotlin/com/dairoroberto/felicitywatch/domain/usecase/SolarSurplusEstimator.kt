package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.data.local.PowerReadingEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Excedente solar de un tramo (una hora, o el día completo): cuánta energía
 * generó el panel por ENCIMA de lo que consumió la casa en ese tramo, en kWh.
 */
data class SolarSurplus(
    val kwh: Double
)

/**
 * Excedente solar acumulado del día, calculado integrando la POTENCIA NETA
 * (generación PV − consumo) en el tiempo — no restando dos totales de
 * energía del día, que perdería la variación minuto a minuto.
 *
 * Regla de acumulación: solo se suman los tramos donde el PV superó al
 * consumo (excedente positivo). Los tramos de déficit (consumo > PV, típico
 * de la noche) NO restan del acumulado — el excedente del día es "cuánta
 * energía sobró", y un tramo sin sobra no puede hacer que el total sea
 * negativo, sino simplemente no aporta nada.
 *
 * Mismo patrón de integración que [BillingEstimator] (regla del trapecio,
 * mismo corte de huecos): se reutiliza el criterio ya validado en vez de
 * inventar uno nuevo con matices sutilmente distintos.
 */
object SolarSurplusEstimator {

    /** Mismo umbral que [BillingEstimator]: un hueco de más de 15 minutos no
     * se integra, para no inventar excedente en el tiempo que el teléfono
     * estuvo sin conexión. */
    private const val MAX_GAP_MINUTES = 15L

    /** Excedente acumulado por hora del día (0..23), sumando SOLO los
     * tramos entre lecturas consecutivas donde hubo PV > consumo.
     *
     * Se agrupa por la hora de la lectura de CIERRE de cada tramo — igual
     * criterio que usa BillingEstimator para el estado de red: es la lectura
     * que confirma qué pasó durante ese intervalo.
     *
     * Si [readings] cubre varios días, las horas se combinan entre todos los
     * días del rango (mismo comportamiento que el resto de reportes "por
     * hora" de la app).
     */
    fun hourlySurplus(readings: List<PowerReadingEntity>, zone: ZoneId = ZoneId.systemDefault()): List<Double> {
        val hourly = DoubleArray(24)
        forEachSurplusInterval(readings) { current, kwh ->
            val hour = Instant.ofEpochMilli(current.timestampEpochMillis).atZone(zone).hour
            hourly[hour] += kwh
        }
        return hourly.toList()
    }

    /** Excedente acumulado por día natural — usado para el indicador del
     * Panel ("excedente de hoy"), filtrando antes las lecturas a un solo
     * día. */
    fun dailySurplusKwh(readings: List<PowerReadingEntity>, zone: ZoneId = ZoneId.systemDefault()): Double {
        var total = 0.0
        forEachSurplusInterval(readings) { _, kwh -> total += kwh }
        return total
    }

    /** Recorre los tramos entre lecturas consecutivas y llama [onSurplus]
     * solo para los que tuvieron excedente positivo, con la lectura de
     * cierre del tramo y el kWh de excedente que aportó. */
    private inline fun forEachSurplusInterval(
        readings: List<PowerReadingEntity>,
        onSurplus: (current: PowerReadingEntity, kwh: Double) -> Unit
    ) {
        val usable = readings
            .filter { it.pvPowerWatts != null && it.loadPowerWatts != null }
            .sortedBy { it.timestampEpochMillis }
        if (usable.size < 2) return

        for (i in 1 until usable.size) {
            val previous = usable[i - 1]
            val current = usable[i]

            val gapMillis = current.timestampEpochMillis - previous.timestampEpochMillis
            if (gapMillis <= 0) continue
            val gapMinutes = gapMillis / 60_000.0
            if (gapMinutes > MAX_GAP_MINUTES) continue

            val previousNet = previous.pvPowerWatts!! - previous.loadPowerWatts!!
            val currentNet = current.pvPowerWatts!! - current.loadPowerWatts!!
            // Regla del trapecio sobre la potencia NETA del tramo.
            val averageNetWatts = (previousNet + currentNet) / 2.0
            if (averageNetWatts <= 0) continue // tramo de déficit: no resta, no aporta.

            val kwh = averageNetWatts * (gapMinutes / 60.0) / 1000.0
            if (kwh <= 0) continue

            onSurplus(current, kwh)
        }
    }
}

/** Filtra lecturas al día natural de [date] en la zona horaria [zone]. */
fun List<PowerReadingEntity>.filterToDay(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<PowerReadingEntity> =
    filter { Instant.ofEpochMilli(it.timestampEpochMillis).atZone(zone).toLocalDate() == date }
