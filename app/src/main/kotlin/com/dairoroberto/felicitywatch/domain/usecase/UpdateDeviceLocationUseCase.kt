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
