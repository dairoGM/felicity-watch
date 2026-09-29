package com.dairoroberto.felicitywatch.data.repository

import com.dairoroberto.felicitywatch.data.local.AppPreferences
import com.dairoroberto.felicitywatch.data.remote.SupabaseApiService
import com.dairoroberto.felicitywatch.data.remote.dto.AccountDeviceApprovalDto
import com.dairoroberto.felicitywatch.data.remote.dto.AccountDeviceDto
import com.dairoroberto.felicitywatch.data.remote.dto.AccountDeviceStatusDto
import com.dairoroberto.felicitywatch.data.remote.dto.DesktopPairingDto
import com.dairoroberto.felicitywatch.domain.model.LicenseState
import com.dairoroberto.felicitywatch.domain.model.LicenseStatus
import com.dairoroberto.felicitywatch.domain.model.MAX_REJECTED_ATTEMPTS
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
    val revoked: Boolean,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationUpdatedAt: Instant? = null,
    /** Estado de licencia (periodo free / transferencia). Ver [LicenseState]. */
    val license: LicenseState = LicenseState(LicenseStatus.NONE)
)

/** Días de prueba que se asumen cuando no se puede leer la configuración del
 * master (sin red, o la migración de licenciamiento todavía sin aplicar). Se
 * prefiere un valor permisivo: un error de infraestructura no debe expulsar a
 * un cliente que está en su periodo legítimo. */
