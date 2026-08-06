package com.dairoroberto.felicitywatch.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta exacta del mockup (felicity-watch-android-mockup.html)
val BgPage = Color(0xFFEDEBE4)
val PanelBg = Color(0xFF0B0F14)
val PanelSurface = Color(0xFF121820)
val PanelSurface2 = Color(0xFF1A222C)
val Hairline = Color(0xFF26313D)
val Teal = Color(0xFF3ECAC0)
val TealDim = Color(0xFF1E4D49)
val Orange = Color(0xFFF2622E)
val Green = Color(0xFF4ADE80)
val GreenDim = Color(0xFF1E3B2A)
val TextHi = Color(0xFFE8EDF0)
val TextMid = Color(0xFF8FA0AC)
val TextLow = Color(0xFF546472)
val DangerBorder = Color(0xFF7A2F2F)
val DangerBg = Color(0xFF211112)
val ChartRed = Color(0xFFEF4444)
val DarkError = Color(0xFFEF4444) // Rojo saturado — sí tiene contraste correcto sobre fondos oscuros
val ChargeAccent = Color(0xFFB794F6) // Morado — serie "Carga" en gráficos multilínea, modo oscuro

// Paleta clara (modo claro) — estilo "SaaS premium" minimalista (Linear/
// Stripe): fondo casi blanco con MUY poco contraste entre background y
// surface (la separación la da la sombra, no el color), hairline casi
// imperceptible, y el acento Teal reservado a lo realmente interactivo en
// vez de decorar cada ícono — todo boton/switch/tab comparte esta misma
// fuente de verdad (LocalFelicityColors.current.accent) para que no
// vuelva a haber mezcla de azul de Material3 con Teal fijo como antes.
val LightBg = Color(0xFFF4F4F6)
val LightSurface = Color(0xFFFFFFFF)
val LightSurface2 = Color(0xFFFFFFFF)
val LightHairline = Color(0xFFE8E8EC)
val LightTextHi = Color(0xFF18181B)
val LightTextMid = Color(0xFF52525B)
val LightTextLow = Color(0xFF8A8A94)
val LightGreen = Color(0xFF16A34A) // Green 600 — estados "OK"/online
val LightGreenDim = Color(0xFFF0FDF4) // Green 50
val LightAccent = Color(0xFF0D9488) // Teal 600 — acento principal de marca en modo claro
val LightAccentDim = Color(0xFFF0FDFA) // Teal 50, fondo tenue del acento
val LightDangerBg = Color(0xFFFEF2F2)
val LightSecondary = Color(0xFFD97706) // Amber 600, reservado para advertencias puntuales
val LightError = Color(0xFFDC2626) // Red 600 — DangerBorder (oscuro apagado) no tiene contraste sobre fondo blanco
val LightChargeAccent = Color(0xFF9333EA) // Purple 600 — serie "Carga" en gráficos multilínea, modo claro
/** Tinte de sombra neutro (no negro puro) para que las Cards floten sobre
 * el fondo casi blanco sin necesitar un borde/hairline marcado. */
val LightShadow = Color(0xFF0F172A)
