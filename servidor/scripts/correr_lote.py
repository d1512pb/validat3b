"""Corre el motor sobre un set de capturas y deja resultados.json (formato oficial) y reporte.json.

Uso, desde servidor/:
  python -m scripts.correr_lote --datos ../datos/desarrollo/capturas.jsonl --salida ../datos/corridas/dev1
Opciones: --formato jsonl|oficial  --trabajadores 4  --modo real|simulado  --tpm 0  --sin-cache
Se puede interrumpir y volver a correr: retoma donde se quedó.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--datos", required=True, type=Path)
    ap.add_argument("--salida", required=True, type=Path)
    ap.add_argument("--formato", default="jsonl")
    ap.add_argument("--trabajadores", type=int, default=4)
    ap.add_argument("--modo", choices=["real", "simulado"], default=None)
    ap.add_argument("--tpm", type=int, default=None, help="Tokens por minuto de la llave (0 = sin límite).")
    ap.add_argument("--sin-cache", action="store_true")
    ap.add_argument("--limite", type=int, default=None, help="Procesar solo las primeras N capturas.")
    a = ap.parse_args()

    # La configuración se lee al importar: se fija antes.
    if not a.sin_cache:
        os.environ.setdefault("CACHE_IA_DIR", str(a.salida.parent / "_cache_ia"))
    if a.tpm is not None:
        os.environ["TPM_LIMITE"] = str(a.tpm)

    from app.lote import ADAPTADORES, Corrida, correr

    if a.formato not in ADAPTADORES:
        sys.exit(f"Formato desconocido {a.formato}. Disponibles: {', '.join(ADAPTADORES)}")
    items = list(ADAPTADORES[a.formato](a.datos))
    if a.limite:
        items = items[: a.limite]

    def avance(hechos: int, total: int) -> None:
        if hechos == total or hechos % 10 == 0:
            print(f"  {hechos}/{total}", flush=True)

    print(f"{len(items)} capturas → {a.salida}")
    rep = correr(items, Corrida(a.salida, a.trabajadores, a.modo), avance)
    print(json.dumps(rep, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
