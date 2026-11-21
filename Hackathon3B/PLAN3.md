# Plan 3 — Corrección de la lectura de precio

Nov 21, 2026 · @richi\_mc

## 1. Resumen

Se reemplaza `RegexPriceParser` por un **parser por puntuación** (`ScoringPriceParser`): primero junta todos los números que podrían ser el precio y después le da a cada uno una puntuación. Gana el de mayor puntuación, y esa puntuación es la confianza del precio. Tiempo estimado: **1 h 15 min**.

**Por qué falla hoy:** sin `$`, el regex solo acepta `24.90` o `24,90`. En etiquetas mexicanas los centavos suelen ir en superíndice, y el OCR los lee como `24 90`, `2490`, o `24` en una línea y `90` en otra. Además, el `$` a veces sale como otro carácter (`S`, `s`, `§`) o no sale.

**Qué incluye este plan**

| Idea | Cómo entra |
| --- | --- |
| Altura de la línea | Es la señal más fuerte de la puntuación: el precio casi siempre es el texto más grande. Un número de 1 a 4 dígitos de la línea más alta puede ganar sin `$` ni decimales |
| Puntuación de candidatos | Sustituye las reglas de todo o nada. Suma altura, `$`, centavos, cercanía a PRECIO y rango razonable; resta precio unitario, "antes" y "ahorro" |
| Mejor imagen para el OCR | Si la confianza sale baja, repite el OCR sobre un recorte ampliado y con más contraste de la zona del precio. En la cámara, una guía para que la etiqueta llene el encuadre |

Como el precio ya es editable en la revisión (Plan 2), la confianza también decide qué ve el usuario: si es baja, el campo muestra la advertencia "Revisa el precio".

## 2. Orquestación y contratos

**Espera a que el Plan 2 termine su Fase 0** (contratos con `lineasOcr`), porque este plan los extiende. Puede correr en paralelo con el resto del Plan 2 si W8 no está tocando el parser.

| Ola | Quién | Tarea | Paquete | Tiempo |
| --- | --- | --- | --- | --- |
| 0 | Orquestador | Cambios de contratos y fakes | `core/model` | 10 min |
| 1 | W10 (Sonnet) | Candidatos + puntuación (`ScoringPriceParser`) | `feature/analysis/price` | 40 min |
| 1 | W11 (Sonnet) | Re-OCR de recorte mejorado + guía de cámara | `feature/analysis/enhance`, `feature/camera` | 35 min |
| 2 | Orquestador | Conectar todo, revisar casos, ajustar pesos | `app` | 20 min |

**Cambios de contratos (Fase 0, orquestador)**

```kotlin
// LineaOcr gana su caja completa y sus elementos (ML Kit Text.Line.elements)
data class CajaPx(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val alto get() = bottom - top
}
data class ElementoOcr(val texto: String, val caja: CajaPx, val simbolos: List<CajaPx>)
data class LineaOcr(val texto: String, val caja: CajaPx, val elementos: List<ElementoOcr>) {
    val alturaPx get() = caja.alto           // compatible con el Plan 2
}

enum class FormaPrecio { DECIMAL, CENTAVOS_MISMA_LINEA, CENTAVOS_PEGADOS, CENTAVOS_OTRA_LINEA, ENTERO }

data class CandidatoPrecio(
    val valor: Double, val forma: FormaPrecio, val textoFuente: String,
    val caja: CajaPx, val alturaRelativa: Float,    // altura / altura máxima de la etiqueta
    val tieneSigno: Boolean, val esUnitario: Boolean, val esAnterior: Boolean,
    val puntuacion: Float = 0f
)

data class PrecioDetectado(
    val valor: Double?, val tipo: TipoPrecio, val tienePrecioUnitario: Boolean,
    val textoFuente: String, val confianza: Float,          // nuevo
    val candidatos: List<CandidatoPrecio> = emptyList()     // top 3, para depurar y auditar
)

interface PriceParser { fun parse(lineas: List<LineaOcr>): PrecioDetectado }
interface OcrEnhancer { suspend fun reOcr(path: String, zona: CajaPx): List<LineaOcr> }
```

