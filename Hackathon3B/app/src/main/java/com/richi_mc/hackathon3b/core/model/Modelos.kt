package com.richi_mc.hackathon3b.core.model

/**
 * Contratos de la app (dueño: orquestador). Los workers NO modifican este paquete;
 * si necesitan un cambio, lo piden.
 */

enum class Cadena(val etiqueta: String, val corta: String, val codigo: String) {
    TRES_B("3B", "3B", "3B"),
    SORIANA("Soriana", "Soriana", "SOR"),
    BODEGA_AURRERA("Bodega Aurrerá", "Bodega", "BA"),
    OTRA("Otra", "Otra", "OTRA");
}

/** `contrato` = valor de `TipoFoto` en ../contratos (fachada/producto/etiqueta). */
enum class TipoFoto(val contrato: String) {
    FACHADA("fachada"), PRODUCTO("producto"), ETIQUETA("etiqueta");
}

enum class TipoPrecio { REGULAR, OFERTA, VOLUMEN, DESCONOCIDO }

/**
 * Problemas detectados on-device. [bloqueante] = impide confirmar la captura.
 * [mensaje] = qué pasó y cómo resolverlo (nunca se comunica solo con color).
 */
enum class Problema(val corto: String, val mensaje: String, val bloqueante: Boolean = true) {
    BORROSA("Borrosa", "Foto borrosa: sostén el teléfono firme, acércate y toca para enfocar."),
    OSCURA("Oscura", "Foto muy oscura: busca más luz o enciende el flash."),
    SOBREEXPUESTA("Sobreexpuesta", "Foto con demasiada luz: evita reflejos y apaga el flash."),
    DEMASIADO_LEJOS("Muy lejos", "El código se ve muy pequeño: acércate al producto."),
    SIN_TEXTO_LEGIBLE("Sin texto", "No se pudo leer texto: acércate y enfoca la etiqueta."),
    SIN_CODIGO_BARRAS("Sin código", "No se encontró el código de barras: gira el producto para que se vea completo."),
    CODIGO_INVALIDO("Código inválido", "El código de barras no es válido: vuelve a tomar la foto con el código derecho y nítido."),
    SIN_PRECIO("Sin precio", "No se encontró el precio en la etiqueta: encuadra la etiqueta completa."),
    SIN_PRECIO_UNITARIO("Sin precio unitario", "No se ve el precio unitario (por kg / por litro). El servidor decidirá.", bloqueante = false);
}

enum class EstadoEnvio { PENDIENTE, ENVIADA, ERROR }

data class Sesion(
    val id: String,
    val cadena: Cadena,
    val tienda: String,
    val lat: Double?,
    val lon: Double?,
    val precisionM: Float?,
    val fachadaPath: String,
    /** Momento en que se tomó la foto de fachada (y la ubicación). */
    val inicioMillis: Long,
    /** Análisis de la foto de fachada (nitidez/brillo); null si no se analizó. */
    val fachadaAnalisis: AnalisisImagen? = null,
)

data class AnalisisImagen(
    val tipo: TipoFoto,
    val path: String,
    val nitidez: Double,          // varianza del Laplaciano a 512 px
    val brillo: Double,           // luminancia media 0..255
    val textoOcr: String,
    val confianzaOcr: Float?,     // promedio de elementos (Text Recognition v2)
    val codigoBarras: String?,
    val formatoCodigo: String?,   // "EAN_13", "EAN_8", "UPC_A", "UPC_E"
    /** Área del bounding box del código / área de la imagen (0..1); null si no hay código. */
    val areaCodigo: Double? = null,
    val problemas: List<Problema>,
) {
    val esValida get() = problemas.isEmpty()
    val tieneBloqueantes get() = problemas.any { it.bloqueante }
}

data class PrecioDetectado(
    val valor: Double?,
    val tipo: TipoPrecio,
    val tienePrecioUnitario: Boolean,
    val textoFuente: String,
    val precioUnitario: Double? = null,
)

/** Lo que el capturista dice estar fotografiando (contrato: `producto_esperado`). */
data class ProductoEsperado(
    val descripcion: String? = null,
    val codigoBarras: String? = null,
    val sku: String? = null,
    val marca: String? = null,
    val contenido: String? = null,
    val esContratipo: Boolean = false,
)

/** Respuesta del servidor (subconjunto de `respuesta_captura.schema.json`). */
data class ResultadoServidor(
    val decision: String,             // aprobada | rechazada | revision_manual
    val confidenceScore: Double?,
    val reasonCode: String?,
    val explicacion: String?,
    val avisos: List<String> = emptyList(),
    val precioDetectado: Double? = null,
    val codigoBarrasDetectado: String? = null,
    val capaDecisiva: String? = null,
)

data class Captura(
    val captureId: String,
    val sesionId: String,
    val productoEsperado: ProductoEsperado,
    val producto: AnalisisImagen,
    val etiqueta: AnalisisImagen,
    val precio: PrecioDetectado,
    val timestampMillis: Long,
    val estado: EstadoEnvio = EstadoEnvio.PENDIENTE,
    val respuesta: ResultadoServidor? = null,
    val error: String? = null,
) {
    val enviada get() = estado == EstadoEnvio.ENVIADA
}

data class Ubicacion(val lat: Double, val lon: Double, val precisionM: Float?)

/** Captura en curso entre las pantallas de producto y revisión. */
data class BorradorCaptura(
    val productoEsperado: ProductoEsperado = ProductoEsperado(),
    val producto: AnalisisImagen? = null,
    val etiqueta: AnalisisImagen? = null,
    val precio: PrecioDetectado? = null,
)
