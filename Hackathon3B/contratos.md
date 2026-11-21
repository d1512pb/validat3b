# Contrato app ↔ servidor (v0.1.0): guía para la app Android

Guía para implementar en esta app (Kotlin + Compose) la comunicación con el servidor de Validat3B. La fuente de verdad es la carpeta [`../contratos/`](../contratos/). Si algo de aquí no coincide con ella, manda la carpeta. Los `.schema.json` y `openapi.json` se generan desde `servidor/app/contrato.py`, así que no se editan a mano.

Leyenda:
- **Contrato:** lo que dice la carpeta `contratos/`.
- **Recomendación:** sugerencia para la app; el contrato no la exige.

---

## 1. Resumen

| Qué | Valor |
|---|---|
| Endpoint principal | `POST {SERVIDOR}/v1/capturas` |
| Formato de la petición | `multipart/form-data`: 1 parte de texto (`captura`, JSON) + 3 fotos |
| Respuesta exitosa | `200` con JSON `RespuestaCaptura` |
| Errores | `400`, `401`, `413`, `415`, `422`, siempre con JSON `ErrorRespuesta` |
| Salud | `GET {SERVIDOR}/salud` → `200` con un objeto JSON libre |
| Documentación interactiva | `{SERVIDOR}/docs` |
| Versión del contrato | `0.1.0` |

Lugar de la app en el flujo general (README raíz):

```
foto ──▶ capas locales (app) ──▶ ¿se resolvió con confianza?
                                   ├─ sí ──▶ decisión
                                   └─ no ──▶ POST /v1/capturas ──▶ decisión
```

---

## 2. La petición: `POST /v1/capturas`

### 2.1 Partes del multipart

| Parte | Tipo | Obligatoria | Contenido |
|---|---|---|---|
| `captura` | texto (JSON serializado) | **sí** | Objeto `CapturaEntrada` (sección 3) |
| `foto_fachada` | archivo | sí* | JPEG, PNG o WebP, máximo 8 MB |
| `foto_producto` | archivo | sí* | Igual |
| `foto_etiqueta` | archivo | sí* | Igual |

\* En el OpenAPI las fotos aparecen como opcionales: si falta una, el servidor no responde con error HTTP, sino que **rechaza la captura** (`200` con `decision: "rechazada"`). La app siempre debe mandar las tres.

Detalles que importan al armar las partes:
- **Nombres exactos:** los nombres de parte son exactamente `captura`, `foto_fachada`, `foto_producto` y `foto_etiqueta`, en minúsculas y con guion bajo.
- **Content-Type de cada foto:** `image/jpeg`, `image/png` o `image/webp`. Con cualquier otro, incluido `application/octet-stream`, el servidor regresa `415 TIPO_NO_SOPORTADO`. En OkHttp hay que poner el media type explícitamente en cada parte de archivo.
- **Nombre de archivo:** cada foto debe ir con uno, p. ej. `fachada.jpg`, `producto.jpg` y `etiqueta.jpg`.
- **Parte `captura`:** es texto con el JSON serializado. El ejemplo `curl` del contrato la manda con `type=application/json`, y en la versión JS va como string plano. Las dos formas sirven.
- **HEIC:** no se manda. Hay que convertir antes.

### 2.2 Header y query

| Elemento | Dónde | Obligatorio | Nota |
|---|---|---|---|
| `X-API-Key` | header | depende | El servidor lo exige solo si tiene un token configurado. Si falta o no coincide: `401 NO_AUTORIZADO` |
| `modo` | query | no | `real` (default) o `simulado` |

**Modo simulado (`?modo=simulado`).** Responde al instante, sin gastar IA, y en la respuesta viene `traza.capa_decisiva = "simulado"`. Sirve para integrar y probar la app. El resultado se fuerza con el sufijo del `capture_id`:

| Sufijo de `capture_id` | Resultado forzado |
|---|---|
| `-APR` (p. ej. `CAP-000123-APR`) | `aprobada` |
| `-REC` | `rechazada` |
| `-REV` | `revision_manual` |

### 2.3 Preparación de las fotos (recomendación del contrato)

- **Formato:** JPEG, calidad 85.
- **Tamaño:** lado mayor de hasta 1600 px. Así pesan poco, el OCR las lee bien y quedan muy por debajo del límite de 8 MB.
- **Orientación (recomendación):** corregirla según EXIF antes de comprimir, para que el servidor no reciba fotos giradas.

