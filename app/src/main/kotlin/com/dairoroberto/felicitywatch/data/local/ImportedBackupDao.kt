package com.dairoroberto.felicitywatch.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ImportedBackupDao {

    @Query("SELECT * FROM imported_backups WHERE backupId = :backupId LIMIT 1")
    suspend fun findById(backupId: String): ImportedBackupEntity?

    /** ABORT y no REPLACE: si el respaldo ya está registrado, la inserción
     * debe fallar para que no se importe dos veces. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(backup: ImportedBackupEntity)

    @Query("SELECT COUNT(*) FROM imported_backups")
    suspend fun count(): Int
}
