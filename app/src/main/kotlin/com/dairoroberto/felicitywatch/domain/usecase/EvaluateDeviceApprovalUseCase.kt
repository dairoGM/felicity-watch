package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.data.local.AppPreferences
import com.dairoroberto.felicitywatch.data.repository.ApprovalStatus
import com.dairoroberto.felicitywatch.data.repository.DeviceRoleRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Si este dispositivo puede monitorear (consultar Felicity y usar la app), o debe quedar bloqueado. */
sealed class DeviceAccessDecision {
    data object Allowed : DeviceAccessDecision()
    data object Blocked : DeviceAccessDecision()
}

/**
 * Chequeo de arranque del modelo master/cliente (guía: una sola instalación
 * master por cuenta, el resto son clientes que requieren aprobación vía PIN).
 *
 * La master siempre está permitida — es la única fuente de verdad que
 * consulta Felicity y sincroniza a Supabase, exactamente como antes de este
 * sistema. Un cliente sin aprobación vigente queda bloqueado por completo,
 * aunque sus credenciales de FSolar sean válidas: Supabase actúa aquí como
 * una verificación de admisión (una especie de segundo factor), no como
 * fuente de datos para el cliente — el cliente sigue consultando Felicity y
 * guardando en Room exactamente igual que siempre una vez aprobado.
 */
class EvaluateDeviceApprovalUseCase @Inject constructor(
    private val appPreferences: AppPreferences,
    private val deviceRoleRepository: DeviceRoleRepository
) {
    suspend fun evaluate(): DeviceAccessDecision {
        if (appPreferences.isMasterDevice.first()) return DeviceAccessDecision.Allowed

        // Revalida contra Supabase (detecta una revocación hecha por la
        // master desde otro momento) — pero un fallo de red no debe bloquear
        // a un cliente que ya estaba aprobado: se conserva el último estado
        // local conocido en ese caso.
        when (deviceRoleRepository.checkOwnApprovalStatus()) {
            ApprovalStatus.Approved -> {
                appPreferences.setClientApprovalConfirmed(true)
                return DeviceAccessDecision.Allowed
            }
            ApprovalStatus.PendingOrRevoked -> {
                appPreferences.setClientApprovalConfirmed(false)
                return DeviceAccessDecision.Blocked
            }
            ApprovalStatus.Unknown -> {
                val lastKnown = appPreferences.clientApprovalConfirmed.first()
                return if (lastKnown) DeviceAccessDecision.Allowed else DeviceAccessDecision.Blocked
            }
        }
    }
}