El `PriceParser` ahora recibe las líneas con geometría en lugar de un `String`. El analizador OCR existente solo tiene que copiar las cajas de `Text.Line`, `Text.Element` y `Text.Symbol` (Text Recognition v2 expone los tres niveles).

## 3. Fase 1 — Generar candidatos (W10)

El parser junta **todos** los números que podrían ser precio, sin decidir todavía. Un mismo texto puede dar dos candidatos (por ejemplo `2490` → 24.90 y 2490); la puntuación decide después.

**Paso 1. Normalizar cada elemento antes de buscar**

- `S`, `s`, `§` pegados al inicio de un número (`S24.90`) se tratan como `$` y marcan `tieneSigno = true`.
- Dentro de un token que ya tiene dígitos: `O`/`o` → `0`, `l`/`I` → `1`, `B` → `8` solo si está entre dígitos.
- Quitar espacios alrededor de `.` y `,` (`24 . 90` → `24.90`).

**Paso 2. Detectores de candidatos**

| Forma | Patrón | Ejemplo OCR | Valor |
| --- | --- | --- | --- |
| `DECIMAL` | `\$?\d{1,4}[.,]\d{2}` | `$24.90`, `24,90` | 24.90 |
| `CENTAVOS_MISMA_LINEA` | Elemento de 1–4 dígitos seguido de un elemento de exactamente 2 dígitos cuya altura es < 0.8× la del entero, o cuya línea solo contiene esos dos números | `24 90` | 24.90 |
| `CENTAVOS_PEGADOS` | Token de 3–6 dígitos donde los **2 últimos símbolos** miden < 0.8× la altura de los primeros (usa `simbolos`) | `2490` | 24.90 |
| `CENTAVOS_OTRA_LINEA` | Línea con entero de 1–4 dígitos + otra línea con exactamente 2 dígitos, a la derecha (su `left` ≥ `right` del entero − 10 px), con su `top` dentro de la mitad superior del entero y más baja | `24` / `90` | 24.90 |
| `ENTERO` | Token de 1–4 dígitos que no formó parte de otra forma | `$25`, `25` | 25.00 |

Si un token de 3–4 dígitos no tiene evidencia de altura (símbolos iguales), se generan **dos** candidatos: `CENTAVOS_PEGADOS` sin evidencia (24.90) y `ENTERO` (2490). El rango y la puntuación eligen.

**Paso 3. Descartar lo que nunca es precio** (no entra como candidato):

- Tokens de 6 o más dígitos seguidos (código de barras, SKU).
- Fechas: `\d{1,2}[/-]\d{1,2}[/-]\d{2,4}`.
- Números pegados o seguidos de unidad de contenido: `ML`, `L`, `LT`, `G`, `GR`, `KG`, `PZ`, `PZS`, `CM`, `%`, `X` (como en `6X355`).
- Mezclas de letras y dígitos que no son `$` normalizado (`A12B`).
- Valores fuera de **1 a 5000**.

**Paso 4. Marcar contexto** (no descarta; lo usa la puntuación):

- `esUnitario`: la misma línea o la de abajo contiene `PRECIO UNITARIO`, `POR KG`, `/KG`, `POR LT`, `/L`, `POR 100`, `C/U`, `X KG`.
- `esAnterior`: la misma línea contiene `ANTES`, `AHORRA`, `AHORRO`, `AHORRAS`, `DESCUENTO`, `PRECIO REGULAR` (cuando también hay `OFERTA`/`AHORA`).
- `alturaRelativa`: altura del elemento entero / altura de la línea más alta de la etiqueta.

## 4. Fase 2 — Puntuar y elegir (W10)

Cada candidato suma y resta puntos. La puntuación final se recorta a 0–1 y es la **confianza del precio**. Todos los pesos van en un `object PesosPrecio` para ajustarlos en la Fase 4 sin tocar la lógica.

