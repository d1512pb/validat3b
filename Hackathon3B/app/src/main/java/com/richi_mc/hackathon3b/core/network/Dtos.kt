package com.richi_mc.hackathon3b.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * DTOs del contrato móvil <-> servidor v0.1.0 (../contratos/). Nombres snake_case exactos.
 * No editar a mano si cambia el contrato: regenerar contra `captura_entrada.schema.json`.
 */

const val VERSION_CONTRATO = "0.1.0"

/** Json compartido para red y exportes. */
val ContratoJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

// ---------------------------------------------------------------- Petición

@Serializable
data class CapturaEntradaDto(
    @SerialName("version_contrato") val versionContrato: String = VERSION_CONTRATO,
    @SerialName("capture_id") val captureId: String,
    @SerialName("codigo_barras_capturado") val codigoBarrasCapturado: String? = null,
    @SerialName("producto_esperado") val productoEsperado: ProductoEsperadoDto,
    val cadena: String,
    val tienda: String,
    @SerialName("tienda_id") val tiendaId: String? = null,
    /** ISO 8601 con zona, p. ej. 2026-11-21T10:42:05-06:00. */
    @SerialName("fecha_hora_captura") val fechaHoraCaptura: String,
    @SerialName("metadatos_app") val metadatosApp: MetadatosAppDto? = null,
    @SerialName("chequeos_locales") val chequeosLocales: List<ChequeoLocalDto> = emptyList(),
)

@Serializable
data class ProductoEsperadoDto(
    val sku: String? = null,
    val descripcion: String? = null,
    val marca: String? = null,
    val contenido: String? = null,
    @SerialName("codigo_barras") val codigoBarras: String? = null,
    @SerialName("es_contratipo") val esContratipo: Boolean = false,
)

@Serializable
data class MetadatosAppDto(
    val dispositivo: String? = null,
    @SerialName("sistema_operativo") val sistemaOperativo: String? = null,
    @SerialName("version_app") val versionApp: String? = null,
    @SerialName("capturista_id") val capturistaId: String? = null,
    val ubicacion: UbicacionDto? = null,
)

@Serializable
data class UbicacionDto(
    val latitud: Double,
    val longitud: Double,
    @SerialName("precision_m") val precisionM: Double? = null,
)

@Serializable
data class ChequeoLocalDto(
    val nombre: String,
    /** "fachada" | "producto" | "etiqueta" | null (aplica a toda la captura). */
    val foto: String? = null,
    /** "ok" | "falla" | "duda". */
    val resultado: String,
    /** Confianza 0..1. */
    val puntaje: Double? = null,
    val valor: String? = null,
    val detalle: String? = null,
)

// --------------------------------------------------------------- Respuesta

/** Tolerante: casi todo es opcional para no romper si el servidor agrega o quita campos. */
@Serializable
data class RespuestaCapturaDto(
    @SerialName("capture_id") val captureId: String,
    /** aprobada | rechazada | revision_manual. */
    val decision: String,
    @SerialName("confidence_score") val confidenceScore: Double? = null,
    @SerialName("reason_code") val reasonCode: String? = null,
    val validaciones: ValidacionesDto? = null,
    @SerialName("precio_detectado") val precioDetectado: Double? = null,
    @SerialName("codigo_barras_detectado") val codigoBarrasDetectado: String? = null,
    val explicacion: String? = null,
    val traza: TrazaDto? = null,
)

@Serializable
data class ValidacionesDto(
    @SerialName("tienda_fachada") val tiendaFachada: String? = null,
    val producto: String? = null,
    @SerialName("codigo_barras") val codigoBarras: String? = null,
    @SerialName("evidencia_precio") val evidenciaPrecio: String? = null,
)

@Serializable
data class TrazaDto(
    @SerialName("version_motor") val versionMotor: String? = null,
    @SerialName("version_contrato") val versionContrato: String? = null,
    /** local | rapido | fuerte | respaldo | simulado. */
    @SerialName("capa_decisiva") val capaDecisiva: String? = null,
    @SerialName("modelos_usados") val modelosUsados: List<String> = emptyList(),
    @SerialName("tokens_entrada") val tokensEntrada: Int = 0,
    @SerialName("tokens_salida") val tokensSalida: Int = 0,
    @SerialName("latencia_ms") val latenciaMs: Int = 0,
    /** regular | oferta | volumen | desconocido. */
    @SerialName("tipo_precio") val tipoPrecio: String? = null,
    @SerialName("precio_unitario_detectado") val precioUnitarioDetectado: Double? = null,
    val promocion: String? = null,
    val avisos: List<String> = emptyList(),
)

// ------------------------------------------------------------------ Errores

@Serializable
data class ErrorDto(val error: DetalleErrorDto)

@Serializable
data class DetalleErrorDto(
    val codigo: String,
    val mensaje: String,
    val detalles: List<String> = emptyList(),
)
