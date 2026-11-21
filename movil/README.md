# Móvil

Responsables: Richi y Luca.

App móvil que valida cada captura en el teléfono, en capas, antes de gastar en IA. Solo manda al [servidor](../servidor/) lo que no pudo resolver con confianza.

## Qué revisa en local

Las capas van de la más barata a la más cara:

1. **Calidad de la foto:** borrosa, demasiado lejos, oscura o tomada a la pantalla de otro celular.
2. **Código de barras:** leerlo de la foto y compararlo con el capturado y con el catálogo.
3. **Etiqueta de precio:** que exista y que tenga precio y precio unitario.
4. **Contexto:** fachada, hora y metadatos de la app.

## Pendiente

- Elegir el stack de la app.
- Acordar con el servidor qué se envía y qué se recibe.
