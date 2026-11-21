# Plan — App móvil Validat3B (captura con OCR on-device)

Nov 21, 2026 · @richi\_mc

## 1. Contexto y decisiones clave

**Lo que pide el reto (Validat3B):** cada captura trae **3 fotos** (fachada de la tienda, producto, etiqueta de precio), código de barras y metadatos, ligados por `capture_id`. El motor del equipo (en servidor, obligatoriamente con el endpoint de Azure AI Foundry asignado) decide `aprobada` / `rechazada` / `revision_manual` con `confidence_score`, `reason_code` y `explicacion`.

**Frentes de rechazo y qué puede atacar la app desde el origen:**

| Frente | Peso | Qué hace la app |
| --- | --- | --- |
| A. Identidad del producto | \~61% | Lee código de barras on-device, valida checksum EAN, extrae texto del producto con OCR y lo manda al servidor para cruzarlo con el catálogo |
| B. Calidad de la evidencia | \~29% | Bloquea fotos borrosas, oscuras o demasiado lejanas antes de guardarlas; foto de fachada obligatoria por sucursal |
| C. Evidencia de precio | \~10% | Exige que el OCR encuentre un precio en la etiqueta; detecta precio unitario y tipo de precio (regular / oferta / volumen) |

**Decisiones que tomo en este plan (cámbialas si tu equipo decide otra cosa):**

1. **Las 3 imágenes** = fachada (1 vez por sucursal, se reutiliza en cada producto de esa sesión) + foto del producto con código de barras + foto de la etiqueta en el rack.
2. **La app no decide la aprobación final.** Hace *pre-validación* (rechaza en campo lo que es claramente malo y pide retomar) y manda al servidor el código de barras, el texto leído por OCR y la ubicación de la foto de la sucursal. Así el motor gasta menos tokens (Eficiencia = 10% de la calificación) y llegan menos capturas malas.
3. **Sí se mandan las imágenes** (comprimidas, \~1280 px, JPEG q80) junto con el JSON: el motor las necesita para validar fachada e identidad del producto. Si el OCR ya es concluyente, el servidor puede evitar la llamada de visión.
4. **Ojo con la calificación:** el set ciego lo procesa el motor, no la app. El valor de la app está en el **pitch (25%)** y en demostrar cómo se evita la basura desde el origen. Prioriza un flujo que se vea sólido en la demo sobre funciones extra.

**Stack:** Kotlin + Jetpack Compose, CameraX, ML Kit (Barcode Scanning + Text Recognition v2, ambos *bundled* para que funcionen sin red), Play Services Location, Room, Retrofit + kotlinx.serialization, Navigation Compose. Sin Hilt para ahorrar tiempo: inyección manual con un `AppContainer`.

## 2. Cómo aplicar el patrón orquestador

**Roles**

- **Orquestador** (tu sesión principal de Claude Code, o tú): es dueño de `PLAN.md`, de los **contratos** (modelos e interfaces) y de la navegación. No implementa features: reparte tareas, revisa diffs, integra y compila.
- **Workers** (subagentes): cada uno recibe una tarea aislada en **su propio paquete**, implementa una interfaz ya definida y no toca archivos de otros workers.

**Modelo de los workers: Sonnet.** Todos los subagentes que escriben código (W1 a W5) corren con **Sonnet**: es más rápido y gasta menos uso, lo que permite lanzar varios en paralelo. El orquestador se queda en el modelo de tu sesión principal, porque es quien planea, revisa diffs e integra.

La forma más segura de fijarlo es definir el worker como agente del proyecto en `.claude/agents/worker-android.md`, para que cada lanzamiento use Sonnet sin depender de que el orquestador lo recuerde:

```markdown
---
name: worker-android
description: Implementa una tarea aislada de la app Validat3B (Kotlin, Compose) dentro del paquete que se le asigna.
model: sonnet
tools: Read, Write, Edit, Bash, Glob, Grep
---

Eres un worker del proyecto Validat3B. Lee PLAN.md y core/model/ antes de empezar.
No modifiques core/model/, MainActivity, NavGraph ni archivos de Gradle.
Sigue el sistema Glass Rojo de la sección 4 de PLAN.md; no inventes colores ni medidas.
Termina solo cuando el proyecto compile (./gradlew assembleDebug) y haya un @Preview o pantalla demo.
Si necesitas cambiar un contrato, detente y descríbelo en tu respuesta.
```

