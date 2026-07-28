package com.dairoroberto.felicitywatch.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.service.MonitoringServiceController
import com.dairoroberto.felicitywatch.ui.theme.TextMid

/** Onboarding obligatorio de primer arranque (guía sección 8.5). */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val formState by viewModel.formState.collectAsState()
    var step by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (step) {
            0 -> {
                Text("Conecta tu cuenta FSolar", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Usa el mismo correo y contraseña de la app FSolar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMid
                )
                OutlinedTextField(
                    value = formState.username,
                    onValueChange = viewModel::onUsernameChange,
                    label = { Text("Correo") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = formState.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { step = 1 },
                    enabled = viewModel.canProceedFromLogin()
                ) { Text("Continuar") }
            }

            1 -> {
                Text("Excluye la app de la optimización de batería", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Sin esto, MIUI y fabricantes similares matarán el servicio de vigilancia y dejarás de recibir avisos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMid
                )
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }) { Text("Excluir de optimización") }
                OutlinedButton(onClick = { step = 2 }) { Text("Continuar") }
            }

            2 -> {
                Text("WhatsApp vía CallMeBot (opcional)", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Sigue las instrucciones de callmebot.com/blog/free-api-whatsapp-messages para obtener tu apiKey. Puedes saltar este paso y configurarlo después en Ajustes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMid
                )
                OutlinedTextField(
                    value = formState.whatsappPhone,
                    onValueChange = viewModel::onWhatsappPhoneChange,
                    label = { Text("Número (sin +)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = formState.callMeBotApiKey,
                    onValueChange = viewModel::onApiKeyChange,
                    label = { Text("API key de CallMeBot") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { step = 3 }) { Text("Continuar") }
            }

            3 -> {
                Text("Todo listo", style = MaterialTheme.typography.titleLarge)
                Text(
                    "El servicio de vigilancia va a iniciar ahora en segundo plano.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMid
                )
                Button(onClick = {
                    viewModel.saveCredentials()
                    MonitoringServiceController.start(context)
                    onFinished()
                }) { Text("Empezar a vigilar") }
            }
        }
    }
}
