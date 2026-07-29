package com.dairoroberto.felicitywatch.data.repository

import com.dairoroberto.felicitywatch.data.local.PowerReadingDao
import com.dairoroberto.felicitywatch.data.local.PowerReadingEntity
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PowerHistoryRepository @Inject constructor(
    private val dao: PowerReadingDao
) {
    fun observeLast24Hours(): Flow<List<PowerReadingEntity>> =
        dao.observeSince(Instant.now().minus(Duration.ofHours(24)).toEpochMilli())

    /** Para el Reporte: rango de fechas elegido por el usuario. */
    fun observeBetween(start: Instant, end: Instant): Flow<List<PowerReadingEntity>> =
        dao.observeBetween(start.toEpochMilli(), end.toEpochMilli())

    suspend fun record(pvPowerWatts: Int?, gridPowerWatts: Int?, socPercent: Int?, now: Instant) {
        dao.insert(
            PowerReadingEntity(
                timestampEpochMillis = now.toEpochMilli(),
                pvPowerWatts = pvPowerWatts,
                gridPowerWatts = gridPowerWatts,
                socPercent = socPercent
            )
        )
        // Poda liviana: retiene 30 días para que el Reporte con filtro de
        // fecha tenga margen razonable sin crecer sin límite.
        dao.deleteOlderThan(now.minus(Duration.ofDays(RETENTION_DAYS)).toEpochMilli())
    }

    suspend fun clearAll() = dao.deleteAll()

    companion object {
        private const val RETENTION_DAYS = 30L
    }
}
