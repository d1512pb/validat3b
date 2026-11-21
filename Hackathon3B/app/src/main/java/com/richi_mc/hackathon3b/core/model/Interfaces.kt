package com.richi_mc.hackathon3b.core.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ImageAnalyzer {
    /** Corre todo el pipeline on-device (nitidez, brillo, código, OCR, precio si es etiqueta). */
    suspend fun analizar(path: String, tipo: TipoFoto): AnalisisImagen
}

interface PriceParser {
    fun parse(textoOcr: String): PrecioDetectado
}

interface CapturaRepository {
    suspend fun guardar(c: Captura)
    suspend fun pendientes(): List<Captura>
    /** Envía al servidor y actualiza estado/respuesta en la base. Devuelve la respuesta. */
    suspend fun enviar(c: Captura): Result<ResultadoServidor>
    /** Reintenta todas las pendientes/con error. Devuelve cuántas se enviaron. */
    suspend fun enviarPendientes(): Int
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
