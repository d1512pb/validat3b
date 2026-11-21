"""Cascada: capa local → modelo rápido (Haiku) → modelo fuerte (Sonnet) solo en la zona gris."""
from __future__ import annotations

import logging
import time

from app import VERSION_MOTOR
from app.config import config
from app.contrato import CapturaEntrada, Decision, Estado, RespuestaCaptura, Traza, Validaciones
from app.motor import decision as dec
from app.motor import compuertas, fusion, geo, ia, simulado
from app.motor.huellas import IndiceHuellas, dhash
from app.motor.imagenes import Foto, cargar

log = logging.getLogger("validat3b")

# En el servidor, el índice vive mientras viva el proceso; en lote se pasa uno por corrida.
INDICE_GLOBAL = IndiceHuellas()


def validar(c: CapturaEntrada, fotos_bytes: dict[str, bytes | None], modo: str | None = None,
            indice: IndiceHuellas | None = None) -> RespuestaCaptura:
    return validar_detallado(c, fotos_bytes, modo, indice)[0]


def _extra(c: CapturaEntrada, fotos: dict[str, Foto], indice: IndiceHuellas) -> dict:
    extra: dict = {"foto_repetida": 0, "repetida_con": []}
    for t in ("producto", "etiqueta"):
        if fotos[t].valida:
            iguales = indice.registrar(t, dhash(fotos[t].imagen), c.capture_id)
            extra["foto_repetida"] += len(iguales)
            extra["repetida_con"] += iguales[:3]
    u = c.metadatos_app.ubicacion
    extra["distancia_cadena_m"] = geo.distancia_a_cadena_m(c.cadena, u.latitud, u.longitud) if u else None
    return extra


def validar_detallado(c: CapturaEntrada, fotos_bytes: dict[str, bytes | None], modo: str | None = None,
                      indice: IndiceHuellas | None = None) -> tuple[RespuestaCaptura, dict]:
    cfg = config()
    inicio = time.perf_counter()
    if (modo or cfg.modo) == "simulado":
        return simulado.responder(c, fotos_bytes), {"modo": "simulado"}

    fotos: dict[str, Foto] = {t: cargar(t, fotos_bytes.get(t)) for t in ("fachada", "producto", "etiqueta")}
    local = dec.senales_locales(c, fotos)
    extra = _extra(c, fotos, indice or INDICE_GLOBAL)
    traza = Traza(version_motor=VERSION_MOTOR, capa_decisiva="local")
    detalle: dict = {"extra": extra, "analisis": {}, "nitidez": {t: f.nitidez for t, f in fotos.items()},
                     "codigos": {t: f.codigos for t, f in fotos.items()}, "fallas_ia": []}

    def evaluar(analisis: ia.AnalisisIA, modelo_ia: str, etapa: str) -> dec.Evaluacion:
        fallas_ia = dec.senales_ia(c, analisis)
        ev = dec.combinar(local, fallas_ia, analisis.confianza)
        codigo_falla = local.codigo_barras_estado == Estado.falla
        x = fusion.caracteristicas(c, fotos, ev.p_valida, codigo_falla, analisis, extra)
        cf = compuertas.fallidas(compuertas.evaluar(c, fotos, analisis, extra, codigo_falla))
        for g in cf:  # cada compuerta fallida también es una señal para la fusión
            x[f"compuerta_{g.nombre}_falla"] = 1.0
        x["compuertas_fallidas"] = float(len(cf))
        p, p_rech, fuente = fusion.p_final(x, ev.p_valida, modelo_ia)
        umbrales = fusion.umbrales(modelo_ia, etapa) or fusion.umbrales(modelo_ia, "final")
        if cfg.compuertas_activas:
            ev.compuertas_fallidas = [(g.nombre, g.detalle) for g in cf]
        registro = dict(p_reglas=ev.p_valida, p_final=p, p_rechazo=p_rech, fuente_p=fuente, caracteristicas=x,
                        umbrales=umbrales, compuertas_fallidas=[g.nombre for g in cf],
                        fallas=[(f.codigo, round(f.severidad, 3)) for f in ev.fallas])
        detalle.update(registro)
        detalle.setdefault("por_modelo", {})[modelo_ia] = registro
        ev.p_valida, ev.p_rechazo, ev.umbrales = p, p_rech, umbrales
        ev.motivo_aprendido = fusion.motivo_aprendido(x, modelo_ia)
        return ev

    # 1. Capa local: si ya es un rechazo seguro (faltan fotos o el código del producto no es el esperado),
    #    se decide con las reglas locales y no se gastan tokens.
    if dec.decidir(local.p_valida) == Decision.rechazada or not any(f.valida for f in fotos.values()):
        detalle.update(p_reglas=local.p_valida, p_final=local.p_valida, fuente_p="local")
        return _armar(c, local, None, traza, inicio), detalle
    ev = local

    # 2. Modelo rápido.
    analisis = None
    try:
        r = ia.analizar(c, fotos, cfg.modelo_rapido)
        _sumar(traza, r, "rapido")
        analisis = r.analisis
        detalle["analisis"]["rapido"] = analisis.model_dump()
        # Con escalado, el rápido solo decide si está muy seguro (umbrales de cascada).
        ev = evaluar(analisis, cfg.modelo_rapido, "cascada" if cfg.escalar_a_fuerte else "final")
    except ia.ERRORES_IA as e:
        log.warning("Falla del modelo rápido en %s: %s", c.capture_id, e)
        traza.avisos.append("Modelo rápido no disponible")
        detalle["fallas_ia"].append("rapido")

    # 3. Modelo fuerte solo si quedó en la zona gris (o si el rápido falló).
    en_zona_gris = dec.decidir(ev.p_valida, ev.p_rechazo, ev.umbrales) == Decision.revision_manual
    if cfg.escalar_a_fuerte and (analisis is None or en_zona_gris):
        analisis_rapido = analisis
        try:
            r = ia.analizar(c, fotos, cfg.modelo_fuerte)
            _sumar(traza, r, "fuerte")
            analisis = r.analisis
            detalle["analisis"]["fuerte"] = analisis.model_dump()
            ev = evaluar(analisis, cfg.modelo_fuerte, "final")
        except ia.ERRORES_IA as e:
            log.warning("Falla del modelo fuerte en %s: %s", c.capture_id, e)
            detalle["fallas_ia"].append("fuerte")
            if analisis_rapido is not None:  # sin el fuerte, decide el rápido con sus umbrales normales
                ev = evaluar(analisis_rapido, cfg.modelo_rapido, "final")

    if analisis is None:
        traza.capa_decisiva = "respaldo"
        return _respaldo(c, traza, inicio), detalle

    # 4. Doble llave: si el rápido quiere aprobar solo, el fuerte debe confirmarlo antes de aprobar.
    quiere_aprobar = (dec.decidir(ev.p_valida, ev.p_rechazo, ev.umbrales) == Decision.aprobada
                      and not ev.compuertas_fallidas)
    if quiere_aprobar and cfg.verificar_aprobaciones and cfg.modelo_fuerte not in traza.modelos_usados:
        try:
            r = ia.analizar(c, fotos, cfg.modelo_fuerte)
            _sumar(traza, r, "fuerte")
            detalle["analisis"]["fuerte"] = r.analisis.model_dump()
            ev_f = evaluar(r.analisis, cfg.modelo_fuerte, "final")
            if dec.decidir(ev_f.p_valida, ev_f.p_rechazo, ev_f.umbrales) == Decision.aprobada and not ev_f.compuertas_fallidas:
                traza.doble_verificacion = True
            else:
                # El fuerte no confirma: manda su lectura, y si también quería aprobar, a revisión.
                if not ev_f.compuertas_fallidas:
                    ev_f.compuertas_fallidas = [("doble_verificacion", "El segundo modelo no confirmó la aprobación.")]
                ev, analisis = ev_f, r.analisis
        except ia.ERRORES_IA as e:
            log.warning("Falla de la verificación en %s: %s", c.capture_id, e)
            detalle["fallas_ia"].append("verificacion")
            ev.compuertas_fallidas = [("doble_verificacion", "No se pudo confirmar la aprobación con el segundo modelo.")]
    detalle["compuertas_finales"] = [n for n, _ in ev.compuertas_fallidas]
    return _armar(c, ev, analisis, traza, inicio), detalle


