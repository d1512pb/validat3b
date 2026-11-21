# Registro de pruebas del motor

21 de noviembre de 2026. Todas las pruebas usan las **100 capturas etiquetadas de desarrollo** del reto: 33 aprobadas, 34 rechazadas y 33 a revisión manual. Las imágenes y los metadatos de 3B **no** están en el repositorio (`datos/` va en `.gitignore`). Aquí solo quedan resultados agregados.

- [`peor-caso.md`](peor-caso.md): **tabla simple para el pitch**, con el costo por imagen en el peor caso (todas las capturas pasan por el modelo) y la precisión de cada modelo.
- [`robustez.md`](robustez.md) y [`robustez-fusion.md`](robustez-fusion.md): la elección de la segunda capa, medida con 10 particiones distintas.
- [`comparacion.md`](comparacion.md): la comparación final entre configuraciones (generada con `scripts/comparar.py`).
- [`reportes/`](reportes/): el `reporte.json` de cada corrida (tokens, tiempo y evaluación agregada).

## Cómo se mide

- **Costo operativo:** la tabla oficial. Aprobar o rechazar bien cuesta 0, revisión manual 1, rechazar una válida 3 y aprobar una inválida 10.
  - **Supuesto pendiente:** si la verdad es revisión manual, mandarla a revisión cuesta 1, rechazarla 3 y aprobarla 10. El script oficial lo confirmará.
  - Si el script cuenta 0 por una revisión correcta, todos los costos de abajo bajan ~31 puntos.
- **VP / VN / FP / FN** («positivo» = aprobar):
  - **VP:** aprobada y era válida.
  - **VN:** rechazada y era rechazo.
  - **FP:** aprobada y no era válida. Es el error caro (10).
  - **FN:** rechazada y era válida (3).
  - Las revisiones manuales se cuentan aparte (1 cada una).
- **Fuera de muestra:**
  - Cada modelo se corre una vez sobre las 100 capturas.
  - Con esas observaciones se calibra la fusión, los umbrales y el clasificador de motivo por **validación cruzada 5×5**: las probabilidades de cada captura salen de modelos que no la vieron.
  - Las cascadas se simulan combinando esas probabilidades.
  - Los umbrales exigen precisión de aprobaciones ≥ 97% (también en el umbral menos 0.05, como margen).
- **Dentro de muestra:** las corridas `dev_final_*` pasan las 100 capturas por el motor real con los modelos ya calibrados con esas mismas capturas. Sirven para comprobar que el motor funciona de punta a punta, pero su costo es optimista.
- **Ruido:** con 100 capturas, diferencias de ±5 a 8 puntos todavía pueden ser ruido.
- **Dinero:** tokens reales de cada llamada, a precios de lista por millón de tokens: Haiku 5.5 cuesta $0.10 de entrada y $0.50 de salida; Sonnet 5.5, $2 y $10. «Haiku razonando» es el mismo Haiku con razonamiento adaptativo; ese razonamiento se cobra como tokens de salida.

## Comparación final (prompt v3, con compuertas)

Fuera de muestra, 100 capturas. Detalle completo en [`comparacion.md`](comparacion.md).

| Configuración | Costo operativo | VP | VN | FP | FN | Tokens/captura | USD/imagen | USD por 10,000 capturas |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Todo a revisión manual (referencia) | 100 | 0 | 0 | 0 | 0 | 0 | $0 | $0 |
| Solo Haiku (sin razonar) | 83 | 0 | 19 | **0** | **0** | 6,631 | $0.00029 | $8.75 |
| **Solo Haiku razonando** | **71** | 14 | 17 | **0** | **0** | **7,429** | $0.00042 | **$12.74** |
| **Cascada Haiku → Haiku razonando + doble llave** | **66** | 14 | 22 | **0** | **0** | 12,514 | $0.00062 | **$18.64** |
| Solo Sonnet | 73 | 8 | 23 | **0** | **0** | 6,667 | $0.00595 | $178.61 |
| Cascada Haiku → Sonnet + doble llave (desplegada) | 69 | 8 | 27 | **0** | **0** | 11,818 | $0.00489 | $146.77 |

