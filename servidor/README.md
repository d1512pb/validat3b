# Servidor

Responsables: el equipo de backend.

Es la última línea de defensa. Recibe capturas de la [app móvil](../movil/), corre la cascada de validación y regresa la decisión en el [formato oficial](../README.md#formato-de-salida-por-captura) más una `traza` para auditar. El contrato está en [`contratos/`](../contratos/).

## Cómo decide

```
fotos + datos ─▶ 1. capa local (0 tokens) ─▶ ¿rechazo seguro? ─ sí ─▶ rechazada
                    fotos faltantes o ilegibles,     │ no
                    código de barras contra catálogo ▼
                 2. Claude Haiku (barato) ─▶ p = probabilidad de captura válida
                                                 │
                    ¿p en zona gris (0.33 – 0.95)? ── sí ─▶ 3. Claude Sonnet ─▶ p
                                                 │ no
                                                 ▼
                 aprobada si p ≥ 0.95 · rechazada si p ≤ 0.33 · si no, revision_manual
```

- **Umbrales por costo:** aprobar una captura mala cuesta 10, rechazar una buena cuesta 3 y la revisión manual cuesta 1. Por eso conviene aprobar solo si p ≥ 0.90 y rechazar si p ≤ 1/3; subimos la aprobación a 0.95 para cuidar la precisión ≥ 97%. Se calibran con el set de validación.
- **Salida del modelo:** JSON garantizado con `output_config.format`. Haiku va sin razonamiento extendido y con `effort: low`, para gastar menos tokens.
- **Si la IA falla:** la captura va a `revision_manual`. Nunca se aprueba a ciegas.

| Archivo | Qué hace |
|---|---|
| `app/contrato.py` | El contrato con la app. Es la fuente de los JSON Schema |
| `app/api.py` | FastAPI: `POST /v1/capturas`, `GET /salud` y `/docs` |
| `app/motor/imagenes.py` | Capa local: carga, nitidez, códigos de barras y reducción de imágenes |
| `app/motor/ia.py` | Llamada a Claude en Azure AI Foundry y esquema de lo que observa |
| `app/motor/decision.py` | Fusión de señales, umbrales, `reason_code` y explicación |
| `app/motor/orquestador.py` | La cascada |
| `app/motor/simulado.py` | Respuestas falsas, para que la app integre sin gastar |

## Correrlo

```bash
cd servidor
python -m venv .venv
.venv/Scripts/python -m pip install -r requirements.txt pytest httpx   # en Linux o Mac: .venv/bin/python
cp .env.ejemplo .env                     # y pega FOUNDRY_API_KEY
.venv/Scripts/python -m scripts.probar_foundry     # ¿responden Haiku y Sonnet?
.venv/Scripts/python -m uvicorn app.api:app --reload --port 8000
.venv/Scripts/python -m pytest -q pruebas          # pruebas sin red
.venv/Scripts/python -m scripts.exportar_contrato  # regenera contratos/ si cambia app/contrato.py
```

## Sets de 3B: lote, evaluación y calibración

Los datos van en `validat3b/datos/`, que está en `.gitignore`.

```bash
# 1. Correr el motor sobre un set. Es reanudable y tiene caché de IA en datos/corridas/_cache_ia
.venv/Scripts/python -m scripts.correr_lote --datos ../datos/desarrollo/capturas.jsonl --salida ../datos/corridas/dev1 --trabajadores 4 --tpm 0
#    Deja resultados.json (formato oficial), detalle.jsonl (señales por captura) y reporte.json.
#    Si hay etiquetas, reporte.json trae costo operativo, precisión de aprobaciones con su cota
#    inferior, acierto de reason_code y de precio, y tokens por captura.

# 2. Calibrar con una corrida etiquetada: aprende la fusión y los umbrales de menor costo.
.venv/Scripts/python -m scripts.calibrar --corrida ../datos/corridas/dev1
#    Escribe modelos/fusion.json y modelos/umbrales.json. El motor los carga al arrancar.
```

- **Calibración honesta:** los umbrales se eligen con p fuera de muestra (validación cruzada) y exigen que la **cota inferior** de la precisión sea ≥ 97%, no solo la observada. Si la fusión aprendida no mejora a las reglas, se quedan las reglas.
- **Señales locales extra:**
  - foto de producto o de etiqueta reusada en otra captura (dHash);
  - distancia del GPS a la tienda más cercana de la cadena seleccionada. Para activarla: `RUTA_TIENDAS=../compartir/tiendas_por_cadena.csv`.
- **Lo que falta cuando lleguen los datos:** un adaptador del formato oficial en `app/lote.py` (`ADAPTADORES`) y la lista oficial de `reason_code`.

## Railway

`railway.json` ya trae el comando de arranque y el chequeo de salud en `/salud`.

1. Crea el servicio con **Root Directory = `servidor`**.
2. Define las variables `FOUNDRY_API_KEY`, `SERVIDOR_TOKEN` y, mientras la app integra, `MODO=simulado`.
3. Despliega con `railway up` desde `servidor/`.

**Las fotos de los sets de 3B (desarrollo y ciego) se procesan en la laptop, no en Railway.** Las reglas piden no subir los datos de 3B a otros servicios. Railway queda para la demo de la app con fotos nuestras.
