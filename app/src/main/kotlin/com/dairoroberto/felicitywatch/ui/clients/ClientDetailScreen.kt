package com.dairoroberto.felicitywatch.ui.clients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors
import java.time.Duration
import java.time.Instant
import java.util.Locale

/**
 * Detalle de UN cliente para la master: PV, consumo, batería y tiempo
 * con/sin corriente — reconstruido a partir de las lecturas que ese cliente
 * ya sube a Supabase con su propio device_id, sin necesitar sus
 * credenciales de FSolar (ver ClientDetailViewModel).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailScreen(
    deviceId: String,
    displayName: String?,
    onBack: () -> Unit,
    viewModel: ClientDetailViewModel = hiltViewModel()
) {
    val colors = LocalFelicityColors.current
    val state by viewModel.state.collectAsState()

    LaunchedEffect(deviceId) { viewModel.load(deviceId) }

    // Sin Scaffold/TopAppBar propio: esta pantalla vive bajo el NavHost
    // general (Más > Clientes > detalle), que ya trae su propia TopAppBar
    // fija "Felicity Watch" — un segundo Scaffold aquí duplicaría la barra
    // superior, mismo patrón que el resto de pantallas de "Más"
    // (SettingsScreen, BillingScreen, etc. tampoco traen la suya). El botón
    // "Volver" queda como una fila de cabecera dentro del contenido.
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(
                displayName ?: "Dispositivo ${deviceId.take(8)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textHi
            )
        }

        when (val current = state) {
            is ClientDetailState.Loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is ClientDetailState.Error -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No se pudo consultar: ${current.message}",
                    color = colors.textMid,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            is ClientDetailState.NoData -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Este dispositivo todavía no reportó datos. Debe tener la " +
                        "\"Sincronización en la nube\" activada en su propio Ajustes > Sistema.",
                    color = colors.textMid,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            is ClientDetailState.Loaded -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Última lectura: ${formatRelative(current.lastReadingAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCard(
                            title = "Generación solar",
                            value = formatWatts(current.pvPowerWatts),
                            accent = colors.pvAccent,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Consumo",
                            value = formatWatts(current.loadPowerWatts),
                            accent = colors.textHi,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCard(
                            title = "Batería",
                            value = current.socPercent?.let { "$it%" } ?: "—",
                            accent = colors.chargeAccent,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = if (current.online) "Con corriente" else "Sin corriente",
                            value = current.gridStateSinceEpochMillis?.let {
                                formatDurationShort(Duration.between(Instant.ofEpochMilli(it), Instant.now()))
                            } ?: "—",
                            accent = if (current.online) colors.green else colors.error,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalFelicityColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = colors.textLow)
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private fun formatWatts(watts: Int?): String {
    if (watts == null) return "—"
    return if (watts >= 1000) {
        String.format(Locale("es", "ES"), "%.2f kW", watts / 1000.0)
    } else {
        "$watts W"
    }
}

private fun formatDurationShort(duration: Duration): String {
    val totalMinutes = duration.toMinutes().coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}min" else "${minutes}min"
}

private fun formatRelative(instant: Instant): String {
    val secondsAgo = Duration.between(instant, Instant.now()).seconds.coerceAtLeast(0)
    return when {
        secondsAgo < 60 -> "hace ${secondsAgo}s"
        secondsAgo < 3600 -> "hace ${secondsAgo / 60}min"
        secondsAgo < 86_400 -> "hace ${secondsAgo / 3600}h"
        else -> "hace ${secondsAgo / 86_400} días"
    }
}
