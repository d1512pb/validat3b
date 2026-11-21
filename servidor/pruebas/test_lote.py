"""Pruebas del modo lote, la evaluación, la calibración y las señales locales. Sin red."""
from __future__ import annotations

import json
import time
from types import SimpleNamespace

import numpy as np
import pytest

from app import evaluacion as ev
from app.config import config
from app.lote import Corrida, adaptador_jsonl, correr
from app.motor import cache as cache_mod
from app.motor import geo, ia
from app.motor.fusion import entrenar
from app.motor.huellas import IndiceHuellas, dhash
from app.motor.ia import ResultadoIA
from app.motor.limitador import LimitadorTPM
from pruebas.test_api import CAPTURA, analisis, jpg


# ------------------------------------------------------------------ evaluación

def test_tabla_de_costos():
    assert ev.costo("aprobada", True) == 0 and ev.costo("rechazada", False) == 0
    assert ev.costo("revision_manual", True) == 1
    assert ev.costo("rechazada", True) == 3
    assert ev.costo("aprobada", False) == 10


def test_wilson_es_mas_estricta_con_pocos_datos():
    assert ev.wilson_inferior(30, 30) < 0.97 < ev.wilson_inferior(600, 600)


def test_umbrales_cumplen_precision_y_bajan_costo():
    rng = np.random.default_rng(0)
    y = rng.random(2000) < 0.7
    p = np.clip(np.where(y, rng.beta(8, 1.5, 2000), rng.beta(1.5, 5, 2000)), 0, 1)
    r = ev.optimizar_umbrales(p, y.astype(int))
    o = r["optimo"]
    assert o["cota_inferior"] >= 0.97
    assert o["costo"] < len(y) / 2                   # mucho mejor que mandar todo a revisión


def test_resumen():
    preds = [{"capture_id": "a", "decision": "aprobada", "reason_code": None, "precio_detectado": 10.0},
             {"capture_id": "b", "decision": "rechazada", "reason_code": "IMAGEN_BORROSA", "precio_detectado": None},
             {"capture_id": "c", "decision": "revision_manual", "reason_code": None, "precio_detectado": None}]
    et = {"a": ev.Etiqueta(True, precio=10.0), "b": ev.Etiqueta(False, "IMAGEN_BORROSA"), "c": ev.Etiqueta(True)}
    r = ev.resumen(preds, et)
    assert r["costo_total"] == 1 and r["acierto_reason_code"] == 1.0 and r["acierto_precio"] == 1.0


# ------------------------------------------------------------------ fusión

def test_fusion_aprende_senal_simple():
    rng = np.random.default_rng(1)
    filas, y = [], []
    for _ in range(400):
        mala = rng.random() < 0.3
        filas.append({"ia_producto_coincide_no": float(mala and rng.random() < 0.9), "ruido": rng.random()})
        y.append(int(not mala))
    m = entrenar(filas, y)
    assert m.p({"ia_producto_coincide_no": 1.0, "ruido": 0.5}) < 0.3
    assert m.p({"ia_producto_coincide_no": 0.0, "ruido": 0.5}) > 0.8


# ------------------------------------------------------------------ señales locales

def test_huellas_detectan_foto_reusada():
    from PIL import Image
    import io
    img = Image.open(io.BytesIO(jpg((10, 120, 200), "PRODUCTO")))
    idx = IndiceHuellas()
    assert idx.registrar("producto", dhash(img), "CAP-1") == []
    assert idx.registrar("producto", dhash(img.resize((380, 285))), "CAP-2") == ["CAP-1"]


def test_distancia_a_cadena(tmp_path, monkeypatch):
    csv = tmp_path / "t.csv"
    csv.write_text("cadena,latitud,longitud\nBodega Aurrerá,19.3574,-99.0927\nOXXO,19.0,-99.0\n", encoding="utf-8")
    monkeypatch.setattr(config(), "ruta_tiendas", str(csv))
    geo._tiendas.cache_clear()
    try:
        assert geo.distancia_a_cadena_m("Mi Bodega Aurrera", 19.3574, -99.0927) < 1
        assert 1000 < geo.distancia_a_cadena_m("Bodega Aurrerá", 19.3674, -99.0927) < 1200
        assert geo.distancia_a_cadena_m("Cadena inexistente", 19, -99) is None
    finally:
        geo._tiendas.cache_clear()


def test_limitador_espera_cuando_se_acaba():
    lim = LimitadorTPM(6000)  # 100 tokens/s
    lim.tomar(6000)
    t = time.perf_counter()
    lim.tomar(50)
    assert time.perf_counter() - t >= 0.4


# ------------------------------------------------------------------ caché de IA

def test_cache_evita_segunda_llamada(tmp_path, monkeypatch):
    llamadas = []

    class Cliente:
        class messages:
            @staticmethod
            def create(**kw):
                llamadas.append(kw["model"])
                return SimpleNamespace(
                    stop_reason="end_turn", content=[SimpleNamespace(type="text", text=analisis().model_dump_json())],
                    usage=SimpleNamespace(input_tokens=1200, output_tokens=150))

    monkeypatch.setattr(ia, "_cliente", lambda: Cliente)
    monkeypatch.setattr(ia, "_cache", lambda: cache_mod.CacheDisco(tmp_path))
    from app.contrato import CapturaEntrada
    from app.motor.imagenes import cargar
    c = CapturaEntrada.model_validate(CAPTURA)
    fotos = {t: cargar(t, jpg()) for t in ("fachada", "producto", "etiqueta")}
    r1 = ia.analizar(c, fotos, "claude-haiku-5-5")
    r2 = ia.analizar(c, fotos, "claude-haiku-5-5")
    assert llamadas == ["claude-haiku-5-5"] and not r1.desde_cache and r2.desde_cache
    assert r2.analisis == r1.analisis


