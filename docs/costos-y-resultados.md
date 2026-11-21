# Costos y resultados medidos

21 de noviembre de 2026. Datos: las 100 capturas etiquetadas de desarrollo (33 aprobadas, 34 rechazadas, 33 a revisión manual).

> **Actualización:** la comparación contra Haiku está en [`pruebas/`](pruebas/README.md). Haiku razonando da la misma calidad que Sonnet (0 FP, 0 FN y costo operativo de 66 a 71) por **$13–19 por 10,000 capturas**, contra $147–179 con Sonnet. Ahí está también el registro completo de corridas y cómo reproducirlas.

## Motor desplegado hoy: cascada Haiku → Sonnet

1. **Capa local, 0 tokens:** fotos faltantes o ilegibles, código de barras del producto, nitidez y fotos repetidas.
2. **Claude Haiku 5.5** revisa las 3 fotos. Si ya está muy seguro (p_aprobar ≥ 0.92 o p_rechazo ≥ 0.56), decide.
3. **Claude Sonnet 5.5** revisa solo el resto: el 62–68% de las capturas en desarrollo.
4. **Fusión aprendida:** convierte lo que observa cada modelo en probabilidades calibradas. Umbrales elegidos por costo y con precisión de aprobaciones ≥ 97%.

## v0.3: reglas para no aprobar nada inválido

Una captura **solo se aprueba si pasan todas las compuertas**. Si cualquiera falla o no se puede verificar, va a revisión manual: cuesta 1, contra 10 de aprobar una inválida. Las compuertas no rechazan; solo impiden aprobar. Cada falla queda en `traza.compuertas_fallidas`.

| Compuerta | Qué exige |
|---|---|
| `fotos_completas` | Las 3 fotos llegan y se pueden abrir |
| `sin_trampa` | Ninguna foto sin relación ni tomada a una pantalla |
| `calidad` | No borrosa ni lejana; nitidez y legibilidad ≥ 0.5 |
| `fachada` | Es una tienda y no contradice la cadena seleccionada |
| `producto` | El producto coincide, sin contradicción en marca ni contenido |
| `descripcion` | Se leen marca, nombre y contenido |
| `etiqueta` | La etiqueta es del producto y no hay varias que confundan |
| `precio_legible` | Se lee el precio |
| `precio_coincide` | El precio capturado (o el que leyó el teléfono) es igual al regular o al de oferta de la etiqueta |
| `precio_en_rango` | El precio está dentro del rango de referencia del catálogo |
| `precio_unitario_coherente` | El precio unitario cuadra con precio ÷ contenido (±15%) |
| `codigo_barras` | El código de la foto del producto es el esperado |
| `foto_no_repetida` | La foto no se usó en otra captura |
| `chequeos_movil` | El teléfono no reportó falla de nitidez, distancia, etiqueta o precio |
| `confianza_ia` | El modelo está seguro (≥ 0.8) |
| `doble_verificacion` | Si Haiku aprueba solo, Sonnet debe confirmarlo; si no lo confirma, va a revisión |

- **Lectura independiente del precio:** la IA lee el precio sin conocer el capturado y la comparación se hace en código, para que el modelo no se deje llevar por el número esperado.
- **Lo que reporta la IA del precio:** precio regular, precio de oferta, promoción por volumen, precio tachado, precio unitario con su unidad y anomalías.
- **Pruebas:** 45 pruebas automáticas cubren cada regla.

### Medido con las 100 capturas de desarrollo (prompt v3)

| | Costo (fuera de muestra) | Aprobaciones inválidas | Tokens por captura |
|---|---:|---:|---:|
| Solo Haiku + compuertas | 79–83 | 0 | 6,600 |
| Solo Sonnet + compuertas | 63–73 | 0 | 6,700 |
| **Cascada + compuertas + doble llave (desplegado)** | **~70** | **0** | **~11,900** |

