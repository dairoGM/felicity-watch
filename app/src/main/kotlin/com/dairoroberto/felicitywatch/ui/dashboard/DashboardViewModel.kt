package com.dairoroberto.felicitywatch.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dairoroberto.felicitywatch.data.local.AlertEventEntity
import com.dairoroberto.felicitywatch.data.local.CredentialsStore
import com.dairoroberto.felicitywatch.data.repository.AlertEventRepository
import com.dairoroberto.felicitywatch.domain.model.BatteryReading
import com.dairoroberto.felicitywatch.domain.model.GridState
import com.dairoroberto.felicitywatch.domain.model.InverterReading
import com.dairoroberto.felicitywatch.notification.NotificationChannels
import com.dairoroberto.felicitywatch.service.MonitoringStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

data class DashboardUiState(
    val liveGridState: GridState = GridState.UNKNOWN,
    val confirmedGridState: GridState = GridState.UNKNOWN,
    val lastGridChangeAt: Instant? = null,
    val inverter: InverterReading? = null,
    val battery: BatteryReading? = null,
    val serviceRunning: Boolean = false,
    val lastError: String? = null,
    val consecutiveFailures: Int = 0,
    val lastSuccessfulReadingAt: Instant? = null,
    val latestEvent: AlertEventEntity? = null,
    val voiceConfigured: Boolean = true,
    val pushConfigured: Boolean = false,
    val whatsappConfigured: Boolean = false
) {
    val connectionHealthy: Boolean get() = consecutiveFailures == 0 && lastError == null
}

private data class ReadingsSnapshot(
    val liveGridState: GridState,
    val confirmedGridState: GridState,
    val lastGridChangeAt: Instant?,
    val inverter: InverterReading?,
    val battery: BatteryReading?
)

private data class StatusSnapshot(
    val serviceRunning: Boolean,
    val lastError: String?,
    val consecutiveFailures: Int,
    val lastSuccessfulReadingAt: Instant?,
    val latestEvent: AlertEventEntity?
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    stateHolder: MonitoringStateHolder,
    alertEventRepository: AlertEventRepository,
    private val credentialsStore: CredentialsStore,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val readingsFlow = combine(
        stateHolder.liveGridState,
        stateHolder.confirmedGridState,
        stateHolder.lastGridChangeAt,
        stateHolder.inverterReading,
        stateHolder.batteryReading
    ) { live, confirmed, lastChangeAt, inverter, battery ->
        ReadingsSnapshot(live, confirmed, lastChangeAt, inverter, battery)
    }

    private val statusFlow = combine(
        stateHolder.serviceRunning,
        stateHolder.lastErrorMessage,
        stateHolder.consecutiveFailures,
        stateHolder.lastSuccessfulReadingAt,
        alertEventRepository.observeEvents()
    ) { running, error, failures, lastOk, events ->
        StatusSnapshot(running, error, failures, lastOk, events.firstOrNull())
    }

    val uiState: StateFlow<DashboardUiState> = combine(readingsFlow, statusFlow) { readings, status ->
        DashboardUiState(
            liveGridState = readings.liveGridState,
            confirmedGridState = readings.confirmedGridState,
            lastGridChangeAt = readings.lastGridChangeAt,
            inverter = readings.inverter,
            battery = readings.battery,
            serviceRunning = status.serviceRunning,
            lastError = status.lastError,
            consecutiveFailures = status.consecutiveFailures,
            lastSuccessfulReadingAt = status.lastSuccessfulReadingAt,
            latestEvent = status.latestEvent,
            voiceConfigured = true,
            pushConfigured = NotificationChannels.areNotificationsEnabled(context),
            whatsappConfigured = credentialsStore.hasWhatsappConfig()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())
}