En el `CLAUDE.md` del proyecto, una línea para el orquestador: *"Delega cada tarea de código al agente `worker-android` (Sonnet); no implementes features tú mismo."* Si prefieres no crear el archivo, el orquestador puede pasar el modelo al lanzar cada subagente (parámetro `model: "sonnet"`), pero es más fácil que se le olvide.

**Reglas para que el paralelismo no se rompa**

1. Los contratos se escriben **antes** de lanzar cualquier worker (Fase 0). Si un worker necesita cambiar un contrato, se lo pide al orquestador; no lo cambia él.
2. Un paquete = un dueño. Nada de editar `MainActivity`, `NavGraph` o `build.gradle` desde un worker.
3. Criterio de terminado de cada tarea: **compila** + hay un `@Preview` o una pantalla de demo que se puede probar a mano en el teléfono.
4. Integración al final de cada ola, no al final del día.

**Olas de trabajo**

| Ola | Workers en paralelo | Depende de |
| --- | --- | --- |
| 0 | Solo orquestador: proyecto, librerías, contratos | — |
| 1 | W1 Sesión y ubicación · W2 Componente de cámara · W3 Motor de análisis on-device | Ola 0 |
| 2 | W4 Flujo de producto y pantalla de revisión · W5 Persistencia y envío | Ola 1 |
| 3 | Orquestador: ciclo completo, pulido, APK de demo | Ola 2 |

**Plantilla de prompt para cada worker**

```text
[Lanzar con el agente worker-android, modelo Sonnet]
Eres un worker del proyecto Validat3B (Android, Kotlin, Compose).
Lee PLAN.md y core/model/ antes de empezar. NO modifiques core/model/,
MainActivity, NavGraph ni archivos de Gradle.

Tarea: <objetivo concreto>
Paquete que puedes tocar: <com.equipo.validat3b.feature.xxx>
Interfaz a implementar: <nombre de la interfaz en core/model>
Entradas/salidas: <tipos>
Terminado cuando: compila y existe <Preview/pantalla demo> que muestra <comportamiento>.
Si necesitas un cambio en un contrato, detente y descríbelo en tu respuesta.
```

## 3. Arquitectura y contratos

**Paquetes**

```text
com.equipo.validat3b
├── core/model        ← contratos (solo orquestador)
├── core/data         ← Room + repositorio (W5)
├── core/network      ← Retrofit + DTOs (W5)
├── feature/session   ← sucursal, ubicación, foto de fachada (W1)
├── feature/camera    ← composable de cámara reutilizable (W2)
├── feature/analysis  ← nitidez, brillo, OCR, código de barras, precio (W3)
├── feature/capture   ← flujo producto → etiqueta → revisión (W4)
└── app               ← MainActivity, NavGraph, AppContainer (orquestador)
```

**Contratos mínimos (`core/model`)**

```kotlin
enum class Cadena { TRES_B, SORIANA, BODEGA_AURRERA, OTRA }
enum class TipoFoto { FACHADA, PRODUCTO, ETIQUETA }
enum class TipoPrecio { REGULAR, OFERTA, VOLUMEN, DESCONOCIDO }

enum class Problema {
    BORROSA, OSCURA, SOBREEXPUESTA, DEMASIADO_LEJOS,
    SIN_TEXTO_LEGIBLE, SIN_CODIGO_BARRAS, CODIGO_INVALIDO,
    SIN_PRECIO, SIN_PRECIO_UNITARIO
}

data class Sesion(
    val id: String, val cadena: Cadena, val tienda: String,
    val lat: Double?, val lon: Double?, val precisionM: Float?,
    val fachadaPath: String, val inicioMillis: Long
)

data class AnalisisImagen(
    val tipo: TipoFoto, val path: String,
    val nitidez: Double,          // varianza del Laplaciano
    val brillo: Double,           // luminancia media 0..255
    val textoOcr: String,
    val confianzaOcr: Float?,     // promedio de elementos (Text Recognition v2)
    val codigoBarras: String?, val formatoCodigo: String?,
    val problemas: List<Problema>
) { val esValida get() = problemas.isEmpty() }

data class PrecioDetectado(
    val valor: Double?, val tipo: TipoPrecio,
    val tienePrecioUnitario: Boolean, val textoFuente: String
)

data class Captura(
    val captureId: String, val sesionId: String,
    val productoEsperado: String?,
    val producto: AnalisisImagen, val etiqueta: AnalisisImagen,
    val precio: PrecioDetectado, val timestampMillis: Long,
    val enviada: Boolean = false
)

interface ImageAnalyzer {
    suspend fun analizar(path: String, tipo: TipoFoto): AnalisisImagen
}
interface PriceParser { fun parse(textoOcr: String): PrecioDetectado }
interface CapturaRepository {
    suspend fun guardar(c: Captura)
    suspend fun pendientes(): List<Captura>
    suspend fun enviar(c: Captura): Result<Unit>
}
```

