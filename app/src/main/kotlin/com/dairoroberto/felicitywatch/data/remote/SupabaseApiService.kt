package com.dairoroberto.felicitywatch.data.remote

import com.dairoroberto.felicitywatch.data.remote.dto.DesktopPairingDto
import com.dairoroberto.felicitywatch.data.remote.dto.SupabasePowerReadingDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
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

    /** Genera un PIN de emparejamiento — llamada desde el celular (Ajustes > Sincronización). */
    @POST("rest/v1/desktop_pairings")
    suspend fun createDesktopPairing(
        @Header("Prefer") prefer: String,
        @Body pairing: List<DesktopPairingDto>
    ): Response<Unit>
}
