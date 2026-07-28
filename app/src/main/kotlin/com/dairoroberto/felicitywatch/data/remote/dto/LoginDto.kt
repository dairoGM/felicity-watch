package com.dairoroberto.felicitywatch.data.remote.dto

import com.google.gson.JsonElement
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

/**
 * El campo "data" del login varía de forma real entre cuentas/firmwares: a
 * veces es un objeto `{"token": "..."}` (lo que asume la referencia Python),
 * y a veces la nube de Felicity devuelve directamente el token como string
 * plano en "data" (confirmado en producción: Gson lanzaba
 * "Expected BEGIN_OBJECT but was STRING" al forzar un objeto). Por eso se
 * deserializa como JsonElement crudo y [extractToken] soporta ambas formas.
 */
data class LoginResponse(
    @SerializedName("code") val code: Int?,
    @SerializedName("msg") val message: String?,
    @SerializedName("data") val data: JsonElement?
) {
    fun extractToken(): String? {
        val data = this.data ?: return null
        if (data.isJsonNull) return null

        if (data.isJsonPrimitive) {
            val raw = data.asString
            return raw.takeIf { it.isNotBlank() }
        }

        if (data.isJsonObject) {
            val tokenElement = data.asJsonObject.get("token") ?: return null
            if (tokenElement.isJsonNull) return null
            return tokenElement.asString.takeIf { it.isNotBlank() }
        }

        return null
    }
}
