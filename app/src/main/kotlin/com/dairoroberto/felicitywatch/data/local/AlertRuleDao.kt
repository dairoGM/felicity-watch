package com.dairoroberto.felicitywatch.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertRuleDao {
    @Query("SELECT * FROM alert_rules ORDER BY id ASC")
    fun observeAll(): Flow<List<AlertRuleEntity>>

    @Query("SELECT * FROM alert_rules WHERE enabled = 1")
    suspend fun getEnabled(): List<AlertRuleEntity>

    @Query("SELECT COUNT(*) FROM alert_rules")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<AlertRuleEntity>)

    @Update
    suspend fun update(rule: AlertRuleEntity)

    @Query("DELETE FROM alert_rules")
    suspend fun deleteAll()
}
