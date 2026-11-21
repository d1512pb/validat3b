# Plan 4 — Envío de capturas al servidor

Nov 21, 2026 · @richi\_mc

## 1. Resumen y orquestación

Al confirmar cada captura, la app llama a **`POST https://validat3b-production.up.railway.app/v1/capturas`** con el JSON completo y las 3 fotos. El servidor solo responde que la recibió, así que **cualquier respuesta 2xx cuenta como recibida** y la app no depende del formato del cuerpo. Si no hay red o el servidor falla, la captura queda pendiente y se reintenta. Tiempo estimado: **1 h**.

| Ola | Quién | Tarea | Paquete | Tiempo |
| --- | --- | --- | --- | --- |
| 0 | Orquestador | URL base, flag de imágenes y contrato `CapturaRepository` | `app`, Gradle | 10 min |
| 1 | W12 (Sonnet) | Cliente Retrofit, DTO, mapeo y llamada | `core/network` | 25 min |
| 1 | W13 (Sonnet) | Estados de envío en Room, cola de reintentos y chip en la confirmación | `core/data`, `feature/capture` (confirmación) | 25 min |
| 2 | Orquestador | Conectar, probar contra el servidor real y con el modo avión | `app` | 15 min |

**Decisión por confirmar con backend:** el plan manda `multipart/form-data` (parte `datos` con el JSON + partes `fachada`, `producto`, `etiqueta`), que es el contrato del Plan 1. Si el servidor solo acepta JSON, un flag (`ENVIAR_IMAGENES = false`) cambia a `application/json` sin imágenes, sin tocar el resto del código.

## 2. Fase 0 — Configuración (orquestador)

**1. URL base y flag en `app/build.gradle.kts`**

```kotlin
android {
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "API_BASE_URL", "\"https://validat3b-production.up.railway.app/\"")
        buildConfigField("boolean", "ENVIAR_IMAGENES", "true")
    }
}
```

La URL base termina en `/` (Retrofit lo exige) y la ruta va en la interfaz: `v1/capturas`.

**2. Manifest:** el permiso `INTERNET` ya está desde el Plan 1. Como la URL es `https`, no hace falta `usesCleartextTraffic`.

**3. Dependencias:** Retrofit, el convertidor de kotlinx.serialization y `logging-interceptor` ya están desde la Fase 0 del Plan 1. No hay nada nuevo que instalar.

**4. Contrato** (si no quedó así en el Plan 1):

```kotlin
enum class EstadoEnvio { PENDIENTE, ENVIANDO, RECIBIDA, ERROR }

interface CapturaRepository {
    suspend fun guardar(c: Captura)
    suspend fun enviar(captureId: String): Result<Unit>
    suspend fun enviarPendientes(): Int            // cuántas se recibieron
    fun estado(captureId: String): Flow<EstadoEnvio>
    fun pendientes(): Flow<Int>
}
```

**Terminado cuando:** compila con `BuildConfig.API_BASE_URL` disponible y los fakes del repositorio siguen funcionando.

## 3. Fase 1 — Cliente y llamada (W12)

**1. Cliente Retrofit** (`core/network/ApiClient.kt`)

```kotlin
object ApiClient {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)      // subida de 3 fotos
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS
                    else HttpLoggingInterceptor.Level.NONE
        })
        .build()

    val api: CapturasApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(http)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(CapturasApi::class.java)

    fun encode(dto: CapturaDto): String = json.encodeToString(dto)
}
```

El log va en `HEADERS` y no en `BODY` para no imprimir las fotos completas en Logcat.

**2. Interfaz**

```kotlin
interface CapturasApi {
    @Multipart
    @POST("v1/capturas")
    suspend fun enviarMultipart(
        @Header("X-Capture-Id") captureId: String,
        @Part("datos") datos: RequestBody,
        @Part fachada: MultipartBody.Part,
        @Part producto: MultipartBody.Part,
        @Part etiqueta: MultipartBody.Part
    ): Response<ResponseBody>

    @POST("v1/capturas")
    suspend fun enviarJson(
        @Header("X-Capture-Id") captureId: String,
        @Body datos: CapturaDto
    ): Response<ResponseBody>
}
```

