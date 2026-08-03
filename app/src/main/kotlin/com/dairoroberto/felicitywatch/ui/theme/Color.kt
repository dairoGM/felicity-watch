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

// Paleta clara (modo claro), diseño elegante, profesional y minimalista.
// Mismo acento Teal de marca que el modo oscuro (oscurecido para contraste
// AA sobre fondo blanco) — antes varias pantallas usaban el Teal fijo del
// modo oscuro directamente, y otras el "primary" de Material3 (azul),
// resultando en botones/acentos inconsistentes entre sí en modo claro.
val LightBg = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurface2 = Color(0xFFF1F5F9)
val LightHairline = Color(0xFFE2E8F0)
val LightTextHi = Color(0xFF0F172A)
val LightTextMid = Color(0xFF475569)
val LightTextLow = Color(0xFF64748B)
val LightGreen = Color(0xFF059669) // Emerald 600 — se reserva para estados "OK"/online
val LightGreenDim = Color(0xFFECFDF5) // Emerald 50
val LightAccent = Color(0xFF0F9C90) // Teal oscurecido — acento principal de marca en modo claro
val LightAccentDim = Color(0xFFE6F7F5) // Teal 50, fondo tenue del acento
val LightDangerBg = Color(0xFFFEF2F2)
val LightSecondary = Color(0xFFD97706) // Amber 600, reservado para advertencias puntuales
val LightError = Color(0xFFDC2626) // Red 600 — DangerBorder (oscuro apagado) no tiene contraste sobre fondo blanco
val LightChargeAccent = Color(0xFF9333EA) // Purple 600 — serie "Carga" en gráficos multilínea, modo claro
