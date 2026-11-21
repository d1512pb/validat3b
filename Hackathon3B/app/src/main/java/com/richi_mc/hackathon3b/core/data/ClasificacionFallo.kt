package com.richi_mc.hackathon3b.core.data

import com.richi_mc.hackathon3b.core.model.EstadoEnvio
import com.richi_mc.hackathon3b.core.network.ErroresApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** HTTP 413 `FOTO_MUY_GRANDE`: el repositorio recomprime las fotos y reintenta una vez antes de clasificar. */
internal const val HTTP_FOTO_MUY_GRANDE = 413

/** Mensaje de un fallo reintentable por respuesta fuera del contrato (5xx, cuerpo ilegible, 200 vacío). */
internal fun mensajeServidorNoRespondio(codigoHttp: Int) =
    "El servidor no respondió bien (HTTP $codigoHttp), se reintentará"

/**
 * Clasifica una respuesta HTTP no exitosa (contratos.md §5 y §6) a (estado, mensaje):
 * - 5xx y cualquier código fuera de 4xx → ERROR (reintentable; nunca aprobada).
 * - 413 → ERROR: la recompresión y el reintento los hace el repositorio; esto solo cubre el caso en que
 *   sigue fallando.
 * - 408 y 429 → ERROR (temporales).
 * - 400, 401, 415, 422 y cualquier otro 4xx → FALLIDA (reintentar no lo arregla), con el mensaje del `ErrorDto`.
 */
fun clasificarFallo(codigoHttp: Int, cuerpo: String?): Pair<EstadoEnvio, String> {
    if (codigoHttp == HTTP_FOTO_MUY_GRANDE) {
        return EstadoEnvio.ERROR to
            "Una foto sigue siendo demasiado grande para el servidor (HTTP 413), se reintentará"
    }
    if (codigoHttp !in 400..499) return EstadoEnvio.ERROR to mensajeServidorNoRespondio(codigoHttp)
    // 408 (timeout) y 429 (demasiadas peticiones) son temporales: reintentar sí los arregla.
    if (codigoHttp == 408 || codigoHttp == 429) {
        return EstadoEnvio.ERROR to "El servidor está ocupado (HTTP $codigoHttp), se reintentará"
    }

    val detalle = ErroresApi.mensaje(codigoHttp, cuerpo)
    val prefijo = when (codigoHttp) {
        401 -> "Revisa la clave de acceso del servidor."
        400, 422 -> "Error de la app al armar la captura, queda para revisión manual."
        415 -> "Formato de foto no aceptado, queda para revisión manual."
        else -> "El servidor rechazó la captura, queda para revisión manual."
    }
    return EstadoEnvio.FALLIDA to "$prefijo $detalle"
}

/** Texto del resultado de `GET /salud`. */
internal fun textoSalud(modoSimulado: Boolean, cuerpo: JsonObject?): String {
    fun campo(nombre: String) = (cuerpo?.get(nombre) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    return buildString {
        append("Servidor en línea")
        if (modoSimulado) append(", modo simulado")
        campo("estado")?.let { append(", estado: ").append(it) }
        campo("version")?.let { append(", versión ").append(it) }
    }
}
