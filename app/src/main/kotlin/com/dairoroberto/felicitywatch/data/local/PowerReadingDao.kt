package com.dairoroberto.felicitywatch.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PowerReadingDao {
    @Insert
    suspend fun insert(reading: PowerReadingEntity)

    @Query("SELECT * FROM power_readings WHERE timestampEpochMillis >= :sinceEpochMillis ORDER BY timestampEpochMillis ASC")
    fun observeSince(sinceEpochMillis: Long): Flow<List<PowerReadingEntity>>

    @Query("DELETE FROM power_readings WHERE timestampEpochMillis < :beforeEpochMillis")
    suspend fun deleteOlderThan(beforeEpochMillis: Long)

    @Query("DELETE FROM power_readings")
    suspend fun deleteAll()
}
