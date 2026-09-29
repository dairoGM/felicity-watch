package com.dairoroberto.felicitywatch.domain.usecase

import com.dairoroberto.felicitywatch.data.local.AppPreferences
import com.dairoroberto.felicitywatch.data.repository.AccountDeviceInfo
import com.dairoroberto.felicitywatch.data.repository.DeviceRoleRepository
import com.dairoroberto.felicitywatch.domain.model.LicenseStatus
import com.dairoroberto.felicitywatch.notification.PushNotifier
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Avisa al MASTER (nunca al cliente) de dos eventos del ciclo de
 * licenciamiento, con el mismo mecanismo que las alertas de consumo/batería
 * ([NotifyLowVoltageUseCase]): una notificación push local, disparada por el
 * propio ciclo de monitoreo del master.
 *
 * No hay un canal push real entre dispositivos (ver 001_licensing.sql /
 * supabase/README.md — la app no tiene FCM ni backend propio). El master se
 * entera porque SU PROPIO ciclo de monitoreo, corriendo cada
 * [pollingIntervalSeconds], consulta el listado de `account_devices` (ya lo
 * hace para la pantalla de Ajustes) y detecta el cambio — el mismo patrón que
 * ya usa para todo lo demás, solo que la "lectura" que vigila es el listado de
 * clientes en vez del inversor.
 *
 * Dos eventos, cada uno con su propio registro de "ya avisado" en
 * [AppPreferences] para no repetirse en cada ciclo:
 *
 * 1. Un cliente CANJEÓ un código (quedó aprobado). No hay aprobación manual
 *    pendiente — el master ya autorizó implícitamente al generar el código —
 *    así que esto es puramente informativo: "un dispositivo nuevo se unió".
 * 2. Un cliente declaró una transferencia y espera validación
 *    (`license_status = 'pending'`). Este SÍ requiere una acción del master.
 */
@Singleton
class NotifyMasterOfClientActivityUseCase @Inject constructor(
    private val appPreferences: AppPreferences,
    private val deviceRoleRepository: DeviceRoleRepository,
    private val pushNotifier: PushNotifier
) {
    suspend fun run() {
        // Nunca en un cliente: estos avisos son exclusivamente para quien
        // administra la cuenta. Sin este chequeo, un dispositivo cliente
        // también listaría account_devices en cada ciclo (una consulta extra
        // e inútil) y podría, en teoría, notificarse a sí mismo.
        if (!appPreferences.isMasterDevice.first()) return

        val devices = deviceRoleRepository.listAccountDevices()
        if (devices.isEmpty()) return

        notifyNewClients(devices)
        notifyPendingTransfers(devices)
    }

    private suspend fun notifyNewClients(devices: List<AccountDeviceInfo>) {
        val alreadyNotified = appPreferences.notifiedPendingDeviceIds.first()

        val newlyApproved = devices.filter { device ->
            device.role == "client" &&
                device.approvedAt != null &&
                !device.revoked &&
                device.deviceId !in alreadyNotified
        }

        for (device in newlyApproved) {
            val name = device.displayName ?: "Dispositivo ${device.deviceId.take(8)}"
            pushNotifier.notifyAlert(
                title = "Nuevo cliente conectado",
                body = "$name canjeó su código de acceso y ya está usando la cuenta.",
                // notificationId por dispositivo (hash), no un ID fijo: a
                // diferencia del voltaje bajo (un solo estado continuo), aquí
                // pueden unirse varios clientes distintos y cada uno merece su
                // propia notificación en vez de que la última pise a la
                // anterior.
                notificationId = notificationIdFor(NEW_CLIENT_NAMESPACE, device.deviceId)
            )
            appPreferences.addNotifiedPendingDeviceId(device.deviceId)
        }

        // Un dispositivo revocado o eliminado sale del registro: si vuelve a
        // canjear un código más adelante, es una reincorporación real y debe
        // notificarse de nuevo, no tratarse como "ya visto".
        val stillPresent = devices.map { it.deviceId }.toSet()
        val revokedIds = devices.filter { it.revoked }.map { it.deviceId }.toSet()
        for (id in alreadyNotified) {
            if (id !in stillPresent || id in revokedIds) {
                appPreferences.removeNotifiedPendingDeviceId(id)
            }
        }
    }

    private suspend fun notifyPendingTransfers(devices: List<AccountDeviceInfo>) {
        val alreadyNotified = appPreferences.notifiedTransferDeviceIds.first()

        val newlyPending = devices.filter { device ->
            device.role == "client" &&
                device.license.status == LicenseStatus.PENDING &&
                device.deviceId !in alreadyNotified
        }

        for (device in newlyPending) {
            val name = device.displayName ?: "Dispositivo ${device.deviceId.take(8)}"
            val reference = device.license.transferReference
            pushNotifier.notifyAlert(
                title = "Transferencia por validar",
                body = if (reference != null) {
                    "$name envió el ID $reference. Entra a Ajustes > Dispositivos para aprobar o rechazar."
                } else {
                    "$name está esperando que valides su transferencia."
                },
                notificationId = notificationIdFor(PENDING_TRANSFER_NAMESPACE, device.deviceId)
            )
            appPreferences.addNotifiedTransferDeviceId(device.deviceId)
        }

        // Sale del registro todo el que YA NO esté pendiente (fue aprobado,
        // rechazado, o el registro desapareció): así, si más adelante vuelve a
        // quedar pendiente (reintento tras un rechazo), se vuelve a avisar.
        val stillPending = newlyPending.map { it.deviceId }.toSet() +
            devices.filter { it.license.status == LicenseStatus.PENDING }.map { it.deviceId }.toSet()
        for (id in alreadyNotified) {
            if (id !in stillPending) {
                appPreferences.removeNotifiedTransferDeviceId(id)
            }
        }
    }

    /** IDs de notificación estables por dispositivo (namespace + hash), para
     * que dos eventos distintos del mismo cliente no colisionen entre sí ni
     * con los IDs fijos que ya usan las alertas existentes (ver
     * NotifyLowVoltageUseCase.NOTIFICATION_ID = 6001 y los ordinals de
     * AlertRuleType). El offset de namespace los aleja de ese rango. */
    private fun notificationIdFor(namespace: Int, deviceId: String): Int =
        namespace + (deviceId.hashCode() and 0x0FFF)

    private companion object {
        const val NEW_CLIENT_NAMESPACE = 7_000
        const val PENDING_TRANSFER_NAMESPACE = 8_000
    }
}