# ------------------------------------------------------------------ lote completo

def _dataset(tmp_path, n=6):
    (tmp_path / "img").mkdir()
    lineas = []
    for i in range(n):
        fotos = {}
        for t in ("fachada", "producto", "etiqueta"):
            ruta = tmp_path / "img" / f"{i}_{t}.jpg"
            ruta.write_bytes(jpg((i * 40 % 255, 80, 160), f"{t}{i}"))
            fotos[t] = f"img/{i}_{t}.jpg"
        if i == 5:
            fotos["producto"] = None  # captura sin foto de producto
        d = {**CAPTURA, "capture_id": f"CAP-{i:03d}", "fotos": fotos,
             "etiqueta": {"valida": i % 3 != 0, "reason_code": None if i % 3 else "PRODUCTO_NO_COINCIDE"}}
        lineas.append(json.dumps(d))
    ruta = tmp_path / "capturas.jsonl"
    ruta.write_text("\n".join(lineas), encoding="utf-8")
    return ruta


def test_lote_completo_y_reanudable(tmp_path, monkeypatch):
    llamadas = []

    def falso(c, fotos, modelo):
        llamadas.append(c.capture_id)
        mala = int(c.capture_id[-1]) % 3 == 0
        return ResultadoIA(analisis(producto_coincide="no" if mala else "si"), modelo, 1500, 150, 5)

    monkeypatch.setattr(ia, "analizar", falso)
    datos = _dataset(tmp_path)
    salida = tmp_path / "corrida"
    rep = correr(list(adaptador_jsonl(datos)), Corrida(salida, trabajadores=3))
    resultados = json.loads((salida / "resultados.json").read_text(encoding="utf-8"))
    assert [r["capture_id"] for r in resultados] == [f"CAP-{i:03d}" for i in range(6)]
    assert all("traza" not in r for r in resultados)
    assert rep["evaluacion"]["n"] == 6 and rep["evaluacion"]["cumple_precision"]
    assert next(r for r in resultados if r["capture_id"] == "CAP-005")["reason_code"] == "PRODUCTO_NO_VISIBLE"
    n = len(llamadas)
    rep2 = correr(list(adaptador_jsonl(datos)), Corrida(salida, trabajadores=3))
    assert rep2["procesadas_ahora"] == 0 and len(llamadas) == n   # retomó sin volver a llamar


def test_lote_reintenta_lo_que_fallo_por_la_ia(tmp_path, monkeypatch):
    caida = {"activa": True}

    def falso(c, fotos, modelo):
        if caida["activa"] and c.capture_id == "CAP-001":
            raise RuntimeError("límite de tokens")
        return ResultadoIA(analisis(), modelo, 1500, 150, 5)

    monkeypatch.setattr(ia, "analizar", falso)
    datos = _dataset(tmp_path, 3)
    salida = tmp_path / "corrida"
    rep = correr(list(adaptador_jsonl(datos)), Corrida(salida, trabajadores=2))
    resultados = json.loads((salida / "resultados.json").read_text(encoding="utf-8"))
    assert len(resultados) == 3 and rep["con_fallas_ia"] == 1   # entregable aunque una falló
    assert next(r for r in resultados if r["capture_id"] == "CAP-001")["decision"] == "revision_manual"
    caida["activa"] = False
    rep2 = correr(list(adaptador_jsonl(datos)), Corrida(salida, trabajadores=2))
    assert rep2["procesadas_ahora"] == 1 and rep2["con_fallas_ia"] == 0   # solo reintentó la que falló


def test_explicacion_dice_que_foto_tiene_el_problema():
    from app.motor import decision as dec
    lejana = analisis(captura_lejana=True, tamano_etiqueta=0.3, tamano_producto=0.6)
    borrosa = analisis(imagen_borrosa=True, nitidez_producto=0.3, nitidez_etiqueta=0.9)
    assert dec.fotos_con_problema("CAPTURA_LEJANA", lejana) == ["etiqueta"]
    assert dec.detalle_foto("CAPTURA_LEJANA", lejana).startswith("Foto de la etiqueta lejana: la etiqueta ocupa 30%")
    assert dec.detalle_foto("IMAGEN_BORROSA", borrosa) == "Foto del producto borrosa (nitidez 0.3 de 1)."


def test_fachada_no_solo_con_evidencia():
    sin_letrero = ia.normalizar_fachada(analisis(fachada_coincide="no", cadena_en_fachada=None), "Soriana")
    otra = ia.normalizar_fachada(analisis(fachada_coincide="no", cadena_en_fachada="OXXO"), "Soriana")
    misma = ia.normalizar_fachada(analisis(fachada_coincide="no", cadena_en_fachada="SORIANA"), "Soriana")
    assert (sin_letrero.fachada_coincide, otra.fachada_coincide, misma.fachada_coincide) == ("no_se_distingue", "no", "si")


def test_lote_sobrevive_a_errores(tmp_path, monkeypatch):
    def rompe(*a, **k):
        raise KeyError("algo raro")
    monkeypatch.setattr("app.motor.orquestador.validar_detallado", rompe)
    datos = _dataset(tmp_path, 3)
    correr(list(adaptador_jsonl(datos)), Corrida(tmp_path / "c", trabajadores=2))
    resultados = json.loads((tmp_path / "c" / "resultados.json").read_text(encoding="utf-8"))
    assert len(resultados) == 3 and {r["decision"] for r in resultados} == {"revision_manual"}
