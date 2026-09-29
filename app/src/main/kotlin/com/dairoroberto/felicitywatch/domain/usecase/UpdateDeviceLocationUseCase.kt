package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.data.local.LocationTracker
import com.dairoroberto.felicitywatch.data.repository.DeviceRoleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Actualiza la ubicación de este dispositivo en Supabase, para la vista de
 * mapa de la master. Se llama desde cada ciclo de monitoreo normal
 * (RunMonitoringCycleUseCase), con la misma cadencia configurada en
 * Ajustes > Sistema > "Frecuencia de consulta" — pero nunca más seguido que
 * [MIN_INTERVAL], como piso de seguridad para que un intervalo de consulta
 * muy corto (la app permite hasta 5s) no dispare una petición de ubicación
 * en cada ciclo. Un intento fallido (sin permiso, sin GPS, error de red) NO
 * cuenta para este piso: se reintenta en el siguiente ciclo sin esperar.
 *
 * Mientras este dispositivo NUNCA haya logrado reportar una ubicación (se
 * verifica contra Supabase, no solo el estado en memoria — ver
 * [DeviceRoleRepository.hasReportedLocationBefore]), el throttle se ignora
 * por completo y se reintenta en CADA ciclo: hay clientes que por alguna
 * razón puntual (GPS lento, permiso concedido a destiempo, primer intento
 * fallido) nunca llegaban a capturar la primera ubicación, y con el
 * throttle normal eso podía tardar mucho en corregirse solo.
 */
@Singleton
class UpdateDeviceLocationUseCase @Inject constructor(
    private val locationTracker: LocationTracker,
    private val deviceRoleRepository: DeviceRoleRepository
) {
    private var lastCaptureAt: Instant? = null

    /**
     * Último resultado del intento de reportar ubicación: null = se guardó
     * bien (o todavía no se intentó), o un mensaje legible de por qué no —
     * lo consume Ajustes > Sistema para no fallar en silencio como antes.
     */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    suspend fun run() {
        if (!locationTracker.hasLocationPermission()) {
            _lastError.value = "Falta el permiso de ubicación."
            return
        }

        val now = Instant.now()
        val last = lastCaptureAt
        if (last != null && Duration.between(last, now) < MIN_INTERVAL) return

        // Si este proceso nunca logró capturar desde que arrancó, puede ser
        // porque de verdad nunca se logró (vale la pena insistir en cada
        // ciclo) o porque el proceso se reinició y en realidad ya había una
        // ubicación guardada de antes (no hace falta insistir). Se consulta
        // Supabase solo en este caso — no en cada ciclo normal — para no
        // sumar una llamada de red de más una vez resuelto el problema.
        if (last == null && deviceRoleRepository.hasReportedLocationBefore() == true) {
            // Ya hay una ubicación de una sesión anterior del proceso — no
            // insistir en cada ciclo; se comporta como si ya hubiera
            // capturado "ahora" para que el throttle normal aplique.
            lastCaptureAt = now
            return
        }

        val location = locationTracker.getCurrentLocation()
        if (location == null) {
            _lastError.value = "No se pudo obtener la ubicación (revisa que el GPS/ubicación del sistema esté encendido)."
            return
        }

        _lastError.value = deviceRoleRepository.updateOwnLocation(location.latitude, location.longitude)
        lastCaptureAt = now
    }

    private companion object {
        val MIN_INTERVAL: Duration = Duration.ofMinutes(2)
    }
}
