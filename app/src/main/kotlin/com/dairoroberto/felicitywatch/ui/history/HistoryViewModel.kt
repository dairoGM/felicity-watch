package com.dairoroberto.felicitywatch.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dairoroberto.felicitywatch.data.local.AlertEventEntity
import com.dairoroberto.felicitywatch.data.repository.AlertEventRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: AlertEventRepository
) : ViewModel() {
    val events: StateFlow<List<AlertEventEntity>> = repository.observeEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearAll() {
        viewModelScope.launch { repository.clearAll() }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
