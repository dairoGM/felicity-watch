package com.dairoroberto.felicitywatch.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import com.dairoroberto.felicitywatch.ui.nav.FelicityWatchNavHost
import com.dairoroberto.felicitywatch.ui.theme.FelicityWatchTheme
import com.dairoroberto.felicitywatch.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkMode by themeViewModel.darkModeEnabled.collectAsState()

            // enableEdgeToEdge() por sí solo no sabe qué color de icono usar
            // porque nuestro tema es un toggle propio, no el modo del sistema;
            // sin esto los iconos de la barra de estado (hora, batería, señal)
            // quedaban del mismo color que el fondo y se volvían invisibles.
            LaunchedEffect(darkMode) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !darkMode
                insetsController.isAppearanceLightNavigationBars = !darkMode
            }

            FelicityWatchTheme(darkTheme = darkMode) {
                FelicityWatchNavHost()
            }
        }
    }
}
