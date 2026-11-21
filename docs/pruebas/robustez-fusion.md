10 particiones distintas de validación cruzada (5 pliegues × 2 repeticiones cada una), 100 capturas.

| Configuración | Costo operativo (media ± desv.) | Mínimo–máximo | VP | FP | FN | Acierto general | Tokens/captura |
|---|---:|---:|---:|---:|---:|---:|---:|
| Haiku solo | 81.7 ± 1.9 | 79–84 | 1.9 | 0.0 | 0.0 | 53% | 6,631 |
| Haiku razonando solo | 70.3 ± 5.0 | 65–80 | 13.3 | 0.0 | 0.0 | 66% | 7,429 |
| Cascada Haiku → Haiku razonando (doble llave) | 68.3 ± 5.0 | 63–78 | 13.3 | 0.0 | 0.0 | 68% | 12,304 |
| Cascada Haiku → fusión de las dos lecturas | 76.1 ± 6.0 | 66–84 | 6.1 | 0.0 | 0.1 | 60% | 12,359 |
| Siempre las dos lecturas (fusión) | 76.9 ± 6.1 | 66–84 | 6.1 | 0.0 | 0.1 | 60% | 14,060 |

Diferencia pareada contra «Haiku razonando solo» (misma partición; negativo = mejor):

- Haiku solo: +11.4 puntos en promedio; es mejor en 0 de 10 particiones, peor en 10.
- Cascada Haiku → Haiku razonando (doble llave): -2.0 puntos en promedio; es mejor en 10 de 10 particiones, peor en 0.
- Cascada Haiku → fusión de las dos lecturas: +5.8 puntos en promedio; es mejor en 1 de 10 particiones, peor en 9.
- Siempre las dos lecturas (fusión): +6.6 puntos en promedio; es mejor en 0 de 10 particiones, peor en 9.