---

## 3. El JSON `captura` (`CapturaEntrada`)

Schema: [`captura_entrada.schema.json`](../contratos/captura_entrada.schema.json). Ejemplo: [`ejemplos/captura_entrada.json`](../contratos/ejemplos/captura_entrada.json).

### 3.1 Campos raíz

| Campo | Tipo | Obligatorio | Reglas / nota |
|---|---|---|---|
| `version_contrato` | string | no (default `"0.1.0"`) | Recomendación: mandarlo siempre como `"0.1.0"` |
| `capture_id` | string | **sí** | De 1 a 64 caracteres, único por captura. Ej.: `CAP-000123` |
| `producto_esperado` | objeto `ProductoEsperado` | **sí** | El objeto es obligatorio, aunque todos sus campos sean opcionales |
| `cadena` | string | **sí** | Cadena que eligió el capturista, p. ej. `"Bodega Aurrerá"` |
| `tienda` | string | **sí** | Nombre o id de la tienda elegida |
| `tienda_id` | string \| null | no | |
| `fecha_hora_captura` | string `date-time` | **sí** | ISO 8601 **con zona horaria**, p. ej. `2026-11-21T10:42:05-06:00`. Nunca sin offset |
| `codigo_barras_capturado` | string \| null | no | El código que leyó la app |
| `metadatos_app` | objeto `MetadatosApp` | no | |
| `chequeos_locales` | lista de `ChequeoLocal` | no | Lo que ya validó el teléfono |

El objeto raíz acepta campos extra (`additionalProperties: true`).

### 3.2 `producto_esperado` (`ProductoEsperado`)

Es el producto o contratipo que el capturista dice estar fotografiando. Acepta campos extra.

| Campo | Tipo | Default | Nota |
|---|---|---|---|
| `sku` | string \| null | null | SKU del catálogo de referencia, p. ej. `3B-000451` |
| `descripcion` | string \| null | null | Descripción del catálogo |
| `marca` | string \| null | null | |
| `contenido` | string \| null | null | Presentación, p. ej. `"600 ml"` |
| `codigo_barras` | string \| null | null | Código de barras del catálogo |
| `es_contratipo` | boolean | `false` | `true` si se captura un contratipo y no el producto 3B |

### 3.3 `metadatos_app` (`MetadatosApp`)

Acepta campos extra, así que la app puede agregar los que le sirvan.

| Campo | Tipo | Nota |
|---|---|---|
| `dispositivo` | string \| null | Modelo del teléfono, p. ej. `"Samsung Galaxy A15"` (`Build.MANUFACTURER` + `Build.MODEL`) |
| `sistema_operativo` | string \| null | P. ej. `"Android 15"` |
| `version_app` | string \| null | `versionName` de la app |
| `capturista_id` | string \| null | P. ej. `"CAPT-017"` |
| `ubicacion` | objeto \| null | Ver abajo |

`ubicacion`:

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `latitud` | number | sí, si se manda `ubicacion` | De -90 a 90 |
| `longitud` | number | sí, si se manda `ubicacion` | De -180 a 180 |
| `precision_m` | number \| null | no | Mayor o igual a 0; precisión del GPS en metros |

Si no hay ubicación, se manda `ubicacion: null` o se omite. No hay que mandarla con `0,0`.

### 3.4 `chequeos_locales` (`ChequeoLocal`)

Reporta cada capa de validación que corrió en el teléfono. El servidor los usa **como pista**: no confía ciegamente en ellos, porque puede haber fotos trampa.

| Campo | Tipo | Obligatorio | Valores |
|---|---|---|---|
| `nombre` | string | **sí** | Ver los nombres sugeridos |
| `resultado` | string | **sí** | `ok`, `falla` o `duda` |
| `foto` | string \| null | no | `fachada`, `producto`, `etiqueta` o `null` si aplica a toda la captura |
| `puntaje` | number \| null | no | Confianza del chequeo, de 0 a 1 |
| `valor` | string \| null | no | Lo que se leyó (código, precio…). Siempre como **string** |
| `detalle` | string \| null | no | Texto libre |

Nombres sugeridos por el contrato (usar estos mismos para que el servidor los reconozca):