const val DEFAULT_FREE_PERIOD_DAYS = 7

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
        val response = api.upsertAccountDeviceApproval(
            onConflict = "device_id",
            prefer = "resolution=merge-duplicates,return=minimal",
            device = listOf(
                AccountDeviceApprovalDto(
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
        return response.body()?.firstOrNull()?.toInfo()
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
        val approveResponse = api.upsertAccountDeviceApproval(
            onConflict = "device_id",
            prefer = "resolution=merge-duplicates,return=minimal",
            device = listOf(
                AccountDeviceApprovalDto(
                    deviceId = deviceId,
                    role = "client",
                    approvedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                )
            )
        )
        if (!approveResponse.isSuccessful) return false

        licenseSetupError = startFreePeriodIfFirstTime(deviceId)

        appPreferences.setClientApprovalConfirmed(true)
        return true
    }

    /**
     * Motivo por el que el último canje no pudo arrancar el periodo de prueba,
     * o null si arrancó bien.
     *
     * El canje en sí tuvo éxito (el dispositivo quedó aprobado), así que no se
     * devuelve como fallo del canje; pero sin esto el usuario entra a la app
     * creyendo que todo está en orden mientras el master lo ve "sin licencia
     * iniciada", sin ninguna pista de la causa.
     */
    @Volatile
    var licenseSetupError: String? = null
        private set

    /**
     * Arranca el periodo free al canjear un código, pero SOLO si este
     * dispositivo nunca lo tuvo.
     *
     * La condición importa: tras un rechazo el cliente canjea un código nuevo,
     * y si el free se repusiera ahí, cada rechazo regalaría otra prueba
     * gratis — el ciclo nunca llegaría a exigir una transferencia válida. Por
     * eso `free_started_at` se fija una vez y no se vuelve a tocar; los canjes
     * posteriores solo restauran el acceso al dispositivo.
     *
     * No es best-effort silencioso: si el arranque falla, el cliente entra a
     * la app sin licencia y en el master aparece como "sin licencia iniciada",
     * sin que nada explique por qué. Devuelve el error para que la UI del
     * canje lo muestre y se pueda corregir, en vez de dejar un dispositivo en
     * un estado que nadie sabe reparar.
     */
    private suspend fun startFreePeriodIfFirstTime(deviceId: String): String? {
        try {
            val lookup = api.getAccountDevices(deviceIdFilter = "eq.$deviceId")
            if (!lookup.isSuccessful) {
                return "No se pudo leer el estado de licencia (HTTP ${lookup.code()})"
            }
            val current = lookup.body()?.firstOrNull()
            val status = LicenseStatus.fromWire(current?.licenseStatus)

            // Un dispositivo ya aprobado no vuelve a 'free' por canjear otro
            // código: sería degradar una licencia válida.
            if (status == LicenseStatus.APPROVED) return null

            // Tras un rechazo el cliente canjea un código nuevo, y ese es
            // justamente el reintento que el flujo contempla. Su periodo free
            // NO se repone (ya lo consumió), pero hay que sacarlo de
            // 'rejected': si se quedara en ese estado, la app le negaría el
            // acceso sin darle siquiera la pantalla para declarar la
            // transferencia nueva — el reintento sería imposible.
            //
            // Queda en 'free' con su free_started_at original, que al estar
            // vencido lo manda directo a la pantalla de transferencia.
            //
            // Mismo caso para un cliente que el master desbloqueó (queda en
            // 'none' con su free_started_at intacto): sin esto se quedaría en
            // 'none', que el chequeo de acceso deja pasar, y tendría la app
            // gratis sin validar nada.
            if (status == LicenseStatus.REJECTED ||
                (status == LicenseStatus.NONE && current?.freeStartedAt != null)
            ) {
                val reactivate = api.updateAccountDevice(
                    deviceIdFilter = "eq.$deviceId",
                    prefer = "return=minimal",
                    updates = mapOf("license_status" to LicenseStatus.FREE.wireValue)
                )
                return if (reactivate.isSuccessful) null
                else "No se pudo reactivar la licencia (HTTP ${reactivate.code()})"
            }

            // Ya está en free con su periodo corriendo: no hay nada que hacer.
            if (current?.freeStartedAt != null) return null

            val start = api.updateAccountDevice(
                deviceIdFilter = "eq.$deviceId",
                prefer = "return=minimal",
                updates = mapOf(
                    "license_status" to LicenseStatus.FREE.wireValue,
                    "free_started_at" to DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                )
            )
            // Un 400 aquí casi siempre significa que 001_licensing.sql no está
            // aplicado (las columnas no existen). Se dice explícito: el
            // síntoma —"sin licencia iniciada" en el master— no apunta por sí
            // solo a una migración faltante.
            return if (start.isSuccessful) null else {
                "No se pudo iniciar el periodo de prueba (HTTP ${start.code()}). " +
                    "Verifica que la migración 001_licensing.sql esté aplicada en Supabase."
            }
        } catch (e: Exception) {
            return "No se pudo iniciar el periodo de prueba: ${e.message ?: "error de red"}"
        }
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
        return response.body().orEmpty().map { it.toInfo() }
    }

    private fun AccountDeviceStatusDto.toInfo() = AccountDeviceInfo(
        deviceId = deviceId,
        role = role,
        displayName = displayName,
        approvedAt = approvedAt?.let(Instant::parse),
        lastSeenAt = lastSeenAt?.let(Instant::parse),
        revoked = revoked,
        latitude = latitude,
        longitude = longitude,
        locationUpdatedAt = locationUpdatedAt?.let(Instant::parse),
        license = LicenseState(
            status = LicenseStatus.fromWire(licenseStatus),
            freeStartedAt = freeStartedAt?.let(Instant::parse),
            transferReference = transferReference,
            transferSubmittedAt = transferSubmittedAt?.let(Instant::parse),
            decidedAt = licenseDecidedAt?.let(Instant::parse),
            rejectedAttempts = rejectedAttempts ?: 0,
            rejectReason = licenseRejectReason
        )
    )

    // ------------------------------------------------------------------
    // Licenciamiento: periodo free + validación de transferencia
    // ------------------------------------------------------------------

    /**
     * Días de periodo free configurados en el master. Ante cualquier fallo
     * devuelve [DEFAULT_FREE_PERIOD_DAYS] en vez de null: este valor decide si
     * un cliente conserva el acceso, y quedarse sin él por un error de red
     * expulsaría a alguien que está en su prueba legítima.
     */
    suspend fun freePeriodDays(): Int {
        return try {
            val response = api.getAccountSettings()
            if (!response.isSuccessful) return DEFAULT_FREE_PERIOD_DAYS
            response.body()?.firstOrNull()?.freePeriodDays ?: DEFAULT_FREE_PERIOD_DAYS
        } catch (e: Exception) {
            DEFAULT_FREE_PERIOD_DAYS
        }
    }

    /** Cambia los días de periodo free (solo master). */
    suspend fun setFreePeriodDays(days: Int): Boolean {
        val response = api.updateAccountSettings(
            updates = mapOf(
                "free_period_days" to days.coerceIn(0, 365),
                "updated_at" to DateTimeFormatter.ISO_INSTANT.format(Instant.now())
            )
        )
        return response.isSuccessful
    }

    /** Estado de licencia de ESTE dispositivo, o null si no se pudo consultar. */
    suspend fun ownLicenseState(): LicenseState? {
        val deviceId = appPreferences.supabaseDeviceId()
        return try {
            val response = api.getAccountDevices(deviceIdFilter = "eq.$deviceId")
            if (!response.isSuccessful) return null
            response.body()?.firstOrNull()?.toInfo()?.license
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Declara el ID de la transferencia y deja este dispositivo pendiente de
     * validación por el master.
     *
     * No toca `rejected_attempts`: el contador lo mueve el master al decidir,
     * que es quien sabe si el intento fue fallido. Contarlo aquí penalizaría
     * al cliente por el solo hecho de enviar.
     */
    suspend fun submitTransferReference(reference: String): Boolean {
        val deviceId = appPreferences.supabaseDeviceId()
        val response = api.updateAccountDevice(
            deviceIdFilter = "eq.$deviceId",
            prefer = "return=minimal",
            updates = mapOf(
                "license_status" to LicenseStatus.PENDING.wireValue,
                "transfer_reference" to reference.trim(),
                "transfer_submitted_at" to DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                // Se limpia la decisión anterior: lo que hay ahora es una
                // transferencia nueva esperando, no el rechazo de la pasada.
                "license_decided_at" to null,
                "license_reject_reason" to null
            )
        )
        return response.isSuccessful
    }

    /** Aprueba la transferencia: acceso indefinido y contador de intentos a 0. */
    suspend fun approveTransfer(deviceId: String): Boolean {
        val response = api.updateAccountDevice(
            deviceIdFilter = "eq.$deviceId",
            prefer = "return=minimal",
            updates = mapOf(
                "license_status" to LicenseStatus.APPROVED.wireValue,
                "license_decided_at" to DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                "license_reject_reason" to null,
                // El contador mide rechazos SEGUIDOS: una aprobación cierra el
                // ciclo, así que un rechazo futuro vuelve a empezar de 1.
                "rejected_attempts" to 0
            )
        )
        return response.isSuccessful
    }

    /**
     * Rechaza la transferencia. Suma un intento y, si con este se alcanzan
     * [MAX_REJECTED_ATTEMPTS], deja el dispositivo bloqueado.
     *
     * [currentAttempts] viene del listado que el master ya tiene cargado. Hay
     * una carrera teórica si dos masters rechazan a la vez (el segundo pisa el
     * contador del primero), pero el modelo es de un solo master por cuenta y
     * la alternativa —un RPC atómico en Postgres— es desproporcionada aquí.
     */
    suspend fun rejectTransfer(deviceId: String, currentAttempts: Int, reason: String?): Boolean {
        val attempts = currentAttempts + 1
        val blocked = attempts >= MAX_REJECTED_ATTEMPTS
        val response = api.updateAccountDevice(
            deviceIdFilter = "eq.$deviceId",
            prefer = "return=minimal",
            updates = mapOf(
                "license_status" to
                    (if (blocked) LicenseStatus.BLOCKED else LicenseStatus.REJECTED).wireValue,
                "license_decided_at" to DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                "license_reject_reason" to reason?.trim()?.ifBlank { null },
                "rejected_attempts" to attempts,
                // El comprobante rechazado se descarta: el próximo ciclo
                // empieza con un código nuevo y una transferencia nueva.
                "transfer_reference" to null,
                "transfer_submitted_at" to null,
                // Revocar la aprobación del dispositivo obliga a canjear un
                // código nuevo, que es justamente lo que pide el flujo tras un
                // rechazo. Sin esto el cliente seguiría "aprobado" como
                // dispositivo y solo cambiaría su estado de licencia.
                "revoked" to true
            )
        )
        return response.isSuccessful
    }

    /**
     * Arranca el periodo de prueba de un cliente desde el master.
     *
     * Red de seguridad para un cliente que quedó en 'none' porque su canje no
     * pudo iniciar la licencia (p. ej. canjeó antes de que la migración
     * estuviera aplicada). Sin esto, la única salida sería editar la fila a
     * mano en Supabase.
     */
    suspend fun startFreePeriodFor(deviceId: String): Boolean {
        val response = api.updateAccountDevice(
            deviceIdFilter = "eq.$deviceId",
            prefer = "return=minimal",
            updates = mapOf(
                "license_status" to LicenseStatus.FREE.wireValue,
                "free_started_at" to DateTimeFormatter.ISO_INSTANT.format(Instant.now())
            )
        )
        return response.isSuccessful
    }

    /**
     * Desbloquea a un cliente que agotó sus intentos: vuelve al inicio del
     * ciclo (necesita código nuevo) con el contador en 0.
     *
     * No se le devuelve el periodo free — ya lo consumió — ni se le aprueba:
     * el master desbloquea para que pueda REINTENTAR, no para dar acceso.
     */
    suspend fun unblockDevice(deviceId: String): Boolean {
        val response = api.updateAccountDevice(
            deviceIdFilter = "eq.$deviceId",
            prefer = "return=minimal",
            updates = mapOf(
                "license_status" to LicenseStatus.NONE.wireValue,
                "rejected_attempts" to 0,
                "license_reject_reason" to null,
                "license_decided_at" to DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                "transfer_reference" to null,
                "transfer_submitted_at" to null
            )
        )
        return response.isSuccessful
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

    /** Borra el registro por completo — a diferencia de revocar, no queda
     * rastro: si el mismo teléfono vuelve a canjear un código, entra como
     * un dispositivo nuevo (sin nombre ni historial previos). */
    suspend fun deleteDevice(deviceId: String) {
        api.deleteAccountDevice(deviceIdFilter = "eq.$deviceId")
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

    /** Sube la ubicación de ESTE dispositivo — llamada best-effort desde el
     * ciclo de monitoreo normal (ver [UpdateDeviceLocationUseCase], que
     * modera la frecuencia real de captura para no gastar batería en cada
     * ciclo). */
    /**
     * Devuelve null si se guardó bien, o un mensaje de error legible si no —
     * quien llama decide qué hacer con eso (UpdateDeviceLocationUseCase lo
     * expone en un StateFlow para que Ajustes > Sistema pueda mostrar por
     * qué el mapa no recibe datos de este dispositivo, en vez de fallar en
     * silencio como antes).
     */
    suspend fun updateOwnLocation(latitude: Double, longitude: Double): String? {
        val deviceId = appPreferences.supabaseDeviceId()
        return try {
            val response = api.updateAccountDevice(
                deviceIdFilter = "eq.$deviceId",
                prefer = "return=minimal",
                updates = mapOf(
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "location_updated_at" to DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                )
            )
            if (response.isSuccessful) {
                null
            } else {
                "Supabase rechazó la actualización (HTTP ${response.code()}): ${response.errorBody()?.string()}"
            }
        } catch (e: Exception) {
            "Error de red al reportar ubicación: ${e.message}"
        }
    }

    private companion object {
        /** Corto a propósito: es solo para el momento de conectar (la
         * persona lo ve en un dispositivo y lo escribe en el otro casi de
         * inmediato), no una credencial de largo plazo. */
        const val PAIRING_TTL_SECONDS = 120L
    }
}
