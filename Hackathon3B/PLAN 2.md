# Plan 2 — Etiquetado con ML Kit y producto esperado editable

Nov 21, 2026 · @richi\_mc

## 1. Resumen

Este plan agrega dos cosas a la app que ya construyó el orquestador, en unas **2 horas**: **etiquetado de imágenes con ML Kit** en el teléfono y una sección **Producto esperado** que se llena sola con la descripción y el precio detectados, y que el usuario puede corregir antes de confirmar.

**Qué aporta cada parte**

| Parte | Qué hace | Frente del reto |
| --- | --- | --- |
| Etiquetado ML Kit (Image Labeling) | Clasifica cada foto en categorías generales ("Bottle", "Food", "Building") con confianza. Sirve para confirmar que hay un producto visible, que la fachada es un edificio y para marcar posibles fotos de pantalla | A (producto no visible) y B (fachada incorrecta, foto de pantalla) |
| Sugerencia de descripción | Arma una descripción del producto con el texto leído: primero del catálogo si hay código de barras, luego de la etiqueta del rack, luego de la foto del producto y al final de la categoría del etiquetado | A (identidad del producto) |
| Precio detectado | Toma el precio que ya extrae el parser de la etiqueta | C (evidencia de precio) |
| Producto esperado editable | Muestra descripción y precio prellenados en campos de texto; el usuario los acepta o los corrige. Se guardan el valor detectado y el final | Calidad del dato y auditoría |

**Límite importante:** el modelo base de Image Labeling reconoce unas 400 categorías generales, en inglés. No identifica marcas ni productos específicos; eso lo resuelven el código de barras, el catálogo y el OCR. El etiquetado es una señal de apoyo, no la identificación.

**Orden:** Fase 0 (orquestador, 15 min) → Fases 1 y 2 en paralelo (45 min) → Fase 3 (40 min) → Fase 4 (20 min).

## 2. Orquestación

Se mantienen las mismas reglas del plan 1: el orquestador (sesión principal) cambia contratos y navegación; los workers corren con el agente `worker-android` en **Sonnet** y solo tocan su paquete.

| Ola | Quién | Tarea | Paquete | Depende de |
| --- | --- | --- | --- | --- |
| 0 | Orquestador | Dependencia, contratos nuevos, fakes | `core/model`, Gradle | — |
| 1 | W7 (Sonnet) | Etiquetado con ML Kit y reglas de validación | `feature/labeling` | Ola 0 |
| 1 | W8 (Sonnet) | Sugerencia de descripción y precio | `feature/suggestion` | Ola 0 |
| 2 | W9 (Sonnet) | Sección Producto esperado editable + JSON | `feature/capture` (revisión), `core/network` | Ola 1 |
| 3 | Orquestador | Integración en el pipeline y prueba con productos reales | `app` | Ola 2 |

W7 y W8 no comparten archivos, así que pueden lanzarse al mismo tiempo. W9 empieza cuando ambos compilan, aunque puede arrancar antes con los fakes de la Fase 0.

## 3. Fase 0 — Dependencia y contratos (15 min, orquestador)

**1. Dependencia** (versión de referencia; usa la que sugiera Android Studio). Es la variante *bundled*: el modelo viene dentro del APK y funciona sin red.

```kotlin
implementation("com.google.mlkit:image-labeling:17.0.9")
```

**2. Contratos nuevos y cambios en `core/model`**

```kotlin
data class EtiquetaImagen(val texto: String, val confianza: Float, val indice: Int)

data class LineaOcr(val texto: String, val alturaPx: Int, val top: Int)

enum class FuenteDescripcion { CATALOGO, ETIQUETA_RACK, FOTO_PRODUCTO, CATEGORIA, MANUAL }

data class Sugerencia(
    val descripcion: String?, val fuente: FuenteDescripcion?,
    val precio: Double?, val confianza: Float      // 0..1
)

data class ProductoEsperado(
    val descripcion: String, val precio: Double?,
    val descripcionDetectada: String?, val precioDetectado: Double?,
    val fuente: FuenteDescripcion, val editadoPorUsuario: Boolean
)

interface ImageLabelerService {
    suspend fun etiquetar(path: String): List<EtiquetaImagen>
}

interface SuggestionService {
    fun sugerir(
        lineasProducto: List<LineaOcr>, lineasEtiqueta: List<LineaOcr>,
        etiquetasProducto: List<EtiquetaImagen>, precio: PrecioDetectado,
        codigoBarras: String?
    ): Sugerencia
}
```

**Cambios a clases existentes**

- `AnalisisImagen` agrega `etiquetas: List<EtiquetaImagen>` y `lineasOcr: List<LineaOcr>` (el analizador OCR ya tiene las líneas de ML Kit; solo hay que exponerlas con la altura de su `boundingBox`).
- `Problema` agrega `PRODUCTO_NO_VISIBLE`, `FACHADA_NO_PARECE_TIENDA` y `POSIBLE_FOTO_DE_PANTALLA` (estos dos últimos como advertencias, no bloqueos).
- `Captura` agrega `productoEsperado: ProductoEsperado`; el campo de texto `productoEsperado: String?` anterior se reemplaza.

