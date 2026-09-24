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

    @Query(
        "SELECT * FROM power_readings WHERE timestampEpochMillis >= :startEpochMillis " +
            "AND timestampEpochMillis <= :endEpochMillis ORDER BY timestampEpochMillis ASC"
    )
    fun observeBetween(startEpochMillis: Long, endEpochMillis: Long): Flow<List<PowerReadingEntity>>

    /** Última lectura ANTES de un instante — para saber si un tramo que
     * parece "empezar" al inicio de un rango filtrado en realidad ya
     * venía del mismo estado desde antes (ej. un corte de luz que empezó
     * ayer y sigue hoy no debe aparentar que comenzó a medianoche). */
    @Query(
        "SELECT * FROM power_readings WHERE timestampEpochMillis < :beforeEpochMillis " +
            "ORDER BY timestampEpochMillis DESC LIMIT 1"
    )
    fun observeLastBefore(beforeEpochMillis: Long): Flow<PowerReadingEntity?>

    @Query("DELETE FROM power_readings WHERE timestampEpochMillis < :beforeEpochMillis")
    suspend fun deleteOlderThan(beforeEpochMillis: Long)

    @Query("SELECT COUNT(*) FROM power_readings")
    suspend fun count(): Int

    /** Página del historial completo para la migración inicial a Supabase —
     * paginada para no cargar meses de lecturas en memoria de una vez. */
    @Query("SELECT * FROM power_readings ORDER BY timestampEpochMillis ASC LIMIT :limit OFFSET :offset")
    suspend fun getPage(limit: Int, offset: Int): List<PowerReadingEntity>

    @Query("DELETE FROM power_readings")
    suspend fun deleteAll()
}
