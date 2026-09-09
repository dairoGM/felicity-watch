package com.dairoroberto.felicitywatch.domain.usecase

/**
 * Horas estimadas de autonomía restante de la batería sin corriente de red,
 * o null si falta algún dato necesario, o [Double.POSITIVE_INFINITY] si la
 * generación solar ya cubre todo el consumo (la batería no se está
 * descargando, así que no hay una cuenta regresiva que calcular).
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

    val availableWh = capacityAh * voltage * (socPercent / 100.0)
    return availableWh / deficitWatts
}