- **Medición sobre los mismos datos del entrenamiento:** la configuración final da 52, con 0 aprobaciones inválidas y 81% de acierto en el motivo de rechazo.
- **Compuertas de precio que se activaron:** 5 veces precio distinto y 4 veces precio unitario incoherente.
- **Costo en tokens:** el esquema del prompt v3 suma ~1,300 tokens por llamada, y la doble llave agrega Sonnet en las aprobaciones.

## Costo operativo (la calificación)

El costo operativo es la suma de puntos: 0 si acierta, 1 por revisión manual, 3 por rechazar una válida y 10 por aprobar una inválida. Las cifras son **fuera de muestra**: validación cruzada repetida, es decir, cómo le iría con capturas nuevas.

| Motor (fotos de producto y etiqueta a 768 px, fachada a 384 px) | Costo en 100 capturas | Aprobaciones falsas | Tokens por captura | USD por 10,000 capturas |
|---|---:|---:|---:|---:|
| Todo a revisión manual (referencia) | 100 | 0 | 0 | $0 |
| Solo Haiku 5.5 | 85–86 | 0 | 5,300 | $7 |
| Solo Sonnet 5.5 | 64–66 | 0 | 5,300 | $138 |
| **Cascada Haiku → Sonnet (elegido)** | **~69** | **0** | **8,900** | **$100** |

- **Ruido de la muestra:** con 100 capturas, diferencias de ±8 puntos todavía son ruido. Con fotos a 960 px los costos fueron iguales dentro de ese ruido, pero con 15% más tokens.
- **Medición sobre los mismos datos del entrenamiento:** la cascada da 53, una cifra optimista. Con 100% de precisión en aprobaciones, procesa 100 capturas en ~32 s con caché y en ~3 min sin caché.
- **Acierto del `reason_code`:** 59–62% fuera de muestra con el clasificador aprendido, contra 41–44% con reglas fijas.
- **Precios por millón de tokens:** Haiku 5.5 cuesta $0.10 de entrada y $0.50 de salida; Sonnet 5.5, $2 y $10.

## Gasto real de la llave

| Corrida | Tokens de entrada | Tokens de salida | USD |
|---|---:|---:|---:|
| Haiku, prompt v1 | 451,561 | 28,261 | 0.06 |
| Sonnet, prompt v1 | 451,648 | 28,559 | 1.19 |
| Haiku, prompt v2 (960 px) | 580,445 | 39,181 | 0.08 |
| Sonnet, prompt v2 (960 px) | 580,545 | 39,300 | 1.55 |
| Haiku, prompt v2 (768 px) | 491,542 | 39,083 | 0.07 |
| Sonnet, prompt v2 (768 px) | 491,642 | 39,245 | 1.38 |
| **Total** | | | **4.32** |

Las corridas repetidas no gastan, gracias a la caché de respuestas.

## Qué aprendimos de los datos

- **La cadena pesa mucho.** En desarrollo solo se aprueban capturas de Soriana, Chedraui y Neto; Bodega Aurrerá, Walmart y Bara siempre van a revisión o se rechazan. Hay que confirmarlo con el documento de reglas de negocio.
- **Las señales que más pesan:** la nitidez del producto y de la etiqueta, la legibilidad de la etiqueta y qué tan grande sale el producto en la foto.
- **El precio unitario no es requisito en estos datos:** ninguna captura aprobada lo muestra.
- **El código de barras de la etiqueta de anaquel es interno de la tienda.** No sirve para validar el producto.
- **Motivos de rechazo en desarrollo:** captura lejana (18), borrosa (11) y descripción incompleta (5).

## Set ciego: cómo se corre

Desde `servidor/`, con el set en `validat3b/datos/ciego/`:

```bash
.venv/Scripts/python -m scripts.correr_lote --formato oficial --datos ../datos/ciego/capturas.json --salida ../datos/corridas/ciego --trabajadores 8
```

- **El entregable es `../datos/corridas/ciego/resultados.json`:** una respuesta por captura en el formato oficial.
- **Si se corta, se vuelve a correr el mismo comando:** retoma sin repetir capturas ni gastar de nuevo.
