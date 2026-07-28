package com.dairoroberto.felicitywatch.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.domain.model.DeviceInfo
import com.dairoroberto.felicitywatch.domain.model.DeviceRole
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors
import com.dairoroberto.felicitywatch.ui.theme.Teal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(viewModel: DevicesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalFelicityColors.current

    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        val error = state.error
        if (error != null) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = colors.dangerBg), shape = RoundedCornerShape(12.dp)) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else if (state.devices.isEmpty() && !state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Todavía no se encontraron dispositivos.\nDesliza hacia abajo para reintentar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMid
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.devices, key = { it.serialNumber }) { device ->
                    DeviceCard(device)
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: DeviceInfo) {
    val colors = LocalFelicityColors.current
    val (icon, roleLabel) = when (device.role) {
        DeviceRole.INVERTER -> Icons.Default.ElectricBolt to "Inversor"
        DeviceRole.BATTERY -> Icons.Default.BatteryChargingFull to "Batería"
        DeviceRole.OTHER -> Icons.Default.DeviceUnknown to "Dispositivo"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface2),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.tealDim),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = roleLabel, tint = Teal)
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(
                    device.alias?.takeIf { it.isNotBlank() } ?: roleLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "$roleLabel · ${device.model ?: "modelo desconocido"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMid
                )
                Text(
                    "Serie: ${device.serialNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textLow,
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (device.plantName != null) {
                    Text(
                        "Planta: ${device.plantName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textLow
                    )
                }
            }
            if (device.status != null) {
                Text(
                    device.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.green
                )
            }
        }
    }
}
