package com.dairoroberto.felicitywatch.notification

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * TTS nativo de Android (guía sección 7.1). Se inicializa una sola vez y
 * se reutiliza; funciona con pantalla apagada porque corre dentro del
 * Foreground Service, no de una Activity.
 */
@Singleton
class VoiceAlertPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null
    private var ready = false

    private suspend fun ensureInitialized(): Boolean {
        if (ready) return true
        return suspendCancellableCoroutine { continuation ->
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val locale = Locale("es", "ES")
                    val result = tts?.setLanguage(locale)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale("es"))
                    }
                    ready = true
                }
                if (continuation.isActive) continuation.resume(ready)
            }
        }
    }

    suspend fun speak(message: String): Boolean {
        val initialized = ensureInitialized()
        if (!initialized) return false

        val utteranceId = "felicity_watch_${System.identityHashCode(message)}_${message.length}"
        return suspendCancellableCoroutine { continuation ->
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    if (continuation.isActive) continuation.resume(true)
                }

                @Deprecated("Deprecated in API, kept for older devices")
                override fun onError(utteranceId: String?) {
                    if (continuation.isActive) continuation.resume(false)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (continuation.isActive) continuation.resume(false)
                }
            })

            val speakResult = tts?.speak(message, TextToSpeech.QUEUE_ADD, null, utteranceId)
            if (speakResult != TextToSpeech.SUCCESS && continuation.isActive) {
                continuation.resume(false)
            }
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
