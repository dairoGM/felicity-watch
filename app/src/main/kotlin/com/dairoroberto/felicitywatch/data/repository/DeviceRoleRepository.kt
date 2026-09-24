package com.dairoroberto.felicitywatch.data.repository

import com.dairoroberto.felicitywatch.data.local.AppPreferences
import com.dairoroberto.felicitywatch.data.remote.SupabaseApiService
import com.dairoroberto.felicitywatch.data.remote.dto.AccountDeviceDto
import com.dairoroberto.felicitywatch.data.remote.dto.DesktopPairingDto
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** Resultado de intentar marcar este dispositivo como master. */
sealed class ClaimMasterResult {
    data object Success : ClaimMasterResult()
    /** Ya existe una master para esta cuenta — [deviceId] identifica cuál. */
    data class AlreadyClaimed(val deviceId: String, val displayName: String?) : ClaimMasterResult()
    data class Failed(val message: String) : ClaimMasterResult()
}

/** Estado de aprobación de un dispositivo cliente, resuelto contra Supabase. */
sealed class ApprovalStatus {
    data object Approved : ApprovalStatus()
    data object PendingOrRevoked : ApprovalStatus()
    /** Sin conexión o error de Supabase — no se puede confirmar ni negar; se
     * usa el último estado conocido en vez de bloquear a ciegas. */
    data object Unknown : ApprovalStatus()
}

data class AccountDeviceInfo(
    val deviceId: String,
    val role: String,
    val displayName: String?,
    val approvedAt: Instant?,
    val lastSeenAt: Instant?,
    val revoked: Boolean
)

/** Un código de emparejamiento recién generado, con su momento de expiración
 * — para que la UI pueda mostrar una cuenta regresiva en vez de un PIN que
 * expira en silencio. */
data class PairingCode(val pin: String, val expiresAt: Instant)

/**
 * Controla el modelo master/cliente entre instalaciones móviles de la misma
 * cuenta: exactamente una es "master" (consulta Felicity y sincroniza a
 * Supabase, igual que siempre); el resto son "cliente" y deben tener una
 * aprobación vigente (canjeada con un PIN generado por la master) para poder
 * monitorear — sin ella, la app se bloquea por completo aunque el login de
 * FSolar sea válido. Ver EvaluateDeviceApprovalUseCase para el chequeo de
 * arranque que usa este repositorio.
 */
