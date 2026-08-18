package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.data.local.ApplianceEntity
import com.dairoroberto.felicitywatch.data.local.PowerReadingEntity
import kotlin.math.abs

/**
 * Un cambio brusco de consumo detectado entre dos lecturas consecutivas.
 * [deltaWatts] positivo = algo se encendió; negativo = algo se apagó.
 */
data class ApplianceEvent(
    val epochMillis: Long,
    val deltaWatts: Int,
    /** Consumo total de la casa justo después del cambio. */
    val totalWattsAfter: Int,
    /** Equipo del catálogo cuyo consumo declarado más se acerca al delta,
     * o null si ninguno cae dentro de su tolerancia. */
    val matchedAppliance: ApplianceEntity?,
    /** Cuántos equipos del catálogo encajaban en este delta. Con más de
     * uno la propuesta es ambigua y la UI debe advertirlo — dos equipos de
     * consumo parecido son indistinguibles con un solo número de watts. */
    val candidateCount: Int
) {
    val turnedOn: Boolean get() = deltaWatts > 0
}

/**
 * Detección de encendido/apagado de equipos por ESCALONES de consumo.
 *
 * Limitaciones reales, deliberadamente no disimuladas:
 *
 * - Las lecturas llegan en la cadencia configurada en Ajustes, así que un
 *   equipo que se enciende y apaga entre dos lecturas es invisible. Solo se
 *   detecta lo que queda encendido el tiempo suficiente. Ojo: bajar esa
 *   cadencia no mejora la resolución más allá de cada cuánto el inversor
 *   publica sus datos en la nube de Felicity.
 * - El inversor solo expone potencia activa total (un número). No hay
 *   potencia reactiva, armónicos ni transitorios de arranque, que es lo
 *   que un sistema NILM real usa para identificar un equipo concreto.
 * - Por lo tanto esto NO identifica equipos: propone el del catálogo cuyo
 *   consumo DECLARADO POR EL USUARIO más se aproxima al escalón. Dos
 *   equipos de watts parecidos son indistinguibles entre sí.
 * - Si se encienden dos equipos a la vez, el escalón es la suma y la
 *   propuesta será incorrecta.
 */
object ApplianceEventDetector {

    /** Escalón mínimo para considerarlo un evento y no ruido de medición
     * o variación normal de un equipo ya encendido (ej. la nevera
     * modulando). Por debajo de esto los "eventos" serían mayormente
     * falsos positivos. */
    const val MIN_DELTA_WATTS = 200

    fun detect(
        readings: List<PowerReadingEntity>,
        appliances: List<ApplianceEntity>,
        minDeltaWatts: Int = MIN_DELTA_WATTS
    ): List<ApplianceEvent> {
        val sorted = readings
            .filter { it.loadPowerWatts != null }
            .sortedBy { it.timestampEpochMillis }
        if (sorted.size < 2) return emptyList()

        val events = mutableListOf<ApplianceEvent>()
        for (i in 1 until sorted.size) {
            val previous = sorted[i - 1].loadPowerWatts ?: continue
            val current = sorted[i].loadPowerWatts ?: continue
            val delta = current - previous
            if (abs(delta) < minDeltaWatts) continue

            val magnitude = abs(delta)
            // Coincidencia por RANGO de consumo (ver ApplianceEntity): un
            // inverter encaja si el escalón cae en cualquier punto entre su
            // mínimo y su máximo. Empate resuelto por cercanía al rango.
            val candidates = appliances.filter { it.matches(magnitude) }
            val best = candidates.minByOrNull { it.distanceTo(magnitude) }

            events += ApplianceEvent(
                epochMillis = sorted[i].timestampEpochMillis,
                deltaWatts = delta,
                totalWattsAfter = current,
                matchedAppliance = best,
                candidateCount = candidates.size
            )
        }
        // Más reciente primero, igual que el resto de bitácoras de la app.
        return events.reversed()
    }
}
