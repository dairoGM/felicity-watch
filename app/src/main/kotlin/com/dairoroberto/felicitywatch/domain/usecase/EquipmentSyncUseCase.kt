package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.data.repository.EquipmentRepository
import com.dairoroberto.felicitywatch.data.repository.FelicityCredentialsMissingException
import com.dairoroberto.felicitywatch.data.repository.FelicityRepository
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sube el snapshot de equipos (alias/modelo/planta/país/propietario) de
 * este dispositivo a Supabase, con throttle largo — a diferencia de las
 * lecturas de potencia (que cambian todo el tiempo), estos metadatos casi
 * nunca cambian, así que no tiene sentido consultarlos en cada ciclo de
 * monitoreo. Se llama desde RunMonitoringCycleUseCase, best-effort: un
 * fallo aquí (sin red, Felicity caído) nunca debe cortar el ciclo.
 */
@Singleton
class EquipmentSyncUseCase @Inject constructor(
    private val felicityRepository: FelicityRepository,
    private val equipmentRepository: EquipmentRepository
) {
    private var lastSyncAt: Instant? = null

    suspend fun run() {
        val now = Instant.now()
        val last = lastSyncAt
        if (last != null && Duration.between(last, now) < MIN_INTERVAL) return

        try {
            val devices = felicityRepository.fetchDevices()
            equipmentRepository.pushOwnEquipment(devices)
            lastSyncAt = now
        } catch (e: FelicityCredentialsMissingException) {
            // No hay nada que sincronizar sin credenciales — no es un fallo
            // real, se reintenta solo cuando el ciclo normal vuelva a llamar.
        }
        // Cualquier otra excepción (red, Supabase, Felicity) se deja
        // propagar al try/catch best-effort de RunMonitoringCycleUseCase;
        // lastSyncAt no avanza, así que el próximo ciclo reintenta sin
        // esperar el throttle completo.
    }

    private companion object {
        val MIN_INTERVAL: Duration = Duration.ofHours(6)
    }
}
