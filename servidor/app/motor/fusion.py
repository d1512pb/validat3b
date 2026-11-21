"""Fusión aprendida: convierte todas las señales en p(captura válida) calibrada.

Mientras no haya datos etiquetados se usa la p de reglas (severidades de decision.py).
Con el set de entrenamiento, `scripts/calibrar.py` ajusta una regresión logística y la guarda en JSON.
Sin dependencias pesadas: numpy, para que corra igual en la laptop y en Railway.
"""
from __future__ import annotations

import json
import math
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

import numpy as np

from app.config import config
from app.contrato import CapturaEntrada
from app.motor.ia import AnalisisIA
from app.motor.imagenes import Foto


def _canon(cadena: str) -> str:
    from app.motor.geo import cadena_canonica
    return cadena_canonica(cadena).replace(" ", "_") or "desconocida"


def _logit(p: float) -> float:
    p = min(max(p, 1e-4), 1 - 1e-4)
    return math.log(p / (1 - p))


def caracteristicas(c: CapturaEntrada, fotos: dict[str, Foto], p_reglas: float, cod_falla: bool,
                    a: AnalisisIA | None, extra: dict | None = None) -> dict[str, float]:
    extra = extra or {}
    x: dict[str, float] = {
        "logit_reglas": _logit(p_reglas),
        "falta_fachada": float(not fotos["fachada"].valida),
        "falta_producto": float(not fotos["producto"].valida),
        "falta_etiqueta": float(not fotos["etiqueta"].valida),
        "codigo_no_coincide": float(cod_falla),
        "codigo_leido": float(any(f.codigos for f in fotos.values())),
        "es_contratipo": float(c.producto_esperado.es_contratipo),
        "foto_repetida": float(extra.get("foto_repetida", 0) > 0),
        "codigo_en_etiqueta": float(bool(fotos["etiqueta"].codigos)),
        f"cadena_{_canon(c.cadena)}": 1.0,
    }
    pe = c.producto_esperado
    if c.precio_capturado is not None:
        if pe.precio_referencia is not None:
            x["precio_capturado_igual_ref"] = float(abs(c.precio_capturado - pe.precio_referencia) < 0.01)
            x["precio_capturado_vs_ref"] = c.precio_capturado / pe.precio_referencia - 1 if pe.precio_referencia else 0.0
        if pe.precio_minimo is not None and pe.precio_maximo is not None:
            x["precio_capturado_fuera_rango"] = float(not pe.precio_minimo <= c.precio_capturado <= pe.precio_maximo)
    for t in ("producto", "etiqueta"):
        n = fotos[t].nitidez
        x[f"log_nitidez_{t}"] = math.log1p(n) if n is not None else 0.0
    dist = extra.get("distancia_cadena_m")
    x["gps_disponible"] = float(dist is not None)
    x["gps_lejos"] = float(dist is not None and dist > 300)
    for ch in c.chequeos_locales:  # lo que reporta el teléfono, como pista
        x[f"movil_{ch.nombre}_falla"] = float(ch.resultado == "falla")
    if a is not None:
        x["ia_disponible"] = 1.0
        for campo in ("fotos_sin_relacion", "es_foto_de_pantalla", "imagen_borrosa", "captura_lejana",
                      "fachada_es_tienda", "producto_visible", "descripcion_completa", "etiqueta_visible",
                      "precio_unitario_visible"):
            x[f"ia_{campo}"] = float(getattr(a, campo))
        for campo in ("fachada_coincide", "producto_coincide", "etiqueta_corresponde", "tipo_precio"):
            x[f"ia_{campo}_{getattr(a, campo)}"] = 1.0
        x["ia_precio_presente"] = float(a.precio is not None)
        x["ia_confianza"] = a.confianza
        for campo in ("nitidez_producto", "nitidez_etiqueta", "tamano_producto", "legibilidad_etiqueta"):
            v = getattr(a, campo, None)
            if v is not None:
                x[f"ia_{campo}"] = float(min(max(v, 0.0), 1.0))
        if a.precio is not None and c.precio_capturado is not None:
            x["ia_precio_igual_capturado"] = float(abs(a.precio - c.precio_capturado) < 0.01)
    else:
        x["ia_disponible"] = 0.0
    return x