**JSON que la app manda al servidor.** La app define este contrato y el backend se adapta. Lleva tres bloques de datos: **código de barras**, **texto leído** (OCR) y **ubicación de la imagen de la sucursal** (dónde se tomó la foto de fachada). Los campos de identificación (`capture_id`, cadena, tienda, fecha) van porque el motor los necesita para ligar la captura y devolver su resultado.

```json
{
  "capture_id": "CAP-3B-20261121-0007",
  "cadena": "TRES_B",
  "tienda": "3B Lindavista",
  "fecha_hora": "2026-11-21T11:32:05-06:00",

  "codigo_barras": {
    "valor": "7501055300075",
    "formato": "EAN_13",
    "checksum_ok": true,
    "fuente": "PRODUCTO"
  },

  "texto_leido": {
    "producto": "LECHE ENTERA 1 L ...",
    "etiqueta": "LECHE ENTERA 1L $24.90 PRECIO POR LITRO $24.90 ...",
    "confianza_producto": 0.88,
    "confianza_etiqueta": 0.91
  },

  "ubicacion_imagen_sucursal": {
    "lat": 19.5012,
    "lon": -99.1301,
    "precision_m": 12.0,
    "tomada_en": "2026-11-21T11:05:40-06:00",
    "archivo": "fachada"
  }
}
```

- `codigo_barras.valor` es `null` si ML Kit no encontró código; `fuente` indica si se leyó en la foto del producto o en la etiqueta.
- `texto_leido` lleva el texto crudo de cada foto, sin interpretar. El precio y su tipo los puede sacar el servidor, o se agregan después como campos opcionales si el parser de la app resulta confiable.
- `ubicacion_imagen_sucursal` se toma con el GPS en el momento de la foto de fachada y se reutiliza en todas las capturas de esa sesión. `archivo` es el nombre de la parte del request donde viaja esa imagen.
- Las 3 imágenes viajan en el mismo request como `multipart/form-data` (partes `fachada`, `producto`, `etiqueta`) y el JSON va en una parte `datos`.

## 4. Diseño visual: sistema Glass Rojo

La app usa el sistema **Glass Rojo**: tema siempre oscuro, superficies de vidrio translúcido (glassmorphism) sobre manchas de color desenfocadas y **un solo acento rojo** para la acción principal. Esta sección resume solo lo que necesita Validat3B; los valores son los del documento de diseño y no se inventan otros.

**Principios**

1. **Siempre oscuro.** Fondo `bg` #020617 en todas las pantallas; no hay tema claro.
2. **El color va detrás del vidrio.** Cada pantalla lleva 2 o 3 manchas desenfocadas en las esquinas y, encima, tarjetas blancas translúcidas.
3. **Un solo acento.** El rojo marca la acción principal (iniciar, confirmar, disparar) y el progreso. Todo lo demás es blanco translúcido y grises slate.
4. **Formas redondas.** Tarjetas de radio 20–24; chips y botones circulares completamente redondos.

**Colores que usa la app**

