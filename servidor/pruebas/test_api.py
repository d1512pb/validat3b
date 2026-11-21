"""Pruebas sin red: el modelo se simula. Correr desde servidor/: python -m pytest"""
from __future__ import annotations

import io
import json

import pytest
from fastapi.testclient import TestClient
from PIL import Image, ImageDraw

from app.api import app
from app.config import config
from app.contrato import RespuestaCaptura, ResultadoOficial
from app.motor import ia
from app.motor.ia import AnalisisIA, ResultadoIA
from app.motor.imagenes import gtin_valido

cli = TestClient(app)

CAPTURA = {
    "capture_id": "CAP-T1",
    "codigo_barras_capturado": "7501055363056",
    "producto_esperado": {"marca": "Coca-Cola", "descripcion": "Refresco de cola", "contenido": "600 ml",
                          "codigo_barras": "7501055363056"},
    "cadena": "Bodega Aurrerá",
    "tienda": "BA Centro",
    "fecha_hora_captura": "2026-11-21T10:42:05-06:00",
}


def jpg(color=(200, 30, 30), texto="3B") -> bytes:
    img = Image.new("RGB", (400, 300), color)
    ImageDraw.Draw(img).text((50, 50), texto * 10, fill=(255, 255, 255))
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    return buf.getvalue()


def enviar(captura=CAPTURA, fotos=("fachada", "producto", "etiqueta"), modo=None, **kw):
    files = {f"foto_{t}": (f"{t}.jpg", jpg(), "image/jpeg") for t in fotos}
    params = {"modo": modo} if modo else {}
    return cli.post("/v1/capturas", data={"captura": json.dumps(captura)}, files=files, params=params, **kw)


def analisis(**cambios) -> AnalisisIA:
    base = dict(
        fotos_sin_relacion=False, es_foto_de_pantalla=False, imagen_borrosa=False, captura_lejana=False,
        nitidez_producto=0.9, nitidez_etiqueta=0.9, tamano_producto=0.6, legibilidad_etiqueta=0.9,
        fachada_es_tienda=True, cadena_en_fachada="Bodega Aurrerá", fachada_coincide="si", producto_visible=True,
        marca_en_foto="Coca-Cola", descripcion_en_foto="Refresco de cola", contenido_en_foto="600 ml",
        marca_coincide="si", contenido_coincide="si",
        producto_coincide="si", descripcion_completa=True, etiqueta_visible=True, etiqueta_corresponde="si",
        descripcion_etiqueta="Refresco de cola 600 ml", varias_etiquetas=False,
        precio=18.5, precio_regular=18.5, precio_promocion=None, precio_tachado=False,
        precio_unitario_visible=True, precio_unitario=30.83, precio_unitario_unidad="l",
        tipo_precio="regular", promocion=None, anomalias=[],
        codigo_barras_leido=None, confianza=0.98, motivo="sin problemas")
    base.update(cambios)
    return AnalisisIA(**base)


@pytest.fixture
def ia_falsa(monkeypatch):
    """Reemplaza la llamada al modelo; guarda qué modelos se llamaron."""
    llamadas: list[str] = []
    respuestas: dict[str, AnalisisIA] = {}

    def falso(c, fotos, modelo):
        llamadas.append(modelo)
        a = respuestas.get(modelo) or respuestas.get("*") or analisis()
        return ResultadoIA(a, modelo, 1500, 150, 10)

    monkeypatch.setattr(ia, "analizar", falso)
    return llamadas, respuestas


def test_salud():
    r = cli.get("/salud")
    assert r.status_code == 200 and r.json()["estado"] == "ok"


def test_simulado_cumple_contrato_y_formato_oficial():
    for sufijo, esperado in (("APR", "aprobada"), ("REC", "rechazada"), ("REV", "revision_manual")):
        r = enviar({**CAPTURA, "capture_id": f"CAP-1-{sufijo}"}, modo="simulado")
        assert r.status_code == 200, r.text
        cuerpo = RespuestaCaptura.model_validate(r.json())
        assert cuerpo.decision.value == esperado
        oficial = {k: v for k, v in r.json().items() if k != "traza"}
        ResultadoOficial.model_validate(oficial)


def test_errores_del_contrato():
    r = cli.post("/v1/capturas", data={"captura": "{no es json"})
    assert r.status_code == 400 and r.json()["error"]["codigo"] == "CAPTURA_NO_ES_JSON"
    r = cli.post("/v1/capturas", data={"captura": json.dumps({"capture_id": "X"})})
    assert r.status_code == 422 and r.json()["error"]["detalles"]
    r = cli.post("/v1/capturas", data={"captura": json.dumps(CAPTURA)},
                 files={"foto_producto": ("p.gif", b"GIF89a", "image/gif")})
    assert r.status_code == 415


def test_aprueba_cuando_todo_coincide(ia_falsa):
    llamadas, _ = ia_falsa
    r = enviar()
    assert r.status_code == 200, r.text
    j = r.json()
    assert j["decision"] == "aprobada" and j["reason_code"] is None
    assert j["precio_detectado"] == 18.5
    # Doble llave: la aprobación del rápido se confirma con el fuerte.
    assert llamadas == [config().modelo_rapido, config().modelo_fuerte]
    assert j["traza"]["doble_verificacion"] is True and j["traza"]["compuertas_fallidas"] == []


def test_rechaza_producto_distinto(ia_falsa):
    _, resp = ia_falsa
    resp["*"] = analisis(producto_coincide="no", marca_en_foto="Pepsi", motivo="Es Pepsi, no Coca-Cola.")
    j = enviar().json()
    assert j["decision"] == "rechazada"
    assert j["reason_code"] == "PRODUCTO_NO_COINCIDE"
    assert j["validaciones"]["producto"] == "falla"


def test_falta_foto_de_producto_se_rechaza_sin_ia(ia_falsa):
    llamadas, _ = ia_falsa
    j = enviar(fotos=("fachada", "etiqueta")).json()
    assert j["decision"] == "rechazada" and j["reason_code"] == "PRODUCTO_NO_VISIBLE"
    assert j["traza"]["capa_decisiva"] == "local" and llamadas == []


def test_zona_gris_escala_a_sonnet(ia_falsa):
    llamadas, resp = ia_falsa
    resp[config().modelo_rapido] = analisis(producto_coincide="dudoso", confianza=0.8)
    resp[config().modelo_fuerte] = analisis(confianza=0.99)
    j = enviar().json()
    assert llamadas == [config().modelo_rapido, config().modelo_fuerte]
    assert j["traza"]["capa_decisiva"] == "fuerte" and j["decision"] == "aprobada"


def test_si_la_ia_falla_va_a_revision(monkeypatch):
    def rompe(*a, **k):
        raise RuntimeError("sin red")
    monkeypatch.setattr(ia, "analizar", rompe)
    j = enviar().json()
    assert j["decision"] == "revision_manual" and j["traza"]["capa_decisiva"] == "respaldo"


def test_gtin():
    assert gtin_valido("7501055363056")
    assert not gtin_valido("7501055363057")