| `nombre` | Para qué | `foto` típica | `valor` |
|---|---|---|---|
| `nitidez` | Foto borrosa | cualquiera | — |
| `pantalla` | Foto tomada a otra pantalla | cualquiera | — |
| `distancia` | Captura demasiado lejana | `producto` / `etiqueta` | — |
| `codigo_barras` | Lectura del código | `producto` | El código leído |
| `etiqueta_presente` | Se ve la etiqueta de precio | `etiqueta` | — |
| `precio_leido` | Lectura del precio | `etiqueta` | El precio, como texto, p. ej. `"42.50"` |
| `fachada_cadena` | La fachada es de la cadena elegida | `fachada` | — |

Ejemplo:

```json
{ "nombre": "nitidez", "foto": "producto", "resultado": "ok", "puntaje": 0.91, "valor": null, "detalle": null }
```

---

## 4. Respuesta exitosa (`200`): `RespuestaCaptura`

Schema: [`respuesta_captura.schema.json`](../contratos/respuesta_captura.schema.json). Son los campos del formato oficial del reto más `traza`.

### 4.1 Campos

| Campo | Tipo | Siempre viene | Nota |
|---|---|---|---|
| `capture_id` | string | sí | El mismo que mandó la app. Sirve para emparejar la respuesta |
| `decision` | enum | sí | `aprobada`, `rechazada` o `revision_manual` |
| `confidence_score` | number | sí | De 0 a 1 |
| `reason_code` | string \| null | no (default null) | `null` cuando se aprueba |
| `validaciones` | objeto | sí | Cuatro estados, cada uno `ok` o `falla` (abajo) |
| `precio_detectado` | number \| null | no | P. ej. `42.5` |
| `codigo_barras_detectado` | string \| null | no | |
| `explicacion` | string | sí | Frase legible del porqué |
| `traza` | objeto `Traza` | sí | Datos extra para auditar y para la app |

`validaciones` (los cuatro campos siempre vienen):

| Campo | Valores | Foto relacionada (recomendación para la UI) |
|---|---|---|
| `tienda_fachada` | `ok` / `falla` | `foto_fachada` |
| `producto` | `ok` / `falla` | `foto_producto` |
| `codigo_barras` | `ok` / `falla` | `foto_producto` (o el código capturado) |
| `evidencia_precio` | `ok` / `falla` | `foto_etiqueta` |

`traza`:

| Campo | Tipo | Siempre viene | Nota |
|---|---|---|---|
| `version_motor` | string | sí | |
| `capa_decisiva` | enum | sí | `local`, `rapido` (Haiku), `fuerte` (Sonnet), `respaldo` (por error) o `simulado` |
| `version_contrato` | string | no (default `"0.1.0"`) | |
| `modelos_usados` | lista de string | no | |
| `tokens_entrada`, `tokens_salida`, `latencia_ms` | int | no (default 0) | |
| `tipo_precio` | enum \| null | no | `regular`, `oferta`, `volumen` o `desconocido` |
| `precio_unitario_detectado` | number \| null | no | |
| `promocion` | string \| null | no | Texto de la promo, p. ej. `"2x$50"` |
| `avisos` | lista de string | no | **Mensajes cortos para mostrarle al capturista**, p. ej. «Acércate más» |

### 4.2 Cómo usar la respuesta en la app

| `decision` | Qué hacer (recomendación) |
|---|---|
| `aprobada` | Marcar la captura como válida. `reason_code` llega `null` |
| `rechazada` | Mostrar `explicacion` y `traza.avisos`, y pedir que repita la foto afectada (ver `validaciones` y `reason_code`) |
| `revision_manual` | La captura queda pendiente de revisión humana. Mostrar `explicacion`; si hay `avisos`, se puede ofrecer repetir la foto |

- **`traza.avisos`:** es el texto pensado para el capturista. Si viene vacío, usar `explicacion`.
- **Foto a repetir:** sale de las `validaciones` en `falla` (tabla 4.1) o del `reason_code` (tabla 4.3).
- **`traza` no va al archivo oficial.** Si la app guarda resultados en el formato oficial ([`resultado_oficial.schema.json`](../contratos/resultado_oficial.schema.json)), son exactamente los mismos campos **sin** `traza`.

### 4.3 `reason_code` provisionales

Son provisionales: se cambian por la lista oficial cuando llegue con los datos. **No hay que modelarlos como un enum cerrado.** Hay que tratarlos como string y tener un caso por defecto para los códigos desconocidos.

