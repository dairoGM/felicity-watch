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
import javax.inject.Inject

/** Rango de meses seleccionado. Un solo mes = from == to. */
data class MonthRange(val from: YearMonth, val to: YearMonth) {
    val isSingleMonth: Boolean get() = from == to
}

data class BillingUiState(
    val estimate: BillingEstimate? = null,
    val availableMonths: List<YearMonth> = emptyList(),
    val selection: MonthRange = MonthRange(YearMonth.now(), YearMonth.now()),
    /** Proyección a fin de mes; solo se calcula para el mes en curso. */
    val projectedMonthEndKwh: Double? = null,
    val projectedMonthEndCost: Double? = null,
    /** true si el historial local no cubre todo el rango pedido. */
    val historyIncomplete: Boolean = false
)

@HiltViewModel
class BillingViewModel @Inject constructor(
    powerHistoryRepository: PowerHistoryRepository
) : ViewModel() {

    private val zone = ZoneId.systemDefault()

    private val _selection = MutableStateFlow(
        MonthRange(YearMonth.now(zone), YearMonth.now(zone))
    )

    val uiState: StateFlow<BillingUiState> = combine(
        powerHistoryRepository.observeLastRetentionWindow(),
        _selection
    ) { readings, selection ->
        val months = BillingEstimator.availableMonths(readings, zone)
        val filtered = BillingEstimator.filterByMonths(readings, selection.from, selection.to, zone)

        val label = if (selection.isSingleMonth) {
            monthLabel(selection.from)
        } else {
            "${monthLabel(selection.from)} – ${monthLabel(selection.to)}"
        }

        val estimate = BillingEstimator.estimate(filtered, label, zone)

        val projectedKwh = if (selection.isSingleMonth) {
            BillingEstimator.projectMonthEnd(estimate, selection.from, LocalDate.now(zone))
        } else null

        BillingUiState(
            estimate = estimate,
            availableMonths = months,
            selection = selection,
            projectedMonthEndKwh = projectedKwh,
            projectedMonthEndCost = projectedKwh?.let {
                com.dairoroberto.felicitywatch.domain.usecase.ElectricityTariff.cost(it)
            },
            // El historial local guarda 30 días. Si el usuario pide un mes que
            // el historial no alcanza a cubrir, el número saldrá bajo y hay que
            // decírselo en vez de dejarlo creer que consumió poco.
            historyIncomplete = months.isNotEmpty() && selection.from < months.min()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BillingUiState())

    fun selectMonth(month: YearMonth) {
        _selection.value = MonthRange(month, month)
    }

    fun selectRange(from: YearMonth, to: YearMonth) {
        _selection.value = if (from <= to) MonthRange(from, to) else MonthRange(to, from)
    }

    private fun monthLabel(month: YearMonth): String {
        val names = listOf(
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
        )
        return names[month.monthValue - 1].replaceFirstChar { it.uppercase() } +
            " " + month.year
    }
}