Medidas dentro de muestra con el motor real, las tres configuraciones finales dan 52 puntos y 0 FP y 0 FN. Solo Haiku sin razonar da 69.

## Análisis: ¿vale la pena pagar Sonnet?

**No, con estos datos.** Haiku razonando da la misma calidad que Sonnet, o mejor, por 8 a 14 veces menos dinero.

1. **Seguridad:** las seis configuraciones tienen **0 FP y 0 FN** fuera de muestra. Las compuertas y la doble llave cumplen: ninguna aprueba algo inválido. Lo que cambia entre ellas es cuántas capturas válidas se atreven a aprobar (VP) en lugar de mandarlas a revisión.
2. **Costo operativo:** Haiku razonando (71) y su cascada (66) quedan igual o mejor que Sonnet (73) y su cascada (69). Las diferencias están dentro del ruido. Haiku **sin** razonar sí se queda atrás (83): no se atreve a aprobar ninguna válida.
3. **Dinero:**

   | | 10,000 capturas (30,000 fotos) | 10,000 fotos |
   |---|---:|---:|
   | Configuraciones con Sonnet | $147–179 | $49–60 |
   | Configuraciones con Haiku razonando | $13–19 | $4–6 |

4. **Tokens** (el criterio de eficiencia vale 10%): Haiku razonando solo usa ~7,400 tokens por captura; las cascadas, ~12,000, porque llaman a dos modelos en casi 8 de cada 10 capturas.

**Recomendación** (a decidir con lo que agregue móvil):
- **Para el menor costo operativo:** cascada **Haiku → Haiku razonando** con doble llave. Cuesta 66 puntos y $18.64 por 10,000 capturas.
- **Para menos tokens y un motor más simple:** **Haiku razonando solo**. Cuesta 71 puntos, $12.74 por 10,000 y usa 40% menos tokens que la cascada.
- **Sonnet queda solo como opción** si en el set ciego aparecen casos que Haiku no resuelva. Cambiarlo es una variable de entorno.

**Riesgos:**
- Son 100 capturas.
- El set ciego puede tener casos que aquí no aparecen: contratipos, fotos de pantalla, precios distintos.
- La regla de costo de las revisiones está por confirmar.
- La señal de cadena (solo se aprueban Soriana, Chedraui y Neto) puede ser un artefacto de la muestra.

**Lo que puede aportar móvil:** si la app manda niveles de confianza de las imágenes en `chequeos_locales.puntaje` (nitidez, distancia, iluminación), entran como señales a la fusión. Con eso habría menos capturas válidas en revisión, sin aflojar las compuertas.

## Elección de la segunda capa (10 particiones)

**Decisión del equipo: Haiku → Haiku razonando (esfuerzo medio) con doble llave.** Es lo que corre en Railway desde el 21-nov-2026.

Con 100 capturas, una sola validación cruzada mueve el costo ±5 puntos. Para separar lo real del ruido, cada configuración se midió con **10 particiones distintas** y se compararon por pares, con la misma partición para todas ([`robustez.md`](robustez.md) y [`robustez-fusion.md`](robustez-fusion.md)).

| Configuración | Costo operativo (media ± desv.) | FP | FN | Acierto general | Tokens/captura | vs. Haiku razonando solo |
|---|---:|---:|---:|---:|---:|---|
| Haiku solo | 81.7 ± 1.9 | 0 | 0 | 53% | 6,631 | +11.4; peor en 10 de 10 |
| Haiku razonando (medio) solo | 70.3 ± 5.0 | 0 | 0 | 66% | 7,429 | — |
| **Cascada Haiku → Haiku razonando (medio)** | **68.3 ± 5.0** | **0** | **0** | **68%** | 12,304 | **−2.0; mejor en 10 de 10** |
| Haiku razonando (alto) solo | 68.9 ± 3.5 | 0 | 0.3 | 69% | 8,319 | −1.4; mejor en 7 de 10 |
| Cascada Haiku → Haiku razonando (alto) | 68.1 ± 3.5 | 0 | 0.3 | 70% | 13,050 | −2.2; mejor en 8 de 10 |
| Cascada Haiku → fusión de las dos lecturas | 76.1 ± 6.0 | 0 | 0.1 | 60% | 12,359 | +5.8; peor en 9 de 10 |

