package com.dairoroberto.felicitywatch.domain.usecase

/**
 * Horas estimadas de autonomía restante de la batería sin corriente de red,
 * o null si falta algún dato necesario, o [Double.POSITIVE_INFINITY] si la
 * generación solar cubre el consumo — sea porque lo supera, sea porque el
 * déficit es tan pequeño que queda dentro del ruido de medición (ver
 * [MIN_MEANINGFUL_DEFICIT_WATTS]). En ambos casos la batería no se está
 * descargando de forma apreciable y no hay cuenta regresiva que calcular.
 *
 * Fórmula: (capacidadAh × voltaje × SOC% / 100) / DÉFICIT, donde déficit =
 * consumoW − pvWatts (nunca negativo). Usa el déficit y no el consumo bruto
 * a propósito: sin esto, con la batería al 100% y el sol generando más de
 * lo que la casa consume, igual se reportaban horas de autonomía como si
 * toda la casa dependiera solo de la batería — un número que ni siquiera
 * corresponde a lo que está pasando en ese momento (la batería casi no se
 * mueve, o incluso está cargando con el excedente).
 *
 * Usada tanto por el anillo de Autonomía del Panel como por la alerta de
 * autonomía baja, para que ambos coincidan siempre (mismo umbral rojo del
 * anillo = mismo umbral de la alerta).
 */
fun estimateBatteryRuntimeHours(
    socPercent: Int?,
    loadWatts: Int?,
    capacityAh: Double?,
    voltage: Double?,
    /** Generación solar en el mismo instante. null = se asume 0 (todo el
     * consumo recae en la batería), que es el comportamiento anterior a
     * este parámetro y sigue siendo correcto cuando de verdad no hay dato
     * de PV. */
    pvWatts: Int? = null
): Double? {
    if (socPercent == null || loadWatts == null || loadWatts <= 0 ||
        capacityAh == null || capacityAh <= 0 || voltage == null || voltage <= 0
    ) return null

    val deficitWatts = loadWatts - (pvWatts ?: 0)
    if (deficitWatts <= 0) return Double.POSITIVE_INFINITY

    // Un déficit marginal no es una autonomía: es un empate entre PV y
    // consumo. Con 700W de sol contra 710W de casa el déficit son 10W, y la
    // división daba 1687 horas — 70 días de autonomía anunciados en el Panel.
    // Aritméticamente correcto, pero la premisa es falsa: esos 10W son la
    // diferencia entre dos medidas que fluctúan en la escala de los cientos de
    // vatios, o sea ruido, no una tendencia que vaya a sostenerse 70 días. Que
    // pase una nube y el déficit real salta a 700W (~24h).
    //
    // Por debajo del umbral se reporta "el sol cubre el consumo" (el mismo
    // INFINITY del caso sin déficit), que es lo que de verdad está pasando:
    // la batería no se está gastando de forma apreciable. Se prefiere eso a un
    // número enorme que el usuario leería como una reserva que no tiene.
    if (deficitWatts < MIN_MEANINGFUL_DEFICIT_WATTS) return Double.POSITIVE_INFINITY

    val availableWh = capacityAh * voltage * (socPercent / 100.0)
    return availableWh / deficitWatts
}

/**
 * Déficit mínimo (W) para que una cuenta regresiva signifique algo.
 *
 * 50W: por debajo de eso el "déficit" queda dentro del margen de fluctuación
 * normal entre generación y consumo, y las horas resultantes son absurdas
 * (>13 días en un banco de 16kWh). Por encima, incluso el peor caso da cifras
 * en el rango de días, que ya es un dato honesto y accionable.
 */
private const val MIN_MEANINGFUL_DEFICIT_WATTS = 50
