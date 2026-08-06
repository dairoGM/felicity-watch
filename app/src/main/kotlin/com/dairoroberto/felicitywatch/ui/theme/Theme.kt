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
    error = DarkError,
    onError = PanelBg
)

private val FelicityLightColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = LightSurface,
    primaryContainer = LightAccentDim,
    onPrimaryContainer = LightTextHi,
    secondary = LightSecondary,
    onSecondary = LightSurface,
    background = LightBg,
    onBackground = LightTextHi,
    surface = LightSurface,
    onSurface = LightTextHi,
    // surfaceTint = fondo por defecto: sin esto, Material3 tiñe cada
    // Card elevada con "primary" (Teal), dándoles un tono verdoso no
    // intencional — el look SaaS que buscamos separa las Cards del fondo
    // con sombra neutra (ver LightShadow), no con un tinte de color.
    surfaceTint = LightSurface,
    surfaceVariant = LightSurface2,
    onSurfaceVariant = LightTextMid,
    outline = LightHairline,
    error = LightError,
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
