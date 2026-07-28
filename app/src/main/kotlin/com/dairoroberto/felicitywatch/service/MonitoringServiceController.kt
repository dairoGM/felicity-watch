package com.dairoroberto.felicitywatch.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object MonitoringServiceController {
    fun start(context: Context) {
        val intent = Intent(context, MonitoringForegroundService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, MonitoringForegroundService::class.java))
    }
}
