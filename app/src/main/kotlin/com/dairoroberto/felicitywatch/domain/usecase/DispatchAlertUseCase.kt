package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.data.local.AlertEventEntity
import com.dairoroberto.felicitywatch.data.local.AlertRuleEntity
import com.dairoroberto.felicitywatch.data.repository.AlertEventRepository
import com.dairoroberto.felicitywatch.notification.PushNotifier
import com.dairoroberto.felicitywatch.notification.VoiceAlertPlayer
import com.dairoroberto.felicitywatch.notification.WhatsappAlertSender
import com.dairoroberto.felicitywatch.notification.WhatsappSendResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orquesta los 3 canales EN PARALELO (guía sección 7.4): un fallo en un
 * canal (ej. WhatsApp lento/caído) no debe impedir que los otros se
 * disparen. Cada resultado se persiste en AlertEventEntity.
 */
@Singleton
class DispatchAlertUseCase @Inject constructor(
    private val voicePlayer: VoiceAlertPlayer,
    private val pushNotifier: PushNotifier,
    private val whatsappSender: WhatsappAlertSender,
    private val eventRepository: AlertEventRepository
) {
    suspend fun dispatch(rule: AlertRuleEntity, message: String) = coroutineScope {
        val voiceDeferred = async {
            if (!rule.channelVoiceEnabled) return@async false
            runCatching { voicePlayer.speak(message) }.getOrDefault(false)
        }
        val pushDeferred = async {
            if (!rule.channelPushEnabled) return@async false
            runCatching {
                pushNotifier.notifyAlert(title = "Felicity Watch", body = message, notificationId = rule.type.ordinal)
            }.getOrDefault(false)
        }
        val whatsappDeferred = async {
            if (!rule.channelWhatsappEnabled) return@async WhatsappSendResult(success = false, error = null)
            runCatching { whatsappSender.send(message) }
                .getOrDefault(WhatsappSendResult(success = false, error = "Error interno al enviar WhatsApp"))
        }

        val voiceSent = voiceDeferred.await()
        val pushSent = pushDeferred.await()
        val whatsappResult = whatsappDeferred.await()

        eventRepository.record(
            AlertEventEntity(
                ruleType = rule.type,
                triggeredAt = Instant.now(),
                message = message,
                voiceSent = voiceSent,
                pushSent = pushSent,
                whatsappSent = whatsappResult.success,
                whatsappError = whatsappResult.error
            )
        )
    }
}
