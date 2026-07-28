# Felicity Watch — Guía de implementación (Android)

> Este documento es una especificación técnica completa para implementar la app "Felicity Watch" desde cero. Está escrito para que otro asistente de IA (o un desarrollador humano) pueda ejecutar la implementación sin ambigüedad. Contiene decisiones de arquitectura ya tomadas, contratos de datos, y el comportamiento exacto esperado — no es un brief abierto para reinterpretar, es una especificación a seguir.

---

## 0. Resumen del proyecto

**Qué es:** una app nativa de Android que monitorea en tiempo real un sistema fotovoltaico Felicity Solar (inversor + batería) a través de la nube de Felicity ("Shine"), y avisa al usuario por voz, notificación push y WhatsApp cuando cambia el estado de la red eléctrica (corte / restablecimiento) u otros eventos de la batería.

**Por qué existe:** el usuario vive en una zona con cortes de electricidad frecuentes e impredecibles. Necesita enterarse de inmediato cuando se va o vuelve la luz, sin depender de abrir la app manualmente.

**Contexto importante:** este proyecto es la reimplementación nativa de un prototipo que ya se construyó y validó manualmente usando Home Assistant + una integración comunitaria de terceros (`felicityAPI` de GitHub, usuario `slauf82`) que se conecta a la API en la nube de Felicity usando el correo y contraseña de la cuenta FSolar del usuario. Ese prototipo demostró que:
- El login con usuario/contraseña de FSolar funciona y da acceso a los datos del inversor y la batería.
- Los datos se actualizan aproximadamente cada 30 segundos desde el lado de Felicity.
- El sensor de potencia de red ("Grid Power") no baja a `0` limpio cuando se corta la luz — pasa a un estado **no disponible / null**, y cuando hay corriente reporta valores bajos (ej. 2–3 W), no una medición real de consumo.
- El valor fluctúa brevemente (varios segundos) antes de estabilizarse tras un cambio real, por lo que se requiere un debounce de tiempo, no solo un umbral.

---

## 1. Stack técnico obligatorio

| Capa | Tecnología |
|---|---|
| Lenguaje | Kotlin (100%, sin Java) |
| UI | Jetpack Compose + Material 3 |
| Arquitectura | MVVM + Repository pattern |
| Inyección de dependencias | Hilt |
| Red / HTTP | Retrofit2 + OkHttp3 (con logging interceptor en debug) |
| Persistencia local | Room (historial de eventos, reglas de alerta, credenciales cifradas) |
| Background execution | `Foreground Service` (tipo `dataSync`) + `WorkManager` como respaldo/reintento |
| Notificaciones | `NotificationManagerCompat`, canal dedicado con prioridad alta |
| Texto a voz | `android.speech.tts.TextToSpeech` (motor nativo de Android, en español) |
| Preferencias / config | DataStore (Preferences), no SharedPreferences crudo |
| Credenciales sensibles | `EncryptedSharedPreferences` (Jetpack Security) — nunca texto plano |
| Min SDK | 26 (Android 8.0) — mínimo requerido para canales de notificación y foreground services modernos |
| Target SDK | el más reciente estable disponible al momento de compilar |

No usar Flutter, React Native, ni ningún framework híbrido. La razón: la app depende críticamente de foreground services confiables y TTS nativo, que son más predecibles en Kotlin puro.

---

## 2. La API de Felicity — estado del conocimiento y cómo proceder

**Esto es crítico y debe leerse antes de escribir una sola línea de networking:**

No se dispone de documentación oficial ni de las URLs/endpoints exactos de la API de Felicity Shine capturados directamente. Lo que se sabe con certeza:

- Existe una integración de Home Assistant open-source, `felicityAPI` (GitHub: `slauf82/felicityAPI`), que **ya implementa correctamente** el login y la lectura de datos de inversor y batería Felicity usando correo/contraseña, y fue probada funcionando en producción con un inversor real (modelo IVGM8KLP2G1) y una batería (modelo FLB48314TG1 / serie 0745...).
- También existe `matheustavarestrindade/felicity_solar_hacs`, otra integración similar, pero con un bug conocido: no reconoce correctamente el `device type` de algunos modelos de inversor (falla con "Unknown device type 'None'"). **No usar esta como referencia principal** — usar `felicityAPI` de slauf82.

**Instrucción obligatoria para quien implemente:**

