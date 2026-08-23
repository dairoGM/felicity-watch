package com.dairoroberto.felicitywatch.domain.usecase

/**
 * Horas estimadas de autonomía restante de la batería sin corriente de red,
 * o null si falta algún dato necesario para el cálculo. Fórmula:
 * (capacidadAh × voltaje × SOC% / 100) / consumoW. Usada tanto por el
 * anillo de Autonomía del Panel como por la alerta de autonomía baja, para
 * que ambos coincidan siempre (mismo umbral rojo del anillo = mismo umbral
 * de la alerta).
 */
fun estimateBatteryRuntimeHours(
    socPercent: Int?,
    loadWatts: Int?,
    capacityAh: Double?,
    voltage: Double?
): Double? {
    if (socPercent == null || loadWatts == null || loadWatts <= 0 ||
        capacityAh == null || capacityAh <= 0 || voltage == null || voltage <= 0
    ) return null
    val availableWh = capacityAh * voltage * (socPercent / 100.0)
    return availableWh / loadWatts
}
