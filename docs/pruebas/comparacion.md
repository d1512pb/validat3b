Capturas con IA: 100 (las decididas por la capa local, sin IA, no cambian entre configuraciones).
Fuera de muestra: validación cruzada 5×5; umbrales con precisión de aprobaciones ≥ 97%.

| Configuración | Costo operativo | VP | VN | **FP** (×10) | **FN** (×3) | Rechazo de revisión (×3) | Revisión: era revisión | Revisión: era válida | Revisión: era rechazo | Escala al 2º modelo | Tokens/captura | USD/captura | USD/imagen | USD por 10,000 capturas |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Todo a revisión manual (referencia) | **100** | 0 | 0 | **0** | **0** | 0 | 33 | 33 | 34 | — | 0 | $0.00000 | $0.00000 | $0.00 |
| Solo Haiku (sin razonar) | **83** | 0 | 19 | **0** | **0** | 1 | 32 | 33 | 15 | 0% | 6,631 | $0.00087 | $0.00029 | $8.75 |
| Solo Haiku razonando | **71** | 14 | 17 | **0** | **0** | 1 | 32 | 19 | 17 | 0% | 7,429 | $0.00127 | $0.00042 | $12.74 |
| Cascada Haiku → Haiku razonando + doble llave | **66** | 14 | 22 | **0** | **0** | 1 | 32 | 19 | 12 | 80% | 12,514 | $0.00186 | $0.00062 | $18.64 |
| Solo Sonnet | **73** | 8 | 23 | **0** | **0** | 2 | 31 | 25 | 11 | 0% | 6,667 | $0.01786 | $0.00595 | $178.61 |
| Cascada Haiku → Sonnet + doble llave (desplegada hoy) | **69** | 8 | 27 | **0** | **0** | 2 | 31 | 25 | 7 | 78% | 11,818 | $0.01468 | $0.00489 | $146.77 |
