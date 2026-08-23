package com.dairoroberto.felicitywatch.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfirmedApplianceEventDao {

    @Query("SELECT * FROM confirmed_appliance_events")
    fun observeAll(): Flow<List<ConfirmedApplianceEventEntity>>

    /** REPLACE y no ABORT: si el usuario se equivoca y vuelve a confirmar el
     * mismo evento con otro equipo, la corrección debe ganar. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(confirmation: ConfirmedApplianceEventEntity)

    @Query("DELETE FROM confirmed_appliance_events WHERE eventEpochMillis < :cutoffEpochMillis")
    suspend fun deleteOlderThan(cutoffEpochMillis: Long)
}
