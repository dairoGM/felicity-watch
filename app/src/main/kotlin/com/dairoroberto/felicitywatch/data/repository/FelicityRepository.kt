package com.dairoroberto.felicitywatch.data.repository

import com.dairoroberto.felicitywatch.data.local.CredentialsStore
import com.dairoroberto.felicitywatch.data.remote.FelicityApiClient
import com.dairoroberto.felicitywatch.data.remote.FelicitySnapshotMapper
import com.dairoroberto.felicitywatch.data.remote.dto.DeviceDto
import com.dairoroberto.felicitywatch.domain.model.BatteryReading
import com.dairoroberto.felicitywatch.domain.model.InverterReading
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class SystemReading(
    val inverter: InverterReading?,
    val battery: BatteryReading?
)

class FelicityCredentialsMissingException : Exception("No hay credenciales FSolar configuradas")

/**
 * Login + descubrimiento de dispositivos + lectura de snapshot, sobre
 * FelicityApiClient (puerto de felicityAPI). No hace polling por sí mismo:
 * el ciclo de 30s y el manejo de fallos consecutivos vive en
 * MonitoringForegroundService (guía sección 6), que llama a
 * [fetchLatestReading] en cada tick.
 */
@Singleton
class FelicityRepository @Inject constructor(
    private val apiClient: FelicityApiClient,
    private val credentialsStore: CredentialsStore
) {
    @Volatile
    private var inverterSerial: String? = null

    @Volatile
    private var batterySerial: String? = null

    private suspend fun ensureDevicesResolved(username: String, password: String) {
        // OJO: reintentar mientras falte CUALQUIERA de los dos seriales, no
        // solo cuando falten ambos — si la cuenta devuelve el inversor pero
        // todavía no la batería (o viceversa) en el primer listado, antes se
        // dejaba de reintentar para siempre y ese dispositivo nunca se leía.
        if (inverterSerial != null && batterySerial != null) return

        val devices = apiClient.listDevices(username, password).extractDeviceList()
        inverterSerial = inverterSerial
            ?: devices.firstOrNull { it.deviceType?.uppercase() == DeviceDto.TYPE_INVERTER }?.deviceSn
        batterySerial = batterySerial
            ?: devices.firstOrNull { it.deviceType?.uppercase() == DeviceDto.TYPE_BATTERY }?.deviceSn
    }

    suspend fun fetchLatestReading(): SystemReading {
        val username = credentialsStore.fsolarUsername
        val password = credentialsStore.fsolarPassword
        if (username.isNullOrBlank() || password.isNullOrBlank()) {
            throw FelicityCredentialsMissingException()
        }

        ensureDevicesResolved(username, password)
        val now = Instant.now()

        val inverterReading = inverterSerial?.let { sn ->
            val snapshot = apiClient.getDeviceSnapshot(username, password, sn)
            snapshot.data?.let { FelicitySnapshotMapper.toInverterReading(sn, it, now) }
        }

        val batteryReading = batterySerial?.let { sn ->
            val snapshot = apiClient.getDeviceSnapshot(username, password, sn)
            snapshot.data?.let { FelicitySnapshotMapper.toBatteryReading(sn, it, now) }
        }

        return SystemReading(inverterReading, batteryReading)
    }

    fun resetDeviceCache() {
        inverterSerial = null
        batterySerial = null
        apiClient.invalidateSession()
    }
}
