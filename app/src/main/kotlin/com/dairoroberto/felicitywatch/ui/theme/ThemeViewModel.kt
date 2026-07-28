package com.dairoroberto.felicitywatch.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dairoroberto.felicitywatch.data.local.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val appPreferences: AppPreferences
) : ViewModel() {

    // Claro por defecto: identidad visual clara y elegante (estilo FSolar),
    // el oscuro queda disponible como preferencia opcional en Ajustes.
    val darkModeEnabled: StateFlow<Boolean> = appPreferences.darkModeEnabled
        .map { it ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setDarkModeEnabled(enabled) }
    }
}