1. Clonar o descargar el código fuente de `https://github.com/slauf82/felicityAPI`.
2. Localizar dentro de ese repositorio el cliente de API en Python (probablemente en un archivo tipo `api.py`, `client.py` o similar dentro de `custom_components/felicity_api/` o `felicity_solar_api/`).
3. **Portar esa lógica de autenticación y consulta de datos a Kotlin**, replicando exactamente:
   - El endpoint de login (URL, método, payload, headers).
   - Cómo se obtiene y renueva el token/sesión.
   - El endpoint para listar dispositivos asociados a la cuenta.
   - El endpoint para obtener las lecturas en tiempo real de un dispositivo (inversor y batería), y el nombre exacto de los campos relevantes (potencia de red, SOC de batería, voltaje, etc.).
4. Si el repositorio no es accesible o el código no es suficientemente claro, la alternativa documentada es interceptar el tráfico de la app oficial "FSolar" (Android) usando un proxy MITM (ej. `mitmproxy` o `HTTP Toolkit`) mientras se usa la app normalmente, para capturar las llamadas reales.
5. No inventar ni asumir endpoints o nombres de campos sin verificarlos contra el código fuente real de `felicityAPI` o una captura de tráfico real. Esta es la parte del proyecto con mayor riesgo de fallo silencioso si se hace por adivinanza.

**Comportamiento de datos ya observado y que el modelo de datos debe soportar explícitamente:**
- El campo de potencia de red puede llegar como número (ej. `0`, `2`, `50`) **o como ausente / null / "unavailable"**. El modelo Kotlin debe representar esto como `Int?` (nullable), nunca asumir que siempre es un entero.
- Intervalo de polling recomendado: 30 segundos (igual al de la integración de referencia). No bajar de ese intervalo sin confirmar que la API de Felicity lo tolera (riesgo de rate-limiting).
- Un fallo temporal de la API (timeout, 401, 5xx) no debe crashear el servicio ni marcar al usuario como desconectado de forma permanente — debe reintentar con backoff exponencial (ver sección 6).

---

## 3. Modelo de datos (Room + dominio)

### 3.1 Entidades de dominio (Kotlin data classes)

```kotlin
data class InverterReading(
    val timestamp: Instant,
    val serialNumber: String,
    val gridPowerWatts: Int?,      // null = no disponible / unavailable
    val pvPowerWatts: Int?,
    val loadPowerWatts: Int?
)

data class BatteryReading(
    val timestamp: Instant,
    val serialNumber: String,
    val socPercent: Int?,          // 0..100, null si no disponible
    val voltage: Double?,
    val current: Double?,
    val healthPercent: Int?
)

enum class GridState { ONLINE, OFFLINE, UNKNOWN }
```

### 3.2 Reglas de alerta (persistidas en Room)

```kotlin
@Entity(tableName = "alert_rules")
data class AlertRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: AlertRuleType,           // enum, ver abajo
    val enabled: Boolean,
    val thresholdValue: Double?,       // null si no aplica (ej. GRID_OFFLINE no necesita umbral configurable)
    val comparisonOperator: ComparisonOperator?, // GTE, LTE — solo para reglas de SOC
    val debounceSeconds: Int,          // default 60, ver sección 5
    val channelVoiceEnabled: Boolean,
    val channelPushEnabled: Boolean,
    val channelWhatsappEnabled: Boolean,
    val messageTemplate: String        // texto a usar en los 3 canales
)

enum class AlertRuleType { GRID_OFFLINE, GRID_ONLINE, BATTERY_SOC_LOW, BATTERY_SOC_HIGH }
enum class ComparisonOperator { GTE, LTE }
```

**Valores por defecto que debe crear la app en el primer arranque (seed data), calcados del prototipo ya validado:**

| type | enabled | threshold | operator | debounceSeconds | mensaje |
|---|---|---|---|---|---|
| GRID_OFFLINE | true | null | null | 60 | "Se ha perdido la corriente eléctrica de la calle" |
| GRID_ONLINE | true | null | null | 60 | "Ha vuelto la corriente eléctrica de la calle" |
| BATTERY_SOC_LOW | true | 20 | LTE | 60 | "La batería está baja" |
| BATTERY_SOC_HIGH | false | 100 | GTE | 60 | "La batería está llena" |

### 3.3 Historial de eventos (para la pantalla "Historial")

```kotlin
@Entity(tableName = "alert_events")
data class AlertEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleType: AlertRuleType,
    val triggeredAt: Instant,
    val message: String,
    val voiceSent: Boolean,
    val pushSent: Boolean,
    val whatsappSent: Boolean,
    val whatsappError: String?   // guardar el error si falló, para diagnóstico
)
```

