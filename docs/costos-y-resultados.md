# Costos y resultados medidos

21 de noviembre de 2026. Datos: las 100 capturas etiquetadas de desarrollo (33 aprobadas, 34 rechazadas, 33 a revisión manual).

## Motor elegido: cascada Haiku → Sonnet

1. **Capa local, 0 tokens:** fotos faltantes o ilegibles, código de barras del producto, nitidez y fotos repetidas.
2. **Claude Haiku 5.5** revisa las 3 fotos. Si ya está muy seguro (p_aprobar ≥ 0.92 o p_rechazo ≥ 0.56), decide.
3. **Claude Sonnet 5.5** revisa solo el resto: el 62–68% de las capturas en desarrollo.
4. **Fusión aprendida:** convierte lo que observa cada modelo en probabilidades calibradas. Umbrales elegidos por costo y con precisión de aprobaciones ≥ 97%.

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
