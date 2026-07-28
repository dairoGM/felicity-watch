package com.dairoroberto.felicitywatch.data.remote

import com.dairoroberto.felicitywatch.data.remote.dto.DeviceListResponse
import com.dairoroberto.felicitywatch.data.remote.dto.LoginResponse
import com.dairoroberto.felicitywatch.data.remote.dto.SnapshotResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
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

    @GET("device/list_device_all_type")
    suspend fun listDevices(
        @Header("Authorization") token: String,
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
