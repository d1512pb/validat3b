"""Fusión de señales y decisión por costo.

Cada señal de falla trae una severidad: la probabilidad de que, por ella sola, la captura sea inválida.
p(válida) = producto de (1 - severidad). Se aprueba, rechaza o manda a revisión según los umbrales
de config, que salen de la tabla de costo (10 / 3 / 1). Las severidades son provisionales: se
calibran con el set de entrenamiento.
"""
from __future__ import annotations

from dataclasses import dataclass, field

from app.config import config
from app.contrato import (REASON_CODES_PROVISIONALES, CapturaEntrada, Decision, Estado,
                          ResultadoOficial, Validaciones)
from app.motor.ia import AnalisisIA
from app.motor.imagenes import Foto, gtin_valido, normalizar_codigo

FRENTE = {
    "CONTRATIPO_INVALIDO": "A", "PRODUCTO_NO_COINCIDE": "A", "DESCRIPCION_INCOMPLETA": "A", "PRODUCTO_NO_VISIBLE": "A",
    "FACHADA_INCORRECTA": "B", "CAPTURA_LEJANA": "B", "IMAGEN_BORROSA": "B", "FOTO_DE_PANTALLA": "B",
    "SIN_ETIQUETA_PRECIO": "C", "SIN_PRECIO_UNITARIO": "C",
}
PRIORIDAD_FRENTE = {"A": 0, "B": 1, "C": 2}

AVISOS = {
    "PRODUCTO_NO_VISIBLE": "Toma la foto con el producto al centro.",
    "PRODUCTO_NO_COINCIDE": "Revisa que sea el producto o contratipo esperado.",
    "CONTRATIPO_INVALIDO": "Ese contratipo no está permitido para este producto.",
    "DESCRIPCION_INCOMPLETA": "Que se lean marca, descripción y contenido.",
    "FACHADA_INCORRECTA": "Toma la fachada de la tienda seleccionada, con el letrero visible.",
    "CAPTURA_LEJANA": "Acércate más.",
    "IMAGEN_BORROSA": "La foto salió borrosa: no muevas el teléfono al tomarla.",
    "FOTO_DE_PANTALLA": "Toma la foto directo al producto, no a una pantalla.",
    "SIN_ETIQUETA_PRECIO": "Falta la etiqueta de precio del anaquel.",
    "SIN_PRECIO_UNITARIO": "Que se vea el precio unitario de la etiqueta.",
}


@dataclass
class Falla:
    codigo: str
    severidad: float
    detalle: str = ""


@dataclass
class Evaluacion:
    p_valida: float
    fallas: list[Falla] = field(default_factory=list)
    codigo_barras_estado: Estado = Estado.ok
    codigo_detectado: str | None = None
    p_rechazo: float | None = None   # None = se usa 1 - p_valida
    umbrales: dict | None = None     # umbrales calibrados del modelo que decidió
    motivo_aprendido: str | None = None  # reason_code del clasificador entrenado, si existe
    compuertas_fallidas: list[tuple[str, str]] = field(default_factory=list)  # (nombre, detalle): impiden aprobar


def senales_locales(c: CapturaEntrada, fotos: dict[str, Foto]) -> Evaluacion:
    """Lo que se sabe sin IA: fotos faltantes y código de barras."""
    fallas: list[Falla] = []
    if not fotos["producto"].valida:
        fallas.append(Falla("PRODUCTO_NO_VISIBLE", 0.97, "No llegó una foto de producto legible."))
    if not fotos["etiqueta"].valida:
        fallas.append(Falla("SIN_ETIQUETA_PRECIO", 0.97, "No llegó una foto de etiqueta legible."))
    if not fotos["fachada"].valida:
        fallas.append(Falla("FACHADA_INCORRECTA", 0.95, "No llegó una foto de fachada legible."))

    esperados = {normalizar_codigo(x) for x in (c.producto_esperado.codigo_barras, c.codigo_barras_capturado)} - {None}
    # Solo la foto de producto: el código de la etiqueta de anaquel suele ser interno de la tienda.
    leidos = [cod for cod in fotos["producto"].codigos if gtin_valido(cod)]
    detectado = leidos[0] if leidos else None
    estado = Estado.ok
    if leidos and esperados and not any(normalizar_codigo(x) in esperados for x in leidos):
        estado = Estado.falla
        fallas.append(Falla("PRODUCTO_NO_COINCIDE", 0.85,
                            f"El código leído en la foto ({detectado}) no es el del producto esperado."))
    elif c.codigo_barras_capturado and not gtin_valido(c.codigo_barras_capturado) and not leidos:
        estado = Estado.falla
    return Evaluacion(p_valida=_p(fallas), fallas=fallas, codigo_barras_estado=estado, codigo_detectado=detectado)


