"""Contrato de datos entre la app móvil y el servidor.

Es la fuente de verdad: los JSON Schema de `contratos/` se generan desde aquí con
`python -m scripts.exportar_contrato`. Cambios que rompen suben la versión mayor.
"""
from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

VERSION_CONTRATO = "0.3.0"

# Lista provisional, armada con los casos reales de la página del reto.
# Se reemplaza por la lista oficial de reason_code cuando llegue con los datos.
REASON_CODES_PROVISIONALES = {
    # A. Identidad del producto (~61% de los rechazos)
    "CONTRATIPO_INVALIDO": "El producto es un contratipo que no está permitido.",
    "PRODUCTO_NO_COINCIDE": "El producto de la foto no coincide con el esperado.",
    "DESCRIPCION_INCOMPLETA": "No se alcanza a leer la descripción completa del producto.",
    "PRODUCTO_NO_VISIBLE": "El producto no se ve en la foto.",
    # B. Calidad de la evidencia (~29%)
    "FACHADA_INCORRECTA": "La fachada no corresponde a la cadena o tienda seleccionada.",
    "CAPTURA_LEJANA": "La foto se tomó demasiado lejos.",
    "IMAGEN_BORROSA": "La imagen está borrosa.",
    "FOTO_DE_PANTALLA": "La foto se tomó a la pantalla de otro dispositivo.",
    # C. Evidencia de precio (~10%)
    "SIN_ETIQUETA_PRECIO": "No hay etiqueta de precio.",
    "SIN_PRECIO_UNITARIO": "La etiqueta no muestra el precio unitario.",
}


class Decision(str, Enum):
    aprobada = "aprobada"
    rechazada = "rechazada"
    revision_manual = "revision_manual"


class Estado(str, Enum):
    ok = "ok"
    falla = "falla"


class TipoFoto(str, Enum):
    fachada = "fachada"
    producto = "producto"
    etiqueta = "etiqueta"


# ---------------------------------------------------------------- Entrada (móvil → servidor)

class ProductoEsperado(BaseModel):
    """Producto o contratipo que el capturista dice estar fotografiando."""
    model_config = ConfigDict(extra="allow")

    sku: str | None = Field(None, description="SKU del catálogo de referencia.")
    descripcion: str | None = Field(None, description="Descripción del catálogo.")
    marca: str | None = None
    contenido: str | None = Field(None, description="Contenido o presentación, p. ej. '600 ml'.")
    codigo_barras: str | None = Field(None, description="Código de barras del catálogo.")
    es_contratipo: bool = Field(False, description="True si se captura un contratipo y no el producto 3B.")
    precio_referencia: float | None = Field(None, description="Precio de referencia del catálogo (product_price).")
    precio_minimo: float | None = Field(None, description="Límite inferior del rango de precio de referencia.")
    precio_maximo: float | None = Field(None, description="Límite superior del rango de precio de referencia.")
    unidad: str | None = Field(None, description="Unidad del contenido, p. ej. 'gramo' (unit_product).")
    cantidad: float | None = Field(None, description="Contenido en esa unidad, p. ej. 1000 (unit_quantity_product).")
    categoria: str | None = Field(None, description="product_group del catálogo.")
    linea: str | None = Field(None, description="product_line del catálogo.")


class Ubicacion(BaseModel):
    latitud: float = Field(..., ge=-90, le=90)
    longitud: float = Field(..., ge=-180, le=180)
    precision_m: float | None = Field(None, ge=0, description="Precisión del GPS en metros.")


class MetadatosApp(BaseModel):
    """Metadatos del teléfono y de la app. Se aceptan campos extra."""
    model_config = ConfigDict(extra="allow")

    dispositivo: str | None = Field(None, description="Modelo del teléfono.")
    sistema_operativo: str | None = None
    version_app: str | None = None
    capturista_id: str | None = None
    ubicacion: Ubicacion | None = None


