package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.data.local.AlertRuleEntity
import com.dairoroberto.felicitywatch.data.repository.SystemReading
import com.dairoroberto.felicitywatch.domain.model.AlertRuleType
import com.dairoroberto.felicitywatch.domain.model.ComparisonOperator
import com.dairoroberto.felicitywatch.domain.model.GridState
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class AlertTrigger(val rule: AlertRuleEntity, val message: String)

/**
 * Motor de evaluación de reglas (guía sección 5). Mantiene un único
 * [GridStateDebouncer] compartido entre GRID_OFFLINE/GRID_ONLINE (son la
 * misma señal subyacente) y un [SocThresholdDebouncer] por regla de SOC,
 * reconstruido solo si el usuario cambia su configuración (umbral,
 * operador o debounce). Anti-duplicado garantizado por los propios
 * debouncers vía `confirmedState`.
 */
@Singleton
class EvaluateAlertRulesUseCase @Inject constructor() {

    private data class GridConfig(val debounceSeconds: Int, val wattThreshold: Int)
    private var gridDebouncer: GridStateDebouncer? = null
    private var gridDebouncerConfig: GridConfig? = null

    private data class SocConfig(val threshold: Double, val operator: ComparisonOperator, val debounceSeconds: Int)
    private val socDebouncers = mutableMapOf<Long, Pair<SocConfig, SocThresholdDebouncer>>()
    private val loadDebouncers = mutableMapOf<Long, Pair<SocConfig, SocThresholdDebouncer>>()
    private val autonomyDebouncers = mutableMapOf<Long, Pair<SocConfig, DoubleThresholdDebouncer>>()
    private val pvLossDebouncers = mutableMapOf<Long, Pair<SocConfig, SocThresholdDebouncer>>()

    fun evaluate(
        rules: List<AlertRuleEntity>,
        reading: SystemReading,
        now: Instant,
        /** Horario esperado de generación solar (hora local, 24h), para
         * [evaluatePvGenerationLossRules] — configurable en Ajustes porque
         * el amanecer/anochecer real depende de la instalación (sombras del
         * terreno, orientación de paneles), y un valor fijo arriesga falsos
         * positivos justo al arrancar o terminar el día. */
        pvAlertWindowStartHour: Int = 7,
        pvAlertWindowEndHour: Int = 18
    ): List<AlertTrigger> {
        val triggers = mutableListOf<AlertTrigger>()

        evaluateGridRules(rules, reading, now)?.let { triggers += it }
        triggers += evaluateSocRules(rules, reading, now)
        triggers += evaluateLoadRules(rules, reading, now)
        triggers += evaluateAutonomyRules(rules, reading, now)
        triggers += evaluatePvGenerationLossRules(rules, reading, now, pvAlertWindowStartHour, pvAlertWindowEndHour)

        return triggers
    }

    private fun evaluateGridRules(
        rules: List<AlertRuleEntity>,
        reading: SystemReading,
        now: Instant
    ): AlertTrigger? {
        val gridOffline = rules.firstOrNull { it.type == AlertRuleType.GRID_OFFLINE }
        val gridOnline = rules.firstOrNull { it.type == AlertRuleType.GRID_ONLINE }
        val inverter = reading.inverter ?: return null

        if (gridOffline?.enabled != true && gridOnline?.enabled != true) return null

        val debounceSeconds = gridOffline?.debounceSeconds ?: gridOnline?.debounceSeconds ?: 60
        val wattThreshold = (gridOffline?.thresholdValue ?: gridOnline?.thresholdValue ?: 1.0).toInt()
        val config = GridConfig(debounceSeconds, wattThreshold)
        if (gridDebouncer == null || gridDebouncerConfig != config) {
            gridDebouncer = GridStateDebouncer(debounceSeconds, wattThreshold)
            gridDebouncerConfig = config
        }

        val confirmed = gridDebouncer?.onNewReading(inverter.gridPowerWatts, now) ?: return null

        return when (confirmed) {
            GridState.OFFLINE -> gridOffline?.takeIf { it.enabled }?.let { AlertTrigger(it, it.messageTemplate) }
            GridState.ONLINE -> gridOnline?.takeIf { it.enabled }?.let { AlertTrigger(it, it.messageTemplate) }
            GridState.UNKNOWN -> null
        }
    }

    private fun evaluateSocRules(
        rules: List<AlertRuleEntity>,
        reading: SystemReading,
        now: Instant
    ): List<AlertTrigger> {
        val soc = reading.battery?.socPercent ?: return emptyList()
        val triggers = mutableListOf<AlertTrigger>()

        rules
            .filter { it.type == AlertRuleType.BATTERY_SOC_LOW || it.type == AlertRuleType.BATTERY_SOC_HIGH }
            .forEach { rule ->
                val threshold = rule.thresholdValue
                val operator = rule.comparisonOperator
                if (!rule.enabled || threshold == null || operator == null) return@forEach

                val config = SocConfig(threshold, operator, rule.debounceSeconds)
                val cached = socDebouncers[rule.id]
                val debouncer = if (cached == null || cached.first != config) {
                    SocThresholdDebouncer(config.debounceSeconds, config.threshold, config.operator).also {
                        socDebouncers[rule.id] = config to it
                    }
                } else {
                    cached.second
                }

                if (debouncer.onNewReading(soc, now) == true) {
                    triggers += AlertTrigger(rule, rule.messageTemplate)
                }
            }

        return triggers
    }

