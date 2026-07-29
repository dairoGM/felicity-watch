# Felicity Watch

App Android nativa que monitorea en tiempo real un sistema fotovoltaico **Felicity Solar** (inversor + batería) a través de la nube "Shine" de Felicity, y avisa al usuario por **voz**, **notificación push** y **WhatsApp** cuando cambia el estado de la red eléctrica (corte / restablecimiento) u otros eventos de la batería.

Nace porque el usuario vive en una zona con cortes de electricidad frecuentes e impredecibles y necesita enterarse de inmediato cuando se va o vuelve la luz, sin depender de abrir la app manualmente.

---

## Tabla de contenidos

- [Stack técnico](#stack-técnico)
- [Arquitectura](#arquitectura)
- [Conexión con Felicity — detalle técnico completo](#conexión-con-felicity--detalle-técnico-completo)
- [Modelo de datos](#modelo-de-datos)
- [Motor de reglas de alerta (debounce)](#motor-de-reglas-de-alerta-debounce)
- [Los 3 canales de aviso](#los-3-canales-de-aviso)
- [Servicio en segundo plano](#servicio-en-segundo-plano)
- [Pantallas](#pantallas)
- [Cómo compilar](#cómo-compilar)
- [Tests](#tests)
- [Problemas conocidos / fuera de alcance](#problemas-conocidos--fuera-de-alcance)

---

## Stack técnico

| Capa | Tecnología |
|---|---|
| Lenguaje | Kotlin 100% |
| UI | Jetpack Compose + Material 3 (tema propio, claro/oscuro) |
| Arquitectura | MVVM + Repository |
| Inyección de dependencias | Hilt |
| Red / HTTP | Retrofit2 + OkHttp3 (logging interceptor en debug) + Gson |
| Persistencia | Room (reglas de alerta, historial de eventos) |
| Background | Foreground Service (`dataSync`) + WorkManager como respaldo |
| Notificaciones | `NotificationManagerCompat`, canal dedicado de alta prioridad |
| Texto a voz | `android.speech.tts.TextToSpeech` nativo, en español |
| Preferencias | DataStore (Preferences) |
| Credenciales | `EncryptedSharedPreferences` (Jetpack Security) — nunca texto plano |
| Min SDK | 26 (Android 8.0) |
| Target/Compile SDK | 36 |

No usa Flutter ni React Native: la app depende de foreground services confiables y TTS nativo, más predecibles en Kotlin puro.

---

## Arquitectura

```
app/src/main/kotlin/com/dairoroberto/felicitywatch/
 ├─ data/
 │   ├─ remote/            (FelicityApiService, FelicityApiClient, RsaPasswordEncryptor, dto/)
 │   ├─ local/              (Room: AppDatabase, DAOs, entidades; CredentialsStore; AppPreferences)
 │   └─ repository/         (FelicityRepository, AlertRuleRepository, AlertEventRepository)
 ├─ domain/
 │   ├─ model/              (InverterReading, BatteryReading, GridState, DeviceInfo, AlertRule...)
 │   └─ usecase/            (GridStateDebouncer, SocThresholdDebouncer, EvaluateAlertRulesUseCase,
 │                            DispatchAlertUseCase, RunMonitoringCycleUseCase)
 ├─ service/                (MonitoringForegroundService, BootCompletedReceiver, ServiceWatchdogWorker,
 │                            MonitoringStateHolder — puente en memoria servicio→UI)
 ├─ notification/           (VoiceAlertPlayer, PushNotifier, WhatsappAlertSender, CallMeBotApi)
 ├─ di/                     (NetworkModule, DatabaseModule — módulos Hilt)
 └─ ui/
     ├─ dashboard/          (Panel general)
     ├─ alerts/             (Alertas — reglas editables)
     ├─ devices/            (Equipos — inversor/batería con serial)
     ├─ history/            (Historial de eventos disparados)
     ├─ settings/           (Ajustes: credenciales, canales, tema, sesión)
     ├─ onboarding/         (Wizard de primer arranque)
     └─ theme/              (paleta clara/oscura + colores "semánticos")
```

`RunMonitoringCycleUseCase` centraliza **un ciclo completo de lectura** (fetch → evaluar reglas → despachar alertas → actualizar estado en memoria) y lo comparten tanto el `MonitoringForegroundService` (cada 30s) como las acciones manuales — pull-to-refresh en el Panel y "Probar conexión" en Ajustes — para no duplicar lógica.

---

## Conexión con Felicity — detalle técnico completo

Esta es la parte más delicada del proyecto: **no existe documentación oficial** de la API de Felicity Shine. Todo lo de aquí abajo fue portado desde la integración open-source de Home Assistant [`slauf82/felicityAPI`](https://github.com/slauf82/felicityAPI) y **verificado en producción**, en vivo, contra el servidor real (`https://shine-api.felicitysolar.com`) usando una cuenta real, comparando además contra una instancia de Home Assistant local que sí funcionaba correctamente. Varias cosas que la referencia documenta como "la forma que funciona" **no coincidían con lo que en verdad acepta el servidor para esta cuenta**, y quedaron documentadas explícitamente en el código.

### 1. Base URL y sesión

- Base URL: `https://shine-api.felicitysolar.com`
- Un único `OkHttpClient` (vía Hilt, `NetworkModule`), con `HttpLoggingInterceptor` solo en builds debug.
- Timeout de conexión/lectura/escritura: 15s.
- A diferencia de la referencia Python (que desactiva la validación del certificado TLS con `check_hostname=False, verify_mode=CERT_NONE`), **aquí se usa validación TLS estándar** — no se relajó por razones de seguridad, y el servidor responde igualmente bien con validación normal.

### 2. Login — cifrado de la contraseña

La contraseña **nunca viaja en texto plano**. Antes de cada intento de login:

1. Se toma una clave pública RSA embebida en la app, en formato **X.509 SubjectPublicKeyInfo** codificada en base64.
2. Se cifra la contraseña con **RSA + padding PKCS#1 v1.5** (no OAEP) — `Cipher.getInstance("RSA/ECB/PKCS1Padding")`.
3. El resultado binario se codifica en base64 y ese string es el que se envía como `"password"` en el JSON del login.

Implementado en [`RsaPasswordEncryptor.kt`](app/src/main/kotlin/com/dairoroberto/felicitywatch/data/remote/RsaPasswordEncryptor.kt). Se verificó independientemente (con OpenSSL y con `jshell`) que Java parsea la clave embebida con **exactamente** el mismo módulo RSA de 512 bits y exponente `65537` que un parseo de referencia — el cifrado en sí no era la causa de ningún bug encontrado.

### 3. Login — las 16 variantes reales

**Este es el hallazgo más importante del proyecto.** La nube de Felicity ha cambiado de forma entre versiones/cuentas, y no hay UNA sola combinación de endpoint/clave/payload que funcione siempre. El cliente (`FelicityApiClient.login()`) prueba, en orden, hasta **16 combinaciones**:

- **2 endpoints:** `/openApi/sec/login` (principal) y `/userlogin` (respaldo)
- **2 claves públicas RSA:** una "principal" y una "de respaldo" (distintas, ambas embebidas en la app)
- **4 estilos de payload:**

  | Estilo | Campo de usuario | Campos extra |
  |---|---|---|
  | `legacy_userName` | `userName` | `"source": "WEB", "lang": "de_DE"` |
  | `modern_userName` | `userName` | `"version": "1.0"` |
  | `legacy_account` | `account` | `"source": "WEB", "lang": "de_DE"` |
  | `modern_account` | `account` | `"version": "1.0"` |

El primer intento (endpoint principal + clave principal + `legacy_userName`) es el que la documentación original marca como "conocido-funcional" — pero **en pruebas reales contra una cuenta real, esa combinación devolvía `"Wrong password"` incluso con la contraseña correcta**. La combinación que sí funcionó para esa cuenta fue: endpoint `/userlogin` + clave de respaldo + estilo `modern_userName`.

Por eso el body del login **no es un data class fijo**: se construye dinámicamente como `Map<String, String>` (ver `FelicityApiClient.buildLoginPayload()`), porque la forma del JSON cambia según el estilo.

Headers de login (no autenticado):
```
Content-Type: application/json
Origin: https://shine.felicitysolar.com
Referer: https://shine.felicitysolar.com/
```

Respuesta esperada:
```json
{ "code": 200, "message": "Success", "data": { "token": "...", "...": "..." } }
```

**Detalle no obvio:** el campo `"data"` de la respuesta **a veces es un objeto** `{"token": "..."}` y **a veces es directamente el token como string plano** (confirmado en producción — forzarlo como objeto tumbaba el parseo con `Expected BEGIN_OBJECT but was STRING`). `LoginResponse.data` se deserializa como `JsonElement` crudo y `extractToken()` soporta ambas formas.

Si el token recibido no empieza con `"Bearer_"`, se le antepone ese prefijo antes de guardarlo, y se usa así en el header `Authorization` de todas las llamadas autenticadas.

### 4. Headers de llamadas autenticadas

```
Authorization: Bearer_<token>
Content-Type: application/json
lang: de_DE
source: WEB
```

### 5. Renovación automática de sesión

Cualquier llamada autenticada pasa por `FelicityApiClient.requestWithRetry()`:

1. Asegura que haya un token (si no hay, hace login primero).
2. Ejecuta la llamada.
3. Si la respuesta no es exitosa, o el `code` del body es `401`, `403` o `998` (o el body es nulo), se descarta el token, se vuelve a hacer login completo (las 16 variantes de nuevo), y se reintenta **esa misma llamada una sola vez** — para no entrar en bucle infinito.

### 6. Listar dispositivos — otro hallazgo real

```
POST /device/list_device_all_type
Authorization: Bearer_<token>

{ "pageNum": 1, "pageSize": 100 }
```

La referencia Python documenta este endpoint como un simple `GET` sin body. **En producción, un `GET` responde `HTTP 405 Method Not Allowed`.** El endpoint real requiere `POST` con paginación (`pageNum`/`pageSize`); sin esos campos responde `code: 2002006, "message": "pageSize: no puede estar vacío, pageNum: no puede estar vacío"`. Verificado y corregido en [`FelicityApiService.kt`](app/src/main/kotlin/com/dairoroberto/felicitywatch/data/remote/FelicityApiService.kt).

Respuesta:
```json
{ "code": 200, "data": { "dataList": [
  { "deviceSn": "120308004826040053", "deviceType": "OC", "deviceModel": "IVGM8KLP2G1", "alias": "...", "status": "NM", "plantName": "..." },
  { "deviceSn": "074504831426110541", "deviceType": "BP", "deviceModel": "FLA48314-EU", "...": "..." }
]}}
```

`deviceType` identifica el rol: `"OC"` = inversor (Off-Grid/On-Grid Controller), `"BP"` = batería (Battery Pack). Igual que en el login, la respuesta de error puede traer `"data"` como string en vez de objeto — `DeviceListResponse.data` también es `JsonElement` crudo por la misma razón.

### 7. Snapshot en tiempo real — la llamada del polling de 30s

```
POST /device/get_device_snapshot
Authorization: Bearer_<token>

{ "deviceSn": "<serial>", "dateStr": "2026-07-28 18:45:00" }
```

`dateStr` se regenera con la hora actual en cada llamada (formato `yyyy-MM-dd HH:mm:ss`). Este endpoint **sí funciona exactamente como lo documenta la referencia** — no necesitó corrección, y se verificó en vivo trayendo decenas de campos reales (voltajes, corrientes, potencias, energía acumulada, etc.) por cada dispositivo.

### 8. El patrón "primero no-nulo" (`_first()`)

La nube de Felicity ha renombrado campos entre versiones de firmware. En vez de leer un único nombre de campo fijo, se prueba una lista ordenada de candidatos y se toma el primero que no sea `null`, `""`, `"unknown"`, `"unavailable"` ni `"null"` (implementado en [`FelicitySnapshotMapper.kt`](app/src/main/kotlin/com/dairoroberto/felicitywatch/data/remote/FelicitySnapshotMapper.kt)):

| Valor | Candidatos en orden |
|---|---|
| Potencia de red | `acTtlInPower` → `acTtlInpower` → `totalAcTtlInPower` → `ctPower` → `ctAcTtlInPower` |
| Generación PV | `pvTotalPower` → `pvPower` → `pv1Power` |
| Consumo/carga | `totalConsumPower` → `ctPower` → `meterPower` |
| SOC de batería | `emsSoc` → `battSoc` |
| Voltaje de batería | `emsVoltage` → `battVolt` |
| Corriente de batería | `emsCurrent` → `battCurr` |
| Salud de batería (SOH) | `battSoh` → `emsSoh` |

**Comportamiento observado y que el modelo de datos soporta explícitamente:** el campo de potencia de red puede llegar como número (`0`, `2`, `50`) o como ausente/`null`/`"unavailable"` — nunca reporta un `0` "limpio" cuando se corta la luz. Por eso el modelo de dominio usa `Int?` (nunca asume que siempre hay un entero) y el motor de alertas usa debounce por tiempo, no un umbral instantáneo (ver más abajo).

### 9. Aislamiento de fallos

Cada snapshot (inversor, batería) se pide y mapea de forma independiente: si uno falla, el otro sigue funcionando. Un fallo de red completo (timeout, DNS, 5xx) no tumba la app ni marca al usuario como desconectado para siempre — se reintenta en el siguiente ciclo de 30s, y tras 5 fallos consecutivos la notificación persistente del servicio lo refleja ("Sin conexión con Felicity desde hace X min").

---

## Modelo de datos

```kotlin
data class InverterReading(
    val timestamp: Instant,
    val serialNumber: String,
    val gridPowerWatts: Int?,   // null = no disponible
    val pvPowerWatts: Int?,
    val loadPowerWatts: Int?
)

data class BatteryReading(
    val timestamp: Instant,
    val serialNumber: String,
    val socPercent: Int?,
    val voltage: Double?,
    val current: Double?,
    val healthPercent: Int?
)

enum class GridState { ONLINE, OFFLINE, UNKNOWN }
```

Reglas de alerta (Room, editables desde la pantalla "Alertas"): tipo, habilitada, umbral (W para red, % para batería), tiempo de espera (debounce, segundos), canales activos (voz/push/WhatsApp) y plantilla de mensaje. Valores por defecto sembrados en el primer arranque: corte de red, volvió la red, batería baja (≤20%) y batería llena (≥100%, deshabilitada por defecto).

---

## Motor de reglas de alerta (debounce)

El sensor de potencia de red fluctúa varios segundos antes de estabilizarse tras un cambio real. Disparar la alerta en el instante exacto del cambio produciría falsas alarmas. `GridStateDebouncer` (y su equivalente para SOC, `SocThresholdDebouncer`) implementan una máquina de estados:

- Un nuevo valor observado se convierte en "candidato"; si el candidato cambia antes de cumplir el tiempo de espera, se reinicia el conteo (no dispara nada).
- Solo si el candidato se mantiene estable durante `debounceSeconds` consecutivos (60s por defecto) se "confirma" y dispara la alerta correspondiente.
- Anti-duplicados: una vez confirmado un estado, no se repite el aviso hasta que el estado confirmado cambie a otro distinto.

Cubierto por tests unitarios ([`GridStateDebouncerTest`](app/src/test/kotlin/com/dairoroberto/felicitywatch/domain/usecase/GridStateDebouncerTest.kt), [`SocThresholdDebouncerTest`](app/src/test/kotlin/com/dairoroberto/felicitywatch/domain/usecase/SocThresholdDebouncerTest.kt)) que simulan fluctuaciones cortas seguidas de un cambio real, y 10 lecturas repetidas en el mismo estado confirmado.

---

## Los 3 canales de aviso

Se despachan **en paralelo** (`DispatchAlertUseCase`, corrutinas independientes) — un fallo en un canal no bloquea a los otros. El resultado de cada uno queda registrado en el historial.

- **Voz:** `TextToSpeech` nativo de Android en español. Corre dentro del Foreground Service, funciona con la pantalla apagada.
- **Push:** notificación local (`NotificationManagerCompat`, canal de prioridad alta). No usa Firebase Cloud Messaging — el propio dispositivo genera y muestra la notificación.
- **WhatsApp:** vía [CallMeBot](https://www.callmebot.com/blog/free-api-whatsapp-messages/) — una llamada `GET` simple a `https://api.callmebot.com/whatsapp.php` con número, mensaje y apiKey configurados por el usuario en Ajustes. Puede tardar minutos en horas pico; eso **no** se trata como error, solo se loguea si el HTTP no fue 200.

Cada canal tiene un botón de prueba independiente en Ajustes.

---

## Servicio en segundo plano

`MonitoringForegroundService` (tipo `dataSync`) hace polling cada 30 segundos y muestra una notificación persistente ("Vigilando el inversor · última lectura hace Xs"). Arranca automáticamente al iniciar el teléfono si ya hay credenciales guardadas (`BootCompletedReceiver`), y `ServiceWatchdogWorker` (WorkManager, cada 15 min) lo relanza si el sistema operativo lo mató — necesario en fabricantes como Xiaomi/MIUI que matan procesos en segundo plano de forma agresiva. El onboarding guía al usuario para excluir la app de la optimización de batería.

---

## Pantallas

| Pantalla | Contenido |
|---|---|
| **Panel** | Estado de red en vivo, generación PV, % de batería, estado de conexión con Felicity (con el error específico si falla), estado de cada canal de aviso. Pull-to-refresh fuerza una lectura inmediata. |
| **Alertas** | Las 4 reglas, con umbral, tiempo de espera, canales y mensaje editables in-place. |
| **Equipos** | Inversor y batería vinculados a la cuenta, con número de serie, modelo, alias y estado. |
| **Historial** | Lista cronológica de alertas disparadas, con éxito/fallo por canal. |
| **Ajustes** | Credenciales FSolar, configuración de WhatsApp/CallMeBot, prueba de cada canal + de la conexión, exclusión de batería, estado del servicio, tema claro/oscuro, cerrar sesión / restablecer de fábrica. |
| **Onboarding** | Wizard de primer arranque: login → exclusión de batería → WhatsApp (opcional) → confirmación. |

---

## Cómo compilar

Requiere Android SDK (compileSdk/targetSdk 36, minSdk 26) y JDK 17+.

```bash
# Windows
set JAVA_HOME=<ruta a tu JDK>
set ANDROID_HOME=<ruta a tu Android SDK>
gradlew.bat assembleDebug

# El APK queda en:
# app/build/outputs/apk/debug/app-debug.apk
```

Configura `local.properties` con `sdk.dir=<ruta a tu Android SDK>`.

---

## Tests

```bash
gradlew.bat testDebugUnitTest
```

Cubre el motor de debounce (fluctuaciones cortas sin disparo falso, cambio real sostenido dispara exactamente una vez, no-repetición) y la oscilación de SOC alrededor del umbral.

---

## Problemas conocidos / fuera de alcance

- Sin soporte multi-inversor / multi-planta en una misma cuenta.
- Sin sincronización en la nube de las reglas de alerta entre dispositivos.
- Sin llamadas telefónicas reales (se evaluó Twilio, descartado por costo/complejidad para v1).
- Sin soporte para otras marcas de inversor que no sean Felicity.
- Los nombres de campo y la combinación de login verificada en este README corresponden a **una cuenta real específica** — Felicity puede tener variaciones adicionales por firmware/región no cubiertas; el cliente ya tolera varias, pero si aparece un error nuevo, el mensaje de error de la app incluye el código y el contenido crudo de la respuesta para poder diagnosticarlo.
