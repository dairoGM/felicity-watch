package com.dairoroberto.felicitywatch.ui.alerts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dairoroberto.felicitywatch.ui.history.HistoryScreen
import com.dairoroberto.felicitywatch.ui.notifications.NotificationsScreen
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors

/** Solo historial de alertas (Eventos disparados + notificaciones Push
 * recibidas) — las Reglas de alerta (umbrales/canales) viven en Ajustes >
 * Alertas, no aquí, para que toda la CONFIGURACIÓN quede en un solo lugar. */
@Composable
fun AlertsHostScreen() {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Eventos", "Push")
    val colors = LocalFelicityColors.current

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = colors.surface2,
            contentColor = colors.accent,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = colors.accent
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedTabIndex == index) {
                                colors.accent
                            } else {
                                colors.textMid
                            }
                        )
                    }
                )
            }
        }

        when (selectedTabIndex) {
            0 -> HistoryScreen()
            1 -> NotificationsScreen()
        }
    }
}
