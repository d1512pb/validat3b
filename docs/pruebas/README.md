# Registro de pruebas del motor

21 de noviembre de 2026. Todas las pruebas usan las **100 capturas etiquetadas de desarrollo** del reto: 33 aprobadas, 34 rechazadas y 33 a revisión manual. Las imágenes y los metadatos de 3B **no** están en el repositorio (`datos/` va en `.gitignore`). Aquí solo quedan resultados agregados.

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

## Cómo cambiar la configuración del servidor

Todo se hace con variables de entorno (Railway → Variables). Los modelos calibrados de cada opción ya están en `servidor/modelos/`.

| Configuración | `MODELO_RAPIDO` | `MODELO_FUERTE` | `ESCALAR_A_FUERTE` | `VERIFICAR_APROBACIONES` |
|---|---|---|---|---|
| Cascada Haiku → Sonnet (hoy) | `claude-haiku-5-5` | `claude-sonnet-5-5` | `true` | `true` |
| Cascada Haiku → Haiku razonando | `claude-haiku-5-5` | `claude-haiku-5-5-pensar` | `true` | `true` |
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
| dev_haiku_p3b | p3 | 384/768/768 | caché | — | — | 6,630 | 28 | $0 | 94 | 0 |
| dev_sonnet_p3b | p3 | 384/768/768 | caché | — | — | 6,667 | 28 | $0 | 97 | 0 |
| dev_cascada_768 | p2 | 384/768/768 | caché | — | — | 8,592 | 32 | $0 | 53 | 0 |
| dev_final_p3 (cascada → Sonnet) | p3 | 384/768/768 | caché | — | — | 11,283 | 29 | $0 | 52 | 0 |
| dev_final_haiku | p3 | 384/768/768 | caché | — | — | 6,630 | 27 | $0 | 69 | 0 |
| dev_final_cascada_haikupensar | p3 | 384/768/768 | caché | — | — | 11,817 | 31 | $0 | 52 | 0 |
| dev_final_solo_haikupensar | p3 | 384/768/768 | caché | — | — | 7,429 | 28 | $0 | 52 | 0 |

**Gasto total de la llave en pruebas: $6.33** en 9 corridas que llamaron al modelo: 874 capturas, 2,622 imágenes. A eso se suman ~$0.02 de 3 pruebas reales en Railway.

## Qué aprendimos en cada versión

| Versión | Cambio | Efecto |
|---|---|---|
| p1 | Reglas fijas con severidades supuestas | Costo 153–172, peor que mandar todo a revisión (100). No aprobó ni una: la regla de «falta precio unitario» tumbaba todo, y el código de barras de la etiqueta (interno de la tienda) causaba rechazos falsos |
| p2 | Puntajes graduados de nitidez, tamaño y legibilidad; señales locales (cadena, nitidez de la etiqueta); fusión aprendida y decisión de 3 clases | Haiku 83 y Sonnet 64 fuera de muestra. La cadena y la nitidez son las señales más fuertes |
| p2 a 768 px | Fotos de producto y etiqueta a 768 px y fachada a 384 px | 15% menos tokens, mismo costo |
| motivo aprendido | Clasificador de `reason_code` con los motivos del analista | 41–56% (reglas) → 59–68% de acierto fuera de muestra |
| p3 | La IA lee precio regular, oferta, volumen, tachado y unitario sin conocer el capturado; 16 compuertas; doble llave | 0 FP en todas las configuraciones. El esquema más grande suma ~1,300 tokens por llamada |
| Haiku razonando | Haiku 5.5 con razonamiento adaptativo como segundo modelo | Misma calidad que Sonnet por 8 a 14 veces menos |