---

## 4. Arquitectura de capas

```
app/
 ├─ data/
 │   ├─ remote/
 │   │   ├─ FelicityApiService.kt      (interfaz Retrofit)
 │   │   ├─ FelicityAuthInterceptor.kt (maneja token/sesión)
 │   │   └─ dto/                       (data classes que mapean el JSON real de la API)
 │   ├─ local/
 │   │   ├─ AppDatabase.kt             (Room)
 │   │   ├─ AlertRuleDao.kt
 │   │   └─ AlertEventDao.kt
 │   └─ repository/
 │       ├─ FelicityRepository.kt      (login, polling, expone Flow<InverterReading>)
 │       └─ AlertRuleRepository.kt
 ├─ domain/
 │   ├─ model/                          (data classes de la sección 3.1)
 │   └─ usecase/
 │       ├─ EvaluateAlertRulesUseCase.kt  (lógica de debounce y disparo, sección 5)
 │       └─ DispatchAlertUseCase.kt       (orquesta los 3 canales, sección 7)
 ├─ service/
 │   └─ MonitoringForegroundService.kt  (sección 6)
 ├─ notification/
 │   ├─ VoiceAlertPlayer.kt             (TTS)
 │   ├─ PushNotifier.kt
 │   └─ WhatsappAlertSender.kt          (CallMeBot, sección 7.3)
 └─ ui/
     ├─ dashboard/  (pantalla "Panel general" del mockup)
     ├─ alerts/     (pantalla "Alertas" del mockup)
     ├─ history/
     └─ settings/   (credenciales FSolar, número de WhatsApp, API key de CallMeBot)
```

---

## 5. Motor de evaluación de reglas (lógica de debounce)

Esta es la parte más delicada de portar correctamente, porque replica un comportamiento ya afinado a mano en el prototipo (ver contexto de fluctuaciones del sensor en la sección 2).

**Requisito funcional exacto:**

Para las reglas `GRID_OFFLINE` y `GRID_ONLINE`, el disparo **no debe ocurrir en el instante en que cambia el valor**, sino solo si el nuevo estado se mantiene estable durante `debounceSeconds` (default 60s) segundos consecutivos, sin volver al estado anterior en ese lapso.

**Pseudocódigo de referencia (implementar como una máquina de estados, no como un simple `if`):**

```kotlin
class GridStateDebouncer(private val debounceSeconds: Int) {
    private var candidateState: GridState? = null
    private var candidateSince: Instant? = null
    private var confirmedState: GridState = GridState.UNKNOWN

    // Se llama en cada lectura nueva (cada ~30s, según el polling)
    fun onNewReading(gridPowerWatts: Int?, now: Instant): GridState? {
        val observed = if (gridPowerWatts == null || gridPowerWatts < 1) GridState.OFFLINE else GridState.ONLINE

        if (observed != candidateState) {
            // el estado cambió respecto al candidato anterior: reiniciar el conteo
            candidateState = observed
            candidateSince = now
            return null // aún no confirmar
        }

        // el estado se mantiene igual al candidato: revisar si ya pasó el debounce
        val elapsed = Duration.between(candidateSince, now).seconds
        if (elapsed >= debounceSeconds && observed != confirmedState) {
            confirmedState = observed
            return observed // ESTE es el momento de disparar la alerta correspondiente
        }
        return null
    }
}
```

**Reglas de SOC de batería (`BATTERY_SOC_LOW`, `BATTERY_SOC_HIGH`):** también deben aplicar el mismo patrón de debounce por tiempo antes de disparar, para evitar avisos repetidos si el valor oscila justo alrededor del umbral (ej. 19% / 21% / 19%).

**Requisito anti-duplicados adicional:** una vez disparada una alerta para una regla, esa regla no debe volver a dispararse hasta que el estado confirmado cambie a algo distinto — es decir, no repetir el mismo aviso en cada ciclo de polling mientras la condición se mantenga. Esto ya está cubierto por la variable `confirmedState` del pseudocódigo, pero debe verificarse explícitamente con un test unitario (ver sección 9).

---

## 6. Servicio en segundo plano (Foreground Service)

**Por qué es obligatorio y no un `WorkManager` periódico simple:** Android mata procesos en segundo plano agresivamente (particularmente en fabricantes como Xiaomi/MIUI, confirmado como el entorno real del usuario). Un `Foreground Service` con notificación persistente es la única forma confiable de garantizar polling continuo cada 30s.

