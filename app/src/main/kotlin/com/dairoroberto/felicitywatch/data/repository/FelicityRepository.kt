package com.dairoroberto.felicitywatch.data.repository

import com.dairoroberto.felicitywatch.data.local.CredentialsStore
import com.dairoroberto.felicitywatch.data.remote.FelicityApiClient
import com.dairoroberto.felicitywatch.data.remote.FelicityApiException
import com.dairoroberto.felicitywatch.data.remote.FelicitySnapshotMapper
import com.dairoroberto.felicitywatch.data.remote.dto.DeviceDto
import com.dairoroberto.felicitywatch.domain.model.BatteryReading
import com.dairoroberto.felicitywatch.domain.model.DeviceInfo
import com.dairoroberto.felicitywatch.domain.model.DeviceRole
import com.dairoroberto.felicitywatch.domain.model.InverterReading
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class SystemReading(
    val inverter: InverterReading?,
    val battery: BatteryReading?,
    val inverterError: String? = null,
    val batteryError: String? = null
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

        // No dejar que ensureDevicesResolved() tumbe todo el ciclo: si falla,
        // se sigue con los seriales que ya se tuvieran en caché (puede haber
        // ninguno la primera vez, pero un fallo puntual de re-resolución no
        // debe borrar una lectura que de otro modo funcionaría).
        try {
            ensureDevicesResolved(username, password)
        } catch (e: Exception) {
            if (inverterSerial == null && batterySerial == null) throw e
        }

        val now = Instant.now()

        // Inversor y batería se leen de forma AISLADA: si uno falla (timeout,
        // respuesta inesperada de ESE dispositivo puntual), el otro debe
        // seguir mostrando datos en vez de que toda la lectura quede en null.
        var inverterReading: InverterReading? = null
        var inverterError: String? = null
        inverterSerial?.let { sn ->
            try {
                val snapshot = apiClient.getDeviceSnapshot(username, password, sn)
                inverterReading = snapshot.dataObject?.let { FelicitySnapshotMapper.toInverterReading(sn, it, now) }
                if (inverterReading == null) inverterError = "Snapshot del inversor sin datos utilizables"
            } catch (e: Exception) {
                inverterError = e.message ?: e.toString()
            }
        } ?: run { inverterError = "No se pudo identificar el inversor en la cuenta" }

        var batteryReading: BatteryReading? = null
        var batteryError: String? = null
        batterySerial?.let { sn ->
            try {
                val snapshot = apiClient.getDeviceSnapshot(username, password, sn)
                batteryReading = snapshot.dataObject?.let { FelicitySnapshotMapper.toBatteryReading(sn, it, now) }
                if (batteryReading == null) batteryError = "Snapshot de la batería sin datos utilizables"
            } catch (e: Exception) {
                batteryError = e.message ?: e.toString()
            }
        } ?: run { batteryError = "No se pudo identificar la batería en la cuenta" }

        if (inverterReading == null && batteryReading == null) {
            throw FelicityApiException(inverterError ?: batteryError ?: "Sin datos de ningún dispositivo")
        }

        return SystemReading(inverterReading, batteryReading, inverterError, batteryError)
    }

    /** Para la vista "Dispositivos": inversor y batería vinculados a la cuenta, con su serial. */
    suspend fun fetchDevices(): List<DeviceInfo> {
        val username = credentialsStore.fsolarUsername
        val password = credentialsStore.fsolarPassword
        if (username.isNullOrBlank() || password.isNullOrBlank()) {
            throw FelicityCredentialsMissingException()
        }

        return apiClient.listDevices(username, password).extractDeviceList().mapNotNull { dto ->
            val sn = dto.deviceSn ?: return@mapNotNull null
            val role = when (dto.deviceType?.uppercase()) {
                DeviceDto.TYPE_INVERTER -> DeviceRole.INVERTER
                DeviceDto.TYPE_BATTERY -> DeviceRole.BATTERY
                else -> DeviceRole.OTHER
            }
            DeviceInfo(
                serialNumber = sn,
                role = role,
                model = dto.deviceModel,
                alias = dto.alias,
                status = dto.status,
                plantName = dto.plantName
            )
        }
    }

    fun resetDeviceCache() {
        inverterSerial = null
        batterySerial = null
        apiClient.invalidateSession()
    }
}
