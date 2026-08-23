package com.dairoroberto.felicitywatch.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Equipo de la casa registrado por el usuario, definido por un RANGO de
 * consumo — se usa para proponer una coincidencia cuando se detecta un
 * escalón de consumo ("subió 1,180 W → probablemente el Aire").
 *
 * Por qué un rango y no un valor único con tolerancia simétrica: los
 * equipos inverter (splits, neveras modernas) modulan su consumo de forma
 * continua. Un split de ~1,100 W nominal puede consumir 400 W manteniendo
 * temperatura y 1,600 W en arranque. Un valor único con ±tolerancia no
 * describe eso: o se pone una tolerancia enorme (y entonces coincide con
 * todo), o se pierde la mitad del rango real de operación.
 *
 * IMPORTANTE sobre la precisión: esto NO identifica equipos de verdad
 * (eso requeriría muestreo de alta frecuencia y análisis de firma
 * eléctrica, que el inversor no expone). Es una heurística sobre el rango
 * que el propio usuario declaró, así que dos equipos con rangos
 * solapados son indistinguibles entre sí.
 */
@Entity(tableName = "appliances")
data class ApplianceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Consumo máximo del equipo (arranque / régimen fuerte). Para equipos
     * de consumo fijo coincide con [minWatts]. */
    val watts: Int,
    /** Consumo mínimo en operación. En un equipo de consumo fijo
     * (plancha, bombillo) es igual a [watts]; en un inverter es lo que
     * consume manteniendo, que puede ser mucho menor. */
    val minWatts: Int = watts,
    /** Holgura extra a ambos extremos del rango, para absorber la
     * imprecisión de la medición y del valor declarado. */
    val toleranceWatts: Int = DEFAULT_TOLERANCE_WATTS,
    /** Local/habitación donde está el equipo — texto libre para no imponer
     * una lista fija de cuartos que nunca calzaría con todas las casas,
     * pero con autocompletado en la UI para que no convivan "Sala" y
     * "sala" como dos locales distintos. Vacío = sin asignar. */
    val room: String = "",
    /** Consumo medido realmente por la app durante la confirmación (el
     * salto observado al encender el equipo), en watts. null = todavía no
     * se ha confirmado midiendo.
     *
     * Al confirmar, este valor SOBRESCRIBE [watts]/[minWatts]: lo medido en
     * esta casa es mas fiable que la etiqueta. Se conserva ademas aparte para
     * saber que el consumo vigente vino de una medicion y no de lo que el
     * usuario escribio, y para poder revertirlo (ver resetLearning). */
    val confirmedWatts: Int? = null,
    /** Cuándo se confirmó (epoch millis). null = sin confirmar. La
     * confirmación se hace una sola vez por equipo, pero se puede repetir
     * manualmente si el usuario quiere recalibrar. */
    val confirmedAtEpochMillis: Long? = null,
    /**
     * Grupo de equipos ELÉCTRICAMENTE SIMILARES, para aprendizaje compartido.
     * Vacío = el equipo es único y solo aprende de sus propias confirmaciones.
     *
     * Existe por un caso concreto: varios splits del mismo tipo consumen
     * prácticamente lo mismo, y son indistinguibles entre sí en el consumo
     * total. Cuando el usuario confirma "fue un split convencional", ese dato
     * vale para TODOS los splits convencionales, no solo para el que marcó —
     * si no se compartiera, habría que confirmar cada uno por separado para
     * aprender el mismo número.
     *
     * Es texto libre con autocompletado (igual que [room]) en vez de una
     * lista fija: los tipos de equipo que se repiten dependen de la casa.
     */
    val similarGroup: String = "",
    /**
     * Cuántas veces se ha confirmado este equipo (o su grupo). Se usa para
     * promediar: cada confirmación nueva corrige el valor aprendido en vez de
     * reemplazarlo de golpe, así una lectura atípica no arruina lo aprendido.
     */
    val confirmationCount: Int = 0
) {
    /** true si el consumo de este equipo ya fue verificado midiendo con la
     * app, no solo declarado desde la etiqueta. */
    val isConfirmed: Boolean get() = confirmedWatts != null

    /** true si el equipo tiene un rango real (típico de inverter), no un
     * consumo fijo — la UI lo usa para explicar la coincidencia. */
    val hasRange: Boolean get() = minWatts != watts

    /**
     * Rango de watts contra el que se comparan los escalones detectados,
     * SIN la holgura.
     *
     * Si el equipo fue confirmado midiendo, el valor medido entra al rango:
     * es el consumo real en esta casa (con su voltaje y condiciones), más
     * fiable que la etiqueta. Se une con el rango declarado en vez de
     * reemplazarlo, porque en un inverter la medición captura solo UN punto
     * de su curva (probablemente el arranque), no todo su rango de
     * operación — descartar lo declarado perdería el resto.
     */
    private fun matchRange(): IntRange {
        val declaredLow = minOf(minWatts, watts)
        val declaredHigh = maxOf(minWatts, watts)
        val measured = confirmedWatts ?: return declaredLow..declaredHigh
        return minOf(declaredLow, measured)..maxOf(declaredHigh, measured)
    }

    /** ¿Un escalón de [magnitude] watts encaja en este equipo? */
    fun matches(magnitude: Int): Boolean {
        val range = matchRange()
        val low = (range.first - toleranceWatts).coerceAtLeast(0)
        val high = range.last + toleranceWatts
        return magnitude in low..high
    }

    /** Distancia del escalón al rango — 0 si cae dentro. Se usa para
     * elegir el candidato más probable cuando varios encajan. */
    fun distanceTo(magnitude: Int): Int {
        val range = matchRange()
        return when {
            magnitude < range.first -> range.first - magnitude
            magnitude > range.last -> magnitude - range.last
            else -> 0
        }
    }

    /** true si este equipo comparte aprendizaje con otros del mismo tipo. */
    val hasSimilarGroup: Boolean get() = similarGroup.isNotBlank()

    /**
     * Incorpora un consumo observado al valor aprendido de este equipo.
     *
     * Se promedia de forma incremental en vez de reemplazar de golpe: la detección
     * mide el escalón del consumo TOTAL de la casa, así que cada observación
     * trae ruido (otro equipo que arrancó a la vez, un inverter en un punto
     * distinto de su curva). Promediando, cada confirmación acerca el valor al
     * consumo real en vez de dejarlo a merced de la última lectura.
     *
     * La primera confirmación sí toma el valor observado tal cual: no hay nada
     * previo con lo que promediar.
     */
    fun withObservation(observedWatts: Int, atEpochMillis: Long): ApplianceEntity {
        val previous = confirmedWatts
        val previousCount = confirmationCount.coerceAtLeast(0)

        val newWatts = if (previous == null || previousCount == 0) {
            observedWatts
        } else {
            // Media móvil: (media_previa * n + nuevo) / (n + 1).
            ((previous.toLong() * previousCount + observedWatts) / (previousCount + 1)).toInt()
        }

        // El valor aprendido SOBRESCRIBE el declarado, no solo se guarda al
        // lado. Antes se dejaba `watts` intacto y el equipo seguía compitiendo
        // por su valor de etiqueta: un microondas registrado con 700 W y medido
        // en 820 W seguía apareciendo como de 700 W en el inventario y seguía
        // emparejando saltos de 700 W. El dato medido en esta casa es más
        // fiable que la etiqueta, así que manda.
        //
        // En equipos con rango (inverter) se conserva el ANCHO del rango y se
        // desplaza a la medición: una sola confirmación captura un punto de su
        // curva, normalmente el arranque, y aplanar el rango a ese punto
        // perdería el resto de su operación.
        val range = maxOf(0, maxOf(watts, minWatts) - minOf(watts, minWatts))
        val newMax = newWatts
        val newMin = if (range > 0) (newWatts - range).coerceAtLeast(1) else newWatts

        return copy(
            watts = newMax,
            minWatts = newMin,
            confirmedWatts = newWatts,
            confirmedAtEpochMillis = atEpochMillis,
            confirmationCount = previousCount + 1
        )
    }

    companion object {
        const val DEFAULT_TOLERANCE_WATTS = 150
    }
}
