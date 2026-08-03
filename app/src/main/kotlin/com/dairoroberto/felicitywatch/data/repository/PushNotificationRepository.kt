package com.dairoroberto.felicitywatch.data.repository

import com.dairoroberto.felicitywatch.data.local.PushNotificationDao
import com.dairoroberto.felicitywatch.data.local.PushNotificationEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushNotificationRepository @Inject constructor(
    private val pushNotificationDao: PushNotificationDao
) {
    fun observeNotifications(): Flow<List<PushNotificationEntity>> = pushNotificationDao.observeAll()

    suspend fun insert(notification: PushNotificationEntity) {
        pushNotificationDao.insert(notification)
    }

    suspend fun clearAll() {
        pushNotificationDao.clearAll()
    }

    suspend fun delete(id: Long) {
        pushNotificationDao.delete(id)
    }
}