**Lectura:**
- **La cascada le gana a Haiku razonando solo en las 10 particiones,** pero por poco: 2 puntos por cada 100 capturas, con 66% más tokens.
  - Vale la pena si cada revisión humana cuesta más que los tokens extra.
  - Con ~1 peso por imagen revisada (dato de finanzas de 3B), 2 revisiones menos por cada 100 capturas ahorran ~2–6 pesos. Los tokens extra cuestan ~$0.09 de dólar, unos 1.6 pesos a ~18 pesos por dólar.
- **Esfuerzo alto no mejora de forma clara:** da el mismo costo con más tokens, y en algunas particiones rechaza una captura válida (FN 0.3 en promedio). No se adopta.
- **Fusión de las dos lecturas** (que la decisión final vea a Haiku y a Haiku razonando juntos): empeora. Con 100 capturas, el doble de señales sobreajusta. No se adopta.
- **Opus no se probó.** El costo humano por revisión lo haría pagable, pero Haiku razonando ya iguala a Sonnet. Queda como opción si el set ciego trae casos difíciles.

**Finanzas:** a ~1 peso por imagen revisada, cada captura que se resuelve sin humano ahorra unos 3 pesos (3 imágenes). La cascada cuesta ~$0.0019 de dólar por captura, unos 0.03 pesos. En 10,000 capturas resuelve sola ~3,600: ahorra del orden de 10,000 pesos de revisión, contra unos 340 pesos de IA.

## Cómo cambiar la configuración del servidor

Todo se hace con variables de entorno (Railway → Variables). Los modelos calibrados de cada opción ya están en `servidor/modelos/`.

| Configuración | `MODELO_RAPIDO` | `MODELO_FUERTE` | `ESCALAR_A_FUERTE` | `VERIFICAR_APROBACIONES` |
|---|---|---|---|---|
| **Cascada Haiku → Haiku razonando (desplegada)** | `claude-haiku-5-5` | `claude-haiku-5-5-pensar` | `true` | `true` |
| Cascada Haiku → Haiku razonando con esfuerzo alto | `claude-haiku-5-5` | `claude-haiku-5-5-pensar-alto` | `true` | `true` |
| Cascada Haiku → Sonnet | `claude-haiku-5-5` | `claude-sonnet-5-5` | `true` | `true` |
| Solo Haiku razonando | `claude-haiku-5-5-pensar` | (no se usa) | `false` | `false` |
| Solo Haiku sin razonar | `claude-haiku-5-5` | (no se usa) | `false` | `false` |

## Cómo reproducir

Desde `servidor/`, con los datos de desarrollo en `../datos/desarrollo/` y la llave en `servidor/.env`:

```bash
# 1. Una corrida por modelo, sin escalar ni verificar (con caché: repetirla no gasta)
for M in claude-haiku-5-5 claude-haiku-5-5-pensar claude-sonnet-5-5; do
  ESCALAR_A_FUERTE=false VERIFICAR_APROBACIONES=false MODELO_RAPIDO=$M \
  .venv/Scripts/python -m scripts.correr_lote --formato oficial --datos ../datos/desarrollo/capturas.json \
    --salida ../datos/corridas/dev_$M --trabajadores 6
done
# 2. Calibrar cada modelo (fusión, umbrales, motivo) y guardarlo en modelos/<modelo>/
.venv/Scripts/python -m scripts.calibrar --corrida ../datos/corridas/dev_claude-haiku-5-5 --guardar
# 3. Umbrales de la cascada con doble llave para cada segundo modelo
.venv/Scripts/python -m scripts.simular_cascada --rapido ../datos/corridas/dev_claude-haiku-5-5 \
  --fuerte ../datos/corridas/dev_claude-haiku-5-5-pensar --modelo-fuerte claude-haiku-5-5-pensar --verificar --guardar
# 4. Comparación fuera de muestra con desglose VP/VN/FP/FN y dinero
.venv/Scripts/python -m scripts.comparar --salida ../docs/pruebas/comparacion.md
```

