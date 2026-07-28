package com.dairoroberto.felicitywatch.data.remote

import com.dairoroberto.felicitywatch.data.remote.dto.DeviceListResponse
import com.dairoroberto.felicitywatch.data.remote.dto.LoginRequest
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
 * FelicityAPI de felicityAPI (api.py): intenta el endpoint/clave/payload
 * "conocido funcional" primero, cae a variantes si falla, y ante un código
 * de negocio 401/403/998 en la respuesta limpia el token y reintenta login
 * una vez antes de repetir la llamada original.
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

    private suspend fun login(username: String, password: String) {
        loginMutex.withLock {
            if (token != null) return

            val attempts = listOf(
                LoginAttempt(useFallbackEndpoint = false, publicKey = RsaPasswordEncryptor.PUBLIC_KEY_PRIMARY),
                LoginAttempt(useFallbackEndpoint = false, publicKey = RsaPasswordEncryptor.PUBLIC_KEY_FALLBACK),
                LoginAttempt(useFallbackEndpoint = true, publicKey = RsaPasswordEncryptor.PUBLIC_KEY_PRIMARY),
                LoginAttempt(useFallbackEndpoint = true, publicKey = RsaPasswordEncryptor.PUBLIC_KEY_FALLBACK)
            )

            val errors = mutableListOf<String>()

            for (attempt in attempts) {
                try {
                    val encryptedPassword = RsaPasswordEncryptor.encrypt(password, attempt.publicKey)
                    val body = LoginRequest(userName = username, password = encryptedPassword)
                    val response = if (attempt.useFallbackEndpoint) {
                        service.loginFallback(body)
                    } else {
                        service.login(body)
                    }

                    if (!response.isSuccessful) {
                        errors += "HTTP ${response.code()}"
                        continue
                    }

                    val loginResponse = response.body()
                    if (loginResponse?.code != 200) {
                        errors += "code=${loginResponse?.code} msg=${loginResponse?.message}"
                        continue
                    }

                    val rawToken = loginResponse.data?.token
                    if (rawToken.isNullOrBlank()) {
                        errors += "sin token en la respuesta"
                        continue
                    }

                    token = if (rawToken.startsWith("Bearer_")) rawToken else "Bearer_$rawToken"
                    return
                } catch (e: Exception) {
                    errors += e.message ?: e.toString()
                }
            }

            throw FelicityAuthException("Login falló en todas las variantes soportadas: ${errors.joinToString(" | ")}")
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
        requestWithRetry(username, password, { it.code }) { token -> service.listDevices(token) }

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

    private data class LoginAttempt(val useFallbackEndpoint: Boolean, val publicKey: String)
}
