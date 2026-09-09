package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.data.local.AppPreferences
import com.dairoroberto.felicitywatch.data.local.CredentialsStore
import com.dairoroberto.felicitywatch.data.repository.AlertRuleRepository
import com.dairoroberto.felicitywatch.data.repository.FelicityCredentialsMissingException
import com.dairoroberto.felicitywatch.data.repository.FelicityRepository
import com.dairoroberto.felicitywatch.data.repository.PowerHistoryRepository
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
 * (en la cadencia configurada en Ajustes) y las acciones manuales de
 * "primera lectura / probar conexión"
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
    private val stateHolder: MonitoringStateHolder,
    private val powerHistoryRepository: PowerHistoryRepository,
    private val notifyApplianceChangeUseCase: NotifyApplianceChangeUseCase,
    private val notifyLowVoltageUseCase: NotifyLowVoltageUseCase
) {
    suspend fun run(): SystemReading {
        if (!credentialsStore.hasFsolarCredentials()) {
            throw FelicityCredentialsMissingException()
        }

        val reading = felicityRepository.fetchLatestReading()
        val now = Instant.now()
        appPreferences.setLastReadingNow(now.toEpochMilli())
        stateHolder.updateReadings(
            inverter = reading.inverter,
            battery = reading.battery,
            now = now,
            inverterError = reading.inverterError,
            batteryError = reading.batteryError,
            inverterRawJson = reading.inverterRawJson,
            batteryRawJson = reading.batteryRawJson
        )
        val pvPowerWatts = reading.inverter?.pvPowerWatts
        val loadPowerWatts = reading.inverter?.loadPowerWatts
        val batteryPowerWatts = if (pvPowerWatts != null && loadPowerWatts != null) {
            pvPowerWatts - loadPowerWatts
        } else null
        powerHistoryRepository.record(
            pvPowerWatts = pvPowerWatts,
            gridPowerWatts = reading.inverter?.gridPowerWatts,
            socPercent = reading.battery?.socPercent,
            loadPowerWatts = loadPowerWatts,
            batteryPowerWatts = batteryPowerWatts,
            pvEnergyTodayKwh = reading.inverter?.pvEnergyTodayKwh,
            loadEnergyTodayKwh = reading.inverter?.loadEnergyTodayKwh,
            gridVoltage = reading.inverter?.gridVoltage,
            outputVoltage = reading.inverter?.outputVoltage,
            now = now
        )

        // Aviso de equipo encendido/apagado. Va después de registrar el
        // historial y envuelto en try/catch porque es una función accesoria:
        // si falla (sin vibrador, audio no disponible), el ciclo de
        // monitoreo debe continuar igual.
        try {
            notifyApplianceChangeUseCase.onLoadReading(loadPowerWatts)
        } catch (e: Exception) {
            // Ignorado a propósito: no vale perder la lectura por el aviso.
        }

        // Aviso de voltaje bajo. Se pasa el estado de red vigente porque de él
        // depende cuál voltaje vigilar: el de la calle o el de la batería.
        try {
            notifyLowVoltageUseCase.onReading(reading, stateHolder.liveGridState.value)
        } catch (e: Exception) {
            // Accesorio igual que el aviso de equipos: no debe cortar el ciclo.
        }

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
