package com.richi_mc.hackathon3b.core.data

import android.content.Context
import com.richi_mc.hackathon3b.BuildConfig
import com.richi_mc.hackathon3b.core.model.AjustesServidor
import com.richi_mc.hackathon3b.core.model.Captura
import com.richi_mc.hackathon3b.core.model.CapturaRepository
import com.richi_mc.hackathon3b.core.model.EstadoEnvio
import com.richi_mc.hackathon3b.core.model.ResultadoServidor
import com.richi_mc.hackathon3b.core.model.Sesion
import com.richi_mc.hackathon3b.core.model.SessionStore
import com.richi_mc.hackathon3b.core.network.ApiFactory
import com.richi_mc.hackathon3b.core.network.CapturaEntradaDto
import com.richi_mc.hackathon3b.core.network.CapturaMapper
import com.richi_mc.hackathon3b.core.network.ContratoJson
import com.richi_mc.hackathon3b.core.network.RespuestaCapturaDto
import com.richi_mc.hackathon3b.core.network.Validat3BApi
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

/** Falla de envío con mensaje listo para mostrar en español. */
class EnvioException(message: String, val sinConexion: Boolean = false, cause: Throwable? = null) :
    Exception(message, cause)

class CapturaRepositoryImpl(
    private val context: Context,
    private val db: Validat3BDatabase,
    private val sessionStore: SessionStore,
    private val ajustes: AjustesServidor,
    private val apiProvider: (String) -> Validat3BApi = ApiFactory::crear,
    private val saludApiProvider: (String) -> Validat3BApi = ApiFactory::crearSalud,
) : CapturaRepository {

    private val capturas get() = db.capturaDao()
    private val sesiones get() = db.sesionDao()

    override suspend fun guardar(c: Captura) {
        capturas.upsert(c.toEntity())
        // Guarda también la sesión actual para poder reenviar aunque luego cambie.
        sessionStore.sesion.value?.takeIf { it.id == c.sesionId }?.let { sesiones.upsert(it.toEntity()) }
    }

    override suspend fun pendientes(): List<Captura> =
        capturas.pendientes().map { it.toModelo() }

    override suspend fun enviar(c: Captura): Result<ResultadoServidor> {
        // Sin sesión o sin foto en disco: reintentar no lo arregla (contratos.md §5) -> FALLIDA.
        val sesion = sesionDe(c.sesionId)
            ?: return fallar(
                c, EstadoEnvio.FALLIDA,
                "No se encontró la sesión de la captura; no se puede enviar, queda para revisión manual.",
            )

        val fachada = File(sesion.fachadaPath)
        val producto = File(c.producto.path)
        val etiqueta = File(c.etiqueta.path)
        val faltante = listOf("fachada" to fachada, "producto" to producto, "etiqueta" to etiqueta)
            .firstOrNull { !it.second.isFile }
        if (faltante != null) {
            return fallar(
                c, EstadoEnvio.FALLIDA,
                "Falta la foto de ${faltante.first} en el teléfono; vuelve a tomarla o déjala para revisión manual.",
            )
        }

        return try {
            val dto = withContext(Dispatchers.IO) { CapturaMapper.toDto(c, sesion) }
            // El capture_id (dentro del JSON) es el mismo en cada reintento.
            val json = ContratoJson.encodeToString(CapturaEntradaDto.serializer(), dto)
            val api = apiProvider(ajustes.url.value)
            val modo = if (ajustes.modoSimulado.value) "simulado" else "real"

            var respuesta = llamar(api, json, modo, fachada, producto, etiqueta)
            if (respuesta.code() == HTTP_FOTO_MUY_GRANDE) {
                // 413: recomprime las 3 fotos (1280 px, q75) y reintenta una sola vez.
                val temporales = mutableListOf<File>()
                try {
                    val reducidas = withContext(Dispatchers.IO) {
                        val dir = File(context.cacheDir, "reenvio")
                        listOf(fachada, producto, etiqueta).map {
                            RecompresionFotos.recomprimir(it, dir).also(temporales::add)
                        }
                    }
                    respuesta = llamar(api, json, modo, reducidas[0], reducidas[1], reducidas[2])
                } finally {
                    temporales.forEach { it.delete() }
                }
            }
            procesar(c, respuesta)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            fallar(c, EstadoEnvio.ERROR, "Sin conexión, se enviará después", sinConexion = true, causa = e)
        } catch (e: SerializationException) {
            // 200 con cuerpo que no es JSON del contrato: nunca se toma como aprobada.
            fallar(c, EstadoEnvio.ERROR, mensajeServidorNoRespondio(200), causa = e)
        } catch (e: IllegalArgumentException) {
            fallar(c, EstadoEnvio.ERROR, "La dirección del servidor no es válida; revísala en los ajustes.", causa = e)
        } catch (e: Exception) {
            fallar(
                c, EstadoEnvio.ERROR,
                "No se pudo enviar la captura: ${e.message ?: e.javaClass.simpleName}", causa = e,
            )
        }
    }

    private suspend fun llamar(
        api: Validat3BApi,
        json: String,
        modo: String,
        fachada: File,
        producto: File,
        etiqueta: File,
    ): Response<RespuestaCapturaDto> {
        val jpeg = "image/jpeg".toMediaType()
        return api.enviar(
            captura = json.toRequestBody("application/json; charset=utf-8".toMediaType()),
            fachada = parteFoto("foto_fachada", "fachada.jpg", fachada, jpeg),
            producto = parteFoto("foto_producto", "producto.jpg", producto, jpeg),
            etiqueta = parteFoto("foto_etiqueta", "etiqueta.jpg", etiqueta, jpeg),
            modo = modo,
            apiKey = BuildConfig.SERVIDOR_API_KEY.ifBlank { null },
        )
    }

    private suspend fun procesar(c: Captura, respuesta: Response<RespuestaCapturaDto>): Result<ResultadoServidor> {
        if (respuesta.isSuccessful) {
            val cuerpo = respuesta.body()
                ?: return fallar(c, EstadoEnvio.ERROR, mensajeServidorNoRespondio(respuesta.code()))
            val resultado = CapturaMapper.toResultado(cuerpo)
            capturas.upsert(c.copy(estado = EstadoEnvio.ENVIADA, respuesta = resultado, error = null).toEntity())
            return Result.success(resultado)
        }
        val texto = runCatching { respuesta.errorBody()?.string() }.getOrNull()
        val (estado, mensaje) = clasificarFallo(respuesta.code(), texto)
        return fallar(c, estado, mensaje)
    }

    override suspend fun enviarPendientes(): Int {
        var enviadas = 0
        for (p in capturas.pendientes()) {
            // Puede haberse enviado mientras tanto (envío inmediato al confirmar).
            if (capturas.obtener(p.captureId)?.estado == EstadoEnvio.ENVIADA.name) continue
            val r = enviar(p.toModelo())
            if (r.isSuccess) enviadas++
            else if ((r.exceptionOrNull() as? EnvioException)?.sinConexion == true) break
        }
        return enviadas
    }

    override suspend fun probarServidor(): Result<String> = try {
        val respuesta = saludApiProvider(ajustes.url.value).salud()
        if (respuesta.isSuccessful) {
            Result.success(textoSalud(ajustes.modoSimulado.value, respuesta.body()))
        } else {
            Result.failure(EnvioException("El servidor respondió con error (HTTP ${respuesta.code()})"))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: InterruptedIOException) {
        Result.failure(EnvioException("El servidor tardó demasiado en responder", sinConexion = true, cause = e))
    } catch (e: IOException) {
        Result.failure(EnvioException("No se pudo conectar con el servidor; revisa la red y la dirección", sinConexion = true, cause = e))
    } catch (e: SerializationException) {
        Result.failure(EnvioException("El servidor respondió, pero no con el formato esperado", cause = e))
    } catch (e: IllegalArgumentException) {
        Result.failure(EnvioException("La dirección del servidor no es válida", cause = e))
    } catch (e: Exception) {
        Result.failure(EnvioException("No se pudo probar la conexión: ${e.message ?: e.javaClass.simpleName}", cause = e))
    }

    override fun observarPendientes(): Flow<Int> = capturas.observarPendientes()

    override fun observarCaptura(captureId: String): Flow<Captura?> =
        capturas.observar(captureId).map { it?.toModelo() }

    override fun observarPorSesion(sesionId: String): Flow<List<Captura>> =
        capturas.observarPorSesion(sesionId).map { l -> l.map { it.toModelo() } }

    override suspend fun siguienteConsecutivo(): Int {
        val zona = ZoneId.systemDefault()
        val hoy = Instant.now().atZone(zona).toLocalDate()
        val desde = hoy.atStartOfDay(zona).toInstant().toEpochMilli()
        val hasta = hoy.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli()
        return capturas.contarEntre(desde, hasta) + 1
    }

    override suspend fun exportarJson(sesionId: String): String = withContext(Dispatchers.IO) {
        val sesion = sesionDe(sesionId) ?: error("No se encontró la sesión $sesionId para exportar.")
        val dtos = capturas.listarPorSesion(sesionId).map { CapturaMapper.toDto(it.toModelo(), sesion) }
        val dir = File(context.cacheDir, "exportes").apply { mkdirs() }
        val seguro = sesionId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val archivo = File(dir, "capturas-$seguro.json")
        val json = Json(ContratoJson) { prettyPrint = true }
        archivo.writeText(json.encodeToString(ListSerializer(CapturaEntradaDto.serializer()), dtos))
        archivo.absolutePath
    }

    // ------------------------------------------------------------- helpers

    private suspend fun sesionDe(id: String): Sesion? {
        sesiones.obtener(id)?.let { return it.toModelo() }
        return sessionStore.sesion.value?.takeIf { it.id == id }?.also { sesiones.upsert(it.toEntity()) }
    }

    private fun parteFoto(nombre: String, nombreArchivo: String, archivo: File, tipo: okhttp3.MediaType) =
        MultipartBody.Part.createFormData(nombre, nombreArchivo, archivo.asRequestBody(tipo))

    private suspend fun fallar(
        c: Captura,
        estado: EstadoEnvio,
        mensaje: String,
        sinConexion: Boolean = false,
        causa: Throwable? = null,
    ): Result<ResultadoServidor> {
        capturas.upsert(c.copy(estado = estado, error = mensaje).toEntity())
        return Result.failure(EnvioException(mensaje, sinConexion, causa))
    }
}
