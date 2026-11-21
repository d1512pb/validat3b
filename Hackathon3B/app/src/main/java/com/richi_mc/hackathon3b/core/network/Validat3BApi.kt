package com.richi_mc.hackathon3b.core.network

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.richi_mc.hackathon3b.BuildConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import kotlinx.serialization.json.JsonObject
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/** `POST /v1/capturas` (multipart). Ver ../contratos/README.md. */
interface Validat3BApi {
    @Multipart
    @POST("v1/capturas")
    suspend fun enviar(
        @Part("captura") captura: RequestBody,
        @Part fachada: MultipartBody.Part,
        @Part producto: MultipartBody.Part,
        @Part etiqueta: MultipartBody.Part,
        @Query("modo") modo: String?,
        @Header("X-API-Key") apiKey: String?,
    ): Response<RespuestaCapturaDto>

    /** `GET /salud`: objeto JSON libre. */
    @GET("salud")
    suspend fun salud(): Response<JsonObject>
}

object ApiFactory {
    private const val TIMEOUT_SALUD_S = 8L
    private val cache = ConcurrentHashMap<String, Validat3BApi>()
    private val cacheSalud = ConcurrentHashMap<String, Validat3BApi>()

    private val cliente: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                }
            }
            .build()
    }

    /** Cliente derivado con timeouts cortos para `GET /salud`. */
    private val clienteSalud: OkHttpClient by lazy {
        cliente.newBuilder()
            .connectTimeout(TIMEOUT_SALUD_S, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SALUD_S, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SALUD_S, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SALUD_S, TimeUnit.SECONDS)
            .build()
    }

    /** Cliente cacheado por URL (la URL puede cambiar en runtime). Lanza IllegalArgumentException si es inválida. */
    fun crear(baseUrl: String): Validat3BApi {
        val url = normalizar(baseUrl)
        return cache.getOrPut(url) { retrofit(url, cliente) }
    }

    /** Igual que [crear] pero con timeout corto (~8 s), para probar la conexión. */
    fun crearSalud(baseUrl: String): Validat3BApi {
        val url = normalizar(baseUrl)
        return cacheSalud.getOrPut(url) { retrofit(url, clienteSalud) }
    }

    private fun retrofit(url: String, client: OkHttpClient): Validat3BApi =
        Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(ContratoJson.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
            .build()
            .create(Validat3BApi::class.java)

    private fun normalizar(url: String): String {
        val u = url.trim()
        return if (u.endsWith("/")) u else "$u/"
    }
}

/** Parseo de errores HTTP del servidor a un mensaje legible. */
object ErroresApi {
    private val CODIGOS_CONOCIDOS = setOf(
        "CAPTURA_NO_ES_JSON", "NO_AUTORIZADO", "FOTO_MUY_GRANDE", "TIPO_NO_SOPORTADO",
        "CAPTURA_INVALIDA", "PETICION_INVALIDA",
    )

    /** [cuerpo] = body crudo de la respuesta no exitosa (puede ser null o no ser JSON). */
    fun mensaje(codigoHttp: Int, cuerpo: String?): String {
        val detalle = cuerpo?.takeIf { it.isNotBlank() }?.let {
            runCatching { ContratoJson.decodeFromString(ErrorDto.serializer(), it).error }.getOrNull()
        }
        if (detalle != null) {
            val extra = if (detalle.detalles.isEmpty()) "" else ": " + detalle.detalles.joinToString("; ")
            // Códigos del contrato se muestran con su código; los desconocidos, solo el mensaje.
            val codigo = if (detalle.codigo in CODIGOS_CONOCIDOS) " (${detalle.codigo})" else ""
            return "${detalle.mensaje}$codigo$extra"
        }
        return when {
            codigoHttp == 401 -> "El servidor rechazó la clave de acceso (HTTP 401)"
            codigoHttp in 500..599 -> "Error del servidor (HTTP $codigoHttp)"
            else -> "Respuesta inesperada del servidor (HTTP $codigoHttp)"
        }
    }
}
