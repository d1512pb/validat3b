"""¿La captura se tomó cerca de una tienda de la cadena seleccionada? Usa compartir/tiendas_por_cadena.csv."""
from __future__ import annotations

import csv
import unicodedata
from functools import lru_cache
from pathlib import Path

import numpy as np

from app.config import config


def normalizar(texto: str) -> str:
    t = unicodedata.normalize("NFKD", texto or "").encode("ascii", "ignore").decode().lower()
    return " ".join("".join(ch if ch.isalnum() else " " for ch in t).split())


# Nombres que suelen venir distintos en la app y en el DENUE
ALIAS = {
    "bodega aurrera": "bodega aurrera", "aurrera": "bodega aurrera", "mi bodega aurrera": "bodega aurrera",
    "walmart": "walmart", "walmart supercenter": "walmart", "soriana": "soriana", "chedraui": "chedraui",
    "oxxo": "oxxo", "7 eleven": "7 eleven", "seven eleven": "7 eleven", "neto": "neto", "tiendas neto": "neto",
    "tiendas 3b": "tiendas 3b", "3b": "tiendas 3b", "bbb": "tiendas 3b", "heb": "heb", "h e b": "heb",
    "la comer": "la comer", "costco": "costco", "sams club": "sams club", "sam s club": "sams club",
    "circle k": "circle k extra", "extra": "circle k extra", "kiosko": "kiosko",
}


def cadena_canonica(nombre: str) -> str:
    n = normalizar(nombre)
    if n in ALIAS:
        return ALIAS[n]
    for alias, canon in ALIAS.items():
        if alias in n:
            return canon
    return n


@lru_cache
def _tiendas() -> dict[str, np.ndarray]:
    ruta = config().ruta_tiendas
    if not ruta or not Path(ruta).exists():
        return {}
    por_cadena: dict[str, list[tuple[float, float]]] = {}
    with open(ruta, encoding="utf-8") as f:
        for fila in csv.DictReader(f):
            try:
                lat, lon = float(fila["latitud"]), float(fila["longitud"])
            except (KeyError, ValueError):
                continue
            por_cadena.setdefault(cadena_canonica(fila["cadena"]), []).append((lat, lon))
    return {k: np.radians(np.array(v)) for k, v in por_cadena.items()}


def distancia_a_cadena_m(cadena: str, lat: float, lon: float) -> float | None:
    """Distancia en metros a la tienda más cercana de esa cadena; None si no hay datos de la cadena."""
    puntos = _tiendas().get(cadena_canonica(cadena))
    if puntos is None or len(puntos) == 0:
        return None
    la, lo = np.radians(lat), np.radians(lon)
    d = np.sin((puntos[:, 0] - la) / 2) ** 2 + np.cos(la) * np.cos(puntos[:, 0]) * np.sin((puntos[:, 1] - lo) / 2) ** 2
    return float(2 * 6_371_000 * np.arcsin(np.sqrt(d.min())))