| Señal | Puntos | Nota |
| --- | --- | --- |
| Altura relativa | +0.35 × `alturaRelativa` | La señal más fuerte: el precio suele ser lo más grande |
| Está en la línea más alta (≥ 0.9) | +0.10 | Permite que un `ENTERO` sin `$` gane |
| Tiene `$` (o `S`/`§` normalizado) | +0.15 |  |
| Tiene centavos con evidencia (`DECIMAL`, `CENTAVOS_MISMA_LINEA`, `CENTAVOS_OTRA_LINEA`, `CENTAVOS_PEGADOS` con símbolos más bajos) | +0.15 |  |
| Cerca de `PRECIO`, `AHORA`, `OFERTA`, `$` suelto (misma línea o la anterior) | +0.10 |  |
| Valor entre 5 y 500 (rango típico de anaquel) | +0.10 | Fuera de ese rango pero dentro de 1–5000: 0 |
| Coincide con el resultado del re-OCR del recorte (Fase 3) | +0.10 |  |
| `esUnitario` | −0.40 | Se guarda aparte como precio unitario y activa `tienePrecioUnitario` |
| `esAnterior` (antes, ahorro, descuento) | −0.30 |  |
| `CENTAVOS_PEGADOS` sin evidencia de altura | −0.10 | Interpretación dudosa |
| `ENTERO` generado de un token de 3–4 dígitos sin evidencia | −0.15 | Prefiere 24.90 sobre 2490 cuando todo lo demás empata |

**Elección**

1. Ordenar por puntuación. Gana el primero. Si dos candidatos con el mismo valor empatan (por ejemplo, el mismo precio leído en dos formas), se fusionan y se suma +0.05.
2. Si el segundo está a menos de 0.05 del primero y tienen valores distintos, se resta 0.15 a la confianza: es una lectura ambigua.
3. **Umbrales de confianza** (en `object PesosPrecio`):
   - ≥ 0.60: se prellena el precio normalmente.
   - 0.30–0.59: se prellena, pero el campo muestra el chip `warning` "Revisa el precio".
   - < 0.30: `valor = null` y el campo queda vacío con "No detectado".
4. **Tipo de precio:** si existe un candidato con `esAnterior` y el ganador no lo es, `tipo = OFERTA`. Los patrones de volumen (`2X`, `3 X $`, `LLEVA … PAGA`) siguen como en el parser actual.
5. Guardar los 3 mejores candidatos en `PrecioDetectado.candidatos`.

**JSON (campos nuevos dentro de `producto_esperado`):**

```json
"confianza_precio": 0.82,
"precio_candidatos": [
  {"valor": 24.90, "forma": "CENTAVOS_MISMA_LINEA", "puntuacion": 0.82},
  {"valor": 2490, "forma": "ENTERO", "puntuacion": 0.31},
  {"valor": 18.50, "forma": "DECIMAL", "puntuacion": 0.12}
]
```

Así el servidor puede revisar la lectura cuando la confianza sea baja. Avisa al backend de estos dos campos.

**Terminado cuando (W10):** compila, `ScoringPriceParser` sustituye a `RegexPriceParser` en `AppContainer` y una función demo imprime el ganador, la forma y la confianza para los casos de la Fase 4.

## 5. Fase 3 — Mejorar la imagen para el OCR (W11)

**A. Re-OCR del recorte de la zona del precio** (`OcrEnhancer`)

Se ejecuta solo cuando hace falta, para no sumar espera en cada foto: si la confianza del ganador es < 0.70, si ganó un `ENTERO` o un `CENTAVOS_PEGADOS` sin evidencia, o si no hubo candidatos pero existe una línea con dígitos.

1. **Zona:** la caja de la línea más alta que tenga dígitos (o la del ganador). Ampliarla 40 % a la derecha y 50 % hacia arriba y abajo, que es donde quedan los centavos en superíndice, y recortarla a los bordes de la imagen.
2. **Recortar sobre la foto original** guardada (no la miniatura), para tener todos los píxeles disponibles.
3. **Ampliar** el recorte hasta que la línea mida unos 120 px de alto, con un máximo de 3× (`Bitmap.createScaledBitmap(…, filter = true)`).
4. **Escala de grises + estiramiento de contraste:** calcular los percentiles 2 % y 98 % de luminancia y estirar ese rango a 0–255. Opcional, si se lee peor: umbral fijo en 128 (blanco y negro).
5. Correr `TextRecognition` sobre el recorte y convertir las cajas de vuelta a coordenadas de la imagen completa (dividir entre la escala y sumar el origen del recorte).
6. **Fusionar:** pasar las líneas del recorte por el mismo detector de candidatos. Si un candidato del recorte tiene el mismo valor que uno original, este recibe +0.10 ("coincide con re-OCR"). Si el recorte encuentra centavos que el original no tenía (por ejemplo `24` → `24.90`), el nuevo candidato entra con la altura relativa de la línea original y compite normal.

