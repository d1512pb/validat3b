"""Compuertas de aprobación: una captura solo se aprueba si TODAS pasan.

Aprobar una captura inválida cuesta 10 y revisarla cuesta 1: ante cualquier duda, revisión manual.
Las compuertas no rechazan; solo impiden aprobar. Cada una deja un motivo legible para auditar.
"""
from __future__ import annotations

from dataclasses import dataclass

from app.config import config
from app.contrato import CapturaEntrada
from app.motor.ia import AnalisisIA
from app.motor.imagenes import Foto

# Chequeos del teléfono que, si reportan falla, impiden aprobar. No bloquean «codigo_barras» ni «precio_leido»:
# el teléfono los marca en falla cuando simplemente no alcanzó a leer (el frente del producto casi nunca muestra
# el código, y el código es opcional). Un código distinto sí bloquea: lo detecta el servidor en la foto y
# «codigo_foto_coincide». El código de la etiqueta de anaquel no cuenta: suele ser interno de la tienda.
CHEQUEOS_MOVILES_CRITICOS = {"nitidez", "distancia", "codigo_foto_coincide", "etiqueta_presente", "pantalla"}

FACTOR_UNIDAD = {  # unidad del catálogo -> (unidad base del precio unitario, cuántas unidades del catálogo hay en ella)
    "gramo": ("kg", 1000), "g": ("kg", 1000), "kilogramo": ("kg", 1), "kg": ("kg", 1),
    "mililitro": ("l", 1000), "ml": ("l", 1000), "litro": ("l", 1), "l": ("l", 1),
    "pieza": ("pieza", 1), "pza": ("pieza", 1), "unidad": ("pieza", 1),
}


@dataclass
class Compuerta:
    nombre: str
    ok: bool
    detalle: str = ""


def precio_capturado(c: CapturaEntrada) -> float | None:
    """El precio que capturó la app: el campo del contrato o, si no viene, el que leyó el teléfono."""
    if c.precio_capturado is not None:
        return c.precio_capturado
    for ch in c.chequeos_locales:  # solo una lectura que el teléfono dio por buena
        if ch.nombre == "precio_leido" and ch.valor and ch.resultado == "ok":
            try:
                return float(str(ch.valor).replace("$", "").replace(",", "").strip())
            except ValueError:
                return None
    return None


def _igual(a: float, b: float) -> bool:
    return abs(a - b) <= max(0.011, 0.005 * max(abs(a), abs(b)))


def precio_unitario_esperado(a: AnalisisIA, c: CapturaEntrada) -> float | None:
    p = c.producto_esperado
    precio = a.precio_promocion if a.tipo_precio == "oferta" and a.precio_promocion else a.precio
    if precio is None or not p.cantidad or not p.unidad:
        return None
    base = FACTOR_UNIDAD.get(p.unidad.strip().lower())
    if base is None:
        return None
    unidad_base, por_unidad = base
    if a.precio_unitario_unidad and unidad_base not in a.precio_unitario_unidad.lower().replace("litro", "l"):
        return None  # la etiqueta usa otra base (p. ej. por 100 g): no se compara
    return precio / (p.cantidad / por_unidad)


def evaluar(c: CapturaEntrada, fotos: dict[str, Foto], a: AnalisisIA | None, extra: dict,
            codigo_falla: bool) -> list[Compuerta]:
    cfg = config()
    g: list[Compuerta] = []

    def poner(nombre: str, ok: bool, detalle: str) -> None:
        g.append(Compuerta(nombre, bool(ok), "" if ok else detalle))

    poner("fotos_completas", all(f.valida for f in fotos.values()), "Falta una foto o no se puede abrir.")
    if a is None:
        poner("analisis_ia", False, "No hay análisis de IA de esta captura.")
        return g
    poner("sin_trampa", not a.fotos_sin_relacion and not a.es_foto_de_pantalla,
          "Hay fotos sin relación o tomadas a una pantalla.")
    poner("calidad", not a.imagen_borrosa and not a.captura_lejana
          and min(a.nitidez_producto, a.nitidez_etiqueta) >= cfg.compuerta_nitidez_min
          and a.legibilidad_etiqueta >= cfg.compuerta_legibilidad_min,
          "La foto no es lo bastante nítida o cercana para leer todo sin zoom.")
    poner("fachada", a.fachada_es_tienda and a.fachada_coincide != "no",
          "La fachada no corresponde a la cadena seleccionada.")
    poner("producto", a.producto_visible and a.producto_coincide == "si" and a.marca_coincide != "no"
          and a.contenido_coincide != "no",
          "No se confirma que el producto (marca y contenido) sea el esperado.")
    poner("descripcion", a.descripcion_completa, "No se lee la descripción completa del producto.")
    poner("etiqueta", a.etiqueta_visible and a.etiqueta_corresponde == "si" and not a.varias_etiquetas,
          "No se confirma que la etiqueta sea la del producto.")
    poner("precio_legible", a.precio is not None, "No se lee el precio de la etiqueta.")

    capturado = precio_capturado(c)
    if capturado is not None and a.precio is not None:
        leidos = [x for x in (a.precio, a.precio_regular, a.precio_promocion) if x is not None]
        poner("precio_coincide", any(_igual(capturado, x) for x in leidos),
              f"El precio capturado (${capturado:g}) no coincide con la etiqueta (${a.precio:g}).")
    p = c.producto_esperado
    if p.precio_minimo is not None and p.precio_maximo is not None:
        fuera = [x for x in (capturado, a.precio) if x is not None and not p.precio_minimo <= x <= p.precio_maximo]
        poner("precio_en_rango", not fuera,
              f"Precio ${fuera[0]:g} fuera del rango de referencia ${p.precio_minimo:g}–${p.precio_maximo:g}." if fuera else "")
    esperado = precio_unitario_esperado(a, c) if a.precio_unitario else None
    if esperado:
        desvio = abs(a.precio_unitario - esperado) / esperado
        poner("precio_unitario_coherente", desvio <= cfg.compuerta_desvio_unitario,
              f"El precio unitario (${a.precio_unitario:g}) no cuadra con precio y contenido (≈ ${esperado:,.2f}).")

    movil_falla = [ch.nombre for ch in c.chequeos_locales if ch.resultado == "falla" and ch.nombre in CHEQUEOS_MOVILES_CRITICOS]
    poner("codigo_barras", not codigo_falla and "codigo_foto_coincide" not in movil_falla,
          "El código de barras leído no es el del producto esperado.")
    poner("foto_no_repetida", extra.get("foto_repetida", 0) == 0,
          f"La foto ya se usó en otra captura ({', '.join(extra.get('repetida_con', [])[:2])}).")
    otras = [n for n in movil_falla if n != "codigo_foto_coincide"]
    poner("chequeos_movil", not otras, f"El teléfono reportó falla en: {', '.join(otras)}.")
    poner("confianza_ia", a.confianza >= cfg.compuerta_confianza_min, "El modelo no está seguro de lo que observa.")
    if cfg.compuerta_anomalias:
        poner("sin_anomalias", not a.anomalias, f"Anomalías: {'; '.join(a.anomalias[:3])}.")
    return g


def fallidas(g: list[Compuerta]) -> list[Compuerta]:
    return [x for x in g if not x.ok]
