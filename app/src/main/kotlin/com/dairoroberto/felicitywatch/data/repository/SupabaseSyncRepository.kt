package com.dairoroberto.felicitywatch.data.repository

import com.dairoroberto.felicitywatch.data.local.AppPreferences
import com.dairoroberto.felicitywatch.data.local.PowerReadingDao
import com.dairoroberto.felicitywatch.data.local.PowerReadingEntity
import com.dairoroberto.felicitywatch.data.remote.SupabaseApiService
import com.dairoroberto.felicitywatch.data.remote.dto.DesktopPairingDto
import com.dairoroberto.felicitywatch.data.remote.dto.SupabasePowerReadingDto
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** La tabla de Supabase respondió con un error (ej. `relation "power_readings" does not exist`
 * si aún no se corrió el script de instalación, o RLS rechazando la escritura). */
class SupabaseSyncException(val httpCode: Int, val body: String?) : Exception(
    "Supabase respondió $httpCode" + if (!body.isNullOrBlank()) ": $body" else ""
)

/** Progreso de la migración inicial, para mostrar una barra en Ajustes. */
sealed class MigrationProgress {
    data object Idle : MigrationProgress()
    data class InProgress(val uploaded: Int, val total: Int) : MigrationProgress()
    data object Success : MigrationProgress()
    data class Failed(val message: String) : MigrationProgress()
}

/**
 * Sincroniza el historial local (Room) con la tabla `power_readings` de
 * Supabase — respaldo remoto para poder ver los datos desde otro
 * dispositivo o una web, SIN que Room deje de ser la fuente de verdad
 * local: la app sigue funcionando 100% offline igual que antes.
 *
 * Todo lo que toca la red aquí es best-effort: cualquier error (sin
 * conexión, Supabase caído, tabla aún no creada) se atrapa y se ignora en
 * el punto de llamada — nunca debe impedir que una lectura se guarde en
 * Room. Ver [PowerHistoryRepository.record].
 */
@Singleton
class SupabaseSyncRepository @Inject constructor(
    private val api: SupabaseApiService,
    private val dao: PowerReadingDao,
    private val appPreferences: AppPreferences
) {
    /** Sube una sola lectura recién guardada localmente — llamada desde el
     * ciclo normal de monitoreo, una vez habilitada la sincronización. */
    suspend fun pushReading(reading: PowerReadingEntity) {
        val deviceId = appPreferences.supabaseDeviceId()
        val response = api.insertReadings(
            onConflict = INSERT_ON_CONFLICT,
            prefer = INSERT_PREFER,
            readings = listOf(reading.toDto(deviceId))
        )
        if (!response.isSuccessful) {
            throw SupabaseSyncException(response.code(), response.errorBody()?.string())
        }
    }

    /**
     * Migración inicial: sube TODO el historial local en lotes, en orden
     * cronológico. Idempotente — se puede reintentar sin duplicar filas
     * gracias al UNIQUE(device_id, timestamp_epoch_millis) de la tabla más
     * el `resolution=ignore-duplicates` del insert.
     */
    suspend fun migrateAll(onProgress: (uploaded: Int, total: Int) -> Unit) {
        val deviceId = appPreferences.supabaseDeviceId()
        val total = dao.count()
        var uploaded = 0
        onProgress(uploaded, total)

        var offset = 0
        while (true) {
            val page = dao.getPage(limit = BATCH_SIZE, offset = offset)
            if (page.isEmpty()) break

            val dtos = page.map { it.toDto(deviceId) }
            val response = api.insertReadings(
                onConflict = INSERT_ON_CONFLICT,
                prefer = INSERT_PREFER,
                readings = dtos
            )
            if (!response.isSuccessful) {
                throw SupabaseSyncException(response.code(), response.errorBody()?.string())
            }

            uploaded += page.size
            offset += page.size
            onProgress(uploaded, total)
        }

        appPreferences.setSupabaseMigrationDone(true)
        appPreferences.setSupabaseSyncEnabled(true)
    }

    suspend fun isMigrationDone(): Boolean = appPreferences.supabaseMigrationDone.first()

    /**
     * Genera un PIN de 6 dígitos para emparejar la app de escritorio con
     * este dispositivo, sin exponer credenciales de FSolar ni el device_id
     * en crudo: la PC solo necesita teclear este código una vez. Expira a
     * los [PAIRING_TTL_MINUTES] minutos y es de un solo uso (la app de
     * escritorio lo marca `used=true` al validar).
     */
    suspend fun createDesktopPairingPin(): String {
        val deviceId = appPreferences.supabaseDeviceId()
        val pin = Random.nextInt(0, 1_000_000).toString().padStart(6, '0')
        val expiresAt = Instant.now().plusSeconds(PAIRING_TTL_MINUTES * 60)

        val response = api.createDesktopPairing(
            prefer = "return=minimal",
            pairing = listOf(
                DesktopPairingDto(
                    pin = pin,
                    deviceId = deviceId,
                    expiresAt = DateTimeFormatter.ISO_INSTANT.format(expiresAt)
                )
            )
        )
        if (!response.isSuccessful) {
            throw SupabaseSyncException(response.code(), response.errorBody()?.string())
        }
        return pin
    }

    private fun PowerReadingEntity.toDto(deviceId: String) = SupabasePowerReadingDto(
        deviceId = deviceId,
        timestampEpochMillis = timestampEpochMillis,
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

    companion object {
        /** PostgREST acepta lotes grandes, pero un tamaño moderado mantiene
         * cada solicitud rápida y fácil de reintentar si falla a la mitad. */
        private const val BATCH_SIZE = 500

        /** `ignore-duplicates` hace que un reintento (falló a mitad de la
         * migración, o una lectura que ya se había subido) no rompa el lote
         * por violar el UNIQUE(device_id, timestamp_epoch_millis) — se
         * pasa explícito en cada llamada porque los valores por defecto de
         * un parámetro @Header en una interfaz Retrofit no siempre se
         * aplican de forma confiable a través del proxy dinámico. */
        private const val INSERT_PREFER = "resolution=ignore-duplicates,return=minimal"

        /** PostgREST solo respeta `ignore-duplicates` si además se le dice
         * contra qué columnas puede haber conflicto — deben ser exactamente
         * las del UNIQUE de la tabla (ver supabase_bootstrap.sql). */
        private const val INSERT_ON_CONFLICT = "device_id,timestamp_epoch_millis"

        /** Tiempo de vida de un PIN de emparejamiento con la app de
         * escritorio — corto a propósito: es solo para el momento de
         * conectar, no una credencial de largo plazo. */
        private const val PAIRING_TTL_MINUTES = 15L
    }
}
