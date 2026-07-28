package com.dairoroberto.felicitywatch.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dairoroberto.felicitywatch.data.local.AlertRuleEntity
import com.dairoroberto.felicitywatch.data.repository.AlertRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val repository: AlertRuleRepository
) : ViewModel() {

    init {
        viewModelScope.launch { repository.seedDefaultsIfEmpty() }
    }

    val rules: StateFlow<List<AlertRuleEntity>> = repository.observeRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleEnabled(rule: AlertRuleEntity) = update(rule.copy(enabled = !rule.enabled))

    fun updateThreshold(rule: AlertRuleEntity, threshold: Double?) = update(rule.copy(thresholdValue = threshold))

    fun updateMessage(rule: AlertRuleEntity, message: String) = update(rule.copy(messageTemplate = message))

    fun toggleVoiceChannel(rule: AlertRuleEntity) = update(rule.copy(channelVoiceEnabled = !rule.channelVoiceEnabled))

    fun togglePushChannel(rule: AlertRuleEntity) = update(rule.copy(channelPushEnabled = !rule.channelPushEnabled))

    fun toggleWhatsappChannel(rule: AlertRuleEntity) = update(rule.copy(channelWhatsappEnabled = !rule.channelWhatsappEnabled))

    private fun update(rule: AlertRuleEntity) {
        viewModelScope.launch { repository.updateRule(rule) }
    }
}
