package com.dairoroberto.felicitywatch.data.remote

import com.dairoroberto.felicitywatch.data.remote.dto.DeviceListResponse
import com.dairoroberto.felicitywatch.data.remote.dto.SnapshotResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

class FelicityApiException(message: String) : Exception(message)
class FelicityAuthException(message: String) : Exception(message)

/**
 * Envoltorio del login/token/reintento, puerto directo de la clase
 * FelicityAPI de felicityAPI (api.py): intenta el endpoint/clave/estilo de
 * payload "conocido funcional" primero, cae a las otras 15 variantes si
 * falla (2 endpoints × 2 claves × 4 estilos de payload), y ante un código
 * de negocio 401/403/998 en la respuesta limpia el token y reintenta login
 * una vez antes de repetir la llamada original.
 *
 * Verificado contra el servidor real de Felicity (no es teórico): para al
 * menos una cuenta real, la combinación que de verdad funciona es
 * endpoint=/userlogin + clave de respaldo + estilo "modern_userName"
 * (payload con "version":"1.0" en vez de "source"/"lang") — la variante
 * que la propia referencia documenta como "known working v1.2.0" (endpoint
 * principal + clave principal + estilo legacy) NO funcionó para esa
 * cuenta. Por eso se prueban las 16 combinaciones, no solo 4.
 */
@Singleton
class FelicityApiClient @Inject constructor(
    private val service: FelicityApiService
) {
    @Volatile
    private var token: String? = null
    private val loginMutex = Mutex()

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private suspend fun ensureLogin(username: String, password: String) {
        if (token != null) return
        login(username, password)
    }

    private fun buildLoginPayload(username: String, encryptedPassword: String, style: PayloadStyle): Map<String, String> {
        val usernameField = if (style.useAccountField) "account" else "userName"
        return if (style.modern) {
            mapOf(usernameField to username, "password" to encryptedPassword, "version" to "1.0")
        } else {
            mapOf(usernameField to username, "password" to encryptedPassword, "source" to "WEB", "lang" to "de_DE")
        }
    }

    private suspend fun login(username: String, password: String) {
        loginMutex.withLock {
            if (token != null) return

            val endpoints = listOf(false, true) // false = principal, true = fallback (/userlogin)
            val keys = listOf(
                RsaPasswordEncryptor.PUBLIC_KEY_PRIMARY to "clave1",
                RsaPasswordEncryptor.PUBLIC_KEY_FALLBACK to "clave2"
            )
            val styles = listOf(
                PayloadStyle(modern = false, useAccountField = false), // legacy_userName: conocido-funcional v1.2.0
                PayloadStyle(modern = true, useAccountField = false),  // modern_userName
                PayloadStyle(modern = false, useAccountField = true),  // legacy_account
                PayloadStyle(modern = true, useAccountField = true)    // modern_account
            )

            // El primer intento (endpoint principal + clave1 + legacy_userName)
            // va primero por ser el históricamente documentado como funcional;
            // el resto cubre las otras 15 combinaciones sin repetirlo.
            val attempts = mutableListOf(LoginAttempt(false, keys[0].first, "clave1", styles[0]))
            for (useFallbackEndpoint in endpoints) {
                for ((key, keyLabel) in keys) {
                    for (style in styles) {
                        val attempt = LoginAttempt(useFallbackEndpoint, key, keyLabel, style)
                        if (attempt !in attempts) attempts += attempt
                    }
                }
            }

            val errors = mutableListOf<String>()

            for (attempt in attempts) {
                val attemptLabel = "${if (attempt.useFallbackEndpoint) "fallback" else "primario"}/${attempt.keyLabel}/${attempt.style.name}"
                try {
                    val encryptedPassword = RsaPasswordEncryptor.encrypt(password, attempt.publicKey)
                    val body = buildLoginPayload(username, encryptedPassword, attempt.style)
                    val response = if (attempt.useFallbackEndpoint) {
                        service.loginFallback(body)
                    } else {
                        service.login(body)
                    }

                    if (!response.isSuccessful) {
                        errors += "[$attemptLabel] HTTP ${response.code()}"
                        continue
                    }

                    val loginResponse = response.body()
                    if (loginResponse?.code != 200) {
                        // "data" a veces trae el motivo real del rechazo como texto (ej.
                        // "contraseña incorrecta") cuando "msg" viene null — se incluye
                        // crudo para no perder esa pista.
                        errors += "[$attemptLabel] code=${loginResponse?.code} msg=${loginResponse?.message} data=${loginResponse?.data}"
                        continue
                    }

                    val rawToken = loginResponse.extractToken()
                    if (rawToken.isNullOrBlank()) {
                        errors += "[$attemptLabel] sin token en la respuesta (data=${loginResponse.data})"
                        continue
                    }

                    token = if (rawToken.startsWith("Bearer_")) rawToken else "Bearer_$rawToken"
                    return
                } catch (e: Exception) {
                    errors += "[$attemptLabel] ${e.message ?: e.toString()}"
                }
            }

            throw FelicityAuthException(
                "Login falló en las 16 variantes soportadas: ${errors.joinToString(" | ")}"
            )
        }
    }

    private suspend fun <T> requestWithRetry(
        username: String,
        password: String,
        codeOf: (T) -> Int?,
        call: suspend (authToken: String) -> retrofit2.Response<T>
    ): T {
        ensureLogin(username, password)

        val firstToken = token ?: throw FelicityAuthException("Sin token tras login")
        val firstResponse = call(firstToken)
        val firstBody = firstResponse.body()

        val needsRetry = !firstResponse.isSuccessful ||
            firstBody == null ||
            codeOf(firstBody) in setOf(401, 403, 998)

        if (!needsRetry) return firstBody!!

        token = null
        login(username, password)
        val retryToken = token ?: throw FelicityAuthException("Sin token tras reintento de login")
        val retryResponse = call(retryToken)
        val retryBody = retryResponse.body()
            ?: throw FelicityApiException("Respuesta vacía tras reintento (HTTP ${retryResponse.code()})")

        return retryBody
    }

    suspend fun listDevices(username: String, password: String): DeviceListResponse =
        requestWithRetry(username, password, { it.code }) { token ->
            service.listDevices(token, DeviceListRequest())
        }

    suspend fun getDeviceSnapshot(username: String, password: String, deviceSn: String): SnapshotResponse =
        requestWithRetry(username, password, { it.code }) { token ->
            service.getDeviceSnapshot(
                token = token,
                body = SnapshotRequest(deviceSn = deviceSn, dateStr = LocalDateTime.now().format(dateFormatter))
            )
        }

    fun invalidateSession() {
        token = null
    }

    private data class PayloadStyle(val modern: Boolean, val useAccountField: Boolean) {
        val name: String get() = "${if (modern) "modern" else "legacy"}_${if (useAccountField) "account" else "userName"}"
    }

    private data class LoginAttempt(
        val useFallbackEndpoint: Boolean,
        val publicKey: String,
        val keyLabel: String,
        val style: PayloadStyle
    )
}
