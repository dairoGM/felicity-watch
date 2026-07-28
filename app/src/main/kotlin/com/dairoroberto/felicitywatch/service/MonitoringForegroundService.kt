package com.dairoroberto.felicitywatch.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dairoroberto.felicitywatch.data.local.AppPreferences
import com.dairoroberto.felicitywatch.data.local.CredentialsStore
import com.dairoroberto.felicitywatch.data.repository.AlertRuleRepository
import com.dairoroberto.felicitywatch.data.repository.FelicityRepository
import com.dairoroberto.felicitywatch.domain.usecase.DispatchAlertUseCase
import com.dairoroberto.felicitywatch.domain.usecase.EvaluateAlertRulesUseCase
import com.dairoroberto.felicitywatch.notification.NotificationChannels
import com.dairoroberto.felicitywatch.domain.model.GridState
import com.dairoroberto.felicitywatch.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Foreground Service tipo dataSync (guía sección 6). Android mata procesos
 * en background agresivamente (Xiaomi/MIUI confirmado como entorno real del
 * usuario); esta es la única forma confiable de garantizar polling continuo
 * cada 30s. WorkManager actúa como respaldo (ver [ServiceWatchdogWorker]).
 */
@AndroidEntryPoint
class MonitoringForegroundService : Service() {

    @Inject lateinit var felicityRepository: FelicityRepository
    @Inject lateinit var alertRuleRepository: AlertRuleRepository
    @Inject lateinit var evaluateAlertRulesUseCase: EvaluateAlertRulesUseCase
    @Inject lateinit var dispatchAlertUseCase: DispatchAlertUseCase
    @Inject lateinit var appPreferences: AppPreferences
    @Inject lateinit var credentialsStore: CredentialsStore
    @Inject lateinit var stateHolder: MonitoringStateHolder

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var pollingJob: Job? = null
    private var tickerJob: Job? = null
    private var consecutiveFailures = 0
    private var lastReadingAt: Instant? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
        startForeground(NOTIFICATION_ID, buildPersistentNotification("Iniciando vigilancia…"))
        stateHolder.setServiceRunning(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (pollingJob?.isActive != true) {
            startPollingLoop()
            startNotificationTicker()
        }
        return START_STICKY
    }

    private fun startPollingLoop() {
        pollingJob = serviceScope.launch {
            while (true) {
                runCycle()
                delay(POLLING_INTERVAL_MILLIS)
            }
        }
    }

    private fun startNotificationTicker() {
        tickerJob = serviceScope.launch {
            while (true) {
                delay(TICKER_INTERVAL_MILLIS)
                if (consecutiveFailures < MAX_CONSECUTIVE_FAILURES_BEFORE_WARNING) {
                    val secondsSinceLastReading = lastReadingAt?.let {
                        java.time.Duration.between(it, Instant.now()).seconds
                    }
                    val text = if (secondsSinceLastReading != null) {
                        "Vigilando el inversor · última lectura hace $secondsSinceLastReading s"
                    } else {
                        "Vigilando el inversor · esperando primera lectura…"
                    }
                    updatePersistentNotification(text)
                }
            }
        }
    }

    private suspend fun runCycle() {
        if (!credentialsStore.hasFsolarCredentials()) {
            updatePersistentNotification("Sin credenciales de FSolar configuradas")
            return
        }

        try {
            val reading = felicityRepository.fetchLatestReading()
            consecutiveFailures = 0
            lastReadingAt = Instant.now()
            appPreferences.setLastReadingNow(lastReadingAt!!.toEpochMilli())
            stateHolder.updateReadings(reading.inverter, reading.battery)

            val enabledRules = alertRuleRepository.getEnabledRules()
            val triggers = evaluateAlertRulesUseCase.evaluate(enabledRules, reading, lastReadingAt!!)
            triggers.forEach { trigger ->
                dispatchAlertUseCase.dispatch(trigger.rule, trigger.message)
                val gridState = when (trigger.rule.type) {
                    com.dairoroberto.felicitywatch.domain.model.AlertRuleType.GRID_OFFLINE -> GridState.OFFLINE
                    com.dairoroberto.felicitywatch.domain.model.AlertRuleType.GRID_ONLINE -> GridState.ONLINE
                    else -> null
                }
                gridState?.let { stateHolder.updateConfirmedGridState(it, lastReadingAt!!) }
            }

            updatePersistentNotification("Vigilando el inversor · última lectura hace 0 s")
        } catch (e: Exception) {
            consecutiveFailures++
            stateHolder.setError(e.message ?: e.toString())
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES_BEFORE_WARNING) {
                val minutesSinceLastReading = lastReadingAt?.let {
                    java.time.Duration.between(it, Instant.now()).toMinutes()
                } ?: 0
                updatePersistentNotification("Sin conexión con Felicity desde hace $minutesSinceLastReading min")
            }
        }
    }

    private fun buildPersistentNotification(text: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NotificationChannels.SERVICE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Felicity Watch")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updatePersistentNotification(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
        manager.notify(NOTIFICATION_ID, buildPersistentNotification(text))
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        tickerJob?.cancel()
        serviceScope.cancel()
        stateHolder.setServiceRunning(false)
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val POLLING_INTERVAL_MILLIS = 30_000L
        const val TICKER_INTERVAL_MILLIS = 5_000L
        const val MAX_CONSECUTIVE_FAILURES_BEFORE_WARNING = 5
    }
}
