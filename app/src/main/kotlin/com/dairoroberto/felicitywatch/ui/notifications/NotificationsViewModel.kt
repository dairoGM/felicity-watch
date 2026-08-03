package com.dairoroberto.felicitywatch.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dairoroberto.felicitywatch.data.local.PushNotificationEntity
import com.dairoroberto.felicitywatch.data.repository.PushNotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: PushNotificationRepository
) : ViewModel() {
    val notifications: StateFlow<List<PushNotificationEntity>> = repository.observeNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearAll() {
        viewModelScope.launch { repository.clearAll() }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
