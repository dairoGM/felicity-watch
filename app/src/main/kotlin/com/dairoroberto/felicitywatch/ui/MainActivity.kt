package com.dairoroberto.felicitywatch.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dairoroberto.felicitywatch.ui.nav.FelicityWatchNavHost
import com.dairoroberto.felicitywatch.ui.theme.FelicityWatchTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FelicityWatchTheme {
                FelicityWatchNavHost()
            }
        }
    }
}
