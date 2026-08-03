package com.dairoroberto.felicitywatch.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dairoroberto.felicitywatch.data.local.PowerReadingEntity
import com.dairoroberto.felicitywatch.data.repository.PowerHistoryRepository
import com.dairoroberto.felicitywatch.domain.model.GridState
import com.dairoroberto.felicitywatch.service.MonitoringStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class DateRange(val start: LocalDate, val end: LocalDate)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportViewModel @Inject constructor(
    private val repository: PowerHistoryRepository,
    stateHolder: MonitoringStateHolder
) : ViewModel() {

    // Misma fuente que el Panel (guía sección 5): estado en vivo sin
    // debounce para "con/sin corriente ahora", y lastGridChangeAt del
    // estado ya CONFIRMADO (post-debounce) para "lleva X tiempo" — si el
    // Reporte recalculara esto por su cuenta desde el historial crudo,
    // discreparía del Panel al no aplicar el mismo debounce.
    val liveGridState: StateFlow<GridState> = stateHolder.liveGridState
    val lastGridChangeAt: StateFlow<Instant?> = stateHolder.lastGridChangeAt

    private val today = LocalDate.now()

    private val _dateRange = MutableStateFlow(DateRange(today, today))
    val dateRange: StateFlow<DateRange> = _dateRange

    val readings: StateFlow<List<PowerReadingEntity>> = _dateRange
        .flatMapLatest { range ->
            val zone = ZoneId.systemDefault()
            val start = range.start.atStartOfDay(zone).toInstant()
            val end = range.end.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1)
            repository.observeBetween(start, end)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setToday() {
        _dateRange.value = DateRange(today, today)
    }

    fun setYesterday() {
        val yesterday = today.minusDays(1)
        _dateRange.value = DateRange(yesterday, yesterday)
    }

    fun setLast7Days() {
        _dateRange.value = DateRange(today.minusDays(6), today)
    }

    fun setLast30Days() {
        _dateRange.value = DateRange(today.minusDays(29), today)
    }

    fun setCustomRange(start: LocalDate, end: LocalDate) {
        _dateRange.value = if (start.isAfter(end)) DateRange(end, start) else DateRange(start, end)
    }

    /** Desplaza el periodo actual manteniendo su duración (ej. si son 7
     * días, "anterior/siguiente" mueve el bloque completo de 7 en 7). */
    fun shiftRange(forward: Boolean) {
        val current = _dateRange.value
        val lengthDays = ChronoUnit.DAYS.between(current.start, current.end) + 1
        val delta = if (forward) lengthDays else -lengthDays
        _dateRange.value = DateRange(current.start.plusDays(delta), current.end.plusDays(delta))
    }

    fun epochMillisToLocalDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
}
