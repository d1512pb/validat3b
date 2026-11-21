# Metodología: índice de confiabilidad local (ICL)

Cómo calcular en el teléfono un porcentaje de confiabilidad por captura para decidir si basta un análisis rápido en el servidor o si hace falta uno más profundo.

Usa cinco parámetros que la app ya calcula, así que no hace falta capturar nada nuevo:
1. Nitidez
2. Distancia
3. OCR
4. Precio leído
5. Tipo de precio

**Qué mide y qué no.** El índice mide si la evidencia es legible y si el precio está bien leído. No verifica que el producto sea el correcto, que la fachada corresponda a la tienda ni que no haya fotos trampa. Por eso un índice alto **no** significa «aprobar sin servidor». Significa que **basta un análisis rápido**; un índice bajo pide **análisis profundo** o retomar la foto.

---

## 1. Los cinco parámetros

**Regla de normalización:** cada medición se convierte a un puntaje de 0 a 1. Justo en el umbral que ya bloquea en la app vale **0.5**; en un valor «bueno» vale **1**. Es la misma lógica que ya usa `CapturaMapper` para nitidez y distancia, así que los puntajes son coherentes con lo que ya se manda en `chequeos_locales`.

`clamp(x)` limita el valor al rango de 0 a 1.

### N: Nitidez (fachada, producto y etiqueta)

- **Dato:** `AnalisisImagen.nitidez`, la varianza del Laplaciano a 512 px. Umbral: `Umbrales.nitidezMin = 100`.
- **Puntaje por foto:** `clamp(nitidez / 200)`. Con 100 vale 0.5; con 200 o más vale 1.
- **N = el mínimo de las tres fotos.** Una sola foto borrosa basta para un rechazo por `IMAGEN_BORROSA`.

### D: Distancia (producto)

- **Dato:** `AnalisisImagen.areaCodigo`, la fracción de la imagen que ocupa el código de barras. Umbral: `Umbrales.areaCodigoMin = 0.02`.
- **Puntaje:** `clamp(areaCodigo / 0.04)`. Con 2 % vale 0.5; con 4 % o más vale 1.
- **Sin código en la foto pero con escaneo válido en vivo:** D = 0.5. Se sabe qué producto es, pero no qué tan cerca se tomó la foto.

### O: OCR (producto y etiqueta)

- **Datos:** `AnalisisImagen.confianzaOcr` (umbral `ocrConfianzaMin = 0.5`) y la cantidad de letras y números leídos (umbral `ocrCaracteresMin = 8`).
- **Confianza:** `s_conf = clamp(0.5 + 1.25 · (confianzaOcr − 0.5))`. Con 0.5 vale 0.5; con 0.9 vale 1.
- **Cantidad de texto:** `s_texto = clamp(alfanuméricos / 16)`. Con 8 vale 0.5; con 16 o más vale 1.
- **Puntaje por foto:** `√(s_conf · s_texto)`.
- **O = 0.6 · etiqueta + 0.4 · producto.** Pesa más la etiqueta porque de su texto sale el precio.

### P: Precio leído (etiqueta)

- **Dato:** `PrecioDetectado`.
- **Base:**
  - **1.0:** se encontró precio con signo `$` (el `textoFuente` lo contiene).
  - **0.8:** precio sin `$`.
  - **0.6:** el parser no encontró precio principal y tomó el unitario. En el código pasa cuando `elegirPrincipal` regresa null; queda `valor == precioUnitario`.
- **Factor de precio unitario:** 1.0 si `tienePrecioUnitario`, 0.6 si no. Cubre el riesgo de `SIN_PRECIO_UNITARIO`.
- **P = base × factor.**

### T: Tipo de precio (etiqueta)

| `TipoPrecio` | T | Por qué |
|---|---|---|
| `REGULAR` | 1.0 | Caso simple |
| `OFERTA` | 0.8 | Puede haber un precio «antes» y uno «ahora»; más riesgo de elegir el equivocado |
| `VOLUMEN` | 0.7 | En «2x$50» el parser toma 50, que es el total de la promoción y no el precio de una pieza |
| `DESCONOCIDO` | — | Solo ocurre cuando no hay precio, y eso ya bloquea la captura |

---

## 2. Bloqueos previos (ya implementados)

Antes de calcular el índice, la app ya impide confirmar una captura con alguno de estos problemas:
- `BORROSA`
- `DEMASIADO_LEJOS` (salvo que haya un escaneo válido en vivo)
- `SIN_TEXTO_LEGIBLE`
- `SIN_PRECIO`

Por eso el índice solo se calcula sobre capturas que ya pasaron esos bloqueos, y cada parámetro queda casi siempre en ≥ 0.5.

---

## 3. Fórmula del índice

Media geométrica ponderada:

> **ICL = N^0.25 · D^0.20 · O^0.20 · P^0.25 · T^0.10**
>
> O, de forma equivalente: ln ICL = 0.25 ln N + 0.20 ln D + 0.20 ln O + 0.25 ln P + 0.10 ln T

**Por qué media geométrica:** un parámetro malo baja el total y los demás no lo compensan. Una foto nítida no salva una etiqueta sin precio unitario.

**Pesos iniciales** (se recalibran en la sección 6):

