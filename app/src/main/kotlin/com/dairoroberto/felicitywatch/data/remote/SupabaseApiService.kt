package com.dairoroberto.felicitywatch.data.remote

import com.dairoroberto.felicitywatch.data.remote.dto.AccountDeviceApprovalDto
import com.dairoroberto.felicitywatch.data.remote.dto.AccountDeviceDto
import com.dairoroberto.felicitywatch.data.remote.dto.AccountDeviceEquipmentDto
import com.dairoroberto.felicitywatch.data.remote.dto.AccountDeviceStatusDto
import com.dairoroberto.felicitywatch.data.remote.dto.AccountSettingsDto
import com.dairoroberto.felicitywatch.data.remote.dto.DesktopPairingDto
import com.dairoroberto.felicitywatch.data.remote.dto.SupabasePowerReadingDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * PostgREST (la API REST automática de Supabase) para la tabla
 * `power_readings`. Se usa Retrofit directo en vez del SDK oficial de
 * Supabase para no sumar una dependencia nueva grande — PostgREST es HTTP
 * plano, encaja con el mismo patrón que ya usa el resto de la app.
 */
interface SupabaseApiService {

    /**
     * Inserta lecturas. `Prefer: resolution=ignore-duplicates` hace que un
     * reintento (ej. subió pero la respuesta se perdió por la red) no falle
     * por violar el UNIQUE(device_id, timestamp_epoch_millis) de la tabla —
     * simplemente ignora las que ya existen, en vez de que todo el lote
     * falle por una fila repetida. PostgREST solo aplica esa resolución
     * cuando además se le dice CONTRA QUÉ columnas puede haber conflicto,
     * vía `on_conflict`; sin este query param, ignora la resolución y hace
     * un INSERT plano que sí revienta con 409 (verificado contra el
     * servidor real).
     */
    @POST("rest/v1/power_readings")
    suspend fun insertReadings(
        @Query("on_conflict") onConflict: String,
        @Header("Prefer") prefer: String,
        @Body readings: List<SupabasePowerReadingDto>
    ): Response<Unit>

    /**
     * Lecturas de UN dispositivo puntual (master o cliente), más recientes
     * primero — usado por la master para ver PV/consumo/batería/red de un
     * cliente elegido en la pestaña Clientes, sin necesitar sus credenciales
     * de FSolar: el cliente ya sube su propio historial con su device_id
     * (ver PowerHistoryRepository.record) si tiene la sincronización activada.
     */
    @GET("rest/v1/power_readings")
    suspend fun getReadingsForDevice(
        @Query("device_id") deviceIdFilter: String,
        @Query("order") order: String = "timestamp_epoch_millis.desc",
        @Query("limit") limit: Int = 200
    ): Response<List<SupabasePowerReadingDto>>

    /** Genera un PIN de emparejamiento — llamada desde el celular (Ajustes > Sincronización). */
    @POST("rest/v1/desktop_pairings")
    suspend fun createDesktopPairing(
        @Header("Prefer") prefer: String,
        @Body pairing: List<DesktopPairingDto>
    ): Response<Unit>

    /** Busca un PIN vigente y no usado, filtrado además por a qué plataforma va dirigido. */
    @GET("rest/v1/desktop_pairings")
    suspend fun findPairing(
        @Query("pin") pinFilter: String,
        @Query("used") usedFilter: String,
        @Query("target_platform") targetPlatformFilter: String,
        @Query("select") select: String
    ): Response<List<Map<String, Any?>>>

    /** Marca un PIN como usado (de un solo uso). */
    @PATCH("rest/v1/desktop_pairings")
    suspend fun markPairingUsed(
        @Query("pin") pinFilter: String,
        @Header("Prefer") prefer: String,
        @Body body: Map<String, Boolean>
    ): Response<Unit>

