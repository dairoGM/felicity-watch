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

    suspend fun record(pvPowerWatts: Int?, gridPowerWatts: Int?, socPercent: Int?, now: Instant) {
        dao.insert(
            PowerReadingEntity(
                timestampEpochMillis = now.toEpochMilli(),
                pvPowerWatts = pvPowerWatts,
                gridPowerWatts = gridPowerWatts,
                socPercent = socPercent
            )
        )
        // Poda liviana: no acumular más de 7 días de historial local.
        dao.deleteOlderThan(now.minus(Duration.ofDays(7)).toEpochMilli())
    }

    suspend fun clearAll() = dao.deleteAll()
}
