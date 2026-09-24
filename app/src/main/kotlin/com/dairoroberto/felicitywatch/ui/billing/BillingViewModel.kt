package com.dairoroberto.felicitywatch.ui.billing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dairoroberto.felicitywatch.data.repository.PowerHistoryRepository
import com.dairoroberto.felicitywatch.domain.usecase.BillingEstimate
import com.dairoroberto.felicitywatch.domain.usecase.BillingEstimator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Rango de fechas seleccionado — mismo modelo que usa el Reporte de Consumo,
 * para que "del 01/09 al 15/09" signifique exactamente lo mismo en ambas
 * pantallas. */
data class BillingDateRange(val start: LocalDate, val end: LocalDate)

/** Qué mostrar en el card de consumo total: todo, solo con corriente, o solo sin corriente. */
enum class BillingConsumptionFilter { TOTAL, GRID, BATTERY }

data class BillingUiState(
    val estimate: BillingEstimate? = null,
    val range: BillingDateRange = defaultRange(),
    val filter: BillingConsumptionFilter = BillingConsumptionFilter.TOTAL,
    /** Proyección a fin de mes; solo se calcula cuando el rango es "1° del mes en curso → hoy". */
    val projectedMonthEndKwh: Double? = null,
    val projectedMonthEndCost: Double? = null,
    /** true si el historial local no alcanza a cubrir el inicio del rango pedido. */
    val historyIncomplete: Boolean = false
)

private fun defaultRange(zone: ZoneId = ZoneId.systemDefault()): BillingDateRange {
    val today = LocalDate.now(zone)
    return BillingDateRange(YearMonth.from(today).atDay(1), today)
}

@HiltViewModel
class BillingViewModel @Inject constructor(
    powerHistoryRepository: PowerHistoryRepository
) : ViewModel() {

    private val zone = ZoneId.systemDefault()

    private val _range = MutableStateFlow(defaultRange(zone))
    private val _filter = MutableStateFlow(BillingConsumptionFilter.TOTAL)

    val uiState: StateFlow<BillingUiState> = combine(
        powerHistoryRepository.observeLastRetentionWindow(),
        _range,
        _filter
    ) { readings, range, filter ->
        val earliest = BillingEstimator.earliestDate(readings, zone)
        val filtered = BillingEstimator.filterByDateRange(readings, range.start, range.end, zone)

        val label = if (range.start == range.end) {
            dayLabel(range.start)
        } else {
            "${dayLabel(range.start)} – ${dayLabel(range.end)}"
        }

        val estimate = BillingEstimator.estimate(filtered, label, zone)
        val projectedKwh = BillingEstimator.projectMonthEnd(estimate, range.start, range.end, LocalDate.now(zone))

        BillingUiState(
            estimate = estimate,
            range = range,
            filter = filter,
            projectedMonthEndKwh = projectedKwh,
            projectedMonthEndCost = projectedKwh?.let {
                com.dairoroberto.felicitywatch.domain.usecase.ElectricityTariff.cost(it)
            },
            // El historial local guarda una ventana limitada. Si el usuario pide
            // un rango que empieza antes de la primera lectura disponible, el
            // número saldrá bajo y hay que decírselo en vez de dejarlo creer que
            // consumió poco.
            historyIncomplete = earliest != null && range.start < earliest
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BillingUiState())

    fun setToday() {
        val today = LocalDate.now(zone)
        _range.value = BillingDateRange(today, today)
    }

    fun setThisMonth() {
        _range.value = defaultRange(zone)
    }

    fun setLast7Days() {
        val today = LocalDate.now(zone)
        _range.value = BillingDateRange(today.minusDays(6), today)
    }

    fun setLast30Days() {
        val today = LocalDate.now(zone)
        _range.value = BillingDateRange(today.minusDays(29), today)
    }

    fun setCustomRange(start: LocalDate, end: LocalDate) {
        _range.value = if (start.isAfter(end)) BillingDateRange(end, start) else BillingDateRange(start, end)
    }

    /** Desplaza el periodo actual manteniendo su duración, igual que en Reportes. */
    fun shiftRange(forward: Boolean) {
        val current = _range.value
        val lengthDays = ChronoUnit.DAYS.between(current.start, current.end) + 1
        val delta = if (forward) lengthDays else -lengthDays
        _range.value = BillingDateRange(current.start.plusDays(delta), current.end.plusDays(delta))
    }

    fun setFilter(filter: BillingConsumptionFilter) {
        _filter.value = filter
    }

    private fun dayLabel(date: LocalDate): String {
        val names = listOf(
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
        )
        return "${date.dayOfMonth} de ${names[date.monthValue - 1]} de ${date.year}"
    }
}
