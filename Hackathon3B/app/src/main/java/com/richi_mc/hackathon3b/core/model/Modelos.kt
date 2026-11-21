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
    ETIQUETA_LEJOS("Muy lejos", "Acércate a la etiqueta para leer el precio.", bloqueante = false),
    SIN_PRECIO_UNITARIO("Sin precio unitario", "No se ve el precio unitario (por kg / por litro). El servidor decidirá.", bloqueante = false),
    PRODUCTO_NO_VISIBLE("Sin producto", "No se ve un producto en la foto: encuadra el producto completo y de frente."),
    FACHADA_NO_PARECE_TIENDA("¿Fachada?", "La foto no parece la fachada de una tienda: incluye el letrero y la entrada.", bloqueante = false),
    POSIBLE_FOTO_DE_PANTALLA("¿Pantalla?", "Parece una foto de una pantalla: fotografía el objeto real, no un monitor o celular.", bloqueante = false);
}

/**
 * Estado del envío al servidor.
 * - PENDIENTE: aún no se intenta (o se está enviando).
 * - ENVIADA: el servidor respondió 200 con una decisión.
 * - ERROR: falla reintentable (sin red, timeout, 5xx, 413 tras recomprimir): se reintenta desde "Enviar pendientes".
 * - FALLIDA: el servidor la rechazó por un problema que reintentar no arregla (400, 401, 415, 422):
 *   no se reintenta sola y queda para revisión manual (contratos.md §5).
 */
enum class EstadoEnvio { PENDIENTE, ENVIADA, ERROR, FALLIDA }

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
    /** Etiquetas de ML Kit Image Labeling, ordenadas por confianza descendente. */
    val etiquetas: List<EtiquetaImagen> = emptyList(),
    /** Líneas del OCR con su altura (px del bitmap analizado) y posición vertical. */
    val lineasOcr: List<LineaOcr> = emptyList(),
    /** Precio leído (solo etiqueta; incluye el re-OCR del recorte si hizo falta). No se persiste aquí. */
    val precio: PrecioDetectado? = null,
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
    /**
     * Confianza 0..1 del precio (puntuación del ganador, PLAN3 §4). null = no calculada
     * (fakes, capturas guardadas antes del Plan 3): la UI la trata como alta.
     */
    val confianza: Float? = null,
    /** Los 3 mejores candidatos, para depurar y auditar (van al JSON como `precio_candidatos`). */
    val candidatos: List<CandidatoPrecio> = emptyList(),
) {
    /** true si hay precio pero la confianza no llega a [Umbrales.confianzaPrecioAlta]: mostrar "Revisa el precio". */
    val requiereRevision: Boolean
        get() = valor != null && confianza != null && confianza < Umbrales.confianzaPrecioAlta
}

/** Caja en px de la imagen analizada (ya rotada). */
data class CajaPx(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val alto: Int get() = bottom - top
    val ancho: Int get() = right - left
}

/** Palabra del OCR (ML Kit `Text.Element`) con las cajas de sus caracteres (`Text.Symbol`). */
data class ElementoOcr(val texto: String, val caja: CajaPx, val simbolos: List<CajaPx> = emptyList())

/** Forma en que se leyó un precio candidato (PLAN3 §3). */
enum class FormaPrecio { DECIMAL, CENTAVOS_MISMA_LINEA, CENTAVOS_PEGADOS, CENTAVOS_OTRA_LINEA, ENTERO }

data class CandidatoPrecio(
    val valor: Double,
    val forma: FormaPrecio,
    val textoFuente: String,
    val caja: CajaPx,
    /** Altura del entero / altura de la línea más alta de la etiqueta (0..1). */
    val alturaRelativa: Float,
    val tieneSigno: Boolean,
    val esUnitario: Boolean,
    val esAnterior: Boolean,
    val puntuacion: Float = 0f,
)

/** Lo que el capturista dice estar fotografiando (contrato: `producto_esperado`). */
data class ProductoEsperado(
    val descripcion: String? = null,
    val codigoBarras: String? = null,
    val sku: String? = null,
    val marca: String? = null,
    val contenido: String? = null,
    val esContratipo: Boolean = false,
    /** Precio final que confirma el capturista (prellenado con el detectado). */
    val precio: Double? = null,
    /** Lo que sugirió la app antes de que el usuario editara (auditoría). */
    val descripcionDetectada: String? = null,
    val precioDetectado: Double? = null,
    val fuente: FuenteDescripcion? = null,
    /** Confianza 0..1 de la sugerencia de descripción. */
    val confianzaSugerencia: Float? = null,
    val editadoPorUsuario: Boolean = false,
)

/** Etiqueta de ML Kit Image Labeling (texto en inglés del modelo base). */
data class EtiquetaImagen(val texto: String, val confianza: Float, val indice: Int)

/**
 * Línea del OCR (ML Kit `Text.Line`) con su caja y sus elementos, en px de la imagen analizada.
 * [elementos] puede venir vacío (capturas viejas, fakes): el parser tokeniza [texto] entonces.
 */
