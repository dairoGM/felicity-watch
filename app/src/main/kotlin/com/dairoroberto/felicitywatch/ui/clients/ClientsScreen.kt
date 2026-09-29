package com.dairoroberto.felicitywatch.ui.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.ui.settings.SettingsViewModel
import com.dairoroberto.felicitywatch.ui.settings.devicesTab

/**
 * Pantalla "Clientes" — al mismo nivel que Ajustes en el menú "Más" (antes
 * era una pestaña más dentro de Ajustes). Reutiliza devicesTab (marcada
 * internal en SettingsScreen.kt) para no duplicar toda la lógica de
 * listado/gestión de dispositivos y configuración de códigos de acceso.
 */
@Composable
fun ClientsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onOpenClientDetail: (deviceId: String, displayName: String?) -> Unit
) {
    var showListView by remember { mutableStateOf(true) }

    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            FilterChip(
                selected = showListView,
                onClick = { showListView = true },
                label = { Text("Listado") }
            )
            FilterChip(
                selected = !showListView,
                onClick = { showListView = false },
                label = { Text("Configuración") }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            devicesTab(
                viewModel = viewModel,
                showListView = showListView,
                onOpenClientDetail = onOpenClientDetail
            )
        }
    }
}
