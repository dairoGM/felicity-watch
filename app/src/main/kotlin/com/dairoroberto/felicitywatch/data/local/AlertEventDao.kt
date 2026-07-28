package com.dairoroberto.felicitywatch.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertEventDao {
    @Query("SELECT * FROM alert_events ORDER BY triggeredAt DESC")
    fun observeAll(): Flow<List<AlertEventEntity>>

    @Query("SELECT * FROM alert_events ORDER BY triggeredAt DESC LIMIT 1")
    suspend fun getLatest(): AlertEventEntity?

    @Insert
    suspend fun insert(event: AlertEventEntity): Long
}
