# Contrato móvil ↔ servidor (v0.3.0)

Qué manda la app, qué regresa el servidor y qué errores puede haber. Los archivos `.schema.json` y `openapi.json` se generan desde `servidor/app/contrato.py`: no se editan a mano.

**Servidor:** `https://validat3b-production.up.railway.app`

| Ruta | Para qué |
|---|---|
| `POST /v1/capturas` | Validar una captura |
| `GET /docs` | Documentación interactiva para probar desde el navegador |
| `GET /salud` | Estado del servidor: modo, modelos y si la IA está configurada |

## La petición

`POST /v1/capturas` como `multipart/form-data`:

| Campo | Tipo | Obligatorio | Contenido |
|---|---|---|---|
| `captura` | texto (JSON) | sí | Los datos de la captura: [`captura_entrada.schema.json`](captura_entrada.schema.json), ejemplo en [`ejemplos/captura_entrada.json`](ejemplos/captura_entrada.json) |
| `foto_fachada` | archivo | sí* | JPEG, PNG o WebP; máximo 8 MB |
| `foto_producto` | archivo | sí* | Igual |
| `foto_etiqueta` | archivo | sí* | Igual |

\* Si falta una foto, el servidor no truena: rechaza la captura con el motivo correspondiente.

- **Header opcional `X-API-Key`:** el servidor lo exige si tiene token configurado.
- **Query opcional `?modo=simulado`:** responde al instante sin gastar IA. Sirve para integrar la app.
  - Un `capture_id` que termina en `-APR`, `-REC` o `-REV` fuerza aprobada, rechazada o revisión manual.
- **Recomendación de fotos:** JPEG, lado mayor de hasta 1600 px, calidad 85. Pesan poco y se leen bien. No manden HEIC.

### Campos de `captura`

| Campo | Obligatorio | Nota |
|---|---|---|
| `capture_id` | sí | Único por captura, p. ej. `CAP-000123` |
| `producto_esperado` | sí | `sku`, `descripcion`, `marca`, `contenido`, `codigo_barras`, `es_contratipo`, `precio_referencia`, `precio_minimo`, `precio_maximo`, `unidad`, `cantidad`, `categoria`, `linea`; todos opcionales salvo el objeto. Son los campos del catálogo de 3B (`product_*`) |
| `cadena`, `tienda` | sí | Lo que seleccionó el capturista; `tienda_id` es opcional |
| `fecha_hora_captura` | sí | ISO 8601 con zona: `2026-11-21T10:42:05-06:00` |
| `codigo_barras_capturado` | no | El código que leyó la app |
| `precio_capturado` | no | El precio que tecleó el capturista (`capture_price`). Se compara contra la etiqueta |
| `marca_capturada` | no | La marca que capturó (`capture_brand`) |
| `metadatos_app` | no | `dispositivo`, `sistema_operativo`, `version_app`, `capturista_id`, `ubicacion` (`latitud`, `longitud`, `precision_m`). Acepta campos extra |
| `chequeos_locales` | no | Lo que ya validó el teléfono (ver abajo) |

### `chequeos_locales`: lo que ya validó el teléfono

Cada capa que corran en la app se reporta así:

```json
{ "nombre": "nitidez", "foto": "producto", "resultado": "ok", "puntaje": 0.91, "valor": null, "detalle": null }
```

- `resultado`: `ok`, `falla` o `duda`.
- `foto`: `fachada`, `producto`, `etiqueta` o `null`.
- **Nombres sugeridos:**
  - `nitidez`, `pantalla` (foto a otro celular), `distancia`
  - `codigo_barras` (en `valor`, el código leído)
  - `etiqueta_presente`, `precio_leido` (en `valor`, el precio)
  - `fachada_cadena`

El servidor los toma como pista y no confía ciegamente en ellos, porque puede haber fotos trampa.

## La respuesta (200)

Son los campos del formato oficial del reto más `traza`: [`respuesta_captura.schema.json`](respuesta_captura.schema.json). Ejemplo real de una captura con un precio que no cuadra:

