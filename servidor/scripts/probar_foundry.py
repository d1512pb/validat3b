"""Prueba de conexión: qué modelos responden con las opciones del motor (JSON garantizado, effort bajo).

Uso, desde servidor/:  python -m scripts.probar_foundry
Usa el proveedor configurado (Foundry o API de Anthropic). No manda datos de 3B: solo una pregunta de texto.
"""
from __future__ import annotations

import json

from app.config import config
from app.motor import ia

ESQUEMA = {"type": "object", "properties": {"capital": {"type": "string"}}, "required": ["capital"],
           "additionalProperties": False}


def probar(modelo: str) -> None:
    opciones = ia._opciones_modelo(modelo)
    opciones["output_config"] = {**opciones["output_config"], "format": {"type": "json_schema", "schema": ESQUEMA}}
    try:
        r = ia._cliente().messages.create(model=ia.modelo_api(modelo), max_tokens=2000,
                                          messages=[{"role": "user", "content": "¿Capital de Francia?"}], **opciones)
        texto = next(b.text for b in r.content if b.type == "text")
        print(f"OK    {modelo}: {json.loads(texto)}  tokens {r.usage.input_tokens}+{r.usage.output_tokens}")
    except Exception as e:  # queremos ver cualquier falla
        print(f"FALLA {modelo}: {type(e).__name__}: {str(e)[:300]}")


def main() -> None:
    cfg = config()
    print("Proveedor:", ia.proveedor())
    for modelo in dict.fromkeys([cfg.modelo_rapido, cfg.modelo_fuerte]):
        probar(modelo)


if __name__ == "__main__":
    main()
