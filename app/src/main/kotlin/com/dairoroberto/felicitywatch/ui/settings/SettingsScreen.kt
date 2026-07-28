package com.dairoroberto.felicitywatch.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.service.MonitoringServiceController
import com.dairoroberto.felicitywatch.ui.theme.Green
import com.dairoroberto.felicitywatch.ui.theme.PanelSurface2
import com.dairoroberto.felicitywatch.ui.theme.TextMid

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val formState by viewModel.formState.collectAsState()
    val serviceRunning by viewModel.serviceRunning.collectAsState()
    var batteryExcluded by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionCard(title = "Cuenta FSolar") {
                OutlinedTextField(
                    value = formState.fsolarUsername,
                    onValueChange = viewModel::onUsernameChange,
                    label = { Text("Correo") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = formState.fsolarPassword,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Button(
                    onClick = { viewModel.saveFsolarCredentials() },
                    modifier = Modifier.padding(top = 10.dp)
                ) { Text("Guardar credenciales") }
            }
        }

        item {
            SectionCard(title = "WhatsApp (CallMeBot)") {
                OutlinedTextField(
                    value = formState.whatsappPhone,
                    onValueChange = viewModel::onWhatsappPhoneChange,
                    label = { Text("Número (sin +, ej. 5355848425)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = formState.callMeBotApiKey,
                    onValueChange = viewModel::onApiKeyChange,
                    label = { Text("API key de CallMeBot") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Button(
                    onClick = { viewModel.saveWhatsappConfig() },
                    modifier = Modifier.padding(top = 10.dp)
                ) { Text("Guardar WhatsApp") }
            }
        }

        item {
            SectionCard(title = "Optimización de batería") {
                Text(
                    if (batteryExcluded) "Excluida — el sistema no debería matar el servicio."
                    else "No excluida — MIUI y fabricantes similares pueden matar el servicio.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (batteryExcluded) Green else TextMid
                )
                Text(
                    "Es necesario excluir Felicity Watch de la optimización de batería para que el aviso llegue con la pantalla apagada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Button(
                    onClick = {
                        requestIgnoreBatteryOptimizations(context)
                        batteryExcluded = isIgnoringBatteryOptimizations(context)
                    },
                    modifier = Modifier.padding(top = 10.dp)
                ) { Text("Solicitar exclusión") }
            }
        }

        item {
            SectionCard(title = "Servicio de vigilancia") {
                Text(
                    if (serviceRunning) "Activo" else "Detenido",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (serviceRunning) Green else TextMid
                )
                Button(
                    onClick = { MonitoringServiceController.start(context) },
                    modifier = Modifier.padding(top = 10.dp)
                ) { Text("Reiniciar servicio") }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = PanelSurface2), shape = RoundedCornerShape(11.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Column(Modifier.padding(top = 10.dp)) { content() }
        }
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
}
