package com.dairoroberto.felicitywatch.domain.model

import java.time.Duration
import java.time.Instant

/** Máximo de rechazos seguidos antes de que el cliente quede bloqueado. Al
 * tercero solo el master puede devolverle el acceso. */
const val MAX_REJECTED_ATTEMPTS = 3

/**
 * Estado de licencia de un dispositivo cliente, tal como lo guarda
 * `account_devices.license_status`.
 *
 * El orden del ciclo es: [NONE] -> [FREE] -> [PENDING] -> [APPROVED], con
 * [REJECTED] devolviendo al inicio (código nuevo) y [BLOCKED] como terminal
 * hasta que el master intervenga.
 */
enum class LicenseStatus(val wireValue: String) {
    /** Sin ciclo de licencia iniciado: aún no canjea un código. */
    NONE("none"),

    /** En periodo de prueba. Tiene acceso pleno hasta que venza. */
    FREE("free"),

    /** Declaró una transferencia y espera la decisión del master. */
    PENDING("pending"),

    /** Transferencia validada: acceso indefinido. */
    APPROVED("approved"),

    /** Transferencia rechazada. Necesita un código nuevo para reintentar. */
    REJECTED("rejected"),

    /** Agotó los [MAX_REJECTED_ATTEMPTS] intentos. Solo el master desbloquea. */
    BLOCKED("blocked");

    companion object {
        /** Un valor desconocido (fila vieja, estado agregado en una versión
         * posterior del master) se trata como [NONE] en vez de reventar: el
         * peor caso es pedir un código, nunca dar acceso de más. */
        fun fromWire(value: String?): LicenseStatus =
            entries.firstOrNull { it.wireValue == value } ?: NONE
    }
}

/**
 * Los datos de licencia de un dispositivo, con la lógica de qué significan.
 *
 * Es el mismo tipo que usan el cliente (para decidir su propio acceso) y el
 * master (para pintar el listado), a propósito: si divergieran, el listado del
 * master podría decir "válido" mientras el cliente se ve bloqueado.
 */
data class LicenseState(
    val status: LicenseStatus,
    val freeStartedAt: Instant? = null,
    val transferReference: String? = null,
    val transferSubmittedAt: Instant? = null,
    val decidedAt: Instant? = null,
    val rejectedAttempts: Int = 0,
    val rejectReason: String? = null
) {
    /**
     * Momento en que vence el periodo free, o null si no está en free.
     *
     * [freePeriodDays] lo configura el master y puede cambiar después de que
     * el cliente empezó su prueba; se aplica siempre el valor vigente, que es
     * lo que el master esperaría al ajustarlo.
     */
    fun freeExpiresAt(freePeriodDays: Int): Instant? {
        val start = freeStartedAt ?: return null
        return start.plus(Duration.ofDays(freePeriodDays.toLong()))
    }

    /** Días completos que faltan para que venza el free (0 si ya venció). */
    fun freeDaysRemaining(freePeriodDays: Int, now: Instant = Instant.now()): Int {
        val expiry = freeExpiresAt(freePeriodDays) ?: return 0
        val remaining = Duration.between(now, expiry)
        if (remaining.isNegative || remaining.isZero) return 0
        // Se redondea hacia arriba: con 6 horas restantes el usuario tiene
        // "1 día", no "0 días" mientras la app todavía funciona.
        return Math.ceil(remaining.toMinutes() / (60.0 * 24.0)).toInt()
    }

    fun isFreeExpired(freePeriodDays: Int, now: Instant = Instant.now()): Boolean {
        val expiry = freeExpiresAt(freePeriodDays) ?: return false
        return !now.isBefore(expiry)
    }

    /** Intentos que le quedan antes del bloqueo. */
    val attemptsRemaining: Int
        get() = (MAX_REJECTED_ATTEMPTS - rejectedAttempts).coerceAtLeast(0)

    /**
     * Si este dispositivo puede usar la app ahora mismo.
     *
     * Solo dan acceso [LicenseStatus.APPROVED] y un [LicenseStatus.FREE] que
     * no haya vencido.
     *
     * [LicenseStatus.PENDING] NO da acceso: declarar una transferencia no es
     * haberla pagado, y el acceso se concede cuando el master valida el pago,
     * no cuando el cliente dice que lo hizo. Si pending diera acceso, bastaría
     * con escribir cualquier ID inventado para seguir usando la app
     * indefinidamente mientras nadie revisa.
     */
    fun allowsAccess(freePeriodDays: Int, now: Instant = Instant.now()): Boolean =
        when (status) {
            LicenseStatus.APPROVED -> true
            LicenseStatus.FREE -> !isFreeExpired(freePeriodDays, now)
            LicenseStatus.PENDING,
            LicenseStatus.NONE, LicenseStatus.REJECTED, LicenseStatus.BLOCKED -> false
        }

    /**
     * Si corresponde pedirle el ID de la transferencia: se le acabó el periodo
     * free y todavía no hay una transferencia esperando decisión.
     */
    fun shouldRequestTransfer(freePeriodDays: Int, now: Instant = Instant.now()): Boolean =
        status == LicenseStatus.FREE && isFreeExpired(freePeriodDays, now)
}
