package com.dairoroberto.felicitywatch.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Reglas del ciclo de licencia: periodo free, acceso por estado y conteo de
 * intentos. Es la lógica que comparten el cliente (para decidir su acceso) y
 * el master (para pintar el listado), así que una regresión aquí se vería en
 * los dos lados a la vez.
 */
class LicenseStateTest {

    private val now: Instant = Instant.parse("2026-09-28T12:00:00Z")
    private val freeDays = 7

    private fun freeStartedDaysAgo(days: Long) = LicenseState(
        status = LicenseStatus.FREE,
        freeStartedAt = now.minus(Duration.ofDays(days))
    )

    @Test
    fun `en periodo free hay acceso y se cuentan los dias restantes`() {
        val state = freeStartedDaysAgo(2)

        assertTrue(state.allowsAccess(freeDays, now))
        assertFalse(state.isFreeExpired(freeDays, now))
        assertEquals(5, state.freeDaysRemaining(freeDays, now))
    }

    @Test
    fun `periodo free vencido quita el acceso y pide transferencia`() {
        val state = freeStartedDaysAgo(8)

        assertFalse(state.allowsAccess(freeDays, now))
        assertTrue(state.isFreeExpired(freeDays, now))
        assertEquals(0, state.freeDaysRemaining(freeDays, now))
        assertTrue(state.shouldRequestTransfer(freeDays, now))
    }

    @Test
    fun `el ultimo dia de prueba todavia da acceso`() {
        // Exactamente 6 dias y medio: sigue dentro de la ventana de 7.
        val state = LicenseState(
            status = LicenseStatus.FREE,
            freeStartedAt = now.minus(Duration.ofHours(6 * 24 + 12))
        )

        assertTrue(state.allowsAccess(freeDays, now))
        // Redondea hacia arriba: con 12h restantes se dice "1 dia", no "0".
        assertEquals(1, state.freeDaysRemaining(freeDays, now))
    }

    @Test
    fun `transferencia pendiente NO da acceso hasta que el master valide`() {
        // Declarar una transferencia no es haberla pagado: si pending diera
        // acceso, bastaria con escribir cualquier ID inventado para usar la
        // app indefinidamente mientras nadie revisa.
        val state = LicenseState(status = LicenseStatus.PENDING)

        assertFalse(state.allowsAccess(freeDays, now))
    }

    @Test
    fun `aprobado da acceso indefinido sin importar el periodo free`() {
        val state = LicenseState(
            status = LicenseStatus.APPROVED,
            freeStartedAt = now.minus(Duration.ofDays(400))
        )

        assertTrue(state.allowsAccess(freeDays, now))
    }

    @Test
    fun `rechazado y bloqueado no dan acceso`() {
        assertFalse(LicenseState(LicenseStatus.REJECTED).allowsAccess(freeDays, now))
        assertFalse(LicenseState(LicenseStatus.BLOCKED).allowsAccess(freeDays, now))
    }

    @Test
    fun `sin licencia iniciada no da acceso por si solo`() {
        assertFalse(LicenseState(LicenseStatus.NONE).allowsAccess(freeDays, now))
    }

    @Test
    fun `los intentos restantes bajan con cada rechazo y no pasan de cero`() {
        assertEquals(3, LicenseState(LicenseStatus.FREE, rejectedAttempts = 0).attemptsRemaining)
        assertEquals(1, LicenseState(LicenseStatus.REJECTED, rejectedAttempts = 2).attemptsRemaining)
        assertEquals(0, LicenseState(LicenseStatus.BLOCKED, rejectedAttempts = 3).attemptsRemaining)
        // Defensivo: un contador inconsistente en el servidor no debe producir
        // un numero negativo en la UI.
        assertEquals(0, LicenseState(LicenseStatus.BLOCKED, rejectedAttempts = 9).attemptsRemaining)
    }

    @Test
    fun `un periodo free de cero dias vence de inmediato`() {
        val state = LicenseState(status = LicenseStatus.FREE, freeStartedAt = now)

        assertTrue(state.isFreeExpired(0, now))
        assertFalse(state.allowsAccess(0, now))
    }

    @Test
    fun `un estado desconocido del servidor se trata como sin licencia`() {
        // Una version futura del master podria escribir un estado que esta
        // app todavia no conoce: el peor caso debe ser pedir un codigo, nunca
        // conceder acceso de mas.
        assertEquals(LicenseStatus.NONE, LicenseStatus.fromWire("algo_nuevo"))
        assertEquals(LicenseStatus.NONE, LicenseStatus.fromWire(null))
    }

    @Test
    fun `fromWire reconoce todos los estados que escribe el master`() {
        assertEquals(LicenseStatus.FREE, LicenseStatus.fromWire("free"))
        assertEquals(LicenseStatus.PENDING, LicenseStatus.fromWire("pending"))
        assertEquals(LicenseStatus.APPROVED, LicenseStatus.fromWire("approved"))
        assertEquals(LicenseStatus.REJECTED, LicenseStatus.fromWire("rejected"))
        assertEquals(LicenseStatus.BLOCKED, LicenseStatus.fromWire("blocked"))
    }

    @Test
    fun `subir los dias de prueba revive un free que ya habia vencido`() {
        // El master puede ampliar el periodo; se aplica sobre la fecha de
        // inicio original, asi que un cliente vencido vuelve a tener acceso.
        val state = freeStartedDaysAgo(8)

        assertFalse(state.allowsAccess(7, now))
        assertTrue(state.allowsAccess(14, now))
    }
}
