package com.dairoroberto.felicitywatch

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.dairoroberto.felicitywatch.data.local.CredentialsStore
import com.dairoroberto.felicitywatch.notification.NotificationChannels
import com.dairoroberto.felicitywatch.service.MonitoringServiceController
import com.dairoroberto.felicitywatch.service.ServiceWatchdogWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FelicityWatchApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var credentialsStore: CredentialsStore

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)

        // Antes esto solo se programaba en BootCompletedReceiver (solo tras
        // reiniciar el teléfono) — si Android/MIUI mataba el servicio de
        // vigilancia sin que hubiera un reinicio de por medio, nada lo volvía
        // a levantar y el polling se detenía silenciosamente. Se programa
        // aquí también, en cada arranque del proceso (abrir la app cuenta),
        // para que el respaldo esté activo sin depender de un reinicio.
        ServiceWatchdogWorker.schedule(this)
        if (credentialsStore.hasFsolarCredentials()) {
            MonitoringServiceController.start(this)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
