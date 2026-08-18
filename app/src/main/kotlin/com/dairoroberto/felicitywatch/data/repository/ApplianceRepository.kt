package com.dairoroberto.felicitywatch.data.repository

import com.dairoroberto.felicitywatch.data.local.ApplianceDao
import com.dairoroberto.felicitywatch.data.local.ApplianceEntity
import com.dairoroberto.felicitywatch.data.local.DismissedApplianceEventDao
import com.dairoroberto.felicitywatch.data.local.DismissedApplianceEventEntity
import com.dairoroberto.felicitywatch.data.local.ImportedBackupDao
import com.dairoroberto.felicitywatch.data.local.ImportedBackupEntity
import com.dairoroberto.felicitywatch.domain.usecase.ApplianceBackup
import com.dairoroberto.felicitywatch.domain.usecase.ImportResult
import com.dairoroberto.felicitywatch.domain.usecase.toBackup
import com.dairoroberto.felicitywatch.domain.usecase.toEntity
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApplianceRepository @Inject constructor(
    private val dao: ApplianceDao,
    private val dismissedDao: DismissedApplianceEventDao,
    private val importedBackupDao: ImportedBackupDao
) {
    fun observeAll(): Flow<List<ApplianceEntity>> = dao.observeAll()

    /** Locales ya registrados, para autocompletar el campo. */
    fun observeRooms(): Flow<List<String>> = dao.observeRooms()

    /** Grupos de equipos similares ya registrados, para autocompletar. */
    fun observeSimilarGroups(): Flow<List<String>> = dao.observeSimilarGroups()

    /**
     * Registra que el escalón de consumo observado corresponde al equipo que
     * el usuario confirmó, y aprende de ello.
     *
     * El aprendizaje se PROPAGA al grupo de equipos similares: si se confirma
     * que fue "un split convencional", el valor observado vale para todos los
     * splits convencionales, porque su consumo es prácticamente el mismo y
     * son indistinguibles en el consumo total de la casa. Sin esa propagación
     * habría que confirmar cada uno por separado para aprender el mismo dato.
     *
     * Devuelve cuántos equipos se actualizaron, para poder decírselo al
     * usuario (confirmar uno puede haber ajustado tres).
     */
    suspend fun learnFromConfirmation(
        applianceId: Long,
        observedWatts: Int,
        nowEpochMillis: Long = Instant.now().toEpochMilli()
    ): Int {
        val confirmed = dao.getById(applianceId) ?: return 0

        // Sin grupo, el equipo aprende solo de sus propias confirmaciones.
        val targets = if (confirmed.hasSimilarGroup) {
            val group = dao.getBySimilarGroup(confirmed.similarGroup)
            // El confirmado siempre entra, aunque la consulta por grupo
            // fallara en incluirlo por alguna diferencia de texto.
            if (group.any { it.id == confirmed.id }) group else group + confirmed
        } else {
            listOf(confirmed)
        }

        val updated = targets.map { it.withObservation(observedWatts, nowEpochMillis) }
        dao.updateAll(updated)
        return updated.size
    }

    suspend fun getAll(): List<ApplianceEntity> = dao.getAll()

    suspend fun save(appliance: ApplianceEntity) {
        if (appliance.id == 0L) dao.insert(appliance) else dao.update(appliance)
    }

    suspend fun delete(appliance: ApplianceEntity) = dao.delete(appliance)

    /**
     * Borra el consumo aprendido de un equipo y lo deja solo con lo declarado.
     *
     * Hace falta porque el aprendizaje puede contaminarse: si se confirmó el
     * equipo equivocado, o si el escalón medido era en realidad dos equipos
     * encendidos a la vez, el valor aprendido queda inflado y el promedio
     * arrastra ese error en cada confirmación siguiente. Reiniciar es la única
     * forma de salir de ahí.
     *
     * NO propaga al grupo de similares: el usuario puede querer descartar el
     * dato de un equipo puntual. Para el grupo completo está
     * [resetSimilarGroupLearning].
     */
    suspend fun resetLearning(applianceId: Long) {
        val appliance = dao.getById(applianceId) ?: return
        dao.update(
            appliance.copy(
                confirmedWatts = null,
                confirmedAtEpochMillis = null,
                confirmationCount = 0
            )
        )
    }

    /**
     * Borra el consumo aprendido de todos los equipos de un grupo similar.
     *
     * Complemento de [learnFromConfirmation]: si el aprendizaje se propagó a
     * todo el grupo a partir de una confirmación errónea, deshacerlo equipo
     * por equipo sería tedioso y fácil de dejar a medias.
     *
     * Devuelve cuántos equipos se reiniciaron.
     */
    suspend fun resetSimilarGroupLearning(group: String): Int {
        if (group.isBlank()) return 0
        val members = dao.getBySimilarGroup(group)
        if (members.isEmpty()) return 0
        dao.updateAll(
            members.map {
                it.copy(
                    confirmedWatts = null,
                    confirmedAtEpochMillis = null,
                    confirmationCount = 0
                )
            }
        )
        return members.size
    }

    suspend fun clearAll() = dao.deleteAll()

    // --- Exportar / importar inventario ---

    /**
     * Serializa el inventario completo a JSON, listo para guardar o compartir.
     *
     * El backupId se genera aquí y es único por exportación: es lo que permite
     * al teléfono destino reconocer un archivo ya importado. Exportar dos veces
     * produce dos ids distintos a propósito — son dos respaldos, y el usuario
     * podría querer importar el más nuevo después de haber importado el viejo.
     */
    suspend fun exportInventory(): String {
        val appliances = dao.getAll()
        val backup = ApplianceBackup(
            backupId = UUID.randomUUID().toString(),
            exportedAtEpochMillis = Instant.now().toEpochMilli(),
            appliances = appliances.map { it.toBackup() }
        )
        return gson.toJson(backup)
    }

    /**
     * Importa un inventario desde el JSON de un respaldo.
     *
     * Protege contra duplicados en DOS niveles:
     *
     * 1. Por respaldo: si el backupId ya está en `imported_backups`, no se
     *    importa nada. Es la regla pedida — el mismo archivo no entra dos veces
     *    aunque se elija de nuevo por error.
     * 2. Por equipo: dentro de lo que sí se importa, se omiten los equipos que
     *    ya existen con el mismo nombre y local. Esto cubre el caso de importar
     *    un respaldo DISTINTO que se solapa con el inventario actual, donde la
     *    regla del backupId no ayuda.
     */
    suspend fun importInventory(json: String): ImportResult {
        val backup = try {
            gson.fromJson(json, ApplianceBackup::class.java)
        } catch (e: Exception) {
            return ImportResult.Invalid("El archivo no tiene un formato válido.")
        } ?: return ImportResult.Invalid("El archivo está vacío.")

        if (backup.backupId.isNullOrBlank()) {
            return ImportResult.Invalid("El archivo no es un respaldo de inventario de Felicity Watch.")
        }
        if (backup.formatVersion > ApplianceBackup.CURRENT_FORMAT_VERSION) {
            return ImportResult.Invalid(
                "El respaldo se creó con una versión más nueva de la app. Actualiza la app para importarlo."
            )
        }
        val incoming = backup.appliances ?: emptyList()
        if (incoming.isEmpty()) {
            return ImportResult.Invalid("El respaldo no contiene equipos.")
        }

        if (importedBackupDao.findById(backup.backupId) != null) {
            return ImportResult.AlreadyImported
        }

        // Clave de identidad de un equipo para detectar solapamientos:
        // nombre + local, normalizados. No se usa el consumo porque el
        // usuario pudo haberlo ajustado en uno de los dos teléfonos.
        fun key(name: String, room: String) = "${name.trim().lowercase()}|${room.trim().lowercase()}"

        val existing = dao.getAll().map { key(it.name, it.room) }.toSet()
        val toInsert = incoming
            .filter { it.name.isNotBlank() }
            .filterNot { key(it.name, it.room) in existing }
            .map { it.toEntity() }

        toInsert.forEach { dao.insert(it) }

        // Se registra el respaldo incluso si todo se omitió por duplicado: el
        // archivo ya fue procesado y no debe volver a intentarse.
        importedBackupDao.insert(
            ImportedBackupEntity(
                backupId = backup.backupId,
                importedAtEpochMillis = Instant.now().toEpochMilli(),
                applianceCount = toInsert.size
            )
        )

        return ImportResult.Success(
            imported = toInsert.size,
            skippedDuplicates = incoming.size - toInsert.size
        )
    }

    // --- Eventos de actividad descartados ---

    /** Instantes de los eventos que el usuario borró de la bitácora. */
    fun observeDismissedEventMillis(): Flow<List<Long>> = dismissedDao.observeDismissedMillis()

    suspend fun dismissEvent(eventEpochMillis: Long) {
        dismissedDao.dismiss(DismissedApplianceEventEntity(eventEpochMillis))
        pruneOldDismissals()
    }

    suspend fun dismissEvents(eventEpochMillis: List<Long>) {
        dismissedDao.dismissAll(eventEpochMillis.map { DismissedApplianceEventEntity(it) })
        pruneOldDismissals()
    }

    /** Los eventos solo se calculan de las últimas 24 h, así que registros
     * de descarte más viejos ya no filtran nada. Se podan para no acumular
     * filas indefinidamente. */
    private suspend fun pruneOldDismissals() {
        val cutoff = Instant.now().minus(Duration.ofHours(DISMISSAL_RETENTION_HOURS)).toEpochMilli()
        dismissedDao.deleteOlderThan(cutoff)
    }

    private companion object {
        const val DISMISSAL_RETENTION_HOURS = 48L

        /** Gson propio: el del cliente HTTP está configurado para la API de
         * Felicity y no tiene por qué compartir configuración con esto. */
        val gson: Gson = Gson()
    }
}
