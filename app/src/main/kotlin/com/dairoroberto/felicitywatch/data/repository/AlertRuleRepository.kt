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
}
