package com.dairoroberto.felicitywatch.data.remote

import com.dairoroberto.felicitywatch.data.remote.dto.SupabasePowerReadingDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
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
     * falle por una fila repetida.
     */
    @POST("rest/v1/power_readings")
    suspend fun insertReadings(
        @Header("Prefer") prefer: String = "resolution=ignore-duplicates,return=minimal",
        @Body readings: List<SupabasePowerReadingDto>
    ): Response<Unit>

    /** Para saber si ESTE dispositivo ya tiene datos migrados (evita repetir la migración inicial). */
    @GET("rest/v1/power_readings")
    suspend fun countReadings(
        @Query("device_id") deviceIdFilter: String,
        @Query("select") select: String = "timestamp_epoch_millis",
        @Header("Prefer") prefer: String = "count=exact",
        @Query("limit") limit: Int = 1
    ): Response<List<Map<String, Any?>>>
}
