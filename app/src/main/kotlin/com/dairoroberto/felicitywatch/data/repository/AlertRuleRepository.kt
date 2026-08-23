package com.dairoroberto.felicitywatch.data.repository

import com.dairoroberto.felicitywatch.data.local.AlertRuleDao
import com.dairoroberto.felicitywatch.data.local.AlertRuleEntity
import com.dairoroberto.felicitywatch.data.local.AppDatabase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertRuleRepository @Inject constructor(
    private val dao: AlertRuleDao
) {
    fun observeRules(): Flow<List<AlertRuleEntity>> = dao.observeAll()

    suspend fun getEnabledRules(): List<AlertRuleEntity> = dao.getEnabled()

    suspend fun updateRule(rule: AlertRuleEntity) = dao.update(rule)

    suspend fun seedDefaultsIfEmpty() {
        if (dao.count() == 0) {
            dao.insertAll(AppDatabase.defaultAlertRules())
        }
    }

    /**
     * Agrega tipos de regla nuevos (ej. LOAD_HIGH, BATTERY_AUTONOMY_LOW
     * agregados en una actualización posterior) que todavía no existen para
     * esta cuenta — sin esto, alguien que instaló la app antes de que
     * existieran esos tipos nunca los vería, porque [seedDefaultsIfEmpty]
     * solo actúa cuando la tabla está completamente vacía.
     */
    suspend fun seedMissingDefaults() {
        val existingTypes = dao.getAll().map { it.type }.toSet()
        val missing = AppDatabase.defaultAlertRules().filter { it.type !in existingTypes }
        if (missing.isNotEmpty()) dao.insertAll(missing)
    }

    /** Restablecimiento de fábrica: borra las reglas editadas y vuelve a los valores por defecto. */
    suspend fun resetToDefaults() {
        dao.deleteAll()
        dao.insertAll(AppDatabase.defaultAlertRules())
    }
}
