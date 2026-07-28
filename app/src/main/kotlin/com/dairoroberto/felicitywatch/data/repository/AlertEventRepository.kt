package com.dairoroberto.felicitywatch.data.repository

import com.dairoroberto.felicitywatch.data.local.AlertEventDao
import com.dairoroberto.felicitywatch.data.local.AlertEventEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertEventRepository @Inject constructor(
    private val dao: AlertEventDao
) {
    fun observeEvents(): Flow<List<AlertEventEntity>> = dao.observeAll()

    suspend fun record(event: AlertEventEntity): Long = dao.insert(event)

    suspend fun clearAll() = dao.deleteAll()
}
