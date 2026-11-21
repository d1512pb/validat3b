"""Caché en disco de respuestas del modelo: correr dos veces la misma captura no gasta tokens otra vez."""
from __future__ import annotations

import hashlib
import json
import threading
from pathlib import Path

_candado = threading.Lock()


def llave(*partes: str) -> str:
    h = hashlib.sha256()
    for p in partes:
        h.update(p.encode("utf-8"))
        h.update(b"\x00")
    return h.hexdigest()


class CacheDisco:
    def __init__(self, carpeta: str | Path):
        self.carpeta = Path(carpeta)
        self.carpeta.mkdir(parents=True, exist_ok=True)

    def _ruta(self, k: str) -> Path:
        return self.carpeta / k[:2] / f"{k}.json"

    def leer(self, k: str) -> dict | None:
        ruta = self._ruta(k)
        if not ruta.exists():
            return None
        try:
            return json.loads(ruta.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return None

    def guardar(self, k: str, valor: dict) -> None:
        ruta = self._ruta(k)
        with _candado:
            ruta.parent.mkdir(parents=True, exist_ok=True)
            tmp = ruta.with_suffix(".tmp")
            tmp.write_text(json.dumps(valor, ensure_ascii=False), encoding="utf-8")
            tmp.replace(ruta)