| Token | Valor | Uso en Validat3B |
| --- | --- | --- |
| `bg` | #020617 | Fondo de toda pantalla |
| `bg-elevated` | #0F172A | Hojas y diálogos sin blur |
| `nav-fill` | rgba(15,23,42,0.55) | Barras fijas (encabezado de sesión, franjas sobre la cámara) |
| `glass-fill` | rgba(255,255,255,0.06) | Relleno de tarjetas, inputs y botones glass |
| `glass-fill-strong` | rgba(255,255,255,0.12) | Chip de cadena seleccionado, pista de la barra de progreso |
| `glass-fill-active` | rgba(255,255,255,0.16) | Opción activa del selector de cadena |
| `glass-border` | rgba(255,255,255,0.12) | Borde de 1 dp de toda superficie glass |
| `ink` | #F8FAFC | Texto principal y títulos |
| `ink-strong` | #E2E8F0 | Íconos y texto de botones glass |
| `ink-secondary` | #CBD5E1 | Etiquetas de campos y descripciones |
| `ink-muted` | #94A3B8 | Metadatos (hora, precisión GPS, `capture_id`) |
| `accent` | #DC2626 | Botón primario y disparador (rellenos con texto o ícono) |
| `accent-bright` | #EF4444 | Barra de progreso, marco guía de la cámara, glow (sin texto encima) |
| `accent-ink` | #F87171 | Texto rojo destacado sobre oscuro |
| `success` | #34D399 | Foto válida, captura enviada |
| `warning` | #FBBF24 | Falta precio unitario, GPS impreciso, pendiente de envío |
| `info` | #38BDF8 | Avisos neutros ("Sin conexión, se enviará después") |
| `danger` | #FCA5A5 | Foto rechazada (borrosa, sin código, sin precio) |
| `blob-1` / `blob-2` / `blob-3` | #EF4444 / #F43F5E / #F97316 | Manchas del fondo, opacidad 0.55 / 0.35 / 0.25–0.3 |

**Reglas obligatorias para esta app**

