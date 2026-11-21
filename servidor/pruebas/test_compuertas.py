"""Reglas para no aprobar nada inválido: cada compuerta, la doble llave y el respaldo. Sin red."""
from __future__ import annotations

import pytest

from app.config import config
from app.motor import ia
from app.motor.ia import ResultadoIA
from pruebas.test_api import CAPTURA, analisis, enviar


@pytest.fixture
def ia_por_modelo(monkeypatch):
    """Respuestas distintas por modelo; '*' aplica a todos."""
    respuestas: dict = {}
    llamadas: list[str] = []

    def falso(c, fotos, modelo):
        llamadas.append(modelo)
        r = respuestas.get(modelo, respuestas.get("*", analisis()))
        if isinstance(r, Exception):
            raise r
        return ResultadoIA(r, modelo, 1000, 100, 5)

    monkeypatch.setattr(ia, "analizar", falso)
    return respuestas, llamadas


def _no_aprobada(j: dict, compuerta: str | None = None) -> None:
    assert j["decision"] != "aprobada", j
    if compuerta:
        assert any(x.startswith(compuerta) for x in j["traza"]["compuertas_fallidas"]), j["traza"]["compuertas_fallidas"]


def test_todo_bien_se_aprueba(ia_por_modelo):
    j = enviar({**CAPTURA, "precio_capturado": 18.5}).json()
    assert j["decision"] == "aprobada" and j["traza"]["doble_verificacion"]


def test_precio_capturado_distinto_de_la_etiqueta(ia_por_modelo):
    j = enviar({**CAPTURA, "precio_capturado": 21.0}).json()
    _no_aprobada(j, "precio_coincide")
    assert j["validaciones"]["evidencia_precio"] == "falla"


def test_promocion_vale_si_coincide_con_precio_de_oferta(ia_por_modelo):
    resp, _ = ia_por_modelo
    resp["*"] = analisis(precio=15.0, precio_regular=18.5, precio_promocion=15.0, tipo_precio="oferta",
                         precio_tachado=True, promocion="Antes $18.50 ahora $15", precio_unitario=25.0)
    j = enviar({**CAPTURA, "precio_capturado": 18.5}).json()
    assert j["decision"] == "aprobada", j["traza"]["compuertas_fallidas"]
    assert j["traza"]["tipo_precio"] == "oferta" and j["traza"]["precio_promocion"] == 15.0


def test_precio_fuera_del_rango_de_referencia(ia_por_modelo):
    c = {**CAPTURA, "producto_esperado": {**CAPTURA["producto_esperado"], "precio_minimo": 10, "precio_maximo": 15}}
    _no_aprobada(enviar(c).json(), "precio_en_rango")


def test_precio_unitario_que_no_cuadra(ia_por_modelo):
    resp, _ = ia_por_modelo
    resp["*"] = analisis(precio_unitario=55.0)   # 18.50 por 600 ml son ~30.83 por litro
    c = {**CAPTURA, "producto_esperado": {**CAPTURA["producto_esperado"], "unidad": "mililitro", "cantidad": 600}}
    _no_aprobada(enviar(c).json(), "precio_unitario_coherente")


def test_precio_leido_por_el_telefono_sirve_si_no_hay_precio_capturado(ia_por_modelo):
    c = {**CAPTURA, "chequeos_locales": [{"nombre": "precio_leido", "foto": "etiqueta", "resultado": "ok", "valor": "$25.00"}]}
    _no_aprobada(enviar(c).json(), "precio_coincide")


@pytest.mark.parametrize("cambio,compuerta", [
    ({"marca_coincide": "no"}, "producto"),
    ({"contenido_coincide": "no"}, "producto"),
    ({"producto_coincide": "dudoso"}, "producto"),
    ({"etiqueta_corresponde": "no_se_distingue"}, "etiqueta"),
    ({"varias_etiquetas": True}, "etiqueta"),
    ({"precio": None}, "precio_legible"),
    ({"nitidez_etiqueta": 0.3}, "calidad"),
    ({"captura_lejana": True}, "calidad"),
    ({"es_foto_de_pantalla": True}, "sin_trampa"),
    ({"fotos_sin_relacion": True}, "sin_trampa"),
    ({"fachada_coincide": "no"}, "fachada"),
    ({"descripcion_completa": False}, "descripcion"),
    ({"confianza": 0.6}, "confianza_ia"),
])
def test_cada_compuerta_impide_aprobar(ia_por_modelo, cambio, compuerta):
    resp, _ = ia_por_modelo
    resp["*"] = analisis(**cambio)
    j = enviar({**CAPTURA, "capture_id": f"CAP-{compuerta}"}).json()
    assert j["decision"] != "aprobada", (cambio, j)


def test_codigo_o_precio_no_leidos_por_el_telefono_no_bloquean(ia_por_modelo):
    c = {**CAPTURA, "chequeos_locales": [
        {"nombre": "codigo_barras", "foto": "producto", "resultado": "falla"},
        {"nombre": "precio_leido", "foto": "etiqueta", "resultado": "falla", "valor": "$99.00"}]}
    j = enviar(c).json()
    assert not {"chequeos_movil", "precio_coincide"} & set(j["traza"]["compuertas_fallidas"]), j["traza"]


def test_falla_reportada_por_el_telefono_impide_aprobar(ia_por_modelo):
    c = {**CAPTURA, "chequeos_locales": [{"nombre": "nitidez", "foto": "producto", "resultado": "falla", "puntaje": 0.2}]}
    _no_aprobada(enviar(c).json(), "chequeos_movil")


def test_doble_llave_el_fuerte_no_confirma(ia_por_modelo):
    resp, llamadas = ia_por_modelo
    resp[config().modelo_fuerte] = analisis(producto_coincide="no", marca_coincide="no", marca_en_foto="Pepsi",
                                            motivo="Es Pepsi.")
    j = enviar().json()
    assert llamadas == [config().modelo_rapido, config().modelo_fuerte]
    assert j["decision"] != "aprobada" and not j["traza"]["doble_verificacion"]


def test_doble_llave_sin_respuesta_del_fuerte_va_a_revision(ia_por_modelo):
    resp, _ = ia_por_modelo
    resp[config().modelo_fuerte] = RuntimeError("sin red")
    j = enviar().json()
    assert j["decision"] == "revision_manual"
    _no_aprobada(j, "doble_verificacion")


def test_foto_repetida_en_otra_captura(ia_por_modelo):
    enviar({**CAPTURA, "capture_id": "CAP-ORIGINAL-1"})
    _no_aprobada(enviar({**CAPTURA, "capture_id": "CAP-COPIA-2"}).json(), "foto_no_repetida")


def test_anomalias_solo_bloquean_si_se_activa_la_compuerta(ia_por_modelo, monkeypatch):
    resp, _ = ia_por_modelo
    resp["*"] = analisis(anomalias=["etiqueta escrita a mano"])
    assert enviar({**CAPTURA, "capture_id": "CAP-AN-1"}).json()["decision"] == "aprobada"
    monkeypatch.setattr(config(), "compuerta_anomalias", True)
    _no_aprobada(enviar({**CAPTURA, "capture_id": "CAP-AN-2"}).json(), "sin_anomalias")


def test_foto_trampa_lleva_motivo_de_regla(ia_por_modelo):
    resp, _ = ia_por_modelo
    resp["*"] = analisis(fotos_sin_relacion=True, producto_visible=False, motivo="Son fotos sin relación.")
    j = enviar({**CAPTURA, "capture_id": "CAP-TRAMPA"}).json()
    assert j["decision"] == "rechazada" and j["reason_code"] == "PRODUCTO_NO_VISIBLE"