**Especificación:**

- Tipo de servicio: `android:foregroundServiceType="dataSync"` en el manifest.
- Debe mostrar una notificación persistente, de baja prioridad, no descartable por el usuario mientras el servicio esté activo, con texto tipo: `"Vigilando el inversor · última lectura hace X s"` (tal como se ve en el mockup).
- Debe arrancar automáticamente al iniciar el teléfono (`BOOT_COMPLETED` receiver) si el usuario ya configuró sus credenciales.
- Debe solicitar explícitamente al usuario, en el onboarding, que **excluya la app de la optimización de batería** (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) — sin esto, MIUI y fabricantes similares matarán el servicio igualmente. Mostrar una pantalla explicativa antes de pedir el permiso, no pedirlo a ciegas.
- Ciclo interno: cada 30s, llama a `FelicityRepository`, obtiene la lectura más reciente, la pasa a `EvaluateAlertRulesUseCase`, y si hay un disparo, lo pasa a `DispatchAlertUseCase`.
- Manejo de errores de red: si una consulta falla, no detener el servicio — loguear el error, mantener el último estado conocido, y reintentar en el siguiente ciclo. Si fallan **5 ciclos consecutivos** (~2.5 minutos sin datos), actualizar la notificación persistente para reflejarlo (ej. "Sin conexión con Felicity desde hace 3 min") para que el usuario lo note.

---

## 7. Los tres canales de aviso

### 7.1 Voz (TTS nativo)

- Usar `TextToSpeech` de Android, configurado en idioma español (`Locale("es", "ES")` o `Locale("es")`, verificar cuál da mejor pronunciación en pruebas reales).
- Reproducir el `messageTemplate` de la regla disparada.
- Debe funcionar con la pantalla apagada y la app en segundo plano (por eso corre dentro del Foreground Service, no en una Activity).

### 7.2 Notificación push local

- Canal de notificación dedicado (`NotificationChannel`) con `IMPORTANCE_HIGH`, sonido y vibración activados, para que se muestre incluso en modo "No molestar" solo si el usuario lo permite explícitamente (no forzar bypass de DND sin consentimiento).
- Título y cuerpo = el mismo `messageTemplate`.
- Como la app corre en el mismo dispositivo que debe recibir el aviso, **no se requiere Firebase Cloud Messaging** — es una notificación local disparada directamente por el propio Foreground Service. Esto es más simple y más confiable que el enfoque usado en el prototipo de escritorio (que sí necesitaba FCM porque el servidor y el receptor eran dispositivos distintos).

### 7.3 WhatsApp (vía CallMeBot)

Ya validado y funcionando en el prototipo. Implementar como una llamada HTTP GET simple:

```kotlin
interface CallMeBotApi {
    @GET("whatsapp.php")
    suspend fun sendMessage(
        @Query("phone") phone: String,       // ej. "5355848425", sin "+"
        @Query("text") text: String,         // el mensaje, Retrofit lo URL-encodea automáticamente
        @Query("apikey") apiKey: String
    ): Response<ResponseBody>
}
```

Base URL: `https://api.callmebot.com/`

**Notas importantes descubiertas durante la validación manual:**
- El número de teléfono y el API key deben ser configurables por el usuario en la pantalla de Ajustes (no hardcodeados) — cada usuario de la app necesita activar su propio bot de CallMeBot siguiendo el proceso descrito en `https://www.callmebot.com/blog/free-api-whatsapp-messages/` (agregar un contacto, enviarle "I allow callmebot to send me messages", recibir el apiKey).
- El servicio es gratuito pero puede tardar hasta varios minutos en entregar el mensaje en horas pico — **no tratar un envío lento como un error**, solo loguear si la respuesta HTTP no fue 200.
- Guardar el resultado (éxito/error) en `AlertEventEntity.whatsappError` para que el usuario pueda diagnosticar fallos desde la pantalla de Historial, sin tener que revisar logs.

### 7.4 Orquestación (`DispatchAlertUseCase`)

Debe ejecutar los 3 canales **en paralelo** (no secuencial), cada uno en su propia corrutina con manejo de error aislado — si WhatsApp falla, eso no debe impedir que la voz o el push se disparen. Registrar el resultado de cada uno en `AlertEventEntity`.

---

## 8. Pantallas (UI)