**3. Fakes** de `ImageLabelerService` y `SuggestionService` en `AppContainer`, para que W9 pueda trabajar desde el inicio.

**Terminado cuando:** compila con los contratos nuevos y la app sigue funcionando con los fakes.

## 4. Fase 1 — Etiquetado de imágenes con ML Kit (45 min, W7)

**Implementación de `ImageLabelerService`**

```kotlin
class MlKitLabeler(private val context: Context) : ImageLabelerService {
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(0.5f).build()
    )
    override suspend fun etiquetar(path: String): List<EtiquetaImagen> {
        val img = InputImage.fromFilePath(context, Uri.fromFile(File(path)))
        return labeler.process(img).await()          // kotlinx-coroutines-play-services
            .map { EtiquetaImagen(it.text, it.confidence, it.index) }
            .sortedByDescending { it.confianza }
    }
}
```

Corre sobre las 3 fotos en el mismo paso que el OCR y el código de barras (en paralelo con `async`), así no agrega otra espera al usuario.

**Reglas que agrega a la validación**

| Foto | Regla | Resultado |
| --- | --- | --- |
| Producto | Ninguna etiqueta del grupo *producto* con confianza ≥ 0.6 **y** no se leyó código de barras | `PRODUCTO_NO_VISIBLE` (bloquea) |
| Fachada | Ninguna etiqueta del grupo *fachada* con confianza ≥ 0.5 | `FACHADA_NO_PARECE_TIENDA` (advertencia) |
| Cualquiera | Etiqueta del grupo *pantalla* con confianza ≥ 0.7 | `POSIBLE_FOTO_DE_PANTALLA` (advertencia) |

**Grupos de etiquetas:** en un `object GruposEtiquetas` con sets de texto, fáciles de ajustar. Punto de partida para *producto*: envases, alimentos y bebidas; para *fachada*: edificio, tienda, letrero, calle; para *pantalla*: monitor, pantalla, teléfono. **Antes de fijarlos, el worker debe correr el etiquetador sobre 20–30 fotos del set de desarrollo y anotar qué etiquetas salen de verdad**, porque los nombres exactos dependen de la lista del modelo de ML Kit.

**Traducción para la interfaz:** un `Map<String, String>` con las 30–40 etiquetas más comunes en español ("Bottle" → "Botella"). Las que no estén en el mapa se muestran en inglés.

**En la pantalla de revisión:** hasta 3 chips de estado en color `info` debajo de cada miniatura, con la etiqueta y su porcentaje ("Botella 91 %"), siguiendo el estilo Glass Rojo.

**Terminado cuando:** compila, hay un `@Preview` con chips de etiquetas y una foto de la pared sin producto marca `PRODUCTO_NO_VISIBLE`.

## 5. Fase 2 — Sugerencia de descripción y precio (45 min, W8)

`SuggestionService` arma la descripción con la mejor fuente disponible, en este orden. Se queda con la primera que dé un resultado válido:

1. **Catálogo** (`CATALOGO`, confianza 1.0): si hay código de barras y se implementó el catálogo local, la descripción oficial del SKU. Si no existe el catálogo, se salta este paso.
2. **Etiqueta del rack** (`ETIQUETA_RACK`, \~0.8): casi todas las etiquetas imprimen la descripción del producto. Se toman las líneas de texto que **no** son el precio ni el precio unitario, están por encima del precio y tienen al menos 2 palabras de letras. Se unen hasta 2 líneas.
3. **Foto del producto** (`FOTO_PRODUCTO`, \~0.6): las 2 o 3 líneas con mayor `alturaPx` (marca y nombre suelen ser lo más grande del empaque) + el contenido neto si aparece.
4. **Categoría** (`CATEGORIA`, \~0.3): la etiqueta de ML Kit con mayor confianza, traducida ("Botella"). Solo sirve de pista; el usuario casi siempre la cambiará.

**Limpieza del texto:** mayúsculas, sin acentos ni símbolos sueltos, sin líneas de solo números, máximo 60 caracteres. El contenido neto se detecta con `\d+([.,]\d+)?\s?(ML|L|LT|G|GR|KG|PZ|PZS)` y se pone al final si no está ya ("REFRESCO COLA 600 ML").

**Precio:** se toma `PrecioDetectado.valor` del parser de la etiqueta que ya existe. Si el parser encontró varios precios, el de la línea con mayor altura. Si es oferta o precio por volumen, se sugiere el precio que paga el cliente por una pieza y el tipo se conserva en `tipo_precio`.

**Terminado cuando:** compila y hay una función demo (o `@Preview`) con tres casos fijos: código en catálogo, solo etiqueta legible y solo foto del producto, cada uno con la descripción y la fuente esperadas.

## 6. Fase 3 — Producto esperado editable y JSON (40 min, W9)

La sección **Producto esperado** se llena sola con la descripción y el precio detectados, y el usuario puede editarlos si no son los que buscaba. Como el precio sale de la foto de la etiqueta, la sección vive en la **pantalla de revisión**, después de las dos fotos (ya no antes de la primera).

