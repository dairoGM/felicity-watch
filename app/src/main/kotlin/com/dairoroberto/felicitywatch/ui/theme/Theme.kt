package com.dairoroberto.felicitywatch.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val FelicityDarkColorScheme = darkColorScheme(
    primary = Teal,
    onPrimary = PanelBg,
    primaryContainer = TealDim,
    onPrimaryContainer = Teal,
    secondary = Orange,
    onSecondary = PanelBg,
    background = PanelBg,
    onBackground = TextHi,
    surface = PanelSurface,
    onSurface = TextHi,
    surfaceVariant = PanelSurface2,
    onSurfaceVariant = TextMid,
    outline = Hairline,
    error = DangerBorder,
    onError = TextHi
)

/**
 * Material 3 con ColorScheme custom fijo (no Material You dinámico),
 * tema oscuro tal cual el mockup aprobado.
 */
@Composable
fun FelicityWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FelicityDarkColorScheme,
        typography = FelicityTypography,
        content = content
    )
}