def _sumar(traza: Traza, r: ia.ResultadoIA, capa: str) -> None:
    traza.capa_decisiva = capa
    traza.modelos_usados.append(r.modelo)
    traza.tokens_entrada += r.tokens_entrada
    traza.tokens_salida += r.tokens_salida


def _armar(c, ev, analisis, traza: Traza, inicio: float) -> RespuestaCaptura:
    oficial, avisos = dec.resultado(c, ev, analisis)
    traza.avisos.extend(avisos)
    if analisis:
        traza.tipo_precio = analisis.tipo_precio
        traza.precio_unitario_detectado = analisis.precio_unitario
        traza.promocion = analisis.promocion
        traza.precio_regular = analisis.precio_regular
        traza.precio_promocion = analisis.precio_promocion
    traza.compuertas_fallidas = [f"{n}: {d}" if d else n for n, d in ev.compuertas_fallidas]
    traza.latencia_ms = int((time.perf_counter() - inicio) * 1000)
    return RespuestaCaptura(**oficial.model_dump(), traza=traza)


def _respaldo(c: CapturaEntrada, traza: Traza, inicio: float) -> RespuestaCaptura:
    """Si la IA no responde, la captura va a revisión: nunca se aprueba a ciegas."""
    traza.latencia_ms = int((time.perf_counter() - inicio) * 1000)
    return RespuestaCaptura(
        capture_id=c.capture_id, decision=Decision.revision_manual, confidence_score=0.5, reason_code=None,
        validaciones=Validaciones(tienda_fachada=Estado.ok, producto=Estado.ok, codigo_barras=Estado.ok, evidencia_precio=Estado.ok),
        explicacion="No se pudo analizar la captura con IA; pasa a revisión manual.", traza=traza)
