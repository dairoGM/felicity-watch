package com.dairoroberto.felicitywatch.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dairoroberto.felicitywatch.data.local.CredentialsStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Arranca el Foreground Service al iniciar el teléfono si el usuario ya
 * configuró sus credenciales (guía sección 6), sin requerir abrir la app.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var credentialsStore: CredentialsStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (credentialsStore.hasFsolarCredentials()) {
            MonitoringServiceController.start(context)
        }
        ServiceWatchdogWorker.schedule(context)
    }
}