`scripts.comparar` espera las corridas con los nombres de este registro (`dev_haiku_p3b`, `dev_haikupensar_p3` y `dev_sonnet_p3b`).

## Registro de corridas

- **«api»:** llamó al modelo y gastó.
- **«caché»:** reprocesó respuestas ya guardadas, $0.
- **Imágenes:** lado mayor en px de fachada/producto/etiqueta.
- **Costo (configuración de ese momento):** es el costo dentro de muestra con las reglas o modelos vigentes en esa corrida. **No es comparable entre filas;** para comparar está la tabla de arriba.

| Corrida | Prompt | Imágenes | Tipo | Tokens de entrada | Tokens de salida | Tokens/captura | Segundos | USD | Costo (config. de ese momento) | FP |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|
| dev_haiku | p1 | 640/768/896 | api | 451,561 | 28,261 | 4,798 | 82 | $0.06 | 153 | 0 |
| dev_sonnet | p1 | 640/768/896 | api | 451,648 | 28,559 | 4,802 | 87 | $1.19 | 172 | 0 |
| dev_haiku_p2 | p2 | 640/960/960 | api | 580,445 | 39,181 | 6,196 | 68 | $0.08 | 94 | 0 |
| dev_sonnet_p2 | p2 | 640/960/960 | api | 580,545 | 39,300 | 6,198 | 82 | $1.55 | 96 | 0 |
| dev_haiku_768 | p2 | 384/768/768 | api | 491,542 | 39,083 | 5,306 | 80 | $0.07 | 76 | 0 |
| dev_sonnet_768 | p2 | 384/768/768 | api | 491,642 | 39,245 | 5,309 | 91 | $1.38 | 62 | 0 |
| dev_haiku_p3 | p3 | 384/768/768 | api | 610,068 | 52,985 | 6,630 | 89 | $0.09 | 76 | 0 |
| dev_sonnet_p3 | p3 | 384/768/768 | api | 610,168 | 56,573 | 6,667 | 110 | $1.79 | 66 | 0 |
| dev_haikupensar_p3 | p3 | 384/768/768 | api | 610,168 | 132,748 | 7,429 | 123 | $0.13 | 102 (sin calibrar) | 0 |
| dev_haikupensaralto_p3 | p3 | 384/768/768 | api | 610,168 | 221,776 | 8,319 | 199 | $0.17 | (sin calibrar) | 0 |
| dev_haiku_p3b | p3 | 384/768/768 | caché | — | — | 6,630 | 28 | $0 | 94 | 0 |
| dev_sonnet_p3b | p3 | 384/768/768 | caché | — | — | 6,667 | 28 | $0 | 97 | 0 |
| dev_cascada_768 | p2 | 384/768/768 | caché | — | — | 8,592 | 32 | $0 | 53 | 0 |
| dev_final_p3 (cascada → Sonnet) | p3 | 384/768/768 | caché | — | — | 11,283 | 29 | $0 | 52 | 0 |
| dev_final_haiku | p3 | 384/768/768 | caché | — | — | 6,630 | 27 | $0 | 69 | 0 |
| dev_final_cascada_haikupensar | p3 | 384/768/768 | caché | — | — | 11,817 | 31 | $0 | 52 | 0 |
| dev_final_solo_haikupensar | p3 | 384/768/768 | caché | — | — | 7,429 | 28 | $0 | 52 | 0 |

**Gasto total de la llave en pruebas: $6.50** en 10 corridas que llamaron al modelo: 974 capturas, 2,922 imágenes. A eso se suman ~$0.02 de 3 pruebas reales en Railway.

## Qué aprendimos en cada versión

