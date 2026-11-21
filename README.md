# Validat3B

Motor de validación de capturas de precio en campo para el Reto 2 del Hackatón Tiendas 3B. Para cada captura decide `aprobada`, `rechazada` o `revision_manual` y explica por qué.

## Cómo se divide

| Carpeta | Qué hace | Responsables |
|---|---|---|
| [`movil/`](movil/) | App móvil. Corre validaciones locales en capas, sin costo de IA, y solo manda al servidor lo que no pudo resolver con confianza | Richi y Luca |
| [`servidor/`](servidor/) | Última línea de defensa. Recibe las capturas dudosas, usa modelos de IA solo donde hacen falta y regresa la decisión. Meta: acertar en más del 90% de los casos | Paulette y el equipo de datos e IA |

```
foto ──▶ capas locales (móvil) ──▶ ¿se resolvió con confianza?
                                      ├─ sí ──▶ decisión
                                      └─ no ──▶ servidor (IA) ──▶ decisión
```

## Principios

- **Volumen:** hay que pensar en 10,000 fotos o más. Cada llamada a un modelo cuesta, así que todo lo que se pueda resolver en el teléfono se resuelve ahí.
- **Fotos trampa:** el capturista puede equivocarse o hacer trampa, y habrá fotos que no tienen nada que ver. No se confía en lo que dice la captura; se valida con la imagen.
- **El error más caro es aprobar una captura inválida:** cuesta 10 puntos. Mandarla a revisión manual cuesta 1 y rechazar una válida cuesta 3. Ante la duda, revisión manual.
- **Los datos de 3B no se suben:** ni a este repositorio ni a otros servicios. `datos/` está en `.gitignore`.
- **Las llaves van en `.env`:** nunca en el repositorio.

## Formato de salida por captura

Es el formato oficial del reto. `decision` solo puede ser `aprobada`, `rechazada` o `revision_manual`; la lista oficial de `reason_code` llega con los datos.

```json
{
  "capture_id": "CAP-000123",
  "decision": "rechazada",
  "confidence_score": 0.93,
  "reason_code": "PRODUCTO_NO_COINCIDE",
  "validaciones": {
    "tienda_fachada": "ok",
    "producto": "falla",
    "codigo_barras": "ok",
    "evidencia_precio": "ok"
  },
  "precio_detectado": 42.50,
  "codigo_barras_detectado": "7501234567890",
  "explicacion": "El producto de la foto es de otra marca que la del contratipo esperado."
}
```
