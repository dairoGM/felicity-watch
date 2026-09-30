package com.dairoroberto.felicitywatch.data.repository

import com.dairoroberto.felicitywatch.data.local.AppPreferences
import com.dairoroberto.felicitywatch.data.remote.SupabaseApiService
import com.dairoroberto.felicitywatch.data.remote.dto.AccountDeviceEquipmentUpsertDto
import com.dairoroberto.felicitywatch.data.remote.dto.EquipmentDeviceDto
import com.dairoroberto.felicitywatch.domain.model.DeviceInfo
import com.dairoroberto.felicitywatch.domain.model.DeviceRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sincroniza el snapshot de equipos (inversor/batería, con su alias/modelo/
 * planta/país/propietario) de ESTE dispositivo hacia Supabase, y permite a
 * la master consultar el de un cliente puntual — ver migración
 * 003_account_device_equipment.sql. Alimenta la pestaña "Equipos" del
 * detalle de un cliente (pestaña Clientes), para que se vea igual que la
 * pantalla Equipos del propio cliente sin necesitar sus credenciales de
 * FSolar.
 */
@Singleton
class EquipmentRepository @Inject constructor(
    private val api: SupabaseApiService,
    private val appPreferences: AppPreferences
) {
    /**
     * Último resultado del intento de subir el snapshot de equipos: null =
     * se guardó bien (o todavía no se intentó), o un mensaje legible de por
     * qué no — lo consume Ajustes para diagnóstico, mismo patrón que
     * UpdateDeviceLocationUseCase.lastError (antes esto fallaba en
     * silencio: cualquier error de Supabase, incluida la tabla sin crear o
     * el device_id todavía sin fila en account_devices, quedaba atrapado en
     * el try/catch best-effort de RunMonitoringCycleUseCase sin dejar
     * ningún rastro visible).
     */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    /** Sube el snapshot de equipos de este dispositivo. Best-effort: quien
     * llama decide qué hacer con una excepción (ver EquipmentSyncUseCase). */
    suspend fun pushOwnEquipment(devices: List<DeviceInfo>) {
        val deviceId = appPreferences.supabaseDeviceId()
        try {
            val response = api.upsertAccountDeviceEquipment(
                equipment = listOf(
                    AccountDeviceEquipmentUpsertDto(
                        deviceId = deviceId,
                        devices = devices.map { it.toDto() }
                    )
                )
            )
            if (!response.isSuccessful) {
                val message = "Supabase rechazó la actualización (HTTP ${response.code()}): ${response.errorBody()?.string()}"
                _lastError.value = message
                throw SupabaseSyncException(response.code(), response.errorBody()?.string())
            }
            _lastError.value = null
        } catch (e: SupabaseSyncException) {
            throw e
        } catch (e: Exception) {
            _lastError.value = "Error de red al subir equipos: ${e.message}"
            throw e
        }
    }

    /** Equipos de un dispositivo puntual, o null si nunca sincronizó (o el
     * cliente no tiene la sincronización aplicada todavía). */
    suspend fun fetchEquipmentForDevice(deviceId: String): List<DeviceInfo>? {
        val response = api.getAccountDeviceEquipment(deviceIdFilter = "eq.$deviceId")
        if (!response.isSuccessful) {
            throw SupabaseSyncException(response.code(), response.errorBody()?.string())
        }
        return response.body()?.firstOrNull()?.devices?.map { it.toDomain() }
    }

    private fun DeviceInfo.toDto() = EquipmentDeviceDto(
        serialNumber = serialNumber,
        role = role.name,
        model = model,
        alias = alias,
        status = status,
        plantName = plantName,
        plantId = plantId,
        ownerName = ownerName,
        countryName = countryName,
        ratedPowerKw = ratedPowerKw
    )

    private fun EquipmentDeviceDto.toDomain() = DeviceInfo(
        serialNumber = serialNumber,
        role = runCatching { DeviceRole.valueOf(role) }.getOrDefault(DeviceRole.OTHER),
        model = model,
        alias = alias,
        status = status,
        plantName = plantName,
        plantId = plantId,
        ownerName = ownerName,
        countryName = countryName,
        ratedPowerKw = ratedPowerKw
    )
}