class ChequeoLocal(BaseModel):
    """Resultado de una validación que la app ya corrió en el teléfono."""
    nombre: str = Field(..., description="p. ej. nitidez, pantalla, distancia, codigo_barras, etiqueta_presente.")
    foto: TipoFoto | None = Field(None, description="Foto revisada; null si aplica a toda la captura.")
    resultado: Literal["ok", "falla", "duda"]
    puntaje: float | None = Field(None, ge=0, le=1, description="Confianza del chequeo, de 0 a 1.")
    valor: str | None = Field(None, description="Lo que se leyó, p. ej. el código de barras o el precio.")
    detalle: str | None = None


class CapturaEntrada(BaseModel):
    """Datos de una captura. Viaja como el campo `captura` (texto JSON) del multipart."""
    model_config = ConfigDict(extra="allow")

    version_contrato: str = Field(VERSION_CONTRATO, description="Versión del contrato con que se armó.")
    capture_id: str = Field(..., min_length=1, max_length=64, examples=["CAP-000123"])
    codigo_barras_capturado: str | None = Field(None, description="Código que capturó la app.")
    precio_capturado: float | None = Field(None, description="Precio que tecleó el capturista en campo (capture_price).")
    marca_capturada: str | None = Field(None, description="Marca que capturó el capturista (capture_brand).")
    producto_esperado: ProductoEsperado
    cadena: str = Field(..., description="Cadena seleccionada, p. ej. 'Bodega Aurrerá'.")
    tienda: str = Field(..., description="Tienda seleccionada (nombre o id).")
    tienda_id: str | None = None
    fecha_hora_captura: datetime = Field(..., description="ISO 8601 con zona horaria.")
    metadatos_app: MetadatosApp = Field(default_factory=MetadatosApp)
    chequeos_locales: list[ChequeoLocal] = Field(
        default_factory=list,
        description="Validaciones que ya corrió el teléfono. El servidor las usa como pista; no confía ciegamente.",
    )


# ---------------------------------------------------------------- Salida (servidor → móvil)

class Validaciones(BaseModel):
    tienda_fachada: Estado
    producto: Estado
    codigo_barras: Estado
    evidencia_precio: Estado


class ResultadoOficial(BaseModel):
    """Formato oficial del reto, campo por campo. Es lo que va al archivo del set ciego."""
    capture_id: str
    decision: Decision
    confidence_score: float = Field(..., ge=0, le=1)
    reason_code: str | None = Field(None, description="null cuando la captura se aprueba.")
    validaciones: Validaciones
    precio_detectado: float | None = None
    codigo_barras_detectado: str | None = None
    explicacion: str


class Traza(BaseModel):
    """Datos extra para auditar y para la app. No van al archivo oficial."""
    version_motor: str
    version_contrato: str = VERSION_CONTRATO
    capa_decisiva: Literal["local", "rapido", "fuerte", "respaldo", "simulado"] = Field(
        ..., description="Qué capa tomó la decisión: chequeos locales, modelo rápido (Haiku), fuerte (Sonnet), respaldo por error o simulado."
    )
    modelos_usados: list[str] = Field(default_factory=list)
    tokens_entrada: int = 0
    tokens_salida: int = 0
    latencia_ms: int = 0
    tipo_precio: Literal["regular", "oferta", "volumen", "desconocido"] | None = None
    precio_unitario_detectado: float | None = None
    promocion: str | None = Field(None, description="Texto de la promoción si la hay, p. ej. '2x$50'.")
    precio_regular: float | None = Field(None, description="Precio normal que muestra la etiqueta.")
    precio_promocion: float | None = Field(None, description="Precio con descuento por pieza, si lo hay.")
    compuertas_fallidas: list[str] = Field(
        default_factory=list,
        description="Por qué no se aprobó: cada compuerta que no pasó (p. ej. 'precio_coincide: ...'). Vacía si todo pasó.")
    doble_verificacion: bool = Field(False, description="True si la aprobación se confirmó con el modelo fuerte.")
    avisos: list[str] = Field(default_factory=list, description="Mensajes cortos para el capturista.")


class RespuestaCaptura(ResultadoOficial):
    traza: Traza


class DetalleError(BaseModel):
    codigo: str = Field(..., examples=["FOTO_INVALIDA"])
    mensaje: str
    detalles: list[str] = Field(default_factory=list)


class ErrorRespuesta(BaseModel):
    error: DetalleError
