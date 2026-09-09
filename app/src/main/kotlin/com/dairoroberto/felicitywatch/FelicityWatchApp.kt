package com.dairoroberto.felicitywatch

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.dairoroberto.felicitywatch.data.local.CredentialsStore
import com.dairoroberto.felicitywatch.data.repository.AlertRuleRepository
import com.dairoroberto.felicitywatch.notification.NotificationChannels
import com.dairoroberto.felicitywatch.service.MonitoringServiceController
import com.dairoroberto.felicitywatch.service.ServiceWatchdogWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FelicityWatchApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var credentialsStore: CredentialsStore
    @Inject lateinit var alertRuleRepository: AlertRuleRepository

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

        // Antes esto solo corría al abrir Ajustes > Alertas (AlertsViewModel)
        // — si el usuario nunca visitaba esa pantalla tras una actualización
        // que agrega un tipo de regla nuevo (ej. PV_GENERATION_LOST), esa
        // regla simplemente no existía en la base de datos y la alerta
        // jamás se evaluaba, aunque apareciera "activada por defecto" en el
        // código. Se corre aquí también, en cada arranque del proceso, para
        // que las reglas existan sin depender de qué pantalla se abra.
        CoroutineScope(Dispatchers.IO).launch {
            alertRuleRepository.seedDefaultsIfEmpty()
            alertRuleRepository.seedMissingDefaults()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
