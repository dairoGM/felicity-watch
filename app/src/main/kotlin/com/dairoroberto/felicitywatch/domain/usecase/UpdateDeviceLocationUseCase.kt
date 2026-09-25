package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.data.local.LocationTracker
import com.dairoroberto.felicitywatch.data.repository.DeviceRoleRepository
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Actualiza la ubicación de este dispositivo en Supabase, para la vista de
 * mapa de la master (guía: "geolocalización de cada dispositivo"). Se llama
 * desde cada ciclo de monitoreo normal, pero solo captura de verdad cada
 * [MIN_INTERVAL] — la ubicación no cambia entre un ciclo de 30s y el
 * siguiente, así que pedirla en cada uno solo gastaría batería sin aportar
 * nada nuevo al mapa.
 */
@Singleton
class UpdateDeviceLocationUseCase @Inject constructor(
    private val locationTracker: LocationTracker,
    private val deviceRoleRepository: DeviceRoleRepository
) {
    private var lastCaptureAt: Instant? = null

    suspend fun run(now: Instant) {
        if (!locationTracker.hasLocationPermission()) return

        val last = lastCaptureAt
        if (last != null && Duration.between(last, now) < MIN_INTERVAL) return

        val location = locationTracker.getCurrentLocation() ?: return
        deviceRoleRepository.updateOwnLocation(location.latitude, location.longitude)
        lastCaptureAt = now
    }

    private companion object {
        val MIN_INTERVAL: Duration = Duration.ofMinutes(20)
    }
}
