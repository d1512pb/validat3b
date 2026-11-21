"""Capa local sobre las fotos: cuesta 0 tokens."""
from __future__ import annotations

import base64
import io
from dataclasses import dataclass, field

import numpy as np
from PIL import Image, ImageOps, UnidentifiedImageError

try:
    import zxingcpp
except ImportError:  # el servidor sigue funcionando sin lector de códigos
    zxingcpp = None


@dataclass
class Foto:
    tipo: str
    imagen: Image.Image | None = None
    error: str | None = None
    nitidez: float | None = None          # varianza del laplaciano; baja = borrosa
    codigos: list[str] = field(default_factory=list)
    exif: dict = field(default_factory=dict)

    @property
    def valida(self) -> bool:
        return self.imagen is not None


def cargar(tipo: str, datos: bytes | None) -> Foto:
    if not datos:
        return Foto(tipo, error="falta_foto")
    try:
        img = Image.open(io.BytesIO(datos))
        exif = {k: str(v)[:80] for k, v in (img.getexif() or {}).items()}
        img = ImageOps.exif_transpose(img).convert("RGB")
    except (UnidentifiedImageError, OSError, ValueError):
        return Foto(tipo, error="foto_ilegible")
    foto = Foto(tipo, imagen=img, exif=exif)
    foto.nitidez = nitidez(img)
    foto.codigos = leer_codigos(img)
    return foto


def nitidez(img: Image.Image, lado: int = 512) -> float:
    """Varianza del laplaciano sobre la imagen en gris reducida."""
    g = ImageOps.contain(img.convert("L"), (lado, lado))
    a = np.asarray(g, dtype=np.float32)
    lap = (-4 * a[1:-1, 1:-1] + a[:-2, 1:-1] + a[2:, 1:-1] + a[1:-1, :-2] + a[1:-1, 2:])
    return float(lap.var())


def leer_codigos(img: Image.Image) -> list[str]:
    if zxingcpp is None:
        return []
    try:
        res = zxingcpp.read_barcodes(img)
    except Exception:
        return []
    return [r.text for r in res if r.text]


def gtin_valido(codigo: str | None) -> bool:
    """Dígito verificador de EAN-8, UPC-A, EAN-13 y GTIN-14."""
    if not codigo or not codigo.isdigit() or len(codigo) not in (8, 12, 13, 14):
        return False
    digitos = [int(c) for c in codigo]
    cuerpo, verificador = digitos[:-1], digitos[-1]
    suma = sum(d * (3 if i % 2 == 0 else 1) for i, d in enumerate(reversed(cuerpo)))
    return (10 - suma % 10) % 10 == verificador


def normalizar_codigo(codigo: str | None) -> str | None:
    """Quita espacios y ceros a la izquierda para comparar UPC-A con EAN-13."""
    if not codigo:
        return None
    c = "".join(ch for ch in codigo if ch.isdigit())
    return c.lstrip("0") or None


def a_base64(img: Image.Image, lado_max: int, calidad: int = 80) -> str:
    """JPEG reducido para el modelo: menos píxeles, menos tokens (~ ancho*alto/750)."""
    chica = ImageOps.contain(img, (lado_max, lado_max))
    buf = io.BytesIO()
    chica.save(buf, format="JPEG", quality=calidad, optimize=True)
    return base64.standard_b64encode(buf.getvalue()).decode("ascii")
