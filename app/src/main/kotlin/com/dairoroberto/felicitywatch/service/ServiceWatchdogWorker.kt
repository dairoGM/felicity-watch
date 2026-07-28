package com.dairoroberto.felicitywatch.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dairoroberto.felicitywatch.data.local.CredentialsStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Respaldo de WorkManager (guía sección 6): si el Foreground Service fue
 * matado por el sistema (Xiaomi/MIUI y similares lo hacen agresivamente),
 * este worker periódico lo vuelve a arrancar. No reemplaza al servicio,
 * solo lo relanza cuando detecta que no está vivo.
 */
@HiltWorker
class ServiceWatchdogWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val credentialsStore: CredentialsStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (credentialsStore.hasFsolarCredentials()) {
            MonitoringServiceController.start(applicationContext)
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "felicity_watch_service_watchdog"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
                Duration.ofMinutes(15).toMinutes(), TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
