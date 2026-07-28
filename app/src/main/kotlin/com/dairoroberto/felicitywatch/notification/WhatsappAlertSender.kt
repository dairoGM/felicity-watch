package com.dairoroberto.felicitywatch.notification

import com.dairoroberto.felicitywatch.data.local.CredentialsStore
import javax.inject.Inject
import javax.inject.Singleton

data class WhatsappSendResult(val success: Boolean, val error: String?)

/**
 * CallMeBot puede tardar varios minutos en horas pico — la guía (sección
 * 7.3) es explícita en que eso NO se trata como error, solo se loguea si
 * la respuesta HTTP no fue 200.
 */
@Singleton
class WhatsappAlertSender @Inject constructor(
    private val api: CallMeBotApi,
    private val credentialsStore: CredentialsStore
) {
    suspend fun send(message: String): WhatsappSendResult {
        val phone = credentialsStore.whatsappPhone
        val apiKey = credentialsStore.callMeBotApiKey

        if (phone.isNullOrBlank() || apiKey.isNullOrBlank()) {
            return WhatsappSendResult(success = false, error = "WhatsApp no configurado (falta número o apiKey)")
        }

        return try {
            val response = api.sendMessage(phone = phone, text = message, apiKey = apiKey)
            if (response.isSuccessful) {
                WhatsappSendResult(success = true, error = null)
            } else {
                WhatsappSendResult(success = false, error = "HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            WhatsappSendResult(success = false, error = e.message ?: e.toString())
        }
    }
}
