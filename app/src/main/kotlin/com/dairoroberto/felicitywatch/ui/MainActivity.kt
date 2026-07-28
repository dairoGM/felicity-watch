package com.dairoroberto.felicitywatch.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
            FelicityWatchTheme(darkTheme = darkMode) {
                FelicityWatchNavHost()
            }
        }
    }
}
