package com.richi_mc.hackathon3b.core.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ImageAnalyzer {
    /** Corre todo el pipeline on-device (nitidez, brillo, código, OCR, precio si es etiqueta). */
    suspend fun analizar(path: String, tipo: TipoFoto): AnalisisImagen
}

/** Etiquetado de imágenes on-device (ML Kit Image Labeling, modelo base bundled). */
interface ImageLabelerService {
    /** Etiquetas ordenadas por confianza descendente; lista vacía si falla. */
    suspend fun etiquetar(path: String): List<EtiquetaImagen>
}

/** Arma la descripción y el precio sugeridos para "Producto esperado". */
interface SuggestionService {
    fun sugerir(
        lineasProducto: List<LineaOcr>,
        lineasEtiqueta: List<LineaOcr>,
        etiquetasProducto: List<EtiquetaImagen>,
        precio: PrecioDetectado,
        codigoBarras: String?,
    ): Sugerencia
}

/** Lee el precio de las líneas OCR de una etiqueta (con geometría). Kotlin puro. */
interface PriceParser {
    fun parse(lineas: List<LineaOcr>): PrecioDetectado = parse(lineas, emptyList())

    /** [lineasReOcr]: líneas del recorte mejorado ([OcrEnhancer]), ya en coordenadas de la imagen completa. */
    fun parse(lineas: List<LineaOcr>, lineasReOcr: List<LineaOcr>): PrecioDetectado
}

/** Re-OCR de una zona de la foto guardada, ampliada y con más contraste (PLAN3 §5). */
interface OcrEnhancer {
    /** Líneas leídas en [zona], con cajas en coordenadas de la imagen completa; vacío si falla. */
    suspend fun reOcr(path: String, zona: CajaPx): List<LineaOcr>
}

interface CapturaRepository {
    suspend fun guardar(c: Captura)
    suspend fun pendientes(): List<Captura>
    /** Envía al servidor y actualiza estado/respuesta en la base. Devuelve la respuesta. */
    suspend fun enviar(c: Captura): Result<ResultadoServidor>
    /** Reintenta las PENDIENTE y ERROR (no las FALLIDA). Devuelve cuántas se enviaron. */
    suspend fun enviarPendientes(): Int
    /** `GET /salud`: Result con un texto corto del estado del servidor, o el error legible. */
    suspend fun probarServidor(): Result<String>
    /** Cuenta las capturas no ENVIADA (PENDIENTE, ERROR y FALLIDA). */
    fun observarPendientes(): Flow<Int>
    fun observarCaptura(captureId: String): Flow<Captura?>
    fun observarPorSesion(sesionId: String): Flow<List<Captura>>
    /** Siguiente consecutivo para el capture_id del día (1, 2, 3…). */
    suspend fun siguienteConsecutivo(): Int
    /** Plan B: escribe las capturas de la sesión como JSON (formato del contrato) y devuelve la ruta. */
    suspend fun exportarJson(sesionId: String): String
}

interface SessionStore {
    val sesion: StateFlow<Sesion?>
    suspend fun iniciar(s: Sesion)
    suspend fun cerrar()
}

interface LocationProvider {
    /** null si no hay permiso o no se pudo obtener a tiempo. */
    suspend fun ubicacionActual(): Ubicacion?
}

/** Ajustes de red editables en runtime (pantalla de inicio / ajustes ocultos). */
interface AjustesServidor {
    val url: StateFlow<String>
    val modoSimulado: StateFlow<Boolean>
    fun setUrl(url: String)
    fun setModoSimulado(simulado: Boolean)
}

/** Estado compartido de la captura en curso (producto → etiqueta → revisión). */
interface BorradorStore {
    val borrador: StateFlow<BorradorCaptura>
    fun actualizar(cambio: (BorradorCaptura) -> BorradorCaptura)
    fun limpiar()
}
