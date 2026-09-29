package com.dairoroberto.felicitywatch.data.repository

import com.dairoroberto.felicitywatch.data.local.AppPreferences
import com.dairoroberto.felicitywatch.data.local.PowerReadingDao
import com.dairoroberto.felicitywatch.data.local.PowerReadingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PowerHistoryRepository @Inject constructor(
    private val dao: PowerReadingDao,
    private val appPreferences: AppPreferences,
    private val supabaseSyncRepository: SupabaseSyncRepository
) {
    fun observeLast24Hours(): Flow<List<PowerReadingEntity>> =
        dao.observeSince(Instant.now().minus(Duration.ofHours(24)).toEpochMilli())

    /** Para el Panel: totales de Hoy/7 días/30 días. Usa el máximo de retención
     * (ver [RETENTION_DAYS]) — hoy son 6 meses, ampliados desde 30 días para
     * que la pantalla de Factura y ahorro pueda comparar meses entre sí. */
    fun observeLastRetentionWindow(): Flow<List<PowerReadingEntity>> =
        dao.observeSince(Instant.now().minus(Duration.ofDays(RETENTION_DAYS)).toEpochMilli())

    /** Para el Reporte: rango de fechas elegido por el usuario. */
    fun observeBetween(start: Instant, end: Instant): Flow<List<PowerReadingEntity>> =
        dao.observeBetween(start.toEpochMilli(), end.toEpochMilli())

    /** Para el Reporte: última lectura antes del inicio del rango elegido
     * — permite saber si el primer tramo del rango en realidad continúa
     * un estado que ya venía de antes (ver [dao.observeLastBefore]). */
    fun observeLastBefore(instant: Instant): Flow<PowerReadingEntity?> =
        dao.observeLastBefore(instant.toEpochMilli())

    suspend fun record(
        pvPowerWatts: Int?,
        gridPowerWatts: Int?,
        socPercent: Int?,
        loadPowerWatts: Int?,
        batteryPowerWatts: Int?,
        pvEnergyTodayKwh: Double?,
        loadEnergyTodayKwh: Double?,
        gridVoltage: Double?,
        outputVoltage: Double?,
        now: Instant
    ) {
        val reading = PowerReadingEntity(
            timestampEpochMillis = now.toEpochMilli(),
            pvPowerWatts = pvPowerWatts,
            gridPowerWatts = gridPowerWatts,
            socPercent = socPercent,
            loadPowerWatts = loadPowerWatts,
            batteryPowerWatts = batteryPowerWatts,
            pvEnergyTodayKwh = pvEnergyTodayKwh,
            loadEnergyTodayKwh = loadEnergyTodayKwh,
            gridVoltage = gridVoltage,
            outputVoltage = outputVoltage
        )
        dao.insert(reading)
        // Poda liviana: retiene RETENTION_DAYS para que el Reporte y la
        // estimación de Factura tengan margen razonable sin crecer sin límite.
        dao.deleteOlderThan(now.minus(Duration.ofDays(RETENTION_DAYS)).toEpochMilli())

        // Espejo en Supabase — cada dispositivo sube con SU PROPIO device_id
        // (ver SupabaseSyncRepository.pushReading), así que nunca puede pisar
        // ni mezclarse con las filas de otro. Esto habilita que la master
        // consulte los datos de un cliente puntual (pestaña Clientes) sin
        // que nadie suba credenciales de FSolar a la nube.
        //
        // Para un CLIENTE esto no es opcional: sin su historial en Supabase,
        // la master no tiene forma de ver nada de él, así que se ignora
        // supabaseSyncEnabled (que sigue siendo la preferencia real solo
        // para la master, quien decide si respalda su propio historial). Un
        // cliente no puede pausar esto — de lo contrario, un cliente que lo
        // desactive quedaría invisible para la master sin que nadie se
        // entere.
        //
        // Nunca debe poder tumbar el guardado local: sin red, con Supabase
        // caído, o con la tabla aún sin crear, esto simplemente se salta en
        // silencio y la próxima lectura lo vuelve a intentar.
        val isMaster = appPreferences.isMasterDevice.first()
        val shouldSync = if (isMaster) appPreferences.supabaseSyncEnabled.first() else true
        if (shouldSync) {
            try {
                // Un cliente nunca ve el botón "Migrar historial a la nube"
                // (forzado, ver arriba) — si ya tenía lecturas locales de
                // antes de convertirse en cliente sincronizado, esas quedarían
                // sin subir para siempre si nadie dispara la migración una
                // vez. Se hace aquí mismo, de forma transparente, la primera
                // vez que se detecta sin migrar.
                if (!isMaster && !appPreferences.supabaseMigrationDone.first()) {
                    supabaseSyncRepository.migrateAll { _, _ -> }
                }
                supabaseSyncRepository.pushReading(reading)
            } catch (_: Exception) {
                // Best-effort: la próxima lectura reintenta sola.
            }
        }
    }

    suspend fun clearAll() = dao.deleteAll()

    companion object {
        /**
         * 6 meses. Antes eran 30 días; se amplió para que "Factura y ahorro"
         * pueda comparar un mes contra otro — con 30 días, en cuanto pasaba
         * el día 1 de cada mes ya no quedaba ni un mes completo anterior para
         * comparar.
         *
         * Costo de espacio, con una fila de ~55 bytes por lectura:
         *   - polling de 30s: ~27 MB en 6 meses
         *   - polling de 5s:  ~163 MB en 6 meses
         * Aceptable para almacenamiento de teléfono; si en el futuro hace
         * falta más rango, subir esto es el único cambio necesario aquí — el
         * resto del código ya lee por ventana relativa, no por un número fijo
         * de días hardcodeado en otro lugar.
         */
        const val RETENTION_DAYS = 183L
    }
}