@dataclass
class ModeloFusion:
    nombres: list[str]
    media: np.ndarray
    escala: np.ndarray
    pesos: np.ndarray      # incluye el intercepto al final
    temperatura: float = 1.0

    def p(self, x: dict[str, float]) -> float:
        v = np.array([x.get(n, 0.0) for n in self.nombres])
        z = ((v - self.media) / self.escala) @ self.pesos[:-1] + self.pesos[-1]
        return float(1 / (1 + math.exp(-z / self.temperatura)))

    def guardar(self, ruta: str | Path) -> None:
        Path(ruta).parent.mkdir(parents=True, exist_ok=True)
        Path(ruta).write_text(json.dumps({
            "nombres": self.nombres, "media": self.media.tolist(), "escala": self.escala.tolist(),
            "pesos": self.pesos.tolist(), "temperatura": self.temperatura}, indent=1), encoding="utf-8")

    @classmethod
    def cargar(cls, ruta: str | Path) -> "ModeloFusion":
        d = json.loads(Path(ruta).read_text(encoding="utf-8"))
        return cls(d["nombres"], np.array(d["media"]), np.array(d["escala"]), np.array(d["pesos"]),
                   d.get("temperatura", 1.0))


def entrenar(filas: list[dict[str, float]], y: list[int], l2: float = 1.0, iteraciones: int = 50) -> ModeloFusion:
    """Regresión logística con L2 por IRLS (Newton). y = 1 si la captura es válida."""
    nombres = sorted({k for f in filas for k in f})
    X = np.array([[f.get(n, 0.0) for n in nombres] for f in filas], dtype=float)
    media, escala = X.mean(0), X.std(0)
    escala[escala == 0] = 1.0
    Z = np.hstack([(X - media) / escala, np.ones((len(X), 1))])
    yv = np.array(y, dtype=float)
    w = np.zeros(Z.shape[1])
    reg = np.eye(Z.shape[1]) * l2
    reg[-1, -1] = 0.0  # no se regulariza el intercepto
    for _ in range(iteraciones):
        p = 1 / (1 + np.exp(-Z @ w))
        W = p * (1 - p) + 1e-9
        H = Z.T @ (Z * W[:, None]) + reg
        g = Z.T @ (p - yv) + reg @ w
        paso = np.linalg.solve(H, g)
        w -= paso
        if np.abs(paso).max() < 1e-6:
            break
    return ModeloFusion(nombres, media, escala, w)


CARPETA_MODELOS = Path("modelos")


def carpeta(modelo_ia: str | None) -> Path:
    """Cada modelo de IA puntúa distinto: su fusión y umbrales viven en modelos/<modelo>/."""
    return CARPETA_MODELOS / modelo_ia if modelo_ia else CARPETA_MODELOS


@lru_cache
def _cargar(ruta: str) -> ModeloFusion | None:
    return ModeloFusion.cargar(ruta) if Path(ruta).exists() else None


@lru_cache
def umbrales(modelo_ia: str | None, etapa: str = "final") -> dict | None:
    """etapa 'final': umbrales para decidir solo con ese modelo. 'cascada': cuándo el rápido ya está seguro."""
    ruta = carpeta(modelo_ia) / ("umbrales_cascada.json" if etapa == "cascada" else "umbrales.json")
    return json.loads(ruta.read_text(encoding="utf-8")) if ruta.exists() else None


@lru_cache
def _motivos(ruta: str) -> dict[str, ModeloFusion]:
    p = Path(ruta)
    if not p.exists():
        return {}
    d = json.loads(p.read_text(encoding="utf-8"))
    return {k: ModeloFusion(v["nombres"], np.array(v["media"]), np.array(v["escala"]), np.array(v["pesos"]))
            for k, v in d.items()}


def motivo_aprendido(x: dict[str, float], modelo_ia: str | None) -> str | None:
    """reason_code más probable según los clasificadores por motivo (uno contra el resto)."""
    modelos = _motivos(str(carpeta(modelo_ia) / "motivos.json"))
    if not modelos:
        return None
    return max(modelos, key=lambda k: modelos[k].p(x))


def guardar_motivos(modelos: dict[str, ModeloFusion], ruta: Path) -> None:
    ruta.parent.mkdir(parents=True, exist_ok=True)
    ruta.write_text(json.dumps({k: {"nombres": m.nombres, "media": m.media.tolist(), "escala": m.escala.tolist(),
                                    "pesos": m.pesos.tolist()} for k, m in modelos.items()}), encoding="utf-8")


def p_final(x: dict[str, float], p_reglas: float, modelo_ia: str | None = None) -> tuple[float, float | None, str]:
    """(p_aprobar, p_rechazo, fuente). p_rechazo None = se usa 1 - p_aprobar."""
    d = carpeta(modelo_ia)
    m = _cargar(str(d / "fusion.json"))
    if m is None:
        return p_reglas, None, "reglas"
    mr = _cargar(str(d / "fusion_rechazo.json"))
    return m.p(x), (mr.p(x) if mr else None), f"aprendida:{modelo_ia}"
