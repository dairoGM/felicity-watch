package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.data.local.AppPreferences
import com.dairoroberto.felicitywatch.data.repository.EquipmentRepository
import com.dairoroberto.felicitywatch.data.repository.FelicityCredentialsMissingException
import com.dairoroberto.felicitywatch.data.repository.FelicityRepository
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sube el snapshot de equipos (alias/modelo/planta/país/propietario) de
 * este dispositivo a Supabase — con throttle largo una vez que ya se
 * registró al menos una vez (estos metadatos casi no cambian, a diferencia
 * de las lecturas de potencia), pero SIN throttle mientras nunca se haya
 * logrado: se consulta contra Supabase (no memoria local, resiliente a que
 * Android mate el proceso) si este dispositivo ya tiene un snapshot
 * guardado, y de no ser así se reintenta en CADA ciclo de monitoreo hasta
 * lograrlo — mismo criterio que UpdateDeviceLocationUseCase. Se llama desde
 * RunMonitoringCycleUseCase, best-effort: un fallo aquí (sin red, Felicity
 * caído) nunca debe cortar el ciclo.
 */
@Singleton
class EquipmentSyncUseCase @Inject constructor(
    private val felicityRepository: FelicityRepository,
    private val equipmentRepository: EquipmentRepository,
    private val appPreferences: AppPreferences
) {
    private var lastSyncAt: Instant? = null

    suspend fun run() {
        val now = Instant.now()
        val last = lastSyncAt
        if (last != null && Duration.between(last, now) < MIN_INTERVAL) return

        // Si este proceso nunca sincronizó desde que arrancó, puede ser
        // porque de verdad nunca se logró (vale la pena insistir en cada
        // ciclo) o porque el proceso se reinició y ya había un snapshot de
        // antes (no hace falta insistir). Se consulta Supabase solo en este
        // caso — no en cada ciclo normal — para no sumar una llamada de red
        // de más una vez resuelto el problema.
        if (last == null) {
            val deviceId = appPreferences.supabaseDeviceId()
            val alreadyReported = try {
                equipmentRepository.fetchEquipmentForDevice(deviceId) != null
            } catch (e: Exception) {
                null
            }
            if (alreadyReported == true) {
                lastSyncAt = now
                return
            }
        }

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