**Comportamiento**

1. Al entrar a revisión se llama a `SuggestionService.sugerir(...)` y los dos campos aparecen prellenados.
2. **Descripción:** `TextField` de una línea, mayúsculas automáticas, máximo 60 caracteres.
3. **Precio:** `TextField` con teclado numérico decimal y prefijo "$". Acepta coma o punto y se guarda como `Double`.
4. Debajo de cada campo, un chip con la fuente: "Del catálogo" (`success`), "Leído de la etiqueta" o "Leído del producto" (`info`), "Sugerencia por categoría" (`warning`).
5. Si el usuario cambia un valor, el chip pasa a **"Editado"** y aparece un botón de ícono para restaurar el valor detectado.
6. Si no se detectó nada, el campo queda vacío con un placeholder ("Escribe la descripción") y un chip `warning` "No detectado".
7. **Confirmar captura** requiere descripción no vacía. El precio vacío se permite con advertencia, porque "no hay etiqueta de precio" también es un caso que el servidor debe ver.
8. Al confirmar se guarda `ProductoEsperado` con los valores finales, los detectados y `editadoPorUsuario`.

**Estado en el ViewModel:** guardar los valores detectados aparte de los editados (`descripcionDetectada` / `descripcion`), para poder restaurar y para mandar ambos. Si el usuario retoma la foto de la etiqueta, se recalcula la sugerencia **solo** en los campos que no editó.

**Diseño (Glass Rojo):** una `GlassCard` con encabezado `label` "PRODUCTO ESPERADO", los dos `TextField` (52 dp, radio 16, foco en `accent-ink`) y los chips de estado. El precio detectado se muestra en `stat` (24 sp bold) dentro del campo para que se vea de un vistazo.

**Cambios al JSON** (campos nuevos; los anteriores se quedan igual):

```json
"producto_esperado": {
  "descripcion": "LECHE ENTERA 1 L",
  "precio": 24.90,
  "editado_por_usuario": true,
  "descripcion_detectada": "LECHE ENTERA 1L",
  "precio_detectado": 24.90,
  "tipo_precio": "REGULAR",
  "fuente_descripcion": "ETIQUETA_RACK",
  "confianza_sugerencia": 0.8
},
"etiquetas_imagen": {
  "fachada":  [{"etiqueta": "Building", "confianza": 0.88}],
  "producto": [{"etiqueta": "Bottle", "confianza": 0.91}],
  "etiqueta": [{"etiqueta": "Text", "confianza": 0.83}]
}
```

Mandar el valor detectado y el editado permite al servidor saber cuándo corrigió una persona y medir qué tan bien detecta la app. Avisa al backend de estos campos antes de integrar.

**Terminado cuando:** en revisión aparecen descripción y precio prellenados, se pueden editar y restaurar, y el JSON enviado (o exportado) trae los dos valores.

## 7. Fase 4 — Integración, prueba y riesgos (20 min, orquestador)

**Integración**

1. Conectar `MlKitLabeler` y el `SuggestionService` real en `AppContainer` en lugar de los fakes.
2. En el pipeline de análisis, correr etiquetado, OCR y código de barras en paralelo por foto.
3. Pasar las sugerencias a la pantalla de revisión y quitar el campo "Producto esperado" que estaba antes de la primera foto.
4. Compilar e instalar en el teléfono.

**Prueba manual con 5 productos reales** (la tienda de la esquina o la alacena sirve):

- [ ] Producto con código legible: descripción prellenada y fuente correcta.
- [ ] Etiqueta de rack legible: precio prellenado igual al de la etiqueta.
- [ ] Editar la descripción: chip "Editado" y el botón de restaurar funciona.
- [ ] Foto de la pared sin producto: marca `PRODUCTO_NO_VISIBLE`.
- [ ] Foto de un producto en la pantalla de otro celular: aparece la advertencia (si no sale, anotarlo para el pitch como límite conocido).
- [ ] El JSON exportado trae `producto_esperado` y `etiquetas_imagen` con valores detectados y finales.

**Riesgos**

- **Etiquetas genéricas:** el modelo base no conoce marcas; no prometas en el pitch que "reconoce el producto" por imagen. La identificación viene del código, el catálogo y el OCR.
- **Grupos mal calibrados:** si `PRODUCTO_NO_VISIBLE` bloquea capturas buenas, bájalo a advertencia. Mejor dejar pasar al servidor que frustrar al capturista.
- **Tiempo de análisis:** tres modelos por foto pueden tardar 1–2 s en teléfonos modestos. Si pasa, analiza a la resolución reducida (\~1280 px) que ya se guarda y muestra un indicador de carga en la revisión.
- **Tamaño del APK:** el modelo *bundled* suma unos MB; no afecta la demo.
- **Descripción de la etiqueta equivocada:** si el rack tiene varias etiquetas juntas, el OCR puede mezclar textos. Por eso el campo es editable y se manda también el valor detectado.
