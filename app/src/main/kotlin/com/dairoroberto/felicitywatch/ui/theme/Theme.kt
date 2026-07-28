package com.dairoroberto.felicitywatch.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

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

private val FelicityLightColorScheme = lightColorScheme(
    primary = Teal,
    onPrimary = LightSurface,
    primaryContainer = LightTealDim,
    onPrimaryContainer = LightTextHi,
    secondary = Orange,
    onSecondary = LightSurface,
    background = LightBg,
    onBackground = LightTextHi,
    surface = LightSurface,
    onSurface = LightTextHi,
    surfaceVariant = LightSurface2,
    onSurfaceVariant = LightTextMid,
    outline = LightHairline,
    error = DangerBorder,
    onError = LightSurface
)

/**
 * Material 3 con ColorScheme custom fijo (no Material You dinámico).
 * [darkTheme] decide entre la paleta oscura del mockup y su equivalente
 * clara; [LocalFelicityColors] queda disponible para las pantallas que
 * necesitan matices adicionales (superficie secundaria, verdes, etc).
 */
@Composable
fun FelicityWatchTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalFelicityColors provides if (darkTheme) DarkFelicityColors else LightFelicityColors
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) FelicityDarkColorScheme else FelicityLightColorScheme,
            typography = FelicityTypography,
            content = content
        )
    }
}
