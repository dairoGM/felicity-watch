package com.dairoroberto.felicitywatch.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dairoroberto.felicitywatch.data.local.PowerReadingEntity
import com.dairoroberto.felicitywatch.data.repository.PowerHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class GenerationChartViewModel @Inject constructor(
    repository: PowerHistoryRepository
) : ViewModel() {

    val last24Hours: StateFlow<List<PowerReadingEntity>> = repository.observeLast24Hours()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