```json
{
  "capture_id": "CAP-000123",
  "decision": "revision_manual",
  "confidence_score": 0.9,
  "reason_code": null,
  "validaciones": { "tienda_fachada": "ok", "producto": "ok", "codigo_barras": "ok", "evidencia_precio": "falla" },
  "precio_detectado": 18.5,
  "codigo_barras_detectado": "7501055363056",
  "explicacion": "Revisión manual: No se aprueba: El precio capturado ($21) no coincide con la etiqueta ($18.5).",
  "traza": {
    "version_motor": "0.1.0",
    "version_contrato": "0.3.0",
    "capa_decisiva": "fuerte",
    "modelos_usados": ["claude-haiku-5-5", "claude-sonnet-5-5"],
    "tokens_entrada": 9800, "tokens_salida": 900, "latencia_ms": 5200,
    "tipo_precio": "regular",
    "precio_unitario_detectado": 30.83,
    "promocion": null,
    "precio_regular": 18.5,
    "precio_promocion": null,
    "compuertas_fallidas": ["precio_coincide: El precio capturado ($21) no coincide con la etiqueta ($18.5)."],
    "doble_verificacion": false,
    "avisos": []
  }
}
```

### Qué usa la app de cada campo

| Campo | Qué es | Para qué le sirve a la app |
|---|---|---|
| `decision` | `aprobada`, `rechazada` o `revision_manual` | El resultado principal: verde, rojo o amarillo |
| `explicacion` | Una frase en español con el porqué | Mostrársela al capturista tal cual |
| `traza.avisos` | Instrucciones cortas («Acércate más», «La foto salió borrosa») | Pedir que repita la foto |
| `traza.compuertas_fallidas` | Cada regla que impidió aprobar, una por línea | Marcar qué corregir: precio, etiqueta, producto… Vacía si todo pasó |
| `validaciones` | `ok` o `falla` por frente: fachada, producto, código de barras, precio | Marcar en rojo la foto con problema |
| `reason_code` | Motivo oficial, p. ej. `CAPTURA_LEJANA`; `null` si se aprueba | Guardarlo o mostrar un ícono |
| `confidence_score` | De 0 a 1, qué tan segura es la decisión | Mostrar un nivel de confianza |
| `precio_detectado` | Precio que leyó la IA en la etiqueta | Compararlo con el que tecleó el capturista |
| `traza.tipo_precio`, `precio_regular`, `precio_promocion`, `promocion` | Regular, oferta o por volumen (2x1, 3x$50) | Mostrar la promoción detectada |
| `traza.precio_unitario_detectado` | Precio por kg, litro o pieza que leyó la IA | Informativo |
| `codigo_barras_detectado` | Código leído en la foto | Confirmarlo |
| `traza.doble_verificacion` | `true` si dos modelos confirmaron la aprobación | Sello de «verificada» |
| `traza.capa_decisiva` | Qué capa decidió: `local` (sin IA), `rapido` (Haiku), `fuerte` (segundo modelo), `respaldo` o `simulado` | Auditoría |
| `traza.modelos_usados`, `tokens_*`, `latencia_ms`, `version_*` | Quién decidió, cuánto costó y cuánto tardó | Auditoría y depuración; no es para el capturista |

### Comportamiento

- **Nunca se aprueba a ciegas.** Si la IA falla, tarda o un modelo no confirma, la respuesta sigue siendo **200** con `decision: "revision_manual"` y la explicación. No es un error HTTP.
- **Tiempo de respuesta en modo real:** de 3 a 6 segundos, según si interviene el segundo modelo. En modo simulado es inmediato.
- **Una captura solo se aprueba si pasan todas las compuertas** (precio, producto, etiqueta, calidad, fachada, código, foto no repetida y chequeos del teléfono). Lista completa en [`docs/costos-y-resultados.md`](../docs/costos-y-resultados.md).
- **Si mandan `chequeos_locales` con `resultado: "falla"`** en nitidez, distancia, etiqueta o precio, la captura no se aprueba: va a revisión.
- **Compatibilidad:** los campos nuevos se agregan sin quitar los anteriores. Lean la respuesta ignorando los campos desconocidos (en Kotlin, `ignoreUnknownKeys = true`).

Ejemplos completos: [`respuesta_aprobada.json`](ejemplos/respuesta_aprobada.json), [`respuesta_rechazada.json`](ejemplos/respuesta_rechazada.json) y [`respuesta_revision_manual.json`](ejemplos/respuesta_revision_manual.json).

