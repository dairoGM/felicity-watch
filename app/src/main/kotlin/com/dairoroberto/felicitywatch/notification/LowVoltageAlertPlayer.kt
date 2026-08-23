package com.dairoroberto.felicitywatch.notification

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Señal de voltaje bajo: tres pulsos graves, "mmm mmm mmm".
 *
 * El patrón de tres es deliberado y distinto del resto de avisos de la app
 * (encendido de equipo, apagón): tres pulsos iguales se reconocen como una
 * advertencia sin tener que mirar la pantalla, y el tono grave se asocia a un
 * problema mejor que un pitido agudo.
 *
 * Se usa ToneGenerator del SDK y no un archivo de audio para no agregar
 * assets ni dependencias.
 */
@Singleton
class LowVoltageAlertPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** [intense] = mismo patrón "mmm mmm mmm" pero más fuerte y con un pulso
     * más — para avisos que ameritan más urgencia (ej. consumo cerca del
     * límite del inversor, autonomía de batería agotándose) sin inventar un
     * sonido nuevo que el usuario tenga que aprender a reconocer. */
    suspend fun play(intense: Boolean = false) {
        vibrate(intense)
        // El tono bloquea mientras suena, así que va en IO.
        withContext(Dispatchers.IO) { playTone(intense) }
    }

    private fun vibrate(intense: Boolean) {
        val vibrator = resolveVibrator() ?: return
        if (!vibrator.hasVibrator()) return

        // Tres pulsos medios con pausa entre ellos: el equivalente táctil del
        // "mmm mmm mmm". La variante intensa agrega un cuarto pulso y sube la
        // amplitud al máximo.
        val pulseCount = if (intense) INTENSE_PULSE_COUNT else PULSE_COUNT
        val amplitude = if (intense) 255 else 190
        val timings = mutableListOf(0L)
        val amplitudes = mutableListOf(0)
        repeat(pulseCount) { index ->
            timings += 220L
            amplitudes += amplitude
            if (index < pulseCount - 1) {
                timings += 140L
                amplitudes += 0
            }
        }

        try {
            vibrator.vibrate(VibrationEffect.createWaveform(timings.toLongArray(), amplitudes.toIntArray(), -1))
        } catch (e: Exception) {
            // Un fallo de vibración no debe tumbar el ciclo de monitoreo.
        }
    }

    private fun resolveVibrator(): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: Exception) {
        null
    }

    private fun playTone(intense: Boolean) {
        var generator: ToneGenerator? = null
        try {
            val volume = if (intense) MAX_TONE_VOLUME else TONE_VOLUME
            val pulseCount = if (intense) INTENSE_PULSE_COUNT else PULSE_COUNT
            generator = ToneGenerator(AudioManager.STREAM_ALARM, volume)
            repeat(pulseCount) { index ->
                // TONE_SUP_ERROR es un tono grave y áspero, apropiado para una
                // advertencia; los BEEP agudos suenan a confirmación.
                generator.startTone(ToneGenerator.TONE_SUP_ERROR, PULSE_DURATION_MILLIS)
                Thread.sleep(PULSE_DURATION_MILLIS.toLong())
                if (index < pulseCount - 1) Thread.sleep(PULSE_GAP_MILLIS)
            }
        } catch (e: Exception) {
            // Sin audio disponible (o en silencio) queda la vibración, que es
            // aceptable para un aviso.
        } finally {
            try {
                generator?.release()
            } catch (e: Exception) {
                // Nada que hacer si ya estaba liberado.
            }
        }
    }

    private companion object {
        /** Sobre 100. Más alto que el aviso de equipos: esto es una advertencia
         * de daño potencial, no una notificación informativa. */
        const val TONE_VOLUME = 85
        const val MAX_TONE_VOLUME = 100
        const val PULSE_COUNT = 3
        const val INTENSE_PULSE_COUNT = 4
        const val PULSE_DURATION_MILLIS = 220
        const val PULSE_GAP_MILLIS = 140L
    }
}
