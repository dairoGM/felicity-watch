package com.dairoroberto.felicitywatch.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.service.MonitoringServiceController
import com.dairoroberto.felicitywatch.ui.components.ApiKeyField
import com.dairoroberto.felicitywatch.ui.components.EmailField
import com.dairoroberto.felicitywatch.ui.components.PasswordField
import com.dairoroberto.felicitywatch.ui.components.PhoneField
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors

private const val TOTAL_STEPS = 4

/** Onboarding obligatorio de primer arranque (guía sección 8.5). */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val formState by viewModel.formState.collectAsState()
    var step by remember { mutableIntStateOf(0) }
    val colors = LocalFelicityColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        LinearProgressIndicator(
            progress = { (step + 1) / TOTAL_STEPS.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            color = colors.accent,
            trackColor = colors.hairline
        )
        Text(
            "Paso ${step + 1} de $TOTAL_STEPS",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMid
        )

        when (step) {
            0 -> {
                StepIcon(Icons.Default.Login)
                Text("Conecta tu cuenta FSolar", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Usa el mismo correo y contraseña de la app FSolar para que Felicity Watch pueda leer tu inversor y batería.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMid
                )
                EmailField(value = formState.username, onValueChange = viewModel::onUsernameChange)
                PasswordField(
                    value = formState.password,
                    onValueChange = viewModel::onPasswordChange,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Button(
                    onClick = { step = 1 },
                    enabled = viewModel.canProceedFromLogin(),
                    modifier = Modifier.padding(top = 4.dp)
                ) { Text("Continuar") }
            }

            1 -> {
                StepIcon(Icons.Default.BatterySaver)
                Text("Excluye la app de la optimización de batería", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Sin esto, MIUI y fabricantes similares matarán el servicio de vigilancia y dejarás de recibir avisos con la pantalla apagada.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMid
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
                StepIcon(Icons.Default.Chat)
                Text("WhatsApp vía CallMeBot (opcional)", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Sigue las instrucciones de callmebot.com/blog/free-api-whatsapp-messages para obtener tu apiKey. Puedes saltar este paso y configurarlo después en Ajustes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMid
                )
                PhoneField(value = formState.whatsappPhone, onValueChange = viewModel::onWhatsappPhoneChange)
                ApiKeyField(
                    value = formState.callMeBotApiKey,
                    onValueChange = viewModel::onApiKeyChange,
                    label = "API key de CallMeBot",
                    modifier = Modifier.padding(top = 4.dp)
                )
                Button(onClick = { step = 3 }, modifier = Modifier.padding(top = 4.dp)) { Text("Continuar") }
            }

            3 -> {
                StepIcon(Icons.Default.CheckCircle)
                Text("Todo listo", style = MaterialTheme.typography.titleLarge)
                Text(
                    "El servicio de vigilancia va a iniciar ahora en segundo plano.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMid
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

@Composable
private fun StepIcon(icon: ImageVector) {
    val colors = LocalFelicityColors.current
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(colors.tealDim, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(32.dp))
    }
}
