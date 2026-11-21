"""API HTTP del servidor Validat3B. Documentación interactiva en /docs."""
from __future__ import annotations

import json
import logging
from typing import Literal

from fastapi import FastAPI, File, Form, Header, Query, Request, UploadFile
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import ValidationError
from starlette.exceptions import HTTPException as StarletteHTTPException

from app import VERSION_MOTOR
from app.config import config
from app.contrato import VERSION_CONTRATO, CapturaEntrada, DetalleError, ErrorRespuesta, RespuestaCaptura
from app.motor import ia, orquestador

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")

app = FastAPI(
    title="Validat3B",
    version=VERSION_CONTRATO,
    description="Valida capturas de precio en campo: aprobada, rechazada o revision_manual, con motivo y confianza.",
)

TIPOS_IMAGEN = {"image/jpeg", "image/png", "image/webp"}
ERRORES = {
    400: {"model": ErrorRespuesta, "description": "Captura o fotos inválidas."},
    401: {"model": ErrorRespuesta, "description": "Falta o no coincide X-API-Key."},
    413: {"model": ErrorRespuesta, "description": "Una foto excede el tamaño máximo."},
    415: {"model": ErrorRespuesta, "description": "Tipo de imagen no soportado."},
    422: {"model": ErrorRespuesta, "description": "La petición no cumple el contrato."},
}


class ErrorApi(Exception):
    def __init__(self, status: int, codigo: str, mensaje: str, detalles: list[str] | None = None):
        self.status, self.error = status, DetalleError(codigo=codigo, mensaje=mensaje, detalles=detalles or [])


def _error(status: int, codigo: str, mensaje: str, detalles: list[str] | None = None) -> JSONResponse:
    cuerpo = ErrorRespuesta(error=DetalleError(codigo=codigo, mensaje=mensaje, detalles=detalles or []))
    return JSONResponse(status_code=status, content=cuerpo.model_dump())


@app.exception_handler(ErrorApi)
async def _manejar_error_api(_: Request, e: ErrorApi):
    return JSONResponse(status_code=e.status, content=ErrorRespuesta(error=e.error).model_dump())


@app.exception_handler(RequestValidationError)
async def _manejar_validacion(_: Request, e: RequestValidationError):
    detalles = [f"{'.'.join(map(str, x['loc']))}: {x['msg']}" for x in e.errors()]
    return _error(422, "PETICION_INVALIDA", "La petición no cumple el contrato.", detalles)


@app.exception_handler(StarletteHTTPException)
async def _manejar_http(_: Request, e: StarletteHTTPException):
    return _error(e.status_code, "HTTP_" + str(e.status_code), str(e.detail))


@app.get("/salud", tags=["servicio"])
def salud() -> dict:
    cfg = config()
    return {
        "estado": "ok",
        "version_motor": VERSION_MOTOR,
        "version_contrato": VERSION_CONTRATO,
        "modo": cfg.modo,
        "modelo_rapido": cfg.modelo_rapido,
        "modelo_fuerte": cfg.modelo_fuerte if cfg.escalar_a_fuerte else None,
        "proveedor": ia.proveedor(),
        "ia_configurada": bool(cfg.foundry_api_key or cfg.anthropic_api_key),
    }


def _leer_foto(nombre: str, archivo: UploadFile | None) -> bytes | None:
    if archivo is None or not archivo.filename:
        return None
    if archivo.content_type not in TIPOS_IMAGEN:
        raise ErrorApi(415, "TIPO_NO_SOPORTADO", f"{nombre}: usa JPEG, PNG o WebP (llegó {archivo.content_type}).")
    datos = archivo.file.read()
    if len(datos) > config().max_mb_foto * 1024 * 1024:
        raise ErrorApi(413, "FOTO_MUY_GRANDE", f"{nombre}: máximo {config().max_mb_foto:g} MB.")
    return datos


@app.post("/v1/capturas", response_model=RespuestaCaptura, responses=ERRORES, tags=["capturas"],
          summary="Valida una captura (multipart: campo captura + 3 fotos)")
def validar_captura(
    captura: str = Form(..., description="CapturaEntrada en JSON (ver contratos/captura_entrada.schema.json)."),
    foto_fachada: UploadFile | None = File(None),
    foto_producto: UploadFile | None = File(None),
    foto_etiqueta: UploadFile | None = File(None),
    modo: Literal["real", "simulado"] | None = Query(None, description="'simulado' responde sin gastar IA."),
    x_api_key: str | None = Header(None),
) -> RespuestaCaptura:
    cfg = config()
    if cfg.servidor_token and x_api_key != cfg.servidor_token:
        raise ErrorApi(401, "NO_AUTORIZADO", "Falta o no coincide el header X-API-Key.")
    try:
        entrada = CapturaEntrada.model_validate(json.loads(captura))
    except json.JSONDecodeError as e:
        raise ErrorApi(400, "CAPTURA_NO_ES_JSON", f"El campo captura no es JSON válido: {e.msg}.")
    except ValidationError as e:
        raise ErrorApi(422, "CAPTURA_INVALIDA", "El campo captura no cumple el contrato.",
                       [f"{'.'.join(map(str, x['loc']))}: {x['msg']}" for x in e.errors()])
    fotos = {
        "fachada": _leer_foto("foto_fachada", foto_fachada),
        "producto": _leer_foto("foto_producto", foto_producto),
        "etiqueta": _leer_foto("foto_etiqueta", foto_etiqueta),
    }
    if modo == "real" and cfg.modo == "simulado":
        modo = "simulado"  # un servidor en modo simulado nunca gasta IA
    return orquestador.validar(entrada, fotos, modo)
