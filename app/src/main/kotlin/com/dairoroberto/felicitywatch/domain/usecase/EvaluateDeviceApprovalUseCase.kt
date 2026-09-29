package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.data.local.AppPreferences
import com.dairoroberto.felicitywatch.data.repository.ApprovalStatus
import com.dairoroberto.felicitywatch.data.repository.DeviceRoleRepository
import com.dairoroberto.felicitywatch.domain.model.LicenseState
import com.dairoroberto.felicitywatch.domain.model.LicenseStatus
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Si este dispositivo puede monitorear (consultar Felicity y usar la app), o debe quedar bloqueado. */
sealed class DeviceAccessDecision {
    data object Allowed : DeviceAccessDecision()
    data object Blocked : DeviceAccessDecision()

    /**
     * Aprobado como dispositivo, pero con la licencia vencida: se le acabó el
     * periodo free y debe declarar el ID de su transferencia.
     *
     * Es distinto de [Blocked] a propósito: no le falta un código, le falta
     * pagar. La pantalla que corresponde pide el comprobante, no un PIN.
     */
    data class TransferRequired(val license: LicenseState, val freePeriodDays: Int) : DeviceAccessDecision()

    /** Agotó los 3 intentos. Solo el master puede devolverle el acceso. */
    data class LicenseBlocked(val license: LicenseState) : DeviceAccessDecision()
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
                return evaluateLicense()
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

    /**
     * Segunda puerta, después de la aprobación del dispositivo: el estado de
     * la licencia (periodo free vencido, transferencia pendiente, bloqueo por
     * intentos agotados).
     *
     * Si no se puede leer el estado se concede el acceso: el dispositivo ya
     * pasó la verificación de aprobación contra Supabase, así que un fallo
     * puntual al leer la licencia no es motivo para expulsarlo. El chequeo se
     * repite en cada arranque.
     */
    private suspend fun evaluateLicense(): DeviceAccessDecision {
        val license = deviceRoleRepository.ownLicenseState() ?: return DeviceAccessDecision.Allowed

        if (license.status == LicenseStatus.BLOCKED) {
            return DeviceAccessDecision.LicenseBlocked(license)
        }

        val freeDays = deviceRoleRepository.freePeriodDays()

        // Un dispositivo aprobado como tal pero sin ciclo de licencia iniciado
        // (fila anterior a la migración, o recién desbloqueado) se deja pasar:
        // el periodo free arranca al canjear el código, y forzar aquí una
        // pantalla de pago a alguien que nunca tuvo prueba sería un cambio de
        // reglas retroactivo para las instalaciones que ya existen.
        if (license.status == LicenseStatus.NONE) return DeviceAccessDecision.Allowed

        if (license.allowsAccess(freeDays)) return DeviceAccessDecision.Allowed

        return DeviceAccessDecision.TransferRequired(license, freeDays)
    }
}
