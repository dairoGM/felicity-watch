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

    // Oscuro por defecto: coincide con el windowBackground fijo de
    // themes.xml (#0B0F14) desde el primer frame de la Activity, antes de
    // que Compose siquiera resuelva este StateFlow — con claro por defecto,
    // esa ventana de arranque (y cualquier pantalla sin un background propio,
    // como DeviceBlockedScreen) mostraba texto de tema claro sobre ese fondo
    // oscuro fijo. El claro sigue disponible como preferencia en Ajustes.
    val darkModeEnabled: StateFlow<Boolean> = appPreferences.darkModeEnabled
        .map { it ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setDarkModeEnabled(enabled) }
    }
}
