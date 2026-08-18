package com.dairoroberto.felicitywatch.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ApplianceDao {
    // Orden descendente por id: el último equipo registrado aparece
    // primero, así lo recién agregado queda visible sin tener que buscar.
    @Query("SELECT * FROM appliances ORDER BY id DESC")
    fun observeAll(): Flow<List<ApplianceEntity>>

    @Query("SELECT * FROM appliances ORDER BY watts DESC")
    suspend fun getAll(): List<ApplianceEntity>

    /** Habitaciones ya usadas — para sugerirlas al registrar un equipo
     * nuevo y evitar duplicados por tipeo ("Sala" vs "sala"). */
    @Query("SELECT DISTINCT room FROM appliances WHERE room != '' ORDER BY room COLLATE NOCASE ASC")
    fun observeRooms(): Flow<List<String>>

    /** Grupos de equipos similares ya usados, para autocompletarlos igual
     * que las habitaciones. */
    @Query(
        "SELECT DISTINCT similarGroup FROM appliances WHERE similarGroup != '' " +
            "ORDER BY similarGroup COLLATE NOCASE ASC"
    )
    fun observeSimilarGroups(): Flow<List<String>>

    /** Equipos de un grupo similar — el aprendizaje se aplica a todos ellos
     * porque son eléctricamente indistinguibles entre sí. */
    @Query("SELECT * FROM appliances WHERE similarGroup != '' AND similarGroup = :group COLLATE NOCASE")
    suspend fun getBySimilarGroup(group: String): List<ApplianceEntity>

    @Query("SELECT * FROM appliances WHERE id = :id")
    suspend fun getById(id: Long): ApplianceEntity?

    @Update
    suspend fun updateAll(appliances: List<ApplianceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(appliance: ApplianceEntity)

    @Update
    suspend fun update(appliance: ApplianceEntity)

    @Delete
    suspend fun delete(appliance: ApplianceEntity)

    @Query("DELETE FROM appliances")
    suspend fun deleteAll()
}
