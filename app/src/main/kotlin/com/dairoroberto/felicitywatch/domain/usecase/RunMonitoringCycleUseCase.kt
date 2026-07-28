package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.data.local.AppPreferences
import com.dairoroberto.felicitywatch.data.local.CredentialsStore
import com.dairoroberto.felicitywatch.data.repository.AlertRuleRepository
import com.dairoroberto.felicitywatch.data.repository.FelicityCredentialsMissingException
import com.dairoroberto.felicitywatch.data.repository.FelicityRepository
import com.dairoroberto.felicitywatch.data.repository.SystemReading
import com.dairoroberto.felicitywatch.domain.model.AlertRuleType
import com.dairoroberto.felicitywatch.domain.model.GridState
import com.dairoroberto.felicitywatch.service.MonitoringStateHolder
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Un ciclo completo de lectura: obtiene el snapshot, actualiza el estado en
 * vivo que consume la UI, evalúa las reglas de alerta y despacha lo que
 * corresponda. Compartido entre [com.dairoroberto.felicitywatch.service.MonitoringForegroundService]
 * (cada 30s) y las acciones manuales de "primera lectura / probar conexión"
 * (Panel al deslizar hacia abajo, botón en Ajustes) para no duplicar la
 * lógica — la única diferencia entre ambos casos es quién cuenta los fallos
 * consecutivos y actualiza la notificación persistente, que se queda en el
 * servicio.
 */
@Singleton
class RunMonitoringCycleUseCase @Inject constructor(
    private val felicityRepository: FelicityRepository,
    private val alertRuleRepository: AlertRuleRepository,
    private val evaluateAlertRulesUseCase: EvaluateAlertRulesUseCase,
    private val dispatchAlertUseCase: DispatchAlertUseCase,
    private val appPreferences: AppPreferences,
    private val credentialsStore: CredentialsStore,
    private val stateHolder: MonitoringStateHolder
) {
    suspend fun run(): SystemReading {
        if (!credentialsStore.hasFsolarCredentials()) {
            throw FelicityCredentialsMissingException()
        }

        val reading = felicityRepository.fetchLatestReading()
        val now = Instant.now()
        appPreferences.setLastReadingNow(now.toEpochMilli())
        stateHolder.updateReadings(reading.inverter, reading.battery, now)

        val enabledRules = alertRuleRepository.getEnabledRules()
        val triggers = evaluateAlertRulesUseCase.evaluate(enabledRules, reading, now)
        triggers.forEach { trigger ->
            dispatchAlertUseCase.dispatch(trigger.rule, trigger.message)
            val gridState = when (trigger.rule.type) {
                AlertRuleType.GRID_OFFLINE -> GridState.OFFLINE
                AlertRuleType.GRID_ONLINE -> GridState.ONLINE
                else -> null
            }
            gridState?.let { stateHolder.updateConfirmedGridState(it, now) }
        }

        return reading
    }
}
