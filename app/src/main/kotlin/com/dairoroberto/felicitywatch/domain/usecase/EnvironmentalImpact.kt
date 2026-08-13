package com.dairoroberto.felicitywatch.domain.usecase

/** Impacto ambiental estimado por la energía PV generada, con los mismos
 * factores estándar de conversión que usan la mayoría de apps de monitoreo
 * solar (no vienen de la API de Felicity, que no reporta nada ambiental). */
data class EnvironmentalImpact(
    val co2AvoidedKg: Double,
    val coalSavedKg: Double,
    val treesEquivalent: Double
)

/**
 * Factor de emisión de la red eléctrica de Cuba (~0.65 kg CO2/kWh, típico
 * de una red con generación mayoritariamente térmica a base de fuel oil/
 * diésel) — determina cuánto CO2 se evita por cada kWh que el sistema
 * solar generó en vez de tomarlo de la red.
 */
private const val CO2_PER_KWH = 0.65

/** kg de carbón equivalentes por kg de CO2 — factor estándar de combustión
 * de carbón (~2.42 kg CO2 por kg de carbón quemado). */
private const val CO2_PER_KG_COAL = 2.42

/** kg de CO2 que un árbol adulto absorbe por año, expresado por kWh vía el
 * factor de emisión — referencia estándar EPA (~21 kg CO2/árbol/año). */
private const val CO2_ABSORBED_PER_TREE_PER_YEAR_KG = 21.0

fun computeEnvironmentalImpact(generatedKwh: Double): EnvironmentalImpact {
    val co2AvoidedKg = generatedKwh * CO2_PER_KWH
    val coalSavedKg = co2AvoidedKg / CO2_PER_KG_COAL
    val treesEquivalent = co2AvoidedKg / CO2_ABSORBED_PER_TREE_PER_YEAR_KG
    return EnvironmentalImpact(co2AvoidedKg, coalSavedKg, treesEquivalent)
}