- La respuesta es `Response<ResponseBody>` para aceptar lo que mande el servidor ("recibido" en texto o en JSON). Solo importa `isSuccessful`; el cuerpo se guarda en el log.
- `X-Capture-Id` sirve para que backend ignore duplicados si un reintento llega dos veces.
- Partes de imagen: `MultipartBody.Part.createFormData("fachada", "fachada.jpg", file.asRequestBody("image/jpeg".toMediaType()))`; la parte `datos` va como `ApiClient.encode(dto).toRequestBody("application/json".toMediaType())`.

**3. DTO del JSON completo** (`@Serializable`, con `@SerialName` en snake\_case). Junta los campos de los planes 1 a 3:

```json
{
  "capture_id": "CAP-3B-20261121-0007",
  "cadena": "TRES_B",
  "tienda": "3B Lindavista",
  "fecha_hora": "2026-11-21T11:32:05-06:00",
  "codigo_barras": {"valor": "7501055300075", "formato": "EAN_13", "checksum_ok": true, "fuente": "PRODUCTO"},
  "texto_leido": {"producto": "...", "etiqueta": "...", "confianza_producto": 0.88, "confianza_etiqueta": 0.91},
  "ubicacion_imagen_sucursal": {"lat": 19.5012, "lon": -99.1301, "precision_m": 12.0, "tomada_en": "2026-11-21T11:05:40-06:00", "archivo": "fachada"},
  "producto_esperado": {
    "descripcion": "LECHE ENTERA 1 L", "precio": 24.90, "editado_por_usuario": true,
    "descripcion_detectada": "LECHE ENTERA 1L", "precio_detectado": 24.90,
    "tipo_precio": "REGULAR", "fuente_descripcion": "ETIQUETA_RACK", "confianza_sugerencia": 0.8,
    "confianza_precio": 0.82,
    "precio_candidatos": [{"valor": 24.90, "forma": "CENTAVOS_MISMA_LINEA", "puntuacion": 0.82}]
  },
  "etiquetas_imagen": {
    "fachada": [{"etiqueta": "Building", "confianza": 0.88}],
    "producto": [{"etiqueta": "Bottle", "confianza": 0.91}],
    "etiqueta": [{"etiqueta": "Text", "confianza": 0.83}]
  }
}
```

**4. Mapeo:** una función `Captura.toDto(sesion: Sesion): CapturaDto` en `core/network`. Las fechas van en ISO 8601 con zona (`OffsetDateTime.now().toString()` o `DateTimeFormatter.ISO_OFFSET_DATE_TIME`).

**Terminado cuando:** compila y una función demo envía una captura de prueba al servidor real y registra en Logcat el código HTTP y el cuerpo de la respuesta.

## 4. Fase 2 — Envío al terminar, estados y reintentos (W13)

**Cuándo se llama:** en cuanto el usuario toca **Confirmar captura**:

1. Se guarda la captura en Room con estado `PENDIENTE`.
2. Se navega de inmediato a la confirmación; el usuario **no espera** a la red para seguir con el siguiente producto.
3. El envío se lanza en un scope de aplicación (`applicationScope` en `AppContainer`, con `SupervisorJob() + Dispatchers.IO`), no en el `viewModelScope`, para que no se cancele al salir de la pantalla.
4. Estado `ENVIANDO` → llamada → nuevo estado según la respuesta.

**Cómo se interpreta la respuesta**

| Resultado | Estado | Qué se hace |
| --- | --- | --- |
| HTTP 2xx (cualquier cuerpo) | `RECIBIDA` | Guardar hora de recepción y el cuerpo en el log |
| Sin red, timeout, `IOException` | `PENDIENTE` | Se reintenta después |
| HTTP 5xx o 429 | `PENDIENTE` | Se reintenta después |
| HTTP 4xx (excepto 429) | `ERROR` | Guardar código y cuerpo; no se reintenta solo (es un problema del contrato) |

**Reintentos** (sencillos, sin WorkManager para ahorrar tiempo):

