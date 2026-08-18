package com.dairoroberto.felicitywatch.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Respaldo de inventario ya importado en este teléfono.
 *
 * Existe para cumplir la regla de "no importar el mismo inventario dos veces":
 * el archivo trae un backupId único, y al importarlo se registra aquí. Un
 * segundo intento con el mismo archivo se rechaza sin insertar nada.
 *
 * Se guarda el backupId como clave primaria, así el propio índice único de
 * SQLite garantiza que no haya dos registros del mismo respaldo.
 */
@Entity(tableName = "imported_backups")
data class ImportedBackupEntity(
    @PrimaryKey val backupId: String,
    /** Cuándo se importó en ESTE teléfono. */
    val importedAtEpochMillis: Long,
    /** Cuántos equipos trajo, para poder mostrar el historial de imports. */
    val applianceCount: Int
)