def senales_ia(c: CapturaEntrada, a: AnalisisIA) -> list[Falla]:
    k = min(max(a.confianza, 0.5), 1.0)  # una IA poco segura pesa menos
    f: list[Falla] = []

    def agrega(cond: bool, codigo: str, sev: float):
        if cond:
            f.append(Falla(codigo, sev * k, a.motivo))

    agrega(a.fotos_sin_relacion, "PRODUCTO_NO_VISIBLE" if not a.producto_visible else "FACHADA_INCORRECTA", 0.9)
    agrega(a.es_foto_de_pantalla, "FOTO_DE_PANTALLA", 0.85)
    agrega(not a.producto_visible, "PRODUCTO_NO_VISIBLE", 0.9)
    codigo_no_coincide = "CONTRATIPO_INVALIDO" if c.producto_esperado.es_contratipo else "PRODUCTO_NO_COINCIDE"
    agrega(a.producto_coincide == "no", codigo_no_coincide, 0.85)
    agrega(a.producto_coincide == "dudoso", codigo_no_coincide, 0.4)
    agrega(a.producto_visible and not a.descripcion_completa, "DESCRIPCION_INCOMPLETA", 0.55)
    agrega(a.imagen_borrosa, "IMAGEN_BORROSA", 0.8)
    agrega(not a.imagen_borrosa and min(a.nitidez_producto, a.nitidez_etiqueta) < 0.4, "IMAGEN_BORROSA", 0.6)
    agrega(a.captura_lejana, "CAPTURA_LEJANA", 0.7)
    agrega(not a.captura_lejana and a.tamano_producto < 0.2, "CAPTURA_LEJANA", 0.5)
    agrega(not a.fachada_es_tienda or a.fachada_coincide == "no", "FACHADA_INCORRECTA", 0.8)
    agrega(a.fachada_es_tienda and a.fachada_coincide == "no_se_distingue", "FACHADA_INCORRECTA", 0.25)
    agrega(not a.etiqueta_visible, "SIN_ETIQUETA_PRECIO", 0.85)
    agrega(a.etiqueta_visible and a.etiqueta_corresponde == "no", "SIN_ETIQUETA_PRECIO", 0.6)
    agrega(a.etiqueta_visible and a.precio is None, "SIN_ETIQUETA_PRECIO", 0.5)
    # El precio unitario faltante NO se castiga: en desarrollo ninguna captura aprobada lo muestra.
    # Queda como característica para la fusión aprendida.
    return f


def _p(fallas: list[Falla], base: float = 1.0) -> float:
    p = base
    for x in fallas:
        p *= 1 - x.severidad
    return max(0.0, min(1.0, p))


def combinar(local: Evaluacion, fallas_ia: list[Falla] | None, confianza_ia: float | None) -> Evaluacion:
    fallas = local.fallas + (fallas_ia or [])
    base = 1.0 if confianza_ia is None else min(max(confianza_ia, 0.0), 0.995)
    return Evaluacion(_p(fallas, base), fallas, local.codigo_barras_estado, local.codigo_detectado)


def decidir(p_aprobar: float, p_rechazo: float | None = None, umbrales: dict | None = None) -> Decision:
    """Aprobar si p_aprobar alcanza el umbral; rechazar si p_rechazo lo alcanza; si no, revisión.
    `umbrales` (calibrados por modelo) manda sobre la configuración general."""
    cfg = config()
    t_apr = (umbrales or {}).get("umbral_aprobar", cfg.umbral_aprobar)
    t_rech = (umbrales or {}).get("umbral_p_rechazo", cfg.umbral_p_rechazo)
    if p_aprobar >= t_apr:
        return Decision.aprobada
    pr = 1 - p_aprobar if p_rechazo is None else p_rechazo
    if pr >= t_rech:
        return Decision.rechazada
    return Decision.revision_manual


def motivo_por_puntajes(a: AnalisisIA) -> str:
    """El defecto más marcado según los puntajes del modelo (0 = peor)."""
    candidatos = {
        "IMAGEN_BORROSA": min(a.nitidez_producto, a.nitidez_etiqueta),
        "CAPTURA_LEJANA": min(a.tamano_producto * 2, a.legibilidad_etiqueta),
        "DESCRIPCION_INCOMPLETA": 0.3 if not a.descripcion_completa else 1.0,
    }
    return min(candidatos, key=candidatos.get)


def principal(fallas: list[Falla]) -> Falla | None:
    if not fallas:
        return None
    return max(fallas, key=lambda x: (round(x.severidad, 3), -PRIORIDAD_FRENTE[FRENTE[x.codigo]]))


