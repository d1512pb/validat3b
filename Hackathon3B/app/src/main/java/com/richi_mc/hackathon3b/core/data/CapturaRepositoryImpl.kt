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
import com.richi_mc.hackathon3b.core.network.ErroresApi
import com.richi_mc.hackathon3b.core.network.Validat3BApi
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Falla de envío con mensaje listo para mostrar en español. */
class EnvioException(message: String, val sinConexion: Boolean = false, cause: Throwable? = null) :
    Exception(message, cause)

class CapturaRepositoryImpl(
    private val context: Context,
    private val db: Validat3BDatabase,
    private val sessionStore: SessionStore,
    private val ajustes: AjustesServidor,
    private val apiProvider: (String) -> Validat3BApi = ApiFactory::crear,
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
        val sesion = sesionDe(c.sesionId)
            ?: return fallar(c, "No se encontró la sesión de la captura; no se puede enviar.")

        val fachada = File(sesion.fachadaPath)
        val producto = File(c.producto.path)
        val etiqueta = File(c.etiqueta.path)
        val faltante = listOf("fachada" to fachada, "producto" to producto, "etiqueta" to etiqueta)
            .firstOrNull { !it.second.isFile }
        if (faltante != null) {
            return fallar(c, "Falta la foto de ${faltante.first} en el teléfono; vuelve a tomarla.")
        }

        return try {
            val dto = withContext(Dispatchers.IO) { CapturaMapper.toDto(c, sesion) }
            val json = ContratoJson.encodeToString(CapturaEntradaDto.serializer(), dto)
            val jpeg = "image/jpeg".toMediaType()
            val respuesta = apiProvider(ajustes.url.value).enviar(
                captura = json.toRequestBody("application/json; charset=utf-8".toMediaType()),
                fachada = parteFoto("foto_fachada", fachada, jpeg),
                producto = parteFoto("foto_producto", producto, jpeg),
                etiqueta = parteFoto("foto_etiqueta", etiqueta, jpeg),
                modo = if (ajustes.modoSimulado.value) "simulado" else null,
                apiKey = BuildConfig.SERVIDOR_API_KEY.ifBlank { null },
            )
            val cuerpo = respuesta.body()
            if (respuesta.isSuccessful && cuerpo != null) {
                val resultado = CapturaMapper.toResultado(cuerpo)
                capturas.upsert(
                    c.copy(estado = EstadoEnvio.ENVIADA, respuesta = resultado, error = null).toEntity()
                )
                Result.success(resultado)
            } else {
                val texto = runCatching { respuesta.errorBody()?.string() }.getOrNull()
                fallar(c, ErroresApi.mensaje(respuesta.code(), texto))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            fallar(c, "Sin conexión, se enviará después", sinConexion = true, causa = e)
        } catch (e: IllegalArgumentException) {
            fallar(c, "La dirección del servidor no es válida; revísala en los ajustes.", causa = e)
        } catch (e: Exception) {
            fallar(c, "No se pudo enviar la captura: ${e.message ?: e.javaClass.simpleName}", causa = e)
        }
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

    private fun parteFoto(nombre: String, archivo: File, tipo: okhttp3.MediaType) =
        MultipartBody.Part.createFormData(nombre, archivo.name, archivo.asRequestBody(tipo))

    private suspend fun fallar(
        c: Captura,
        mensaje: String,
        sinConexion: Boolean = false,
        causa: Throwable? = null,
    ): Result<ResultadoServidor> {
        capturas.upsert(c.copy(estado = EstadoEnvio.ERROR, error = mensaje).toEntity())
        return Result.failure(EnvioException(mensaje, sinConexion, causa))
    }
}