Costo esperado: 200–500 ms extra, solo en las etiquetas difíciles. Mostrar el indicador de carga de la revisión mientras corre.

**B. Guía en la cámara para la foto de la etiqueta**

- Marco guía en `accent-bright` con proporción 3:2 que ocupe \~85 % del ancho, y la instrucción en la franja superior: **"Que la etiqueta llene el marco"**.
- Después de tomar la foto: si la línea más alta con dígitos mide menos de 4 % del alto de la imagen, marcar la advertencia `DEMASIADO_LEJOS` con el texto "Acércate a la etiqueta para leer el precio" y ofrecer retomar.
- Activar el enfoque táctil en el centro del marco al abrir la cámara (`startFocusAndMetering`), porque las etiquetas están cerca y el autoenfoque a veces enfoca el fondo.

**Terminado cuando (W11):** compila, existe una pantalla o función demo que muestra el recorte mejorado de una etiqueta del set de desarrollo junto con el texto que lee, y la guía aparece en la cámara de la etiqueta.

## 6. Fase 4 — Integración, casos y riesgos (orquestador)

**Integración**

1. Registrar `ScoringPriceParser` y `OcrEnhancer` en `AppContainer` y eliminar `RegexPriceParser`.
2. En el pipeline de la foto de etiqueta: OCR → candidatos → puntuación → si hace falta, re-OCR del recorte → puntuación final.
3. Pasar `confianza` a la sección Producto esperado del Plan 2 para mostrar el chip "Revisa el precio" o "No detectado" según los umbrales.
4. Incluir `confianza_precio` y `precio_candidatos` en el JSON.

**Casos para revisar con la función demo** (resultado esperado del ganador):

| Texto que entrega el OCR | Esperado | Forma |
| --- | --- | --- |
| `$24.90` | 24.90 | `DECIMAL` |
| `24 90` (el 90 más bajo) | 24.90 | `CENTAVOS_MISMA_LINEA` |
| `2490` (los 2 últimos símbolos más bajos) | 24.90 | `CENTAVOS_PEGADOS` |
| `24` en una línea y `90` pequeño a la derecha | 24.90 | `CENTAVOS_OTRA_LINEA` |
| `S24.90` | 24.90 | `DECIMAL` (con `$` normalizado) |
| `$25` | 25.00 | `ENTERO` |
| `25` solo, en la línea más alta | 25.00, confianza 0.30–0.59 | `ENTERO` |
| `600 ML $15.00` + `PRECIO POR LITRO $25.00` | 15.00 (25.00 queda como unitario) | `DECIMAL` |
| `ANTES $32.00` + `AHORA $27.50` | 27.50, tipo `OFERTA` | `DECIMAL` |
| `7501055300075` + `12/11/26` sin otros números | sin precio | — |

Además, correr la demo sobre 10–15 etiquetas del set de desarrollo y anotar en cuáles falla. Ajustar primero los pesos de altura y de `ENTERO` sin evidencia, que son los que más mueven el resultado.

**Riesgos**

- **`$` leído como `5` u `8`** (`$24` → `524`): no se corrige con reglas sin romper precios reales. Lo mitiga el re-OCR del recorte y que el campo sea editable; si pasa seguido, anotarlo como límite conocido.
- **Etiquetas con varios precios grandes** (precio por pieza y por caja): la altura no basta para elegir. La cercanía a palabras clave y el chip "Revisa el precio" cubren el caso.
- **ML Kit sin símbolos fiables:** si las cajas de `Text.Symbol` vienen vacías o iguales, `CENTAVOS_PEGADOS` depende solo del rango y la penalización; el re-OCR ayuda a separar los centavos.
- **Tiempo:** el re-OCR solo corre en los casos dudosos; si aun así se siente lento, bajar la ampliación máxima a 2×.