Implementar siguiendo exactamente la identidad visual ya diseñada y aprobada (referencia: mockups HTML entregados previamente, tema oscuro con acento teal `#3ECAC0` y naranja Felicity `#F2622E`, tipografía tipo `Space Grotesk` para títulos y monoespaciada para valores numéricos). Compose Material 3 con un `ColorScheme` custom, no Material You dinámico por defecto.

### 8.1 Pantalla "Panel general" (Dashboard)
- Card hero con estado de red actual (punto de color + texto + hace cuánto cambió).
- Card de inversor: PV, red, carga.
- Card de batería: gauge visual de SOC, voltaje, salud.
- Lista de "Canales de aviso" mostrando el último disparo de cada canal con hora.

### 8.2 Pantalla "Alertas"
- Lista de `AlertRuleEntity`, una tarjeta por regla, con toggle de activado/desactivado, campo editable de umbral (donde aplique), selector de los 3 canales (V/P/W), y campo de texto editable para el mensaje.
- Todo editable in-place, sin pantalla de edición separada (igual que el mockup).

### 8.3 Pantalla "Historial"
- Lista cronológica de `AlertEventEntity`, mostrando qué regla se disparó, cuándo, y qué canales tuvieron éxito/fallo (iconografía simple: check verde / X roja por canal).

### 8.4 Pantalla "Ajustes"
- Login con correo/contraseña de FSolar (guardado en `EncryptedSharedPreferences`).
- Número de WhatsApp y API key de CallMeBot.
- Botón para solicitar exclusión de optimización de batería, con el estado actual visible (concedido / no concedido).
- Indicador de estado del Foreground Service (activo / detenido) con botón para reiniciarlo manualmente si algo falla.

### 8.5 Onboarding (primera vez que se abre la app)
Flujo obligatorio antes de llegar al Dashboard:
1. Login FSolar.
2. Explicación + solicitud de exclusión de batería.
3. Explicación + activación de WhatsApp vía CallMeBot (opcional, se puede saltar y configurar después).
4. Confirmación de que el servicio de vigilancia está corriendo.

---

## 9. Criterios de aceptación (para validar que la implementación es correcta)

1. **Test unitario del debouncer** (sección 5): simular una secuencia de lecturas que incluya al menos 3 fluctuaciones cortas (menos de `debounceSeconds`) seguidas de un cambio real y sostenido — el resultado esperado es **cero** disparos falsos y **exactamente uno** disparo real.
2. **Test unitario de no-repetición:** simular 10 lecturas consecutivas todas en estado `OFFLINE` tras la confirmación — debe dispararse la alerta una sola vez, no diez.
3. **Prueba manual de resiliencia:** forzar que el teléfono esté 30+ minutos con la pantalla apagada y la app en segundo plano; confirmar que el servicio sigue vivo (revisar notificación persistente) y que sigue actualizando datos.
4. **Prueba manual de reinicio:** reiniciar el teléfono con la app ya configurada; confirmar que el servicio arranca solo sin abrir la app manualmente.
5. **Prueba manual de canal aislado:** desconectar el WiFi del teléfono brevemente (para simular fallo de red) durante un ciclo de polling, y confirmar que el servicio se recupera solo en el siguiente ciclo sin necesidad de reabrir la app.
6. **Prueba manual end-to-end:** con datos reales de un inversor Felicity conectado, provocar (o simular editando el estado esperado en un entorno de prueba) un corte y restablecimiento real, y confirmar que llegan las 3 notificaciones (voz, push, WhatsApp) una sola vez cada una, dentro de un margen de ~90 segundos desde el cambio real (30s de polling + 60s de debounce).

---

## 10. Fuera de alcance para la versión 1 (no implementar todavía)

- Soporte multi-inversor / multi-sitio en una misma cuenta (el usuario actual tiene un solo sistema).
- Sincronización en la nube de las reglas de alerta entre varios dispositivos.
- Llamadas telefónicas reales (se evaluó Twilio en el prototipo pero se descartó por costo/complejidad para v1).
- Panel web / versión de escritorio (ya existe un prototipo funcional aparte basado en Home Assistant, no es parte de este proyecto).
- Soporte para otras marcas de inversor que no sean Felicity.

---

## 11. Nombre del paquete y branding sugeridos

- Nombre visible: **Felicity Watch**
- Package id sugerido: `com.dairoroberto.felicitywatch` (ajustar al dominio/identidad real que el usuario prefiera)
- Ícono: basado en el badge naranja "F" usado en los mockups, sobre fondo oscuro.