| Parámetro | Peso | Motivo de rechazo que anticipa |
|---|---|---|
| Nitidez | 0.25 | `IMAGEN_BORROSA`; además afecta a todo lo demás |
| Precio leído | 0.25 | `SIN_ETIQUETA_PRECIO`, `SIN_PRECIO_UNITARIO` |
| Distancia | 0.20 | `CAPTURA_LEJANA` |
| OCR | 0.20 | Legibilidad de la etiqueta y del producto |
| Tipo de precio | 0.10 | Calidad del dato; los errores en promociones son menos frecuentes |

---

## 4. Bandas de decisión

Una captura que pasa los bloqueos con todos los parámetros justo en el umbral saca alrededor de 53 %. Con esa referencia:

| ICL | Banda | Acción |
|---|---|---|
| **≥ 80 %** | Alta | Análisis rápido: el servidor puede resolver con la capa `rapido` |
| **60–80 %** | Media | **Análisis profundo**, capa `fuerte` |
| **< 60 %** | Baja | En la pantalla de revisión, sugerir retomar la foto más débil (no bloquea). Si se confirma así, va a análisis profundo |

---

## 5. Ejemplos

### Captura buena

| Parámetro | Medición | Puntaje |
|---|---|---|
| N | Nitidez mínima 220 | 1 |
| D | El código ocupa 5 % | 1 |
| O | Etiqueta con confianza 0.92; producto con 0.88 | 0.995 |
| P | «$42.50» con precio unitario | 1 |
| T | Regular | 1 |

**ICL ≈ 99.9 % → análisis rápido.**

### Captura regular

| Parámetro | Medición | Puntaje |
|---|---|---|
| N | Nitidez mínima 130 | 0.65 |
| D | El código ocupa 3 % | 0.75 |
| O | — | 0.9 |
| P | «$42.50» sin precio unitario | 0.6 |
| T | Regular | 1 |

ln ICL = 0.25(−0.431) + 0.20(−0.288) + 0.20(−0.105) + 0.25(−0.511) = −0.314

**ICL ≈ 73 % → análisis profundo.**

### Captura débil

| Parámetro | Medición | Puntaje |
|---|---|---|
| N | Nitidez mínima 110 | 0.55 |
| D | Foto sin código, con escaneo válido | 0.5 |
| O | — | 0.7 |
| P | «2x$50» sin precio unitario | 0.6 |
| T | Volumen | 0.7 |

ln ICL = 0.25(−0.598) + 0.20(−0.693) + 0.20(−0.357) + 0.25(−0.511) + 0.10(−0.357) = −0.523

**ICL ≈ 59 % → sugerir retomar la etiqueta**, que es la que más baja el índice.

---

## 6. Calibración con el set de desarrollo

Los pesos, los anclas y los cortes de arriba son un punto de partida. Para ajustarlos:

1. **Medir.** Correr el pipeline sobre `Validat3B_desarrollo_imagenes.zip` y guardar N, D, O, P y T de cada captura.
2. **Etiquetar.** Marcar como «evidencia débil» las capturas rechazadas por `IMAGEN_BORROSA`, `CAPTURA_LEJANA`, `SIN_ETIQUETA_PRECIO` o `SIN_PRECIO_UNITARIO`.
3. **Ajustar los pesos.** Usar una regresión logística con ln N, ln D, ln O, ln P y ln T como variables. Como la fórmula ya es lineal en logaritmos, los coeficientes reemplazan directamente a los pesos.
4. **Ajustar los anclas.** Revisar los valores «buenos» (200, 0.04, 0.9 y 16) con la distribución real de cada parámetro.
5. **Elegir los cortes de las bandas** minimizando el costo total en el set de desarrollo. El costo combina los tokens del análisis profundo con los errores de la capa rápida, penalizados con la matriz del reto: aprobar una inválida cuesta 10, la revisión manual 1 y rechazar una válida 3.
6. **Verificar la banda alta.** Menos de 5 % de sus capturas deben haber sido rechazadas por calidad o precio. Si no, subir el corte de 80 %.

---

## 7. Cómo encaja con lo que ya existe

- **Cuándo se calcula:** al confirmar la captura. Todos los datos ya están en `Captura` y `Sesion`.
- **Cómo llega al servidor:** como un chequeo más en `chequeos_locales`, p. ej. `nombre: "confiabilidad_local"`, `puntaje: 0.73` y en `detalle` los cinco subpuntajes. El contrato acepta nombres libres, así que no lo rompe (ver [`contratos.md`](contratos.md)). El servidor decide entre la capa `rapido` y la `fuerte` con ese valor.
- **Recalibración:** guardar el ICL junto a la decisión que regrese el servidor para ajustar pesos y bandas con datos reales.

### Dónde está cada dato en el código

| Parámetro | Archivo |
|---|---|
| Nitidez, OCR, distancia | `app/src/main/java/com/richi_mc/hackathon3b/feature/analysis/MlKitImageAnalyzer.kt` |
| Precio leído, tipo de precio | `app/src/main/java/com/richi_mc/hackathon3b/feature/analysis/RegexPriceParser.kt` |
| Umbrales | `app/src/main/java/com/richi_mc/hackathon3b/core/model/Umbrales.kt` |
| Modelos (`AnalisisImagen`, `PrecioDetectado`, `Captura`) | `app/src/main/java/com/richi_mc/hackathon3b/core/model/Modelos.kt` |
| Chequeos que ya se mandan al servidor | `app/src/main/java/com/richi_mc/hackathon3b/core/network/CapturaMapper.kt` |
