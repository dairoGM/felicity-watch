package com.dairoroberto.felicitywatch.data.remote

import com.dairoroberto.felicitywatch.data.remote.dto.DeviceListResponse
import com.dairoroberto.felicitywatch.data.remote.dto.LoginResponse
import com.dairoroberto.felicitywatch.data.remote.dto.SnapshotResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * Interfaz Retrofit portada de felicityAPI (custom_components/felicity_api/api.py
 * y const.py, repo slauf82/felicityAPI). Base URL: https://shine-api.felicitysolar.com
 */
interface FelicityApiService {

    // El body es un mapa (no un data class fijo) porque su forma varía
    // según la variante de login: "legacy" agrega source/lang, "modern"
    // agrega version, y el campo de usuario puede ser "userName" o
    // "account" — ver FelicityApiClient.buildLoginPayload().
    @Headers(
        "Content-Type: application/json",
        "Origin: https://shine.felicitysolar.com",
        "Referer: https://shine.felicitysolar.com/"
    )
    @POST("openApi/sec/login")
    suspend fun login(@Body body: Map<String, String>): Response<LoginResponse>

    @Headers(
        "Content-Type: application/json",
        "Origin: https://shine.felicitysolar.com",
        "Referer: https://shine.felicitysolar.com/"
    )
    @POST("userlogin")
    suspend fun loginFallback(@Body body: Map<String, String>): Response<LoginResponse>

    // Verificado contra el servidor real: este endpoint devuelve HTTP 405
    // (Method Not Allowed) con GET — a pesar de que la referencia Python lo
    // documenta como GET sin body, la nube real de Felicity lo expone como
    // POST paginado y exige "pageNum"/"pageSize" en el body (si faltan,
    // responde code=2002006 "pageSize/pageNum no puede estar vacío").
    @POST("device/list_device_all_type")
    suspend fun listDevices(
        @Header("Authorization") token: String,
        @Body body: DeviceListRequest,
        @Header("lang") lang: String = "de_DE",
        @Header("source") source: String = "WEB"
    ): Response<DeviceListResponse>

    @POST("device/get_device_snapshot")
    suspend fun getDeviceSnapshot(
        @Header("Authorization") token: String,
        @Body body: SnapshotRequest,
        @Header("lang") lang: String = "de_DE",
        @Header("source") source: String = "WEB"
    ): Response<SnapshotResponse>
}

data class SnapshotRequest(
    val deviceSn: String,
    val dateStr: String
)

data class DeviceListRequest(
    val pageNum: Int = 1,
    val pageSize: Int = 100
)
