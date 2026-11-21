"""Huellas perceptuales (dHash) para detectar la misma foto reusada en capturas distintas."""
from __future__ import annotations

import threading

import numpy as np
from PIL import Image, ImageOps


def dhash(img: Image.Image, lado: int = 8) -> int:
    g = np.asarray(ImageOps.grayscale(img).resize((lado + 1, lado), Image.Resampling.LANCZOS), dtype=np.int16)
    bits = (g[:, 1:] > g[:, :-1]).flatten()
    return int("".join("1" if b else "0" for b in bits), 2)


def distancia(a: int, b: int) -> int:
    return (a ^ b).bit_count()


class IndiceHuellas:
    """Recuerda las huellas vistas. `registrar` devuelve los capture_id con una foto casi idéntica."""

    def __init__(self, umbral: int = 4, maximo: int = 200_000):
        self.umbral, self.maximo = umbral, maximo
        self._vistas: dict[str, list[tuple[int, str]]] = {}
        self._c = threading.Lock()

    def registrar(self, tipo: str, huella: int, capture_id: str) -> list[str]:
        with self._c:
            lista = self._vistas.setdefault(tipo, [])
            iguales = [cid for h, cid in lista if cid != capture_id and distancia(h, huella) <= self.umbral]
            if len(lista) < self.maximo:
                lista.append((huella, capture_id))
            return iguales
