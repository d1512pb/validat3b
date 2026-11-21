# Servidor

Responsables: Paulette y el equipo de datos e IA.

Es la última línea de defensa. Recibe las capturas que la [app móvil](../movil/) no pudo resolver, usa modelos de IA solo para las validaciones que lo requieren y regresa la decisión en el [formato oficial](../README.md#formato-de-salida-por-captura). Meta: acertar en más del 90% de los casos.

## Cómo decide

- **Modelo según la dificultad:** el más barato primero; uno más capaz solo para los casos ambiguos. Se busca el equilibrio entre costo y error.
- **Confianza calibrada:** cada decisión lleva un `confidence_score`. Si no alcanza el umbral, la captura va a `revision_manual`.
- **Respuesta siempre válida:** si algo falla, la respuesta sigue cumpliendo el formato y la captura va a `revision_manual`.

## Pendiente

- Acordar con móvil qué se envía y qué se recibe.
- Probar los modelos disponibles y medir costo contra error.