| Frente | Código | Foto a repetir (recomendación) |
|---|---|---|
| A. Producto | `CONTRATIPO_INVALIDO` | producto |
| | `PRODUCTO_NO_COINCIDE` | producto |
| | `DESCRIPCION_INCOMPLETA` | producto |
| | `PRODUCTO_NO_VISIBLE` | producto |
| B. Evidencia | `FACHADA_INCORRECTA` | fachada |
| | `CAPTURA_LEJANA` | la de la validación en `falla` |
| | `IMAGEN_BORROSA` | la de la validación en `falla` |
| | `FOTO_DE_PANTALLA` | la de la validación en `falla` |
| C. Precio | `SIN_ETIQUETA_PRECIO` | etiqueta |
| | `SIN_PRECIO_UNITARIO` | etiqueta |

Ejemplos completos:
- [`respuesta_aprobada.json`](../contratos/ejemplos/respuesta_aprobada.json)
- [`respuesta_rechazada.json`](../contratos/ejemplos/respuesta_rechazada.json)
- [`respuesta_revision_manual.json`](../contratos/ejemplos/respuesta_revision_manual.json)

---

## 5. Errores HTTP: `ErrorRespuesta`

Schema: [`error.schema.json`](../contratos/error.schema.json). Todos los errores tienen la misma forma:

```json
{ "error": { "codigo": "CAPTURA_INVALIDA", "mensaje": "El campo captura no cumple el contrato.", "detalles": ["producto_esperado: Field required"] } }
```

Campos:
- `codigo`: string, siempre viene.
- `mensaje`: string, siempre viene.
- `detalles`: lista de string, opcional.

| HTTP | `codigo` | Cuándo | Qué hacer en la app (recomendación) |
|---|---|---|---|
| 400 | `CAPTURA_NO_ES_JSON` | La parte `captura` no es JSON válido | Bug de la app: registrarlo y no reintentar igual |
| 401 | `NO_AUTORIZADO` | Falta o no coincide `X-API-Key` | Revisar la configuración de la llave; no reintentar |
| 413 | `FOTO_MUY_GRANDE` | Una foto pesa más de 8 MB | Recomprimir (sección 2.3) y reintentar |
| 415 | `TIPO_NO_SOPORTADO` | La foto no es JPEG, PNG ni WebP, o el Content-Type está mal | Convertir a JPEG y revisar el media type de la parte |
| 422 | `CAPTURA_INVALIDA`, `PETICION_INVALIDA` | Faltan campos o tienen otro tipo | Bug de la app: `detalles` dice cuáles |

Notas:
- **Códigos desconocidos:** el schema muestra `FOTO_INVALIDA` como ejemplo de `codigo` y el OpenAPI describe el 400 como «Captura o fotos inválidas». Hay que manejar cualquier `codigo` desconocido con un caso genérico que muestre `mensaje`.
- **Respuestas fuera del contrato:** los errores de red, los timeouts y los `5xx` no están en el contrato y pueden llegar sin cuerpo JSON. Recomendación: tratarlos como «sin respuesta», dejar la captura en cola para reintentar y **nunca** marcarla como aprobada. Si no se puede reintentar, queda como `revision_manual`, según el principio del proyecto: aprobar una inválida cuesta 10 puntos y la revisión manual cuesta 1.

---

## 6. Reglas de robustez para el cliente

- **Ignorar campos desconocidos al leer.** Según la política de versionado, un cambio menor (`0.2.0`) puede agregar campos opcionales o códigos nuevos sin avisar, y la app no debe tronar. Con kotlinx.serialization esto es `ignoreUnknownKeys = true`.
- **Defaults en lo opcional.** Todo lo que no está en «siempre viene» puede faltar o llegar `null`.
- **Enums tolerantes.** `decision`, `Estado` y `capa_decisiva` son enums cerrados en v0.1.0, pero conviene un valor «desconocido» de respaldo. Si llega una `decision` desconocida, tratarla como `revision_manual`.
- **`capture_id` estable.** Se genera una vez por captura, se persiste y se reutiliza en los reintentos. Sirve para emparejar la respuesta.
- **Timeouts.** En modo `real` el servidor puede llamar a uno o dos modelos de IA; en el ejemplo, `latencia_ms` es de unos 2.4 s, pero puede tardar más. Recomendación: timeout de lectura de al menos 60 s y subida de fotos fuera del hilo principal.

---

## 7. Notas específicas de esta app Android

Son recomendaciones para cuando se implemente; aquí no se programa nada.

