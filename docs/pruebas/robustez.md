10 particiones distintas de validación cruzada (5 pliegues × 2 repeticiones cada una), 100 capturas.

| Configuración | Costo operativo (media ± desv.) | Mínimo–máximo | VP | FP | FN | Acierto general | Tokens/captura |
|---|---:|---:|---:|---:|---:|---:|---:|
| Haiku solo | 81.7 ± 1.9 | 79–84 | 1.9 | 0.0 | 0.0 | 53% | 6,631 |
| Haiku razonando (esfuerzo medio) solo | 70.3 ± 5.0 | 65–80 | 13.3 | 0.0 | 0.0 | 66% | 7,429 |
| Cascada Haiku → Haiku razonando (esfuerzo medio, doble llave) | 68.3 ± 5.0 | 63–78 | 13.3 | 0.0 | 0.0 | 68% | 12,304 |
| Haiku razonando (esfuerzo alto) solo | 68.9 ± 3.5 | 64–75 | 14.4 | 0.0 | 0.3 | 69% | 8,319 |
| Cascada Haiku → Haiku razonando (esfuerzo alto) (doble llave) | 68.1 ± 3.5 | 63–74 | 14.4 | 0.0 | 0.3 | 70% | 13,050 |

Diferencia pareada contra «Haiku razonando (esfuerzo medio) solo» (misma partición; negativo = mejor):

- Haiku solo: +11.4 puntos en promedio; es mejor en 0 de 10 particiones, peor en 10.
- Cascada Haiku → Haiku razonando (esfuerzo medio, doble llave): -2.0 puntos en promedio; es mejor en 10 de 10 particiones, peor en 0.
- Haiku razonando (esfuerzo alto) solo: -1.4 puntos en promedio; es mejor en 7 de 10 particiones, peor en 1.
- Cascada Haiku → Haiku razonando (esfuerzo alto) (doble llave): -2.2 puntos en promedio; es mejor en 8 de 10 particiones, peor en 1.