@Singleton
class DeviceRoleRepository @Inject constructor(
    private val api: SupabaseApiService,
    private val appPreferences: AppPreferences
) {
    /**
     * Intenta registrar este dispositivo como master. El índice único
     * parcial de `account_devices` (un solo role='master' no revocado por
     * cuenta) hace que Postgres rechace un segundo intento con 409 — la
     * decisión de quién gana nunca depende de la app.
     */
    suspend fun claimMaster(): ClaimMasterResult {
        val deviceId = appPreferences.supabaseDeviceId()
        val response = api.createAccountDevice(
            onConflict = "device_id",
            prefer = "resolution=merge-duplicates,return=minimal",
            device = listOf(
                AccountDeviceDto(
                    deviceId = deviceId,
                    role = "master",
                    approvedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                )
            )
        )
        if (response.isSuccessful) {
            appPreferences.setIsMasterDevice(true)
            return ClaimMasterResult.Success
        }
        if (response.code() == 409) {
            val existing = currentMaster()
            return if (existing != null) {
                ClaimMasterResult.AlreadyClaimed(existing.deviceId, existing.displayName)
            } else {
                ClaimMasterResult.Failed("Ya existe un dispositivo principal, pero no se pudo identificar cuál")
            }
        }
        return ClaimMasterResult.Failed("Supabase respondió ${response.code()}")
    }

    /** Master vigente de la cuenta (si alguna instalación ya reclamó el rol). */
    suspend fun currentMaster(): AccountDeviceInfo? {
        val response = api.getAccountDevices(roleFilter = "eq.master", revokedFilter = "eq.false")
        if (!response.isSuccessful) return null
        return response.body()?.firstOrNull()?.let {
            AccountDeviceInfo(
                deviceId = it.deviceId,
                role = it.role,
                displayName = it.displayName,
                approvedAt = it.approvedAt?.let(Instant::parse),
                lastSeenAt = it.lastSeenAt?.let(Instant::parse),
                revoked = it.revoked
            )
        }
    }

    /** true si ESTE dispositivo ya es la master (cache local, ver [AppPreferences.isMasterDevice]). */
    suspend fun isThisDeviceMaster(): Boolean = appPreferences.isMasterDevice.first()

    /**
     * Genera un PIN de 6 dígitos para aprobar OTRO dispositivo (celular
     * cliente o escritorio). Solo tiene sentido llamarlo desde la master —
     * la UI ya oculta esta acción en un dispositivo cliente, pero el
     * servidor no depende de eso: cualquier device_id puede generar un PIN
     * en la tabla, la protección real está en que un cliente sin aprobar
     * jamás llega a mostrar esta pantalla (ver EvaluateDeviceApprovalUseCase).
     */
    suspend fun createPairingPin(targetPlatform: String): PairingCode {
        val deviceId = appPreferences.supabaseDeviceId()
        val pin = Random.nextInt(0, 1_000_000).toString().padStart(6, '0')
        val expiresAt = Instant.now().plusSeconds(PAIRING_TTL_SECONDS)

        val response = api.createDesktopPairing(
            prefer = "return=minimal",
            pairing = listOf(
                DesktopPairingDto(
                    pin = pin,
                    deviceId = deviceId,
                    expiresAt = DateTimeFormatter.ISO_INSTANT.format(expiresAt),
                    targetPlatform = targetPlatform
                )
            )
        )
        if (!response.isSuccessful) {
            throw SupabaseSyncException(response.code(), response.errorBody()?.string())
        }
        return PairingCode(pin, expiresAt)
    }

    /**
     * Canjea un PIN generado por la master para aprobar ESTE dispositivo
     * como cliente. Un solo uso: lo marca `used=true` al validarlo.
     */
    suspend fun redeemPairingPin(pin: String): Boolean {
        val response = api.findPairing(
            pinFilter = "eq.$pin",
            usedFilter = "eq.false",
            targetPlatformFilter = "eq.android",
            select = "device_id,expires_at"
        )
        if (!response.isSuccessful) return false
        val row = response.body()?.firstOrNull() ?: return false

        val expiresAt = (row["expires_at"] as? String)?.let(Instant::parse) ?: return false
        if (expiresAt.isBefore(Instant.now())) return false

        api.markPairingUsed(
            pinFilter = "eq.$pin",
            prefer = "return=minimal",
            body = mapOf("used" to true)
        )

        val deviceId = appPreferences.supabaseDeviceId()
        val approveResponse = api.createAccountDevice(
            onConflict = "device_id",
            prefer = "resolution=merge-duplicates,return=minimal",
            device = listOf(
                AccountDeviceDto(
                    deviceId = deviceId,
                    role = "client",
                    approvedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                )
            )
        )
        if (!approveResponse.isSuccessful) return false

        appPreferences.setClientApprovalConfirmed(true)
        return true
    }

    /**
     * Revalida el estado de aprobación de ESTE dispositivo contra Supabase —
     * detecta una revocación hecha por la master desde otro momento, o que
     * este dispositivo fue registrado como master directamente en Supabase
     * (ej. de forma manual, sin pasar por el switch de Ajustes) — en ese
     * caso sincroniza también el flag local [AppPreferences.isMasterDevice].
     * Nunca bloquea a un cliente ya aprobado por un simple fallo de red:
     * distingue "revocado de verdad" de "no se pudo confirmar ahora mismo".
     */
    suspend fun checkOwnApprovalStatus(): ApprovalStatus {
        val deviceId = appPreferences.supabaseDeviceId()
        val response = try {
            api.getAccountDevices(deviceIdFilter = "eq.$deviceId")
        } catch (e: Exception) {
            return ApprovalStatus.Unknown
        }
        if (!response.isSuccessful) return ApprovalStatus.Unknown

        val row = response.body()?.firstOrNull() ?: return ApprovalStatus.PendingOrRevoked
        if (row.role == "master" && !row.revoked) {
            appPreferences.setIsMasterDevice(true)
            return ApprovalStatus.Approved
        }
        return if (!row.revoked && row.approvedAt != null) ApprovalStatus.Approved else ApprovalStatus.PendingOrRevoked
    }

    /** Todos los dispositivos registrados de la cuenta — para la vista "Dispositivos" de la master. */
    suspend fun listAccountDevices(): List<AccountDeviceInfo> {
        val response = api.getAccountDevices()
        if (!response.isSuccessful) return emptyList()
        return response.body().orEmpty().map {
            AccountDeviceInfo(
                deviceId = it.deviceId,
                role = it.role,
                displayName = it.displayName,
                approvedAt = it.approvedAt?.let(Instant::parse),
                lastSeenAt = it.lastSeenAt?.let(Instant::parse),
                revoked = it.revoked
            )
        }
    }

    suspend fun renameDevice(deviceId: String, displayName: String) {
        api.updateAccountDevice(
            deviceIdFilter = "eq.$deviceId",
            prefer = "return=minimal",
            updates = mapOf("display_name" to displayName)
        )
    }

    suspend fun revokeDevice(deviceId: String) {
        api.updateAccountDevice(
            deviceIdFilter = "eq.$deviceId",
            prefer = "return=minimal",
            updates = mapOf("revoked" to true)
        )
    }

    /** Actualiza el "visto por última vez" de este dispositivo — llamada
     * best-effort desde el ciclo de monitoreo normal. */
    suspend fun touchLastSeen() {
        val deviceId = appPreferences.supabaseDeviceId()
        try {
            api.updateAccountDevice(
                deviceIdFilter = "eq.$deviceId",
                prefer = "return=minimal",
                updates = mapOf("last_seen_at" to DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
            )
        } catch (e: Exception) {
            // Best-effort: no debe afectar el ciclo de monitoreo.
        }
    }

    private companion object {
        /** Corto a propósito: es solo para el momento de conectar (la
         * persona lo ve en un dispositivo y lo escribe en el otro casi de
         * inmediato), no una credencial de largo plazo. */
        const val PAIRING_TTL_SECONDS = 120L
    }
}