| Versión | Cambio | Efecto |
|---|---|---|
| p1 | Reglas fijas con severidades supuestas | Costo 153–172, peor que mandar todo a revisión (100). No aprobó ni una: la regla de «falta precio unitario» tumbaba todo, y el código de barras de la etiqueta (interno de la tienda) causaba rechazos falsos |
| p2 | Puntajes graduados de nitidez, tamaño y legibilidad; señales locales (cadena, nitidez de la etiqueta); fusión aprendida y decisión de 3 clases | Haiku 83 y Sonnet 64 fuera de muestra. La cadena y la nitidez son las señales más fuertes |
| p2 a 768 px | Fotos de producto y etiqueta a 768 px y fachada a 384 px | 15% menos tokens, mismo costo |
| motivo aprendido | Clasificador de `reason_code` con los motivos del analista | 41–56% (reglas) → 59–68% de acierto fuera de muestra |
| p3 | La IA lee precio regular, oferta, volumen, tachado y unitario sin conocer el capturado; 16 compuertas; doble llave | 0 FP en todas las configuraciones. El esquema más grande suma ~1,300 tokens por llamada |
| Haiku razonando | Haiku 5.5 con razonamiento adaptativo como segundo modelo | Misma calidad que Sonnet por 8 a 14 veces menos |
| Segunda capa | 10 particiones: esfuerzo medio contra alto, y fusión de las dos lecturas | La cascada con esfuerzo medio es la más consistente; alto y fusión no mejoran |

## Set de evaluación (21 de noviembre) y prompt v4

**El set real:** 50 capturas sin etiquetas, con todos los metadatos y el mismo formato que desarrollo. 27 son de Bodega Aurrerá, Bara o Walmart, cadenas que en desarrollo nunca salieron aprobadas.

### ¿Reentrenar solo con aprobadas y rechazadas?
Lo medimos con 10 particiones, puntuando solo las 67 capturas de desarrollo que son aprobada o rechazada:

| Entrenamiento | Costo en las 67 | Aprobadas bien | Aprobadas mal |
|---|---:|---:|---:|
| Con las 100 (incluye «revisión manual») | **28.1 ± 5.3** | 13.3 | 0 |
| Solo con las 67 | 37.5 ± 1.0 | 0 | 0 |

Entrenar sin las «revisión manual» sale **9 puntos peor**: el modelo pierde la confianza para aprobar. Las capturas de revisión sí enseñan algo.

### Prompt v4: qué significa «lejana» para los analistas
«Capturas lejanas» es el motivo de rechazo más común (18 de 34). Revisando las fotos de desarrollo:
- En la mayoría, **la foto de etiqueta muestra el anaquel con la etiqueta como un recuadro chico**. El precio grande se lee, pero la descripción no.
- En otras, la foto de producto muestra el anaquel completo en vez de una unidad de cerca.
- El prompt v3 decía «el texto no se lee sin zoom», y el modelo daba por buenas esas fotos porque leía el precio grande. Solo detectaba 9 de las 18.

El v4 define «lejana» así y agrega el campo `tamano_etiqueta`. Ahora detecta **18 de 18**.

| Desarrollo, 10 particiones | Prompt v3 | Prompt v4 |
|---|---:|---:|
| Haiku razonando solo, 100 capturas | 70.3 ± 5.0 | **65.0 ± 1.3** |
| Cascada Haiku → Haiku razonando, 100 capturas | 68.3 ± 5.0 | **64.8 ± 1.2** |
| Haiku razonando solo, 67 aprobadas o rechazadas | 30.1 ± 5.5 | **25.6 ± 0.8** |
| Cascada, 67 aprobadas o rechazadas | 28.1 ± 5.3 | **25.4 ± 0.9** |

Con el v4, la cascada ya no gana nada: empata con Haiku razonando solo (−0.2 puntos) y gasta 13,700 tokens por captura contra 7,900. **Configuración elegida: Haiku razonando solo con el prompt v4.**

También probamos quitar el bloqueo duro de la regla de calidad y dejar «lejana» solo como señal. Sale peor (65.7 y 26.3), así que se queda.

