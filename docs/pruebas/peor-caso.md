# Peor caso: costo y precisión por modelo

**Supuesto de peor caso:** cada captura pasa completa por el modelo, con sus 3 fotos. No hay atajos de la capa local ni caché de respuestas. Medido con las 100 capturas reales de desarrollo; aún falta probar con la app completa.

| Modelo | Costo por imagen | Costo por captura (3 fotos) | 10,000 fotos | 10,000 capturas | Precisión al aprobar | Acierto general |
|---|---:|---:|---:|---:|---:|---:|
| **Haiku 5.5** | $0.0003 | $0.0009 | **$3** | **$9** | No aprueba ninguna¹ | 51% |
| **Haiku 5.5 razonando** | $0.0004 | $0.0013 | **$4** | **$13** | **100%** | **63%** |
| **Sonnet 5.5** | $0.0060 | $0.0179 | **$60** | **$179** | **100%** | 62% |

Dólares estadounidenses a precio de lista.

**En una frase:** Haiku razonando tiene la misma precisión que Sonnet y cuesta **14 veces menos**: $13 contra $179 por 10,000 capturas.

## Qué significa cada columna

- **Precisión al aprobar:** de lo que el sistema aprueba, cuánto era realmente válido. Es el requisito del reto (≥ 97%). En las pruebas, **ningún modelo aprobó una captura inválida**.
- **Acierto general:** en cuántas capturas la decisión (aprobada, rechazada o revisión manual) fue la misma que la del analista de 3B.
- **¹ Haiku sin razonar es demasiado tímido:** no se atreve a aprobar nada y lo dudoso lo manda a revisión. Nunca se equivoca al aprobar, pero ahorra poco trabajo humano.

## Un poco más de detalle

| Modelo | Se resuelven sin humano | Mandadas a revisión | Aprobadas bien (VP) | Rechazadas bien (VN) | Aprobadas mal (FP) | Rechazadas mal (FN) | Costo operativo (100 capturas)² |
|---|---:|---:|---:|---:|---:|---:|---:|
| Haiku 5.5 | 20% | 80 | 0 | 19 | **0** | **0** | 83 |
| Haiku 5.5 razonando | 32% | 68 | 14 | 17 | **0** | **0** | 71 |
| Sonnet 5.5 | 33% | 67 | 8 | 23 | **0** | **0** | 73 |

- **Cómo se midió la precisión:** fuera de muestra, con validación cruzada. Cada captura se juzgó con un modelo calibrado sin esa captura.
- **² Costo operativo:** la tabla de puntos del reto. Mandar todo a revisión cuesta 100; menos es mejor. Un tercio del set viene etiquetado como revisión manual, así que unos 33 puntos no se pueden evitar con la regla de costo que suponemos.
- **Peor del peor caso:** si todas las capturas costaran como la más cara de la prueba, 10,000 capturas saldrían en $10 con Haiku, $18 con Haiku razonando y $191 con Sonnet.
- **Si se usan dos modelos en cascada y ambos revisan todas las capturas:** Haiku + Haiku razonando cuesta $22 por 10,000 capturas; Haiku + Sonnet, $188.

## Cómo se obtuvo

- **Regenerar la tabla:** `python -m scripts.peor_caso` (desde `servidor/`). La salida está en [`peor-caso-tabla.md`](peor-caso-tabla.md).
- **Tokens:** los reales de cada captura, con el prompt v3 y fotos a 768 px (fachada a 384 px).
- **Precios por millón de tokens:** Haiku 5.5 cuesta $0.10 de entrada y $0.50 de salida; Sonnet 5.5, $2 y $10. El razonamiento de Haiku se cobra como tokens de salida.
- **Metodología completa y registro de corridas:** [`README.md`](README.md).
