"""Modo lote: corre el motor sobre un set completo (desarrollo o ciego) en la laptop.

- Reanudable: lo ya procesado queda en un JSONL y no se repite (ni gasta tokens).
- Siempre entrega una respuesta válida por captura: si algo truena, esa captura va a revisión manual.
- El archivo final es una lista JSON en el formato oficial, validada campo por campo.
"""
from __future__ import annotations

import json
import logging
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, Iterable

from app import VERSION_MOTOR
from app.contrato import (CapturaEntrada, Decision, Estado, ResultadoOficial, Validaciones)
from app.evaluacion import Etiqueta, resumen
from app.motor import orquestador
from app.motor.huellas import IndiceHuellas

log = logging.getLogger("validat3b.lote")


@dataclass
class Item:
    captura: CapturaEntrada
    fotos: dict[str, Path | None]
    etiqueta: Etiqueta | None = None
    error_carga: str | None = None


# ------------------------------------------------------------------ adaptadores de datos

def adaptador_jsonl(ruta: Path) -> Iterable[Item]:
    """Formato genérico: una CapturaEntrada por línea, más `fotos` (rutas) y `etiqueta` opcional.

    El adaptador del formato oficial se escribe cuando bajemos los datos (adaptador_oficial).
    """
    base = ruta.parent
    with open(ruta, encoding="utf-8") as f:
        for linea in f:
            if not linea.strip():
                continue
            d = json.loads(linea)
            fotos = {t: (base / p if p else None) for t, p in (d.pop("fotos", {}) or {}).items()}
            et = d.pop("etiqueta", None)
            yield Item(CapturaEntrada.model_validate(d), fotos, Etiqueta(**et) if et else None)


MOTIVO_A_CODIGO = {
    "capturas lejanas": "CAPTURA_LEJANA",
    "capturas borrosas": "IMAGEN_BORROSA",
    "descripcion de producto incompleta": "DESCRIPCION_INCOMPLETA",
}


def _sin_acentos(t: str) -> str:
    import unicodedata
    return unicodedata.normalize("NFKD", t).encode("ascii", "ignore").decode().lower().strip()


def _num(v) -> float | None:
    try:
        return None if v in (None, "") else float(v)
    except (TypeError, ValueError):
        return None


def _texto(v) -> str | None:
    if v in (None, ""):
        return None
    if isinstance(v, float) and v.is_integer():
        v = int(v)
    return str(v)


def fila_a_captura(d: dict) -> CapturaEntrada:
    """Una fila del formato oficial (capturas.json / capturas.csv) al contrato."""
    fecha = str(d.get("capture_date") or "")
    if fecha and "T" not in fecha:
        fecha = fecha.replace(" ", "T") + "-06:00"  # hora del centro de México
    return CapturaEntrada(
        capture_id=str(d["capture_id"]),
        codigo_barras_capturado=_texto(d.get("capture_barcode")),
        precio_capturado=_num(d.get("capture_price")),
        marca_capturada=_texto(d.get("capture_brand")),
        producto_esperado={
            "sku": _texto(d.get("id_product")), "descripcion": _texto(d.get("product_name")),
            "marca": _texto(d.get("capture_brand")), "codigo_barras": _texto(d.get("product_barcode")),
            "precio_referencia": _num(d.get("product_price")), "precio_minimo": _num(d.get("min_product_price")),
            "precio_maximo": _num(d.get("max_product_price")), "unidad": _texto(d.get("unit_product")),
            "cantidad": _num(d.get("unit_quantity_product")), "categoria": _texto(d.get("product_group")),
            "linea": _texto(d.get("product_line")),
        },
        cadena=str(d.get("capture_store_name") or "?"),
        tienda=str(d.get("capture_store_name") or "?"),
        fecha_hora_captura=fecha or "1970-01-01T00:00:00-06:00",
    )


def adaptador_oficial(ruta: Path) -> Iterable[Item]:
    """capturas.json o capturas.csv del reto, con las imágenes en imagenes/ junto al archivo."""
    import csv
    base = ruta.parent
    if ruta.suffix.lower() == ".json":
        filas = json.loads(ruta.read_text(encoding="utf-8"))
    else:
        with open(ruta, encoding="utf-8-sig") as f:
            filas = list(csv.DictReader(f))
    for d in filas:
        fotos = {t: (base / d[f"{k}_file"]) if d.get(f"{k}_file") else None
                 for t, k in (("fachada", "facade"), ("producto", "product"), ("etiqueta", "shelf"))}
        etiqueta = None
        if d.get("decision_esperada"):
            motivo = _sin_acentos(d.get("motivo_rechazo") or "")
            etiqueta = Etiqueta(decision=d["decision_esperada"], reason_code=MOTIVO_A_CODIGO.get(motivo, motivo or None))
        try:
            yield Item(fila_a_captura(d), fotos, etiqueta)
        except Exception as e:  # una fila mal formada no detiene el set: va a revisión manual
            log.warning("Fila inválida %s: %s", d.get("capture_id"), e)
            yield Item(CapturaEntrada(capture_id=str(d.get("capture_id", "SIN_ID")), producto_esperado={},
                                      cadena="?", tienda="?", fecha_hora_captura="1970-01-01T00:00:00-06:00"),
                       fotos, etiqueta, error_carga=repr(e))