### Versiones del JSON del set real
| Versión | Configuración | Aprobadas | Rechazadas | Revisión | Tokens por captura |
|---|---|---:|---:|---:|---:|
| v1 | Prompt v3, cascada Haiku → Haiku razonando | 9 | 21 | 20 | 11,549 |
| **v2** | Prompt v4, Haiku razonando solo | 4 | 18 | 28 | **7,743** |

En la v2, 5 aprobaciones de la v1 pasan a revisión: 3 porque ahora se detectan como lejanas y 2 porque quedan apenas abajo del umbral. Ninguna decisión se cambió a mano; todas salen del motor.

### Fachada estable para la demo
- **El problema:** con fotos dudosas, el modelo alternaba entre «la fachada no corresponde» y «no se distingue» para la misma foto, y eso cambiaba la decisión.
- **El arreglo:** ahora solo cuenta como «no corresponde» si el letrero dice otra cadena. En desarrollo y en el set real el modelo nunca contestó «no», así que la calibración y las decisiones no cambian.
- **En Railway**, la caché de respuestas (`CACHE_IA_DIR`) hace que la misma foto reciba siempre la misma respuesta.

### ¿Más esfuerzo de razonamiento? (prompt v4)
| Haiku razonando, 10 particiones | Medio (en línea) | Alto | Máximo |
|---|---:|---:|---:|
| Costo en las 67 aprobadas o rechazadas | **25.6 ± 0.8** | 27.8 ± 1.9 | sin medir |
| Costo en las 100, con el supuesto conservador | 65.0 ± 1.3 | 64.2 ± 2.4 | sin medir |
| Aprobadas bien (de 33) | 13.6 | 17.0 | — |
| Tokens por captura | **7,858** | 8,866 | ~33,000 (piloto de 3) |
| Segundos por captura | ~10 | ~13 | ~140 |

- **El alto aprueba más válidas, pero en las capturas aprobadas o rechazadas sale 2.2 puntos peor** (peor en 8 de 10 particiones), porque rechaza menos inválidas. Como en el set ciego solo hay aprobada o rechazada, **nos quedamos con el medio**.
- **El máximo tarda unos 140 segundos por captura,** más que los 60 que espera la app, y gasta 4 veces más tokens. Queda como opción (`-pensar-max`, con streaming), sin usarse.

### Estabilidad entre corridas
El servidor en línea y la v2 del lote son el mismo motor y coinciden en 45 de 50. Las 5 diferencias son casos límite. Haiku 5.5 no permite fijar la temperatura, así que esa variación es del modelo; con la misma foto, la caché del servidor da siempre la misma respuesta.

### v3: explicaciones auditables (mismas decisiones)
- **Qué cambia:** cuando la captura es lejana o borrosa, la explicación ahora dice qué foto tiene el problema, por ejemplo «Foto de la etiqueta lejana: la etiqueta ocupa 30% del ancho de su foto…» o «Foto del producto borrosa (nitidez 0.5 de 1)». La validación que falla es la de esa foto: `producto` o `evidencia_precio`.
- **Qué no cambia:** la v3 tiene exactamente las mismas decisiones, motivos y precios que la v2. Cambian 18 explicaciones y 10 validaciones.
- **Es el JSON recomendado para entregar:** `datos/entregas/validat3b_v3.json`.

### JSON final: códigos de barras válidos
- **El hallazgo:** las etiquetas de Walmart y Bodega Aurrerá imprimen el código con **0 en lugar del dígito verificador**: la etiqueta dice 7501791668870 y la lata 7501791668873. La IA transcribía lo de la etiqueta y reportaba un código inválido.
- **El arreglo:** un código transcrito por la IA solo se reporta si es un GTIN válido. Si termina en 0 y no es válido, se recalcula el dígito verificador.
- **El resultado:** se corrigen 3 códigos y los 3 quedan iguales al esperado. Todo lo demás es idéntico a la v3. Archivo para entregar: `datos/entregas/validat3b_final.json`.