- **Permiso de red:** el `AndroidManifest.xml` todavía no declara `android.permission.INTERNET`; hay que agregarlo.
- **HTTP local:** si en desarrollo el servidor corre en `http://` (sin TLS), Android bloquea el tráfico en claro. Hace falta un `network_security_config` que lo permita solo para el host de desarrollo.
- **Emulador:** el `localhost` de la PC se alcanza como `10.0.2.2`. En un teléfono físico hay que usar la IP de la PC en la red local.
- **Configuración:** `SERVIDOR` y `X-API-Key` no van en el repositorio. Pueden ir en `local.properties`, que ya está ignorado, y exponerse como `BuildConfig`. Cambiar entre `modo=simulado` y `modo=real` también debe ser configurable, p. ej. simulado en `debug`.
- **Librerías sugeridas:** OkHttp (multipart), Retrofit (opcional) y kotlinx.serialization para JSON. Hay que respetar las reglas de [`CLAUDE.md`](CLAUDE.md):
  - Versiones en `gradle/libs.versions.toml`.
  - Sin bloques `repositories {}` en el módulo.
  - AGP 9 con Kotlin integrado, así que para serialización solo se agrega el plugin `org.jetbrains.kotlin.plugin.serialization`.
  - Si R8 elimina clases de serialización, las reglas van en `app/src/main/keepRules/`.
- **Modelos sugeridos:** un modelo Kotlin por objeto del contrato. Los nombres de los campos JSON van en `snake_case`, igual que en el contrato; usar `@SerialName` si las propiedades Kotlin van en camelCase.

| Objeto del contrato | Dirección |
|---|---|
| `CapturaEntrada`, `ProductoEsperado`, `MetadatosApp`, `Ubicacion`, `ChequeoLocal` | App → servidor |
| `RespuestaCaptura`, `Validaciones`, `Traza` | Servidor → app |
| `ErrorRespuesta`, `DetalleError` | Servidor → app (errores) |

---

## 8. Cómo probar la integración

1. Verificar que el servidor responde: `GET {SERVIDOR}/salud`.
2. Mandar `ejemplos/captura_entrada.json` con tres fotos cualquiera a `?modo=simulado`.
3. Repetir con `capture_id` terminados en `-APR`, `-REC` y `-REV`, y comparar las respuestas contra los JSON de `contratos/ejemplos/`.
4. Provocar errores a propósito:
   - Quitar `producto_esperado` → `422`.
   - Mandar una foto con Content-Type `application/octet-stream` → `415`.
   - Mandar una foto de más de 8 MB → `413`.
   - Usar una `X-API-Key` incorrecta → `401`, si el servidor tiene token.
5. Al final, probar en modo `real` con fotos de prueba.

Equivalente en `curl` (desde la carpeta `contratos/`):

```bash
curl -X POST "$SERVIDOR/v1/capturas?modo=simulado" \
  -F "captura=@ejemplos/captura_entrada.json;type=application/json" \
  -F "foto_fachada=@fachada.jpg;type=image/jpeg" \
  -F "foto_producto=@producto.jpg;type=image/jpeg" \
  -F "foto_etiqueta=@etiqueta.jpg;type=image/jpeg"
```

---

## 9. Versionado y preguntas abiertas

**Versionado (contrato).**
- **Cambio aditivo** (campo opcional nuevo, código nuevo): sube el menor, p. ej. `0.2.0`, y no rompe la app.
- **Cambio que rompe** (quitar o renombrar un campo, volver obligatorio uno opcional): sube el mayor y se avisa antes.

**Preguntas abiertas para acordar con el servidor:**
1. **Reintentos:** ¿qué pasa si la app reenvía el mismo `capture_id` tras un timeout? El contrato no dice si es idempotente.
2. **Decisiones locales:** cuando la app decide sola (sin servidor), ¿con qué formato se reporta y a dónde va? El contrato solo cubre lo que se resuelve en el servidor; `capa_decisiva: "local"` sugiere que el servidor también puede decidir con los chequeos locales.
3. **Lista oficial de `reason_code`:** llega con los datos del reto.
4. **Errores de servidor:** ¿los `5xx` también regresan `ErrorRespuesta`, o la app debe esperar cualquier cuerpo?
5. **Precio en los chequeos:** ¿qué formato exacto de texto espera el servidor en `valor` para `precio_leido` (p. ej. `"42.50"` frente a `"$42.50"`)?