ADAPTADORES: dict[str, Callable[[Path], Iterable[Item]]] = {"jsonl": adaptador_jsonl, "oficial": adaptador_oficial}


# ------------------------------------------------------------------ corrida

@dataclass
class Corrida:
    salida: Path                      # carpeta de la corrida
    trabajadores: int = 4
    modo: str | None = None
    procesados: dict[str, dict] = field(default_factory=dict)

    @property
    def jsonl(self) -> Path:
        return self.salida / "detalle.jsonl"

    def cargar_previos(self) -> None:
        if self.jsonl.exists():
            for linea in self.jsonl.read_text(encoding="utf-8").splitlines():
                if linea.strip():
                    d = json.loads(linea)
                    self.procesados[d["oficial"]["capture_id"]] = d


def _leer(p: Path | None) -> bytes | None:
    return p.read_bytes() if p and p.exists() else None


def _respaldo(capture_id: str, motivo: str) -> dict:
    r = ResultadoOficial(
        capture_id=capture_id, decision=Decision.revision_manual, confidence_score=0.5, reason_code=None,
        validaciones=Validaciones(tienda_fachada=Estado.ok, producto=Estado.ok, codigo_barras=Estado.ok,
                                  evidencia_precio=Estado.ok),
        explicacion=f"Revisión manual: no se pudo procesar ({motivo}).")
    return json.loads(r.model_dump_json())


def correr(items: Iterable[Item], corrida: Corrida, al_avanzar: Callable[[int, int], None] | None = None) -> dict:
    corrida.salida.mkdir(parents=True, exist_ok=True)
    corrida.cargar_previos()
    items = list(items)
    pendientes = [it for it in items if it.captura.capture_id not in corrida.procesados]
    indice = IndiceHuellas()
    candado = threading.Lock()
    inicio = time.perf_counter()

    def procesar(it: Item) -> dict:
        try:
            fotos = {t: _leer(it.fotos.get(t)) for t in ("fachada", "producto", "etiqueta")}
            resp, detalle = orquestador.validar_detallado(it.captura, fotos, corrida.modo, indice)
            oficial = json.loads(resp.model_dump_json(exclude={"traza"}))
            traza = json.loads(resp.traza.model_dump_json())
        except Exception as e:  # una captura rota no tumba la corrida
            log.exception("Falla en %s", it.captura.capture_id)
            oficial, traza, detalle = _respaldo(it.captura.capture_id, type(e).__name__), {}, {"error": repr(e)}
        fila = {"oficial": oficial, "traza": traza, "detalle": detalle,
                "etiqueta": it.etiqueta.__dict__ if it.etiqueta else None}
        with candado:
            with open(corrida.jsonl, "a", encoding="utf-8") as f:
                f.write(json.dumps(fila, ensure_ascii=False, default=str) + "\n")
            corrida.procesados[oficial["capture_id"]] = fila
        return fila

    hechos = 0
    with ThreadPoolExecutor(max_workers=corrida.trabajadores) as ex:
        for _ in as_completed([ex.submit(procesar, it) for it in pendientes]):
            hechos += 1
            if al_avanzar:
                al_avanzar(hechos, len(pendientes))

    # Archivo final: una respuesta por captura de entrada, en el orden de entrada.
    oficiales = []
    for it in items:
        fila = corrida.procesados.get(it.captura.capture_id)
        oficial = fila["oficial"] if fila else _respaldo(it.captura.capture_id, "sin_resultado")
        oficiales.append(json.loads(ResultadoOficial.model_validate(oficial).model_dump_json()))
    (corrida.salida / "resultados.json").write_text(json.dumps(oficiales, ensure_ascii=False, indent=1), encoding="utf-8")

    filas = [corrida.procesados[o["capture_id"]] for o in oficiales if o["capture_id"] in corrida.procesados]
    tokens_in = sum(f["traza"].get("tokens_entrada", 0) for f in filas)
    tokens_out = sum(f["traza"].get("tokens_salida", 0) for f in filas)
    capas: dict[str, int] = {}
    for f in filas:
        capa = f["traza"].get("capa_decisiva", "error")
        capas[capa] = capas.get(capa, 0) + 1
    rep = {
        "version_motor": VERSION_MOTOR,
        "capturas": len(items),
        "procesadas_ahora": len(pendientes),
        "segundos": round(time.perf_counter() - inicio, 1),
        "tokens_entrada": tokens_in,
        "tokens_salida": tokens_out,
        "tokens_por_captura": round((tokens_in + tokens_out) / max(len(filas), 1), 1),
        "capa_decisiva": capas,
    }
    etiquetas = {it.captura.capture_id: it.etiqueta for it in items if it.etiqueta}
    if etiquetas:
        rep["evaluacion"] = resumen(oficiales, etiquetas)
    (corrida.salida / "reporte.json").write_text(json.dumps(rep, ensure_ascii=False, indent=1), encoding="utf-8")
    return rep
