package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.data.local.AppPreferences
import com.dairoroberto.felicitywatch.data.repository.SystemReading
import com.dairoroberto.felicitywatch.domain.model.GridState
import com.dairoroberto.felicitywatch.notification.LowVoltageAlertPlayer
import com.dairoroberto.felicitywatch.notification.PushNotifier
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aviso cuando el voltaje cae por debajo del umbral configurado.
 *
 * Un voltaje bajo daña equipos (motores y compresores sobre todo), y a
 * diferencia de un apagón no se nota: la casa sigue encendida mientras la
 * nevera y el aire trabajan forzados. Por eso el aviso vale la pena aunque
 * todo "parezca" normal.
 *
 * Vigila el voltaje de la fuente que está alimentando la casa: la red cuando
 * hay corriente, la salida del inversor hacia la casa cuando no.
 *
 * Dos protecciones contra el spam de avisos, que es el riesgo real de vigilar
 * un valor que fluctúa alrededor de un umbral:
 *
 * - Solo se avisa al CRUZAR el umbral hacia abajo, no en cada lectura que siga
 *   estando baja. Sin esto, con el intervalo de 5 s el teléfono sonaría doce
 *   veces por minuto.
 * - Tras avisar, hay un tiempo mínimo de silencio antes de poder volver a
 *   avisar, incluso si el voltaje sube y baja repetidamente (lo que ocurre de
 *   verdad cuando la red está inestable).
 */
@Singleton
class NotifyLowVoltageUseCase @Inject constructor(
    private val appPreferences: AppPreferences,
    private val pushNotifier: PushNotifier,
    private val alertPlayer: LowVoltageAlertPlayer
) {
    /** Si la lectura anterior ya estaba bajo el umbral. */
    private var wasBelow = false

    /** Cuándo se avisó por última vez, para el silencio mínimo. */
    private var lastAlertAt: Instant? = null

    /**
     * Se llama con cada lectura nueva. Devuelve el voltaje que disparó el
     * aviso, o null si no hubo nada que avisar.
     */
    suspend fun onReading(reading: SystemReading, gridState: GridState): Double? {
        if (!appPreferences.lowVoltageAlertEnabled.first()) {
            // Se reinicia el estado al desactivar: si el usuario lo vuelve a
            // activar con el voltaje ya bajo, debe recibir el aviso.
            wasBelow = false
            return null
        }

        val online = gridState == GridState.ONLINE
        // Ambos en la misma escala AC (110/120V): el de red con corriente, el
        // de salida del inversor sin ella. reading.battery?.voltage es el DC
        // del banco (48V nominal) y NO es comparable con el umbral configurado
        // en voltios de red — usarlo aqui habria dejado esta alerta sin
        // disparar nunca estando sin corriente.
        val voltage = if (online) {
            reading.inverter?.gridVoltage
        } else {
            reading.inverter?.outputVoltage
        }

        // Sin dato de voltaje no se puede juzgar. NO se marca como "no bajo":
        // un hueco en las lecturas no significa que el voltaje se recuperó, y
        // asumirlo dispararía un aviso duplicado en la siguiente lectura baja.
        if (voltage == null || voltage < 1.0) return null

        val threshold = appPreferences.lowVoltageThreshold.first()
        val isBelow = voltage < threshold

        if (!isBelow) {
            wasBelow = false
            return null
        }

        // Ya estaba bajo en la lectura anterior: no se repite el aviso.
        if (wasBelow) return null
        wasBelow = true

        // Silencio mínimo entre avisos, para el caso de una red que oscila
        // alrededor del umbral.
        val now = Instant.now()
        val since = lastAlertAt?.let { Duration.between(it, now).toMinutes() }
        if (since != null && since < MIN_MINUTES_BETWEEN_ALERTS) return null
        lastAlertAt = now

        val source = if (online) "la red" else "el inversor (con batería)"
        pushNotifier.notifyAlert(
            title = "Voltaje bajo",
            body = "El voltaje de $source cayó a ${formatVolts(voltage)}, por debajo de los " +
                "$threshold V configurados. Un voltaje bajo puede dañar motores y compresores.",
            notificationId = NOTIFICATION_ID
        )
        alertPlayer.play()

        return voltage
    }

    /** Olvida el estado (ej. al reiniciar el servicio). */
    fun reset() {
        wasBelow = false
    }

    private fun formatVolts(volts: Double): String =
        String.format(java.util.Locale("es", "ES"), "%.1f V", volts)

    private companion object {
        /** ID fijo: un aviso de voltaje bajo reemplaza al anterior en vez de
         * apilarse, porque describe un estado continuo y no eventos sueltos. */
        const val NOTIFICATION_ID = 6_001
        const val MIN_MINUTES_BETWEEN_ALERTS = 10L
    }
}
