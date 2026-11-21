"""Modo simulado: respuestas válidas sin llamar a la IA, para que la app integre desde ya.

Es determinista por capture_id: el mismo id siempre da la misma decisión.
Para forzar un caso, termina el capture_id en -APR, -REC o -REV.
"""
from __future__ import annotations

import hashlib

from app import VERSION_MOTOR
from app.contrato import CapturaEntrada, Decision, Estado, RespuestaCaptura, Traza, Validaciones
from app.motor.decision import AVISOS

_CASOS = [
    (Decision.aprobada, None, {}),
    (Decision.rechazada, "PRODUCTO_NO_COINCIDE", {"producto": Estado.falla}),
    (Decision.rechazada, "IMAGEN_BORROSA", {"producto": Estado.falla}),
    (Decision.revision_manual, "FACHADA_INCORRECTA", {"tienda_fachada": Estado.falla}),
    (Decision.rechazada, "SIN_PRECIO_UNITARIO", {"evidencia_precio": Estado.falla}),
]


def responder(c: CapturaEntrada, fotos: dict[str, bytes | None]) -> RespuestaCaptura:
    sufijo = c.capture_id.upper().rsplit("-", 1)[-1]
    if sufijo == "APR":
        caso = _CASOS[0]
    elif sufijo == "REC":
        caso = _CASOS[1]
    elif sufijo == "REV":
        caso = _CASOS[3]
    else:
        caso = _CASOS[int(hashlib.sha1(c.capture_id.encode()).hexdigest(), 16) % len(_CASOS)]
    decision, reason, fallas = caso
    if not fotos.get("producto"):
        decision, reason, fallas = Decision.rechazada, "PRODUCTO_NO_VISIBLE", {"producto": Estado.falla}
    val = {"tienda_fachada": Estado.ok, "producto": Estado.ok, "codigo_barras": Estado.ok, "evidencia_precio": Estado.ok}
    val.update(fallas)
    return RespuestaCaptura(
        capture_id=c.capture_id,
        decision=decision,
        confidence_score=0.9,
        reason_code=reason,
        validaciones=Validaciones(**val),
        precio_detectado=42.5,
        codigo_barras_detectado=c.codigo_barras_capturado,
        explicacion="[SIMULADO] " + ("Captura consistente." if reason is None else AVISOS[reason]),
        traza=Traza(version_motor=VERSION_MOTOR, capa_decisiva="simulado", tipo_precio="regular",
                    precio_unitario_detectado=7.08, avisos=[] if reason is None else [AVISOS[reason]]),
    )