    /**
     * Consumo de la casa por encima del umbral (por defecto 7kW, con un
     * inversor de 8k) — aviso de que se está cerca del límite del equipo,
     * no un simple dato informativo.
     */
    private fun evaluateLoadRules(
        rules: List<AlertRuleEntity>,
        reading: SystemReading,
        now: Instant
    ): List<AlertTrigger> {
        val loadWatts = reading.inverter?.loadPowerWatts ?: return emptyList()
        val triggers = mutableListOf<AlertTrigger>()

        rules
            .filter { it.type == AlertRuleType.LOAD_HIGH }
            .forEach { rule ->
                val threshold = rule.thresholdValue
                val operator = rule.comparisonOperator
                if (!rule.enabled || threshold == null || operator == null) return@forEach

                val config = SocConfig(threshold, operator, rule.debounceSeconds)
                val cached = loadDebouncers[rule.id]
                val debouncer = if (cached == null || cached.first != config) {
                    SocThresholdDebouncer(config.debounceSeconds, config.threshold, config.operator).also {
                        loadDebouncers[rule.id] = config to it
                    }
                } else {
                    cached.second
                }

                if (debouncer.onNewReading(loadWatts, now) == true) {
                    triggers += AlertTrigger(rule, rule.messageTemplate)
                }
            }

        return triggers
    }

    /**
     * Autonomía de batería baja (mismo umbral de horas que pone en rojo el
     * anillo "AUTONOMÍA" del Panel — ver [estimateBatteryRuntimeHours]).
     * Solo aplica SIN corriente de red: con red, la batería está protegida
     * (cargando) y el anillo nunca se pone rojo, así que la alerta tampoco
     * debe evaluarse en ese caso.
     */
    private fun evaluateAutonomyRules(
        rules: List<AlertRuleEntity>,
        reading: SystemReading,
        now: Instant
    ): List<AlertTrigger> {
        val activeRules = rules.filter { it.type == AlertRuleType.BATTERY_AUTONOMY_LOW && it.enabled }
        if (activeRules.isEmpty()) return emptyList()

        val onGrid = (reading.inverter?.gridPowerWatts ?: 0) >= 1
        if (onGrid) return emptyList()

        val runtimeHours = estimateBatteryRuntimeHours(
            socPercent = reading.battery?.socPercent,
            loadWatts = reading.inverter?.loadPowerWatts,
            capacityAh = reading.battery?.capacityAh,
            voltage = reading.battery?.voltage,
            pvWatts = reading.inverter?.pvPowerWatts
        ) ?: return emptyList()
        // Infinita = el sol cubre el consumo, la batería no se está
        // descargando ahora mismo: no hay "autonomía baja" que avisar,
        // aunque el umbral configurado sea un número finito de horas.
        if (runtimeHours.isInfinite()) return emptyList()

        val triggers = mutableListOf<AlertTrigger>()
        activeRules.forEach { rule ->
            val threshold = rule.thresholdValue
            val operator = rule.comparisonOperator
            if (threshold == null || operator == null) return@forEach

            val config = SocConfig(threshold, operator, rule.debounceSeconds)
            val cached = autonomyDebouncers[rule.id]
            val debouncer = if (cached == null || cached.first != config) {
                DoubleThresholdDebouncer(config.debounceSeconds, config.threshold, config.operator).also {
                    autonomyDebouncers[rule.id] = config to it
                }
            } else {
                cached.second
            }

            if (debouncer.onNewReading(runtimeHours, now) == true) {
                triggers += AlertTrigger(rule, rule.messageTemplate)
            }
        }

        return triggers
    }

    /**
     * Generación fotovoltaica caída a 0 (o casi) DURANTE el horario en que
     * debería haber sol — síntoma de una falla real (inversor, cableado,
     * panel desconectado), no de la noche, que es cuando 0W es lo normal y
     * esperado. Se evalúa solo dentro de [pvAlertWindowStartHour]..
     * [pvAlertWindowEndHour] (hora local, configurable en Ajustes); fuera de
     * esa franja la regla ni siquiera se considera, para no avisar cada
     * noche ni disparar un falso positivo si el sol todavía no llegó a los
     * paneles a la hora configurada.
     *
     * El debounce (varios minutos, configurable) es lo que distingue una
     * nube pasajera momentánea de una falla real y sostenida — igual
     * criterio que el resto de reglas de umbral de esta clase.
     */
    private fun evaluatePvGenerationLossRules(
        rules: List<AlertRuleEntity>,
        reading: SystemReading,
        now: Instant,
        pvAlertWindowStartHour: Int,
        pvAlertWindowEndHour: Int
    ): List<AlertTrigger> {
        val activeRules = rules.filter { it.type == AlertRuleType.PV_GENERATION_LOST && it.enabled }
        if (activeRules.isEmpty()) return emptyList()

        val hour = now.atZone(ZoneId.systemDefault()).hour
        if (hour !in pvAlertWindowStartHour until pvAlertWindowEndHour) return emptyList()

        val pvWatts = reading.inverter?.pvPowerWatts ?: return emptyList()
        val triggers = mutableListOf<AlertTrigger>()

        activeRules.forEach { rule ->
            val threshold = rule.thresholdValue
            val operator = rule.comparisonOperator
            if (threshold == null || operator == null) return@forEach

            val config = SocConfig(threshold, operator, rule.debounceSeconds)
            val cached = pvLossDebouncers[rule.id]
            val debouncer = if (cached == null || cached.first != config) {
                SocThresholdDebouncer(config.debounceSeconds, config.threshold, config.operator).also {
                    pvLossDebouncers[rule.id] = config to it
                }
            } else {
                cached.second
            }

            if (debouncer.onNewReading(pvWatts, now) == true) {
                triggers += AlertTrigger(rule, rule.messageTemplate)
            }
        }

        return triggers
    }
}
