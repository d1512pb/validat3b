package com.richi_mc.hackathon3b.core.network

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.richi_mc.hackathon3b.BuildConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
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
}

object ApiFactory {
    private val cache = ConcurrentHashMap<String, Validat3BApi>()

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

    /** Cliente cacheado por URL (la URL puede cambiar en runtime). Lanza IllegalArgumentException si es inválida. */
    fun crear(baseUrl: String): Validat3BApi {
        val url = normalizar(baseUrl)
        return cache.getOrPut(url) {
            Retrofit.Builder()
                .baseUrl(url)
                .client(cliente)
                .addConverterFactory(ContratoJson.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
                .build()
                .create(Validat3BApi::class.java)
        }
    }

    private fun normalizar(url: String): String {
        val u = url.trim()
        return if (u.endsWith("/")) u else "$u/"
    }
}

/** Parseo de errores HTTP del servidor a un mensaje legible. */
object ErroresApi {
    /** [cuerpo] = body crudo de la respuesta no exitosa (puede ser null o no ser JSON). */
    fun mensaje(codigoHttp: Int, cuerpo: String?): String {
        val detalle = cuerpo?.takeIf { it.isNotBlank() }?.let {
            runCatching { ContratoJson.decodeFromString(ErrorDto.serializer(), it).error }.getOrNull()
        }
        if (detalle != null) {
            val extra = if (detalle.detalles.isEmpty()) "" else ": " + detalle.detalles.joinToString("; ")
            return "${detalle.mensaje} (${detalle.codigo})$extra"
        }
        return when {
            codigoHttp == 401 -> "El servidor rechazó la clave de acceso (HTTP 401)"
            codigoHttp in 500..599 -> "Error del servidor (HTTP $codigoHttp)"
            else -> "Respuesta inesperada del servidor (HTTP $codigoHttp)"
        }
    }
}
