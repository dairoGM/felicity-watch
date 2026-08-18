package com.dairoroberto.felicitywatch.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DismissedApplianceEventDao {
    @Query("SELECT eventEpochMillis FROM dismissed_appliance_events")
    fun observeDismissedMillis(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun dismiss(event: DismissedApplianceEventEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun dismissAll(events: List<DismissedApplianceEventEntity>)

    /** Limpieza: los eventos se recalculan solo de las últimas 24 h, así
     * que registros más viejos que eso ya no filtran nada y solo ocupan
     * espacio. */
    @Query("DELETE FROM dismissed_appliance_events WHERE eventEpochMillis < :beforeEpochMillis")
    suspend fun deleteOlderThan(beforeEpochMillis: Long)

    @Query("DELETE FROM dismissed_appliance_events")
    suspend fun deleteAll()
}
