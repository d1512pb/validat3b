"""Límite de tokens por minuto compartido entre hilos (todas las llaves del hackatón tienen el mismo TPM)."""
from __future__ import annotations

import threading
import time


class LimitadorTPM:
    """Cubeta de tokens: se rellena a `tpm/60` por segundo. tpm <= 0 desactiva el límite."""

    def __init__(self, tpm: int):
        self.tpm = tpm
        self.disponibles = float(tpm)
        self.ultimo = time.monotonic()
        self._c = threading.Condition()

    def _rellenar(self) -> None:
        ahora = time.monotonic()
        self.disponibles = min(self.tpm, self.disponibles + (ahora - self.ultimo) * self.tpm / 60)
        self.ultimo = ahora

    def tomar(self, tokens: int) -> None:
        if self.tpm <= 0:
            return
        tokens = min(tokens, self.tpm)
        with self._c:
            while True:
                self._rellenar()
                if self.disponibles >= tokens:
                    self.disponibles -= tokens
                    return
                faltan = tokens - self.disponibles
                self._c.wait(timeout=faltan * 60 / self.tpm)

    def ajustar(self, estimado: int, real: int) -> None:
        """Corrige la cubeta con lo que de verdad gastó la llamada."""
        if self.tpm <= 0:
            return
        with self._c:
            self.disponibles -= real - estimado
            self._c.notify_all()


def tokens_imagen(ancho: int, alto: int) -> int:
    """Estimación de tokens de una imagen para Claude: ancho * alto / 750."""
    return int(ancho * alto / 750) + 1
