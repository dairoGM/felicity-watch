package com.dairoroberto.felicitywatch.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Payload real portado de felicityAPI (slauf82), estilo "legacy_userName":
 * este es el path que la referencia marca como conocido-funcional (v1.2.0).
 */
data class LoginRequest(
    @SerializedName("userName") val userName: String,
    @SerializedName("password") val password: String,
    @SerializedName("source") val source: String = "WEB",
    @SerializedName("lang") val lang: String = "de_DE"
)

data class LoginResponse(
    @SerializedName("code") val code: Int?,
    @SerializedName("msg") val message: String?,
    @SerializedName("data") val data: LoginData?
)

data class LoginData(
    @SerializedName("token") val token: String?
)
