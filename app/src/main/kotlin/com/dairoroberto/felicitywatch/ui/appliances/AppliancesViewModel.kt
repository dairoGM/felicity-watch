package com.dairoroberto.felicitywatch.ui.appliances

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dairoroberto.felicitywatch.data.local.ApplianceEntity
import com.dairoroberto.felicitywatch.data.local.AppPreferences
import com.dairoroberto.felicitywatch.data.repository.ApplianceRepository
import com.dairoroberto.felicitywatch.data.repository.PowerHistoryRepository
import com.dairoroberto.felicitywatch.domain.usecase.ApplianceEvent
import com.dairoroberto.felicitywatch.domain.usecase.ApplianceEventDetector
import com.dairoroberto.felicitywatch.domain.usecase.ImportResult
import com.dairoroberto.felicitywatch.domain.usecase.RoomConsumption
import com.dairoroberto.felicitywatch.domain.usecase.RoomConsumptionStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.math.abs
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class AppliancesViewModel @Inject constructor(
    private val applianceRepository: ApplianceRepository,
    private val powerHistoryRepository: PowerHistoryRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    /** Catálogo completo, del último registrado al primero (ver ApplianceDao). */
    val appliances: StateFlow<List<ApplianceEntity>> = applianceRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Locales ya registrados, para autocompletar el campo. */
    val rooms: StateFlow<List<String>> = applianceRepository.observeRooms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Grupos de equipos similares registrados, para autocompletar. */
    val similarGroups: StateFlow<List<String>> = applianceRepository.observeSimilarGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Aviso al usuario tras aprender de una confirmación (cuántos equipos
     * se ajustaron). Se consume una vez y se limpia. */
    private val _learningMessage = MutableStateFlow<String?>(null)
    val learningMessage: StateFlow<String?> = _learningMessage

    fun clearLearningMessage() {
        _learningMessage.value = null
    }

    /**
     * El usuario confirma qué equipo produjo el escalón detectado. La app
     * aprende de ello y ajusta el consumo de ese equipo (y de sus similares).
     */
    fun confirmEventAppliance(event: ApplianceEvent, appliance: ApplianceEntity) {
        val observed = abs(event.deltaWatts)
        viewModelScope.launch {
            val affected = applianceRepository.learnFromConfirmation(appliance.id, observed)
            _learningMessage.value = when {
                affected <= 0 -> null
                affected == 1 -> "Consumo de ${appliance.name} ajustado a partir de ${observed} W medidos."
                else -> "Consumo ajustado en $affected equipos del grupo " +
                    "\"${appliance.similarGroup}\" a partir de ${observed} W medidos."
            }
        }
    }

    /** Escalones de consumo de las últimas 24 h, emparejados contra el
     * catálogo. Se usan 24 h porque esta pantalla no tiene filtro de fecha
     * e interesa lo reciente, cuando el usuario recuerda qué encendió. */
    val events: StateFlow<List<ApplianceEvent>> = combine(
        powerHistoryRepository.observeLast24Hours(),
        appliances,
        applianceRepository.observeDismissedEventMillis(),
        // Mismo umbral que usan las notificaciones de equipos: si la lista
        // usara uno distinto, el usuario recibiría avisos de eventos que
        // luego no aparecen en la bitácora (o al revés).
        appPreferences.applianceAlertThresholdWatts
    ) { readings, catalog, dismissed, threshold ->
        val dismissedSet = dismissed.toSet()
        ApplianceEventDetector.detect(readings, catalog, threshold)
            .filterNot { it.epochMillis in dismissedSet }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Umbral vigente, para mostrarlo en el texto de la bitácora. */
    val alertThresholdWatts: StateFlow<Int> = appPreferences.applianceAlertThresholdWatts
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AppPreferences.DEFAULT_APPLIANCE_ALERT_THRESHOLD_WATTS
        )

    /** Consumo estimado por local en las últimas 24 h. Se pasa "ahora" como
     * fin del periodo para que los equipos que siguen encendidos cuenten su
     * tiempo en curso, en vez de aportar cero. */
    val roomStats: StateFlow<List<RoomConsumption>> = combine(events, appliances) { e, a ->
        RoomConsumptionStats.compute(e, a, Instant.now().toEpochMilli())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Borra el consumo aprendido de un equipo; queda solo lo declarado. */
    fun resetLearning(appliance: ApplianceEntity) {
        viewModelScope.launch {
            applianceRepository.resetLearning(appliance.id)
            _learningMessage.value = "Consumo aprendido de ${appliance.name} borrado."
        }
    }

    /** Borra el consumo aprendido de todo un grupo de equipos similares. */
    fun resetSimilarGroupLearning(group: String) {
        viewModelScope.launch {
            val affected = applianceRepository.resetSimilarGroupLearning(group)
            if (affected > 0) {
                _learningMessage.value =
                    "Consumo aprendido borrado en $affected equipos de \"$group\"."
            }
        }
    }

    // --- Exportar / importar inventario ---

    /** JSON listo para escribir en el archivo que el usuario elija. */
    private val _pendingExport = MutableStateFlow<String?>(null)
    val pendingExport: StateFlow<String?> = _pendingExport

    fun prepareExport() {
        viewModelScope.launch {
            val json = applianceRepository.exportInventory()
            _pendingExport.value = json
        }
    }

    fun clearPendingExport() {
        _pendingExport.value = null
    }

    fun onExportWritten(count: Int) {
        _learningMessage.value = "Inventario exportado ($count equipos)."
    }

    fun onExportFailed() {
        _learningMessage.value = "No se pudo guardar el archivo."
    }

    fun importInventory(json: String) {
        viewModelScope.launch {
            _learningMessage.value = when (val result = applianceRepository.importInventory(json)) {
                is ImportResult.AlreadyImported ->
                    "Este respaldo ya se importó antes. No se agregó nada para no duplicar equipos."
                is ImportResult.Invalid -> result.reason
                is ImportResult.Success -> buildString {
                    append("${result.imported} equipos importados")
                    if (result.skippedDuplicates > 0) {
                        append(" · ${result.skippedDuplicates} omitidos por estar ya registrados")
                    }
                    append(".")
                }
            }
        }
    }

    fun onImportReadFailed() {
        _learningMessage.value = "No se pudo leer el archivo."
    }

    fun save(appliance: ApplianceEntity) {
        viewModelScope.launch { applianceRepository.save(appliance) }
    }

    fun delete(appliance: ApplianceEntity) {
        viewModelScope.launch { applianceRepository.delete(appliance) }
    }

    /** Borra un evento de la bitácora. Los eventos se recalculan desde el
     * historial, así que se registra como "descartado" en vez de borrarse
     * (un DELETE reaparecería al siguiente recálculo). */
    fun dismissEvent(event: ApplianceEvent) {
        viewModelScope.launch { applianceRepository.dismissEvent(event.epochMillis) }
    }

    /** Borra todos los eventos visibles actualmente. */
    fun dismissAllEvents() {
        val current = events.value.map { it.epochMillis }
        if (current.isEmpty()) return
        viewModelScope.launch { applianceRepository.dismissEvents(current) }
    }

}
