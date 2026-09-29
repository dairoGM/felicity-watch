package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.data.local.AppPreferences
import com.dairoroberto.felicitywatch.data.local.CredentialsStore
import com.dairoroberto.felicitywatch.data.repository.AlertRuleRepository
import com.dairoroberto.felicitywatch.data.repository.DeviceRoleRepository
import com.dairoroberto.felicitywatch.data.repository.FelicityCredentialsMissingException
import com.dairoroberto.felicitywatch.data.repository.FelicityRepository
import com.dairoroberto.felicitywatch.data.repository.PowerHistoryRepository
import com.dairoroberto.felicitywatch.data.repository.SystemReading
import com.dairoroberto.felicitywatch.domain.model.AlertRuleType
import com.dairoroberto.felicitywatch.domain.model.GridState
import com.dairoroberto.felicitywatch.service.MonitoringStateHolder
import kotlinx.coroutines.flow.first
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
    private val notifyLowVoltageUseCase: NotifyLowVoltageUseCase,
    private val deviceRoleRepository: DeviceRoleRepository,
    private val updateDeviceLocationUseCase: UpdateDeviceLocationUseCase,
    private val evaluateDeviceApprovalUseCase: EvaluateDeviceApprovalUseCase,
    private val notifyMasterOfClientActivityUseCase: NotifyMasterOfClientActivityUseCase
) {
    suspend fun run(): SystemReading {
        if (!credentialsStore.hasFsolarCredentials()) {
            throw FelicityCredentialsMissingException()
        }

        // Revalida en CADA ciclo (misma cadencia que la consulta a
        // Felicity), no solo al reabrir la app — así, si la master revoca o
        // elimina a este cliente, se detecta en la siguiente lectura
        // programada, sin depender de que el usuario minimice y vuelva a
        // abrir la app. EvaluateDeviceApprovalUseCase ya escribe
        // clientApprovalConfirmed=false al detectarlo, que RootViewModel
        // observa para sacar al usuario de inmediato a la pantalla de
        // código. Se corta ANTES de consultar Felicity: sin acceso, no
        // tiene sentido gastar esa lectura.
        // Cualquier decisión distinta de Allowed corta el ciclo, no solo
        // Blocked: una licencia vencida o bloqueada también quita el acceso, y
        // comparar contra Blocked a secas dejaría a ese cliente monitoreando en
        // segundo plano mientras la UI le muestra la pantalla de pago.
        if (evaluateDeviceApprovalUseCase.evaluate() != DeviceAccessDecision.Allowed) {
            // Avisa a la UI para que re-evalúe y cambie de pantalla sola. Sin
            // esto, una licencia vencida cortaba las lecturas pero dejaba al
            // usuario viendo el Panel hasta que cerrara y reabriera la app: el
            // canal que ya existía (clientApprovalConfirmed) solo cubre la
            // revocación del dispositivo, no el vencimiento de la licencia.
            stateHolder.signalAccessLost()
            throw DeviceAccessRevokedException()
        }

        // No-op en un cliente (ver el chequeo de isMasterDevice adentro). Va
        // ANTES de consultar Felicity para que un fallo de Supabase (offline,
        // migración sin aplicar) no bloquee la lectura real del inversor —
        // esto es informativo, la lectura de potencia no lo es.
        runCatching { notifyMasterOfClientActivityUseCase.run() }

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

        // "Última actividad" para la vista de Dispositivos en la master —
        // cada ciclo exitoso (master o cliente ya aprobado) es evidencia de
        // que este dispositivo sigue vivo. Best-effort: sin red o con
        // Supabase caído, no debe cortar el ciclo de monitoreo.
        try {
            deviceRoleRepository.touchLastSeen()
        } catch (e: Exception) {
            // Ignorado a propósito.
        }

        // Ubicación para el mapa de la master — moderada internamente por
        // UpdateDeviceLocationUseCase (no en cada ciclo). Best-effort igual
        // que touchLastSeen: sin permiso de ubicación o sin GPS/red
        // disponible, simplemente no actualiza nada este ciclo.
        try {
            updateDeviceLocationUseCase.run()
        } catch (e: Exception) {
            // Ignorado a propósito.
        }

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
        val triggers = evaluateAlertRulesUseCase.evaluate(
            rules = enabledRules,
            reading = reading,
            now = now,
            pvAlertWindowStartHour = appPreferences.pvAlertWindowStartHour.first(),
            pvAlertWindowEndHour = appPreferences.pvAlertWindowEndHour.first()
        )
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