def resultado(c: CapturaEntrada, ev: Evaluacion, a: AnalisisIA | None) -> tuple[ResultadoOficial, list[str]]:
    decision = decidir(ev.p_valida, ev.p_rechazo, ev.umbrales)
    bloqueada = decision == Decision.aprobada and bool(ev.compuertas_fallidas)
    if bloqueada:  # alguna compuerta no pasó: nunca se aprueba, va a revisión manual
        decision = Decision.revision_manual
    p_rech = 1 - ev.p_valida if ev.p_rechazo is None else ev.p_rechazo
    if decision == Decision.rechazada and not any(x.severidad >= 0.5 for x in ev.fallas) and a is not None:
        # La fusión rechaza sin una falla fuerte de reglas: el motivo sale del peor puntaje observado.
        ev.fallas.append(Falla(motivo_por_puntajes(a), 0.5, a.motivo))
    fuertes = {x.codigo for x in ev.fallas if x.severidad >= 0.5}
    top = principal(ev.fallas)
    # El clasificador aprendido solo conoce los motivos del set de desarrollo (lejana, borrosa, descripción).
    # Ante trampas, pantallas o producto no visible manda la regla, que sí conoce esos motivos.
    fuera_de_lo_aprendido = a is not None and (a.fotos_sin_relacion or a.es_foto_de_pantalla or not a.producto_visible)
    if ev.motivo_aprendido and decision != Decision.aprobada and not fuera_de_lo_aprendido:
        # El clasificador entrenado con los motivos reales del analista manda sobre las reglas.
        detalle = next((f.detalle for f in ev.fallas if f.codigo == ev.motivo_aprendido), "") or (a.motivo if a else "")
        top = Falla(ev.motivo_aprendido, 1.0, detalle)
        fuertes.add(ev.motivo_aprendido)
    frentes = {FRENTE[cod] for cod in fuertes}
    nombres_cf = {n for n, _ in ev.compuertas_fallidas}
    validaciones = Validaciones(
        tienda_fachada=Estado.falla if ("FACHADA_INCORRECTA" in fuertes or "fachada" in nombres_cf) else Estado.ok,
        producto=Estado.falla if ("A" in frentes or fuertes & {"IMAGEN_BORROSA", "CAPTURA_LEJANA", "FOTO_DE_PANTALLA"}
                                  or nombres_cf & {"producto", "descripcion", "calidad", "sin_trampa"}) else Estado.ok,
        codigo_barras=Estado.falla if "codigo_barras" in nombres_cf else ev.codigo_barras_estado,
        evidencia_precio=Estado.falla if ("C" in frentes or nombres_cf & {
            "etiqueta", "precio_legible", "precio_coincide", "precio_en_rango", "precio_unitario_coherente"}) else Estado.ok,
    )
    if decision == Decision.aprobada:
        reason, confianza = None, ev.p_valida
        precio = f" Precio ${a.precio:,.2f}." if a and a.precio is not None else ""
        explicacion = f"Fachada, producto y etiqueta son consistentes con lo esperado.{precio}"
    else:
        # Rechazo: qué tan probable es que la verdad sea "rechazada".
        # Revisión: qué tan lejos estamos de poder aprobar o rechazar con seguridad.
        confianza = p_rech if decision == Decision.rechazada else max(1 - ev.p_valida, 1 - p_rech) * 0.5 + 0.25
        reason = top.codigo if top else None
        if bloqueada:
            confianza = 0.9  # el modelo quería aprobar pero una regla lo impide: la revisión es la decisión segura
            reason = top.codigo if top and top.severidad >= 0.5 else None
            explicacion = "No se aprueba: " + " ".join(d for _, d in ev.compuertas_fallidas[:3] if d)
        elif top:
            explicacion = top.detalle or REASON_CODES_PROVISIONALES[top.codigo]
        else:
            explicacion = "La evidencia no alcanza la confianza necesaria para aprobar."
        if decision == Decision.revision_manual:
            explicacion = f"Revisión manual: {explicacion}"
    codigo = ev.codigo_detectado or (a.codigo_barras_leido if a else None)
    oficial = ResultadoOficial(
        capture_id=c.capture_id,
        decision=decision,
        confidence_score=round(confianza, 4),
        reason_code=reason,
        validaciones=validaciones,
        precio_detectado=a.precio if a else None,
        codigo_barras_detectado=codigo,
        explicacion=explicacion,
    )
    avisos = [AVISOS[cod] for cod in sorted(fuertes, key=lambda x: PRIORIDAD_FRENTE[FRENTE[x]])]
    return oficial, avisos
