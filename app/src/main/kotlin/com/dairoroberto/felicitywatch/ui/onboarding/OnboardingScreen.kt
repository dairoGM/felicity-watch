package com.dairoroberto.felicitywatch.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dairoroberto.felicitywatch.service.MonitoringServiceController
import com.dairoroberto.felicitywatch.ui.components.ApiKeyField
import com.dairoroberto.felicitywatch.ui.components.EmailField
import com.dairoroberto.felicitywatch.ui.components.PasswordField
import com.dairoroberto.felicitywatch.ui.components.PhoneField
import com.dairoroberto.felicitywatch.ui.theme.LocalFelicityColors
import com.dairoroberto.felicitywatch.ui.theme.SpaceGroteskFamily

private const val TOTAL_STEPS = 4

private data class StepMeta(val icon: ImageVector, val title: String, val optional: Boolean)

private val STEP_META = listOf(
    StepMeta(Icons.Default.Login, "Conecta tu cuenta FSolar", optional = false),
    StepMeta(Icons.Default.BatterySaver, "Optimización de batería", optional = true),
    StepMeta(Icons.Default.Chat, "WhatsApp vía CallMeBot", optional = true),
    StepMeta(Icons.Default.CheckCircle, "Todo listo", optional = true)
)

/** Onboarding obligatorio de primer arranque (guía sección 8.5). Único paso
 * que bloquea el avance: credenciales de FSolar (paso 1) — sin ellas la app
 * no puede leer nada. El resto son mejoras opcionales que el usuario puede
 * saltar y configurar después en Ajustes. */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val formState by viewModel.formState.collectAsState()
    var step by remember { mutableIntStateOf(0) }
    val colors = LocalFelicityColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "FELICITY WATCH",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = SpaceGroteskFamily,
                fontWeight = FontWeight.SemiBold,
                color = colors.textLow,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            StepDotsIndicator(currentStep = step, totalSteps = TOTAL_STEPS)

            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface2),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val forward = targetState > initialState
                        (slideInHorizontally(tween(280)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(280)))
                            .togetherWith(slideOutHorizontally(tween(280)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(280)))
                    },
                    label = "onboarding-step"
                ) { animatedStep ->
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val meta = STEP_META[animatedStep]
                        StepIcon(meta.icon)

                        Row(
                            modifier = Modifier.padding(top = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                meta.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = SpaceGroteskFamily,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }

                        if (meta.optional) {
                            OptionalBadge(modifier = Modifier.padding(top = 8.dp))
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        when (animatedStep) {
                            0 -> LoginStepContent(
                                username = formState.username,
                                password = formState.password,
                                onUsernameChange = viewModel::onUsernameChange,
                                onPasswordChange = viewModel::onPasswordChange,
                                canProceed = viewModel.canProceedFromLogin(),
                                onContinue = { step = 1 }
                            )

                            1 -> BatteryOptimizationStepContent(
                                onExclude = {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                },
                                onContinue = { step = 2 }
                            )

                            2 -> WhatsappStepContent(
                                phone = formState.whatsappPhone,
                                apiKey = formState.callMeBotApiKey,
                                onPhoneChange = viewModel::onWhatsappPhoneChange,
                                onApiKeyChange = viewModel::onApiKeyChange,
                                onContinue = { step = 3 }
                            )

                            3 -> FinishStepContent(
                                onFinish = {
                                    viewModel.saveCredentials()
                                    MonitoringServiceController.start(context)
                                    onFinished()
                                }
                            )
                        }
                    }
                }
            }

            Text(
                "Paso ${step + 1} de $TOTAL_STEPS",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textLow,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
private fun LoginStepContent(
    username: String,
    password: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    canProceed: Boolean,
    onContinue: () -> Unit
) {
    val colors = LocalFelicityColors.current
    Text(
        "Usa el mismo correo y contraseña de la app FSolar para que Felicity Watch pueda leer tu inversor y batería.",
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textMid,
        textAlign = TextAlign.Center
    )
    EmailField(
        value = username,
        onValueChange = onUsernameChange,
        modifier = Modifier.padding(top = 20.dp)
    )
    PasswordField(
        value = password,
        onValueChange = onPasswordChange,
        modifier = Modifier.padding(top = 12.dp)
    )
    Button(
        onClick = onContinue,
        enabled = canProceed,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .height(52.dp)
    ) { Text("Continuar", style = MaterialTheme.typography.titleSmall) }
}

@Composable
private fun BatteryOptimizationStepContent(
    onExclude: () -> Unit,
    onContinue: () -> Unit
) {
    val colors = LocalFelicityColors.current
    Text(
        "Sin esto, MIUI y fabricantes similares matarán el servicio de vigilancia y dejarás de recibir avisos con la pantalla apagada.",
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textMid,
        textAlign = TextAlign.Center
    )
    Button(
        onClick = onExclude,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .height(52.dp)
    ) { Text("Excluir de optimización", style = MaterialTheme.typography.titleSmall) }
    OutlinedButton(
        onClick = onContinue,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textMid),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .height(52.dp)
    ) { Text("Saltar por ahora") }
}

@Composable
private fun WhatsappStepContent(
    phone: String,
    apiKey: String,
    onPhoneChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onContinue: () -> Unit
) {
    val colors = LocalFelicityColors.current
    Text(
        "Sigue las instrucciones de callmebot.com/blog/free-api-whatsapp-messages para obtener tu apiKey. Puedes saltar este paso y configurarlo después en Ajustes.",
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textMid,
        textAlign = TextAlign.Center
    )
    PhoneField(value = phone, onValueChange = onPhoneChange, modifier = Modifier.padding(top = 20.dp))
    ApiKeyField(
        value = apiKey,
        onValueChange = onApiKeyChange,
        label = "API key de CallMeBot",
        modifier = Modifier.padding(top = 12.dp)
    )
    Button(
        onClick = onContinue,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .height(52.dp)
    ) { Text("Continuar", style = MaterialTheme.typography.titleSmall) }
}

@Composable
private fun FinishStepContent(onFinish: () -> Unit) {
    val colors = LocalFelicityColors.current
    Text(
        "El servicio de vigilancia va a iniciar ahora en segundo plano y traerá la primera lectura automáticamente.",
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textMid,
        textAlign = TextAlign.Center
    )
    Button(
        onClick = onFinish,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .height(52.dp)
    ) { Text("Empezar a vigilar", style = MaterialTheme.typography.titleSmall) }
}

@Composable
private fun OptionalBadge(modifier: Modifier = Modifier) {
    val colors = LocalFelicityColors.current
    Box(
        modifier = modifier
            .background(colors.tealDim, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            "OPCIONAL · PUEDES SALTARLO",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.accent
        )
    }
}

@Composable
private fun StepDotsIndicator(currentStep: Int, totalSteps: Int) {
    val colors = LocalFelicityColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val active = index == currentStep
            val done = index < currentStep
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(if (active) 28.dp else 6.dp)
                    .background(
                        color = if (active || done) colors.accent else colors.hairline,
                        shape = RoundedCornerShape(50)
                    )
            )
        }
    }
}

@Composable
private fun StepIcon(icon: ImageVector) {
    val colors = LocalFelicityColors.current
    Box(
        modifier = Modifier
            .size(72.dp)
            .background(colors.tealDim, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(34.dp))
    }
}
