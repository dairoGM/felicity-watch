package com.dairoroberto.felicitywatch.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dairoroberto.felicitywatch.data.local.AlertRuleEntity
import com.dairoroberto.felicitywatch.data.repository.AlertRuleRepository
import com.dairoroberto.felicitywatch.domain.usecase.DispatchAlertUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val repository: AlertRuleRepository,
    private val dispatchAlertUseCase: DispatchAlertUseCase
) : ViewModel() {

    init {
        viewModelScope.launch {
            repository.seedDefaultsIfEmpty()
            repository.seedMissingDefaults()
        }
    }

    val rules: StateFlow<List<AlertRuleEntity>> = repository.observeRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    /** Dispara la alerta tal cual está configurada (mismos canales/mensaje
     * que usaría un disparo real) — así se puede escuchar/ver el aviso sin
     * esperar a que la condición real ocurra (ej. consumo alto, autonomía
     * baja, que pueden tardar en darse). */
    fun testRule(rule: AlertRuleEntity) {
        viewModelScope.launch {
            runCatching { dispatchAlertUseCase.dispatch(rule, rule.messageTemplate) }
                .onSuccess { _messages.tryEmit("Alerta de prueba disparada") }
                .onFailure { _messages.tryEmit("No se pudo probar la alerta: ${it.message ?: it}") }
        }
    }

    fun toggleEnabled(rule: AlertRuleEntity) = update(rule.copy(enabled = !rule.enabled))

    fun updateThreshold(rule: AlertRuleEntity, threshold: Double?) = update(rule.copy(thresholdValue = threshold))

    fun updateDebounceSeconds(rule: AlertRuleEntity, seconds: Int) = update(rule.copy(debounceSeconds = seconds))

    fun updateMessage(rule: AlertRuleEntity, message: String) = update(rule.copy(messageTemplate = message))

    fun toggleVoiceChannel(rule: AlertRuleEntity) = update(rule.copy(channelVoiceEnabled = !rule.channelVoiceEnabled))

    fun togglePushChannel(rule: AlertRuleEntity) = update(rule.copy(channelPushEnabled = !rule.channelPushEnabled))

    fun toggleWhatsappChannel(rule: AlertRuleEntity) = update(rule.copy(channelWhatsappEnabled = !rule.channelWhatsappEnabled))

    private fun update(rule: AlertRuleEntity) {
        viewModelScope.launch { repository.updateRule(rule) }
    }
}
