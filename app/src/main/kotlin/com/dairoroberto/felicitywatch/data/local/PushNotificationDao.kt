package com.dairoroberto.felicitywatch.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PushNotificationDao {
    @Insert
    suspend fun insert(notification: PushNotificationEntity)

    @Query("SELECT * FROM push_notifications ORDER BY receivedAt DESC")
    fun observeAll(): Flow<List<PushNotificationEntity>>

    @Query("DELETE FROM push_notifications")
    suspend fun clearAll()

    @Query("DELETE FROM push_notifications WHERE id = :id")
    suspend fun delete(id: Long)
}