- Nunca texto sobre `accent-bright`; todo relleno con texto usa `accent` (#DC2626).
- Como el acento es rojo, **un rechazo nunca se comunica solo con color**: lleva ícono, texto que diga qué pasó y cómo resolverlo ("Foto borrosa: acércate y toca para enfocar") y el color `danger`. Lo mismo para `success` y `warning`.
- Sin emojis como íconos: Lucide o Material Symbols Rounded, trazo de 2 dp, 20–22 dp.
- Áreas táctiles de 44–48 dp mínimo y descripción accesible en botones de solo ícono.

**Tipografía (Inter, pesos 400–700)**

| Estilo | Tamaño / interlínea | Peso | Uso en la app |
| --- | --- | --- | --- |
| `display` | 30 / 36 | 700 | Título de pantalla ("Nueva captura") |
| `title` | 17 / 24 | 600 | Encabezados de sección y de tarjeta |
| `body-strong` | 15 / 22 | 600 | Texto de botones, nombre de tienda |
| `body` | 15 / 22 | 400 | Texto e inputs |
| `secondary` | 13 / 18 | 400 | Metadatos, texto leído por OCR |
| `label` | 12 / 16 | 500, MAYÚSCULAS, 0.08em | Cadena sobre el nombre de tienda, "PRODUCTO 4" |

**Espaciado y radios:** escala de 4 (4, 8, 12, 16, 20, 24, 32). Padding de tarjeta 16, margen lateral 20, margen superior 24. Radios: inputs 16, tarjetas 20, tarjeta principal 24, chips y botones circulares 9999.

**Superficie glass (receta única):** relleno `glass-fill` + borde 1 dp `glass-border` + brillo interno `inset 0 1px 0 rgba(255,255,255,0.08)` + desenfoque de fondo de 20. Sin blur (API < 31): relleno blanco al 10 % y nada más.

**Componentes que necesita Validat3B**

| Componente | Especificación | Dónde se usa |
| --- | --- | --- |
| `GlassBackground` | `bg` + mancha `blob-1` \~300 dp arriba-izquierda (0.55) + `blob-2` \~260 dp abajo-derecha (0.35), blur 90–100 | Envuelve todas las pantallas excepto la cámara |
| `GlassCard` | Receta glass, padding 16, radio 20 (principal 24); contenedor de ícono 44 dp, radio 14 | Sesión activa, tarjetas de las 3 fotos, resultado |
| Botón primario | Alto 52–56, radio 18, fondo `accent`, borde blanco 25 %, glow `0 8px 24px -6px rgba(239,68,68,0.6)`. Uno por pantalla | "Iniciar captura", "Confirmar captura", "Siguiente producto" |
| Botón glass | Mismas medidas, receta glass, texto `ink-strong` | "Retomar", "Cambiar de sucursal" |
| Botón circular | 44 dp, glass, ícono 20 dp | Flash, regresar, cerrar |
| FAB | 54 dp, fondo `accent`, glow | Disparador de la cámara |
| `SegmentedControl` | Contenedor glass redondo, padding 4; activa `glass-fill-active` + texto `ink` 600 | Selector de cadena: 3B / Soriana / Bodega / Otra |
| `TextField` | 52 dp, radio 16, glass, etiqueta 13 sp `ink-secondary`; foco con contorno 2 dp `accent-ink` al 70 % | Tienda, producto esperado |
| Chip de estado | Redondo, padding 4×10, 12 sp medium; texto del color, fondo al 14 %, borde al 30 % | "Válida", "Borrosa", "Sin precio", "GPS ±12 m" |
| `ProgressBar` | 8 dp, redonda, pista `glass-fill-strong`, relleno `accent-bright` | Paso 1/3, 2/3, 3/3 del flujo de captura |
| Diálogos y hojas | Glass radio 24, blur 24, sombra `0 20px 40px -12px rgba(0,0,0,0.6)`; sin blur usa `bg-elevated` | Confirmar cambio de sucursal, detalle del texto leído |

No aplican a esta app: barra de navegación flotante (el flujo es lineal), `ProgressRing`, switches, pickers y colores de categoría.

**Cómo se ve cada pantalla**

| Pantalla | Elementos clave |
| --- | --- |
| Inicio | `GlassBackground`, título `display` "Validat3B", `GlassCard` de sesión activa (label con la cadena, tienda en `title`), chip `warning` con pendientes de envío y botón primario "Iniciar captura" |
| Sesión | `SegmentedControl` de cadena, `TextField` de tienda, chip de GPS (`success` si ≤ 20 m, `warning` si es mayor) y botón primario "Tomar foto de fachada" |
| Cámara | Vista a pantalla completa, sin manchas. Franja superior en `nav-fill` con la instrucción y la `ProgressBar`, marco guía en `accent-bright`, FAB disparador abajo al centro y botón circular de flash |
| Revisión | Tres `GlassCard` (fachada, producto, etiqueta) con miniatura y chip de estado; debajo, código de barras y extracto del texto leído en `secondary`; botón glass "Retomar" y primario "Confirmar captura" (deshabilitado: opacidad 0.4, sin glow) |
| Confirmación | Ícono `success` en contenedor de 44 dp, `capture_id` en `ink-muted`, chip de estado de envío, primario "Siguiente producto" y glass "Cambiar de sucursal" |

**Implementación en Compose**

- Archivo `ui/theme/GlassRojo.kt` con el `object GlassRojo` de colores del documento de diseño, expuesto con `CompositionLocal` (`LocalGlassColors`, `LocalGlassTypography`) y un composable `GlassRojoTheme { }`. No usar el estilo por defecto de `MaterialTheme` (sin elevaciones tonales ni ripples de color).
- Blur real con **Haze** (`dev.chrisbanes.haze`): el `GlassBackground` es `hazeSource` y cada superficie glass aplica `hazeEffect` con radio 20 dp. En API < 31 se usa el relleno de respaldo al 10 %.
- **Sobre la cámara no hay blur:** `PreviewView` dibuja en una superficie que Haze no puede desenfocar, así que las franjas y botones encima usan `nav-fill` sólido.
- Inter se agrega como fuente descargable de Google Fonts (`ui-text-google-fonts`) o empaquetada en `res/font`; empaquetarla evita depender de la red en la demo.
- Para ahorrar tiempo en el hackathon, crear primero solo `GlassBackground`, `GlassCard`, los botones y el chip de estado (Fase 0); el resto lo hace cada worker cuando lo necesite, siguiendo esta tabla.

## 5. Fase 0 — Configuración del proyecto y librerías (0:00–0:45)

**Dueño:** orquestador. Nada en paralelo todavía.

**Tareas**

1. Proyecto nuevo *Empty Activity (Compose)*, `minSdk 26`, `targetSdk` el más reciente.
2. Agregar plugins: `kotlin-serialization` y `ksp` (para Room).
3. Dependencias en `libs.versions.toml` (versiones de referencia; acepta las que sugiera Android Studio):

```kotlin
// Cámara
implementation("androidx.camera:camera-camera2:1.4.+")
implementation("androidx.camera:camera-lifecycle:1.4.+")
implementation("androidx.camera:camera-view:1.4.+")
// ML Kit on-device (bundled: no descarga modelos en runtime)
implementation("com.google.mlkit:barcode-scanning:17.3.0")
implementation("com.google.mlkit:text-recognition:16.0.1")
// Ubicación
implementation("com.google.android.gms:play-services-location:21.3.0")
// Navegación y corrutinas
implementation("androidx.navigation:navigation-compose:2.8.+")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.+")
// Persistencia
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")
// Red
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.+")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
// Imágenes en UI
implementation("io.coil-kt:coil-compose:2.7.0")
// Diseño Glass Rojo: blur real (usa la estable compatible con tu Compose)
implementation("dev.chrisbanes.haze:haze:1.+")
// Inter como fuente descargable (o empáquetala en res/font)
implementation("androidx.compose.ui:ui-text-google-fonts")
```

4. `AndroidManifest.xml`: permisos `CAMERA`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `INTERNET`; `<uses-feature android:name="android.hardware.camera" android:required="true"/>`.
5. Crear ui/theme/GlassRojo.kt con los tokens de la sección 4 y los componentes base (GlassBackground, GlassCard, botones, chip de estado), los paquetes vacíos y escribir **todos los contratos** de la sección 3 en `core/model`.
6. `AppContainer` con implementaciones *fake* de cada interfaz (devuelven datos fijos) para que los workers puedan probar su pantalla sin depender de los demás.
7. `NavGraph` con rutas vacías: `inicio` → `sesion` → `producto` → `etiqueta` → `revision` → (`producto` | `inicio`).
8. Escribir `PLAN.md` en la raíz (este documento, exportado a Markdown) para que los subagentes lo lean.
9. Compilar y correr en **teléfono físico** (la cámara del emulador no sirve para probar OCR ni nitidez).

**Terminado cuando:** la app abre, navega entre pantallas vacías y compila con todas las dependencias.

## 6. Fase 1 — Inicio de sesión: sucursal y ubicación (0:45–1:45)

**Worker W1** — paquete `feature/session`.

**Pantalla de inicio**

- Botón grande **"Iniciar captura"** y, si hay capturas pendientes de envío, un contador.
- Si ya hay una sesión activa: botones **"Continuar en \<tienda>"** y **"Cambiar de sucursal"**.

**Pantalla de sesión**

1. Pide permiso de ubicación (`rememberLauncherForActivityResult` + `RequestMultiplePermissions`).
2. Obtiene ubicación con `FusedLocationProviderClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, …)` y muestra la precisión en metros.
3. Selector de cadena: chips **3B / Soriana / Bodega Aurrera / Otra**.
4. Campo de texto para el nombre de la tienda (con sugerencias fijas por cadena para la demo; un catálogo real de sucursales con coordenadas sería un extra).
5. **Foto de fachada obligatoria** usando el componente de W2 (mientras no esté listo, usa un botón que cargue una imagen de prueba).
6. Guarda la `Sesion` en memoria (un `SessionStore` simple con `StateFlow`) y navega a `producto`.

**Terminado cuando:** se puede elegir cadena y tienda, se ven coordenadas y precisión, y se guarda una sesión con la foto de fachada.

**En paralelo durante esta fase:**

- **W2 — componente de cámara** (`feature/camera`): `@Composable fun CameraCapture(tipo: TipoFoto, onFoto: (path: String) -> Unit)` con `PreviewView` + `ImageCapture`, flash on/off, guía visual en pantalla ("encuadra el código de barras" / "encuadra la etiqueta completa"), guarda en `filesDir/capturas/<uuid>.jpg`. Tap para enfocar con `cameraControl.startFocusAndMetering`.
- **W3 — motor de análisis** (`feature/analysis`): implementa `ImageAnalyzer` y `PriceParser` (detalle en la Fase 3). Arranca desde ya porque es la parte con más lógica.

## 7. Fase 2 — Captura por producto: 2 fotos (1:45–3:00)

**Worker W4** — paquete `feature/capture`. Usa el componente de W2.

**Flujo**

1. Encabezado fijo con la sesión activa: cadena, tienda y número de producto ("Producto 4 · 3B Lindavista").
2. Campo opcional **"Producto esperado"** (texto libre o lista corta de la demo). El reto lo incluye como insumo, así que conviene capturarlo.
3. **Foto 1 — producto:** guía "El código de barras debe verse completo". Al tomarla, se analiza de inmediato (Fase 3) y, si falla, se pide retomar en ese momento.
4. **Foto 2 — etiqueta de precio en el rack:** guía "Que se lean el precio y la descripción". Mismo análisis inmediato.
5. Generar `capture_id` local: `CAP-<cadena>-<yyyyMMdd>-<consecutivo>`.
6. Las imágenes se reescalan a \~1280 px de lado mayor y se comprimen a JPEG q80 antes de guardar (menos peso de envío y análisis más rápido).

**Terminado cuando:** desde la sesión se toman las dos fotos en orden, se ven en miniatura y se pueden retomar individualmente.

## 8. Fase 3 — Validación on-device de las imágenes (3:00–4:15)

**Worker W3** implementa; **W4** integra los resultados en la pantalla de revisión.

**Pipeline por imagen** (todo en `Dispatchers.Default`):

| Paso | Cómo | Problema que marca |
| --- | --- | --- |
| 1. Nitidez | Escala a gris, reduce a 512 px de ancho, aplica kernel Laplaciano 3×3 y calcula la varianza | `BORROSA` si varianza < umbral |
| 2. Brillo | Luminancia media (0.299R + 0.587G + 0.114B) | `OSCURA` < 40, `SOBREEXPUESTA` > 220 |
| 3. Código de barras | ML Kit `BarcodeScanning` con formatos EAN-13, EAN-8, UPC-A, UPC-E | `SIN_CODIGO_BARRAS` (solo foto de producto) |
| 4. Checksum | Validar dígito verificador EAN/UPC | `CODIGO_INVALIDO` |
| 5. Distancia | Área del bounding box del código respecto a la imagen | `DEMASIADO_LEJOS` si < \~2% |
| 6. OCR | ML Kit `TextRecognition` (Latin); guardar texto completo y confianza promedio por elemento | `SIN_TEXTO_LEGIBLE` si hay pocos caracteres o confianza baja |
| 7. Precio | `PriceParser` sobre el OCR de la etiqueta | `SIN_PRECIO`, `SIN_PRECIO_UNITARIO` |

**Varianza del Laplaciano en Kotlin puro (sin OpenCV):**

```kotlin
fun varianzaLaplaciano(bmp: Bitmap): Double {
    val w = 512; val h = (bmp.height * w.toFloat() / bmp.width).toInt()
    val s = Bitmap.createScaledBitmap(bmp, w, h, true)
    val px = IntArray(w * h).also { s.getPixels(it, 0, w, 0, 0, w, h) }
    val g = DoubleArray(px.size) { i ->
        val c = px[i]
        0.299 * (c shr 16 and 0xFF) + 0.587 * (c shr 8 and 0xFF) + 0.114 * (c and 0xFF)
    }
    var sum = 0.0; var sumSq = 0.0; var n = 0
    for (y in 1 until h - 1) for (x in 1 until w - 1) {
        val i = y * w + x
        val lap = g[i - w] + g[i + w] + g[i - 1] + g[i + 1] - 4 * g[i]
        sum += lap; sumSq += lap * lap; n++
    }
    val mean = sum / n
    return sumSq / n - mean * mean
}
```

**Calibración de umbrales (importante, \~20 min):** corre la función sobre las imágenes del set de desarrollo (`Validat3B_desarrollo_imagenes.zip`) y compara con las que en los metadatos aparecen rechazadas por "imagen borrosa". Un punto de partida razonable es 80–120 a 512 px, pero el valor real sale de los datos. Deja los umbrales en un `object Umbrales` para ajustarlos rápido.

**Parser de precio (reglas sobre el texto OCR):**

- Precio: regex tipo `\$?\s?(\d{1,4})[.,](\d{2})`; también el caso de centavos en superíndice que el OCR separa (`$ 24 90`). Si hay varios, el de mayor tamaño de bloque suele ser el precio principal.
- Precio unitario: buscar `PRECIO UNITARIO`, `POR KG`, `/KG`, `POR LT`, `/L`, `C/U`, `POR 100 G`.
- Tipo de precio: `OFERTA`, `AHORRA`, `PRECIO ESPECIAL`, `ANTES` → `OFERTA`; patrones `2X`, `3 X $`, `LLEVA … PAGA` → `VOLUMEN`; si no, `REGULAR`. Esto suma en *Calidad del dato* (15%), que premia identificar el tipo de precio.
- Cruce extra: muchas etiquetas de rack traen código de barras o SKU; si el código de la etiqueta coincide con el del producto, es una señal fuerte de que la etiqueta corresponde.

**Pantalla de revisión**

- Miniaturas de fachada, producto y etiqueta con un check verde o una alerta roja por imagen.
- Datos detectados: código de barras, precio, tipo de precio y un fragmento del OCR.
- Lista de problemas en lenguaje claro ("La etiqueta está borrosa, acércate y toca para enfocar").
- Botones: **Retomar foto** (por imagen) y **Confirmar captura** (deshabilitado si hay problemas bloqueantes; `SIN_PRECIO_UNITARIO` puede ser advertencia y no bloqueo, para que decida el servidor).

**Terminado cuando:** una foto borrosa a propósito se detecta y se pide retomar, y una etiqueta legible muestra el precio correcto.

## 9. Fase 4 — Ciclo: nuevo producto o nueva sucursal + envío (4:15–5:15)

**Worker W5** (persistencia y red) en paralelo con el **orquestador** (navegación del ciclo).

**Ciclo (orquestador)**

- Al confirmar: guardar la captura y mostrar dos acciones:
  - **"Siguiente producto"** → vuelve a `producto` con la misma sesión y fachada.
  - **"Cambiar de sucursal"** → cierra la sesión y vuelve a `sesion` (nueva cadena, tienda, ubicación y fachada).
- Resumen de la sesión: N productos capturados, N enviados, N pendientes.

**Persistencia y envío (W5)**

1. Room: tabla `capturas` (campos de `Captura` + rutas de imágenes + estado `PENDIENTE | ENVIADA | ERROR`) y tabla `sesiones`.
2. `CapturaRepository.enviar()`: `@Multipart POST /capturas` con el JSON de la sección 3 y las 3 imágenes.
3. Envío inmediato al confirmar; si falla (sin red), queda en `PENDIENTE` y se reintenta desde un botón **"Enviar pendientes"** en el inicio. WorkManager es un extra si sobra tiempo.
4. Mostrar la respuesta del servidor si viene a tiempo (`decision`, `confidence_score`, `explicacion`): queda muy bien en la demo.
5. **Plan B si el backend no está listo:** un botón "Exportar JSON" que comparta las capturas de la sesión como archivo, para que el equipo de backend lo procese.

**Terminado cuando:** se capturan 2 productos en una tienda, se cambia de sucursal, se captura uno más y los 3 llegan al servidor (o quedan pendientes y se reenvían).

## 10. Fase 5 — Buffer y demo (5:15–6:00)

- Corregir lo que falló en la integración.
- Generar el APK de debug e instalarlo en 2 teléfonos.
- Ensayar el recorrido de la demo: tienda → producto bueno (aprobado) → foto borrosa (la app pide retomar) → etiqueta sin precio (la app lo detecta) → cambio de sucursal.
- Capturas de pantalla para la ficha técnica y el pitch.

## 11. Riesgos y extras

**Riesgos**

- **Cámara del emulador:** prueba siempre en teléfono físico.
- **Umbrales mal calibrados:** si la app es demasiado estricta, el capturista se frustra; si es laxa, no aporta. Calíbralos con el set de desarrollo.
- **Contrato con backend:** el JSON lo define la app; compártelo con backend en la primera media hora y no lo cambies después sin avisar.
- **Modelos de ML Kit:** usa las variantes *bundled* (las del plan) para no depender de que se descarguen.
- **Reglas del reto:** no uses ninguna API de IA externa; ML Kit on-device es librería local, pero confírmalo con los organizadores al arranque por si acaso.
- **Datos:** las imágenes del reto no salen del equipo; no las subas a repositorios públicos.

**Extras si sobra tiempo (en este orden)**

1. Análisis en vivo con `ImageAnalysis` de CameraX: indicador "código detectado ✓" antes de disparar.
2. Catálogo local de productos (del set de referencia) para comparar código de barras ↔ producto esperado en el teléfono.
3. Validar que la ubicación GPS está cerca de la sucursal elegida.
4. Detección de "foto de la pantalla de otro celular" (patrón moiré o reflejo): difícil, mejor dejarlo al servidor.