### `reason_code` provisionales

Se cambian por la lista oficial cuando llegue con los datos.

| Frente | Códigos |
|---|---|
| A. Producto | `CONTRATIPO_INVALIDO`, `PRODUCTO_NO_COINCIDE`, `DESCRIPCION_INCOMPLETA`, `PRODUCTO_NO_VISIBLE` |
| B. Evidencia | `FACHADA_INCORRECTA`, `CAPTURA_LEJANA`, `IMAGEN_BORROSA`, `FOTO_DE_PANTALLA` |
| C. Precio | `SIN_ETIQUETA_PRECIO`, `SIN_PRECIO_UNITARIO` |

## Errores

Todos los errores llegan con la misma forma: [`error.schema.json`](error.schema.json).

```json
{ "error": { "codigo": "CAPTURA_INVALIDA", "mensaje": "El campo captura no cumple el contrato.", "detalles": ["producto_esperado: Field required"] } }
```

| HTTP | `codigo` | Cuándo |
|---|---|---|
| 400 | `CAPTURA_NO_ES_JSON` | El campo `captura` no es JSON |
| 401 | `NO_AUTORIZADO` | Falta o no coincide `X-API-Key` |
| 413 | `FOTO_MUY_GRANDE` | Una foto pesa más de 8 MB |
| 415 | `TIPO_NO_SOPORTADO` | La foto no es JPEG, PNG ni WebP |
| 422 | `CAPTURA_INVALIDA`, `PETICION_INVALIDA` | Faltan campos o tienen otro tipo; `detalles` dice cuáles |

## Probarlo

```bash
curl -X POST "$SERVIDOR/v1/capturas?modo=simulado" \
  -F "captura=@ejemplos/captura_entrada.json;type=application/json" \
  -F "foto_fachada=@fachada.jpg;type=image/jpeg" \
  -F "foto_producto=@producto.jpg;type=image/jpeg" \
  -F "foto_etiqueta=@etiqueta.jpg;type=image/jpeg"
```

Desde JavaScript (React Native, Expo o web):

```js
const form = new FormData();
form.append("captura", JSON.stringify(captura));
form.append("foto_fachada",  { uri: fachadaUri,  name: "fachada.jpg",  type: "image/jpeg" });
form.append("foto_producto", { uri: productoUri, name: "producto.jpg", type: "image/jpeg" });
form.append("foto_etiqueta", { uri: etiquetaUri, name: "etiqueta.jpg", type: "image/jpeg" });
const r = await fetch(`${SERVIDOR}/v1/capturas?modo=simulado`, { method: "POST", body: form });
const resultado = await r.json();   // RespuestaCaptura o { error }
```

- **Documentación interactiva:** `$SERVIDOR/docs`.
- **Estado del servidor:** `GET $SERVIDOR/salud`.

## Cambios

- **Motor 0.4.2** (sin cambios en el contrato): solo impiden aprobar los chequeos del teléfono `pantalla`, `nitidez`, `distancia`, `etiqueta_presente` y `codigo_foto_coincide` cuando llegan en `falla`. `codigo_barras` y `precio_leido` en `falla` ya no bloquean, porque el teléfono los marca así cuando no alcanzó a leer y el código es opcional. Si no llega `precio_capturado`, el servidor usa el `valor` de `precio_leido` solo si viene con `resultado: ok`. Recomendación para la app: «falla» solo cuando hay evidencia de un problema; si el chequeo no pudo concluir, «duda».
- **0.3.0** (aditivo): en `traza` vienen `compuertas_fallidas`, `precio_regular`, `precio_promocion` y `doble_verificacion`. **Pídanle a la app que mande `precio_capturado`**: si no viene, el servidor usa el `valor` del chequeo `precio_leido`.
- **0.2.0** (aditivo): `precio_capturado`, `marca_capturada` y los campos del catálogo dentro de `producto_esperado`, con los mismos datos que trae el set oficial de 3B. Lo que ya mandaban sigue funcionando.

## Versionado

- **Cambio aditivo** (campo nuevo opcional, código nuevo): sube el menor, p. ej. `0.2.0`. No rompe la app.
- **Cambio que rompe** (quitar o renombrar un campo, volver obligatorio uno opcional): sube el mayor y se avisa antes.
