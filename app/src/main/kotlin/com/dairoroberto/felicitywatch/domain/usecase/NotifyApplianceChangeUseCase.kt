package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.data.local.AppPreferences
import com.dairoroberto.felicitywatch.data.repository.ApplianceRepository
import com.dairoroberto.felicitywatch.notification.PushNotifier
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Aviso en vivo, por notificación push, cuando un equipo del inventario se
 * enciende o se apaga.
 *
 * Por qué existe aparte de [ApplianceEventDetector]: la bitácora de
 * Actividad RECALCULA sus eventos desde el historial cada vez que se abre la
 * pantalla, así que no hay ningún instante en el que "ocurra" un evento. Para
 * avisar hace falta detectar el cambio en el momento en que llega la lectura,
 * que es lo que hace esta clase desde el ciclo de monitoreo.
 *
 * Deliberadamente NO avisa de todo:
 *
 * - Solo cuando el escalón coincide con un equipo del inventario. Un aviso
 *   por "algo de 300 W" no le sirve de nada al usuario y llegaría a lo largo
 *   de todo el día.
 * - Solo por encima del umbral configurado, que el usuario puede subir si su
 *   casa genera demasiados avisos (el compresor de la nevera es el caso
 *   típico).
 *
 * Limitación heredada que conviene no disimular: el aviso llega cuando la app
 * LEE el dato, no cuando el equipo se enciende. Entre ambos momentos está el
 * intervalo de consulta más el retraso con que el inversor publica sus datos
 * en la nube de Felicity.
 */
@Singleton
class NotifyApplianceChangeUseCase @Inject constructor(
    private val applianceRepository: ApplianceRepository,
    private val appPreferences: AppPreferences,
    private val pushNotifier: PushNotifier
) {
    /** Consumo de la lectura anterior, para calcular el escalón. */
    private var previousLoadWatts: Int? = null

    /**
     * Se llama con el consumo de cada lectura nueva. Devuelve el equipo
     * detectado si se emitió un aviso, o null si no había nada que avisar.
     */
    suspend fun onLoadReading(loadPowerWatts: Int?): ApplianceAlert? {
        // Sin dato de consumo no se puede comparar, y además la cadena de
        // lecturas queda cortada: se descarta la referencia anterior para no
        // calcular después un escalón contra una lectura vieja de hace rato.
        if (loadPowerWatts == null) {
            previousLoadWatts = null
            return null
        }

        val previous = previousLoadWatts
        previousLoadWatts = loadPowerWatts
        if (previous == null) return null

        if (!appPreferences.applianceAlertsEnabled.first()) return null

        val delta = loadPowerWatts - previous
        val magnitude = abs(delta)
        val threshold = appPreferences.applianceAlertThresholdWatts.first()
        if (magnitude < threshold) return null

        // Solo se avisa si el escalón coincide con un equipo conocido; se usa
        // la misma regla de coincidencia por rango que la bitácora, para que
        // el aviso y lo que después se ve listado no se contradigan.
        val catalog = applianceRepository.getAll()
        val candidates = catalog.filter { it.matches(magnitude) }
        val best = candidates.minByOrNull { it.distanceTo(magnitude) } ?: return null

        val turnedOn = delta > 0
        val title = if (turnedOn) "Se conectó un equipo" else "Se desconectó un equipo"

        val body = buildString {
            append(if (turnedOn) "Se ha conectado" else "Se ha desconectado")
            append(" un equipo que sobrepasa el umbral de los ")
            append(threshold)
            append(" W: ")
            append(best.name)
            if (best.room.isNotBlank()) {
                append(" (")
                append(best.room)
                append(")")
            }
            append(". Consumo detectado: ")
            append(magnitude)
            append(" W.")
            // Si varios equipos encajan, el nombre es una propuesta y no un
            // hecho: decirlo evita que el usuario confíe de más en el aviso.
            if (candidates.size > 1) {
                append(" Coincide con ${candidates.size} equipos, puede ser otro de consumo parecido.")
            }
        }

        // ID único por evento para que los avisos no se sobreescriban entre
        // sí: con un ID fijo, un segundo equipo reemplazaría el aviso del
        // primero y el usuario perdería el anterior.
        val notificationId = NOTIFICATION_ID_BASE + (System.currentTimeMillis() % 10_000).toInt()
        pushNotifier.notifyAlert(title = title, body = body, notificationId = notificationId)

        return ApplianceAlert(
            applianceName = best.name,
            room = best.room,
            turnedOn = turnedOn,
            deltaWatts = delta,
            ambiguous = candidates.size > 1
        )
    }

    /** Olvida la referencia anterior (ej. al reiniciar el servicio). */
    fun reset() {
        previousLoadWatts = null
    }

    private companion object {
        /** Rango propio de IDs, para no chocar con los push de las reglas de
         * alerta ni con la notificación persistente del servicio. */
        const val NOTIFICATION_ID_BASE = 5_000
    }
}

/** Aviso emitido, para que quien lo dispare pueda además notificarlo. */
data class ApplianceAlert(
    val applianceName: String,
    val room: String,
    val turnedOn: Boolean,
    val deltaWatts: Int,
    /** Varios equipos encajaban en el escalón: el nombre es una propuesta. */
    val ambiguous: Boolean
)