data class LineaOcr(val texto: String, val caja: CajaPx, val elementos: List<ElementoOcr> = emptyList()) {
    /** Compatibilidad con el Plan 2: solo altura y borde superior. */
    constructor(texto: String, alturaPx: Int, top: Int) : this(texto, CajaPx(0, top, 0, top + alturaPx))

    val alturaPx: Int get() = caja.alto
    val top: Int get() = caja.top
}

/** De dónde salió la descripción sugerida. */
enum class FuenteDescripcion { CATALOGO, ETIQUETA_RACK, FOTO_PRODUCTO, CATEGORIA, MANUAL }

/** Sugerencia de producto esperado. [confianza] 0..1. */
data class Sugerencia(
    val descripcion: String?,
    val fuente: FuenteDescripcion?,
    val precio: Double?,
    val confianza: Float,
)

/** Decisiones del contrato. `decision` desconocida → REVISION_MANUAL (contratos.md §6). */
object Decision {
    const val APROBADA = "aprobada"
    const val RECHAZADA = "rechazada"
    const val REVISION_MANUAL = "revision_manual"
    fun normalizar(d: String?): String = when (d) {
        APROBADA, RECHAZADA, REVISION_MANUAL -> d
        else -> REVISION_MANUAL
    }
}

/** Respuesta del servidor (subconjunto de `respuesta_captura.schema.json`). */
data class ResultadoServidor(
    val decision: String,             // aprobada | rechazada | revision_manual (ya normalizada)
    val confidenceScore: Double?,
    val reasonCode: String?,
    val explicacion: String?,
    val avisos: List<String> = emptyList(),
    val precioDetectado: Double? = null,
    val codigoBarrasDetectado: String? = null,
    val capaDecisiva: String? = null,
    /** `validaciones` del servidor: tienda_fachada / producto / codigo_barras / evidencia_precio → "ok" | "falla". */
    val validaciones: Map<String, String> = emptyMap(),
    /** Fotos que conviene repetir, derivadas de validaciones en falla y del reason_code (contratos.md §4.3). */
    val fotosARepetir: List<TipoFoto> = emptyList(),
    val tipoPrecio: String? = null,
    val precioUnitarioDetectado: Double? = null,
    val promocion: String? = null,
) {
    /** Texto para el capturista: avisos si hay; si no, la explicación (contratos.md §4.2). */
    val mensajesCapturista: List<String>
        get() = avisos.ifEmpty { listOfNotNull(explicacion?.takeIf { it.isNotBlank() }) }
}

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
    /** Código escaneado aparte (paso "Escanear código"); null si no se escaneó. */
    val codigoEscaneado: CodigoEscaneado? = null,
) {
    val enviada get() = estado == EstadoEnvio.ENVIADA

    /** Código final de la captura: escaneado válido > foto del producto > etiqueta. */
    val codigoBarrasFinal: String?
        get() = codigoEscaneado?.takeIf { it.checksumOk }?.valor
            ?: producto.codigoBarras ?: etiqueta.codigoBarras ?: codigoEscaneado?.valor
}

data class Ubicacion(val lat: Double, val lon: Double, val precisionM: Float?)

/** Captura en curso entre las pantallas de producto y revisión. */
data class BorradorCaptura(
    val productoEsperado: ProductoEsperado = ProductoEsperado(),
    val producto: AnalisisImagen? = null,
    val etiqueta: AnalisisImagen? = null,
    val precio: PrecioDetectado? = null,
    val codigoEscaneado: CodigoEscaneado? = null,
) {
    /** Problemas efectivos de la foto del producto (ver [problemasProductoEfectivos]). */
    val problemasProducto: List<Problema>
        get() = producto?.let { problemasProductoEfectivos(it, codigoEscaneado) }.orEmpty()

    /** El paso de escaneo es obligatorio si la foto del producto no leyó un código válido. */
    val requiereEscaneo: Boolean
        get() = producto != null && producto.codigoBarras == null ||
            producto?.problemas?.contains(Problema.CODIGO_INVALIDO) == true
}

/** Código de barras leído con el escáner en vivo (algunos productos lo traen fuera del empaque). */
data class CodigoEscaneado(
    val valor: String,
    val formato: String?,       // "EAN_13", "EAN_8", "UPC_A", "UPC_E"
    val checksumOk: Boolean,
    val timestampMillis: Long,
)

/** Problemas de la foto del producto que dependen del código de barras. */
val PROBLEMAS_DE_CODIGO = setOf(Problema.SIN_CODIGO_BARRAS, Problema.CODIGO_INVALIDO, Problema.DEMASIADO_LEJOS)

/**
 * Con un código escaneado válido, los problemas de código de la foto del producto dejan de
 * contar: el código ya se obtuvo por otra vía. Los de calidad (borrosa, oscura…) siguen.
 */
fun problemasProductoEfectivos(producto: AnalisisImagen, escaneado: CodigoEscaneado?): List<Problema> =
    if (escaneado?.checksumOk == true) producto.problemas - PROBLEMAS_DE_CODIGO else producto.problemas
