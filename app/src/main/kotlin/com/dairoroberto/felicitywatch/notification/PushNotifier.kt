package com.dairoroberto.felicitywatch.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

import com.dairoroberto.felicitywatch.data.repository.PushNotificationRepository
import com.dairoroberto.felicitywatch.data.local.PushNotificationEntity
import com.dairoroberto.felicitywatch.domain.model.AlertRuleType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Notificación push local (guía sección 7.2). No usa FCM: el propio
 * Foreground Service dispara la notificación en el mismo dispositivo.
 */
@Singleton
class PushNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PushNotificationRepository
) {
    fun notifyAlert(title: String, body: String, notificationId: Int, ruleType: AlertRuleType? = null): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) return false

        val notification = NotificationCompat.Builder(context, NotificationChannels.ALERTS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        val success = sendNotification(notificationId, notification)
        if (success) {
            CoroutineScope(Dispatchers.IO).launch {
                repository.insert(PushNotificationEntity(title = title, body = body, ruleType = ruleType))
            }
        }
        return success
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun sendNotification(notificationId: Int, notification: android.app.Notification): Boolean {
        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