    /**
     * Intenta registrar este dispositivo como master. El índice único parcial
     * de la tabla (un solo `role='master', revoked=false` por cuenta) hace
     * que un segundo intento falle con 409 — la app nunca decide sola quién
     * gana, lo decide Postgres.
     */
    @POST("rest/v1/account_devices")
    suspend fun createAccountDevice(
        @Query("on_conflict") onConflict: String,
        @Header("Prefer") prefer: String,
        @Body device: List<AccountDeviceDto>
    ): Response<Unit>

    /**
     * Mismo upsert que [createAccountDevice] pero sin `display_name` en el
     * payload — usado al aprobar/reclamar (claimMaster, redeemPairingPin),
     * donde el conflicto puede ser un dispositivo ya conocido (y ya
     * renombrado por el master). Enviar `display_name: null` en ese caso lo
     * borraría en el UPDATE que hace `resolution=merge-duplicates`.
     */
    @POST("rest/v1/account_devices")
    suspend fun upsertAccountDeviceApproval(
        @Query("on_conflict") onConflict: String,
        @Header("Prefer") prefer: String,
        @Body device: List<AccountDeviceApprovalDto>
    ): Response<Unit>

    /** Estado actual (rol, aprobación, revocación) de cualquier device_id, o de la master de la cuenta. */
    @GET("rest/v1/account_devices")
    suspend fun getAccountDevices(
        @Query("select") select: String = "*",
        @Query("device_id") deviceIdFilter: String? = null,
        @Query("role") roleFilter: String? = null,
        @Query("revoked") revokedFilter: String? = null
    ): Response<List<AccountDeviceStatusDto>>

    @PATCH("rest/v1/account_devices")
    suspend fun updateAccountDevice(
        @Query("device_id") deviceIdFilter: String,
        @Header("Prefer") prefer: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any?>
    ): Response<Unit>

    /**
     * Borra por completo el registro de un dispositivo cliente — a
     * diferencia de revocar (que solo marca `revoked=true` y conserva la
     * fila para poder re-aprobarlo con el mismo nombre), esto elimina la
     * fila entera. Si ese mismo teléfono vuelve a canjear un código después,
     * entra como un dispositivo nuevo sin nombre ni historial previos.
     */
    @DELETE("rest/v1/account_devices")
    suspend fun deleteAccountDevice(
        @Query("device_id") deviceIdFilter: String,
        @Header("Prefer") prefer: String = "return=minimal"
    ): Response<Unit>

    /** Configuración de la cuenta (días de periodo free) — fila única
     * `id='default'`, ver 001_licensing.sql. */
    @GET("rest/v1/account_settings")
    suspend fun getAccountSettings(
        @Query("id") idFilter: String = "eq.default",
        @Query("select") select: String = "*"
    ): Response<List<AccountSettingsDto>>

    @PATCH("rest/v1/account_settings")
    suspend fun updateAccountSettings(
        @Query("id") idFilter: String = "eq.default",
        @Header("Prefer") prefer: String = "return=minimal",
        @Body updates: Map<String, @JvmSuppressWildcards Any?>
    ): Response<Unit>

    /**
     * Sube (upsert) el snapshot de equipos de ESTE dispositivo — ver
     * migración 003_account_device_equipment.sql. `on_conflict=device_id`
     * porque es upsert por clave primaria, no inserción con historial: cada
     * dispositivo tiene una sola fila que se sobrescribe completa en cada
     * sincronización (a diferencia de power_readings, que sí acumula
     * historial por timestamp).
     */
    @POST("rest/v1/account_device_equipment")
    suspend fun upsertAccountDeviceEquipment(
        @Query("on_conflict") onConflict: String = "device_id",
        @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=minimal",
        @Body equipment: List<AccountDeviceEquipmentDto>
    ): Response<Unit>

    /** Equipos de UN dispositivo puntual — para la pestaña "Equipos" del
     * detalle de un cliente (pestaña Clientes de la master). */
    @GET("rest/v1/account_device_equipment")
    suspend fun getAccountDeviceEquipment(
        @Query("device_id") deviceIdFilter: String
    ): Response<List<AccountDeviceEquipmentDto>>
}