- Al abrir la app y al volver a la pantalla de inicio: `enviarPendientes()`.
- Botón **"Enviar pendientes (N)"** en el inicio cuando N > 0.
- Después de cada envío exitoso, intentar también los pendientes anteriores (aprovecha que ya hay red).
- Un `Mutex` en el repositorio evita que dos envíos de la misma captura corran a la vez.
- Extra si sobra tiempo: `WorkManager` con restricción `NetworkType.CONNECTED`.

**Room:** la tabla `capturas` agrega `estadoEnvio`, `intentos`, `ultimoError` y `recibidaEn`. Las rutas de las 3 fotos ya están guardadas; no se borran hasta que la captura esté `RECIBIDA`.

**En la pantalla de confirmación (Glass Rojo)** un chip de estado que observa `estado(captureId)`:

| Estado | Chip | Color |
| --- | --- | --- |
| `ENVIANDO` | Ícono de subida + "Enviando…" | `info` |
| `RECIBIDA` | Palomita + "Recibido por el servidor" | `success` |
| `PENDIENTE` | Nube tachada + "Sin conexión, se enviará después" | `warning` |
| `ERROR` | Alerta + "El servidor rechazó la captura (código 400)" + botón de reintentar | `danger` |

En el inicio, la tarjeta de sesión muestra el contador de pendientes con el mismo chip `warning`.

**Terminado cuando:** al confirmar una captura con red, el chip pasa de "Enviando" a "Recibido"; en modo avión queda "Sin conexión" y, al quitarlo y volver al inicio, se envía sola.

## 5. Fase 3 — Prueba, integración y riesgos (orquestador)

**1. Probar el endpoint antes de integrar** (desde tu computadora, 2 minutos). Así sabes si acepta multipart antes de que W12 termine:

```bash
# Multipart: datos + 3 fotos (lo que manda la app por defecto)
curl -i -X POST https://validat3b-production.up.railway.app/v1/capturas \
  -H "X-Capture-Id: CAP-TEST-0001" \
  -F "datos=@captura.json;type=application/json" \
  -F "fachada=@fachada.jpg;type=image/jpeg" \
  -F "producto=@producto.jpg;type=image/jpeg" \
  -F "etiqueta=@etiqueta.jpg;type=image/jpeg"

# Solo JSON (si el anterior responde 4xx)
curl -i -X POST https://validat3b-production.up.railway.app/v1/capturas \
  -H "Content-Type: application/json" \
  -H "X-Capture-Id: CAP-TEST-0002" \
  --data @captura.json
```

`captura.json` es el ejemplo de la Fase 1. Si solo responde bien el segundo, pon `ENVIAR_IMAGENES = false` en Gradle.

**2. Integración**

1. Registrar el repositorio real y `applicationScope` en `AppContainer` en lugar de los fakes.
2. Llamar a `guardar()` + `enviar()` desde el botón Confirmar captura.
3. Llamar a `enviarPendientes()` al abrir la app y en el inicio.

**3. Prueba manual**

- [ ] Captura con red: chip "Recibido por el servidor" y en Logcat el 2xx con el cuerpo.
- [ ] Pedir a backend que confirme que llegó con `capture_id`, código de barras, texto leído y ubicación de la fachada.
- [ ] Modo avión: la captura queda "Sin conexión" y el contador de pendientes sube.
- [ ] Quitar modo avión y volver al inicio: se envía sola y el contador baja.
- [ ] Dos productos seguidos sin esperar: los dos llegan y no se duplican.

**Riesgos**

- **Formato del envío:** si backend espera otros nombres de campos o solo JSON, la prueba con curl lo descubre antes de integrar; el flag evita reescribir código.
- **Primera petición lenta:** si el servicio de Railway tarda en arrancar o está configurado para dormir, la primera llamada puede tardar. El timeout de lectura de 30 s lo cubre y, si falla, queda pendiente.
- **Tamaño de la subida:** 3 fotos a \~1280 px y JPEG q80 son \~0.6–1 MB por captura. Con datos móviles débiles puede tardar; por eso el envío no bloquea al usuario.
- **Duplicados por reintento:** si una respuesta se pierde después de que el servidor la procesó, la app reintenta. `X-Capture-Id` permite que backend lo ignore; avísales.
- **Demo sin internet en el lugar del pitch:** la cola de pendientes es parte de la historia ("funciona en tienda sin señal"), así que muéstrala a propósito.
