"""Genera contratos/ (JSON Schema, OpenAPI y ejemplos) desde app/contrato.py.

Uso, desde servidor/:  python -m scripts.exportar_contrato
"""
from __future__ import annotations

import json
from pathlib import Path

from app.api import app
from app.contrato import (CapturaEntrada, ChequeoLocal, ErrorRespuesta, MetadatosApp, ProductoEsperado,
                          RespuestaCaptura, ResultadoOficial, Ubicacion)
from app.motor import simulado

DESTINO = Path(__file__).resolve().parents[2] / "contratos"


def escribir(ruta: Path, datos) -> None:
    ruta.parent.mkdir(parents=True, exist_ok=True)
    ruta.write_text(json.dumps(datos, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("  ", ruta.relative_to(DESTINO.parent))


def ejemplo_captura(capture_id: str) -> CapturaEntrada:
    return CapturaEntrada(
        capture_id=capture_id,
        codigo_barras_capturado="7501055363056",
        producto_esperado=ProductoEsperado(
            sku="3B-000451", descripcion="Refresco de cola", marca="Coca-Cola", contenido="600 ml",
            codigo_barras="7501055363056", es_contratipo=False),
        cadena="Bodega Aurrerá",
        tienda="Bodega Aurrerá Iztapalapa Centro",
        tienda_id="BA-0187",
        fecha_hora_captura="2026-11-21T10:42:05-06:00",
        metadatos_app=MetadatosApp(
            dispositivo="Samsung Galaxy A15", sistema_operativo="Android 15", version_app="1.0.0",
            capturista_id="CAPT-017", ubicacion=Ubicacion(latitud=19.3574, longitud=-99.0927, precision_m=12)),
        chequeos_locales=[
            ChequeoLocal(nombre="nitidez", foto="producto", resultado="ok", puntaje=0.91),
            ChequeoLocal(nombre="codigo_barras", foto="producto", resultado="ok", valor="7501055363056"),
        ],
    )


def main() -> None:
    print("Escribiendo en", DESTINO)
    escribir(DESTINO / "captura_entrada.schema.json", CapturaEntrada.model_json_schema())
    escribir(DESTINO / "respuesta_captura.schema.json", RespuestaCaptura.model_json_schema())
    escribir(DESTINO / "resultado_oficial.schema.json", ResultadoOficial.model_json_schema())
    escribir(DESTINO / "error.schema.json", ErrorRespuesta.model_json_schema())
    escribir(DESTINO / "openapi.json", app.openapi())

    ej = DESTINO / "ejemplos"
    escribir(ej / "captura_entrada.json", json.loads(ejemplo_captura("CAP-000123").model_dump_json(exclude_none=True)))
    fotos = {"fachada": b"x", "producto": b"x", "etiqueta": b"x"}
    for sufijo, nombre in (("APR", "aprobada"), ("REC", "rechazada"), ("REV", "revision_manual")):
        r = simulado.responder(ejemplo_captura(f"CAP-000123-{sufijo}"), fotos)
        r.explicacion = r.explicacion.replace("[SIMULADO] ", "")
        escribir(ej / f"respuesta_{nombre}.json", json.loads(r.model_dump_json()))
    escribir(ej / "error_422.json", {"error": {
        "codigo": "CAPTURA_INVALIDA", "mensaje": "El campo captura no cumple el contrato.",
        "detalles": ["producto_esperado: Field required"]}})


if __name__ == "__main__":
    main()
