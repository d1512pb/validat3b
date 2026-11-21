package com.richi_mc.hackathon3b.core.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.richi_mc.hackathon3b.core.model.AnalisisImagen
import com.richi_mc.hackathon3b.core.model.Cadena
import com.richi_mc.hackathon3b.core.model.Captura
import com.richi_mc.hackathon3b.core.model.EstadoEnvio
import com.richi_mc.hackathon3b.core.model.PrecioDetectado
import com.richi_mc.hackathon3b.core.model.Problema
import com.richi_mc.hackathon3b.core.model.ProductoEsperado
import com.richi_mc.hackathon3b.core.model.ResultadoServidor
import com.richi_mc.hackathon3b.core.model.Sesion
import com.richi_mc.hackathon3b.core.model.TipoFoto
import com.richi_mc.hackathon3b.core.model.TipoPrecio
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ------------------------------------------------------------------ Entities

@Entity(tableName = "sesiones")
data class SesionEntity(
    @PrimaryKey val id: String,
    val cadena: String,
    val tienda: String,
    val lat: Double?,
    val lon: Double?,
    val precisionM: Float?,
    val fachadaPath: String,
    val inicioMillis: Long,
    /** AnalisisImagen de la fachada serializado a JSON. */
    val fachadaAnalisisJson: String?,
)

@Entity(tableName = "capturas")
data class CapturaEntity(
    @PrimaryKey val captureId: String,
    val sesionId: String,
    val espDescripcion: String?,
    val espCodigoBarras: String?,
    val espSku: String?,
    val espMarca: String?,
    val espContenido: String?,
    val espEsContratipo: Boolean,
    val productoPath: String,
    val etiquetaPath: String,
    /** AnalisisImagen (métricas, OCR, código, problemas) serializados a JSON. */
    val productoJson: String,
    val etiquetaJson: String,
    val precioJson: String,
    val timestampMillis: Long,
    /** Nombre de [EstadoEnvio]. */
    val estado: String,
    val respuestaJson: String?,
    val error: String?,
)

// ---------------------------------------------------------------------- DAOs

@Dao
interface SesionDao {
    @Upsert suspend fun upsert(e: SesionEntity)
    @Query("SELECT * FROM sesiones WHERE id = :id") suspend fun obtener(id: String): SesionEntity?
    @Query("SELECT * FROM sesiones WHERE id = :id") fun observar(id: String): Flow<SesionEntity?>
    @Query("SELECT * FROM sesiones ORDER BY inicioMillis DESC") fun observarTodas(): Flow<List<SesionEntity>>
}

@Dao
interface CapturaDao {
    @Upsert suspend fun upsert(e: CapturaEntity)
    @Query("SELECT * FROM capturas WHERE captureId = :id") suspend fun obtener(id: String): CapturaEntity?
    @Query("SELECT * FROM capturas WHERE captureId = :id") fun observar(id: String): Flow<CapturaEntity?>
    @Query("SELECT * FROM capturas WHERE sesionId = :sesionId ORDER BY timestampMillis ASC")
    fun observarPorSesion(sesionId: String): Flow<List<CapturaEntity>>
    @Query("SELECT * FROM capturas WHERE sesionId = :sesionId ORDER BY timestampMillis ASC")
    suspend fun listarPorSesion(sesionId: String): List<CapturaEntity>
    @Query("SELECT * FROM capturas WHERE estado != 'ENVIADA' ORDER BY timestampMillis ASC")
    suspend fun pendientes(): List<CapturaEntity>
    @Query("SELECT COUNT(*) FROM capturas WHERE estado != 'ENVIADA'") fun observarPendientes(): Flow<Int>
    @Query("SELECT COUNT(*) FROM capturas WHERE timestampMillis >= :desde AND timestampMillis < :hasta")
    suspend fun contarEntre(desde: Long, hasta: Long): Int
}

// ------------------------------------------------------------------ Database

@Database(entities = [CapturaEntity::class, SesionEntity::class], version = 1, exportSchema = false)
abstract class Validat3BDatabase : RoomDatabase() {
    abstract fun capturaDao(): CapturaDao
    abstract fun sesionDao(): SesionDao

    companion object {
        @Volatile private var instancia: Validat3BDatabase? = null

        /** Singleton de la base "validat3b.db". */
        fun obtener(context: Context): Validat3BDatabase = instancia ?: synchronized(this) {
            instancia ?: Room.databaseBuilder(
                context.applicationContext, Validat3BDatabase::class.java, "validat3b.db"
            ).fallbackToDestructiveMigration(dropAllTables = true).build().also { instancia = it }
        }
    }
}

// ------------------------------------- JSON interno (no se anota core/model)

internal val JsonDb = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}

@Serializable
internal data class AnalisisJson(
    val tipo: TipoFoto,
    val path: String,
    val nitidez: Double,
    val brillo: Double,
    val textoOcr: String,
    val confianzaOcr: Float? = null,
    val codigoBarras: String? = null,
    val formatoCodigo: String? = null,
    val areaCodigo: Double? = null,
    val problemas: List<Problema> = emptyList(),
)

@Serializable
internal data class PrecioJson(
    val valor: Double? = null,
    val tipo: TipoPrecio = TipoPrecio.DESCONOCIDO,
    val tienePrecioUnitario: Boolean = false,
    val textoFuente: String = "",
    val precioUnitario: Double? = null,
)

@Serializable
internal data class ResultadoJson(
    val decision: String,
    val confidenceScore: Double? = null,
    val reasonCode: String? = null,
    val explicacion: String? = null,
    val avisos: List<String> = emptyList(),
    val precioDetectado: Double? = null,
    val codigoBarrasDetectado: String? = null,
    val capaDecisiva: String? = null,
)

internal fun AnalisisImagen.toJson() = JsonDb.encodeToString(
    AnalisisJson.serializer(),
    AnalisisJson(tipo, path, nitidez, brillo, textoOcr, confianzaOcr, codigoBarras, formatoCodigo, areaCodigo, problemas)
)

internal fun analisisDeJson(s: String): AnalisisImagen = JsonDb.decodeFromString(AnalisisJson.serializer(), s).let {
    AnalisisImagen(
        it.tipo, it.path, it.nitidez, it.brillo, it.textoOcr, it.confianzaOcr,
        it.codigoBarras, it.formatoCodigo, it.areaCodigo, it.problemas
    )
}

internal fun PrecioDetectado.toJson() = JsonDb.encodeToString(
    PrecioJson.serializer(), PrecioJson(valor, tipo, tienePrecioUnitario, textoFuente, precioUnitario)
)

internal fun precioDeJson(s: String): PrecioDetectado = JsonDb.decodeFromString(PrecioJson.serializer(), s).let {
    PrecioDetectado(it.valor, it.tipo, it.tienePrecioUnitario, it.textoFuente, it.precioUnitario)
}

internal fun ResultadoServidor.toJson() = JsonDb.encodeToString(
    ResultadoJson.serializer(),
    ResultadoJson(
        decision, confidenceScore, reasonCode, explicacion, avisos,
        precioDetectado, codigoBarrasDetectado, capaDecisiva
    )
)

internal fun resultadoDeJson(s: String): ResultadoServidor =
    JsonDb.decodeFromString(ResultadoJson.serializer(), s).let {
        ResultadoServidor(
            it.decision, it.confidenceScore, it.reasonCode, it.explicacion, it.avisos,
            it.precioDetectado, it.codigoBarrasDetectado, it.capaDecisiva
        )
    }

// ----------------------------------------------------------------- Mappers

internal fun Sesion.toEntity() = SesionEntity(
    id = id, cadena = cadena.name, tienda = tienda, lat = lat, lon = lon, precisionM = precisionM,
    fachadaPath = fachadaPath, inicioMillis = inicioMillis, fachadaAnalisisJson = fachadaAnalisis?.toJson(),
)

internal fun SesionEntity.toModelo() = Sesion(
    id = id,
    cadena = runCatching { Cadena.valueOf(cadena) }.getOrDefault(Cadena.OTRA),
    tienda = tienda, lat = lat, lon = lon, precisionM = precisionM,
    fachadaPath = fachadaPath, inicioMillis = inicioMillis,
    fachadaAnalisis = fachadaAnalisisJson?.let { runCatching { analisisDeJson(it) }.getOrNull() },
)

internal fun Captura.toEntity() = CapturaEntity(
    captureId = captureId, sesionId = sesionId,
    espDescripcion = productoEsperado.descripcion, espCodigoBarras = productoEsperado.codigoBarras,
    espSku = productoEsperado.sku, espMarca = productoEsperado.marca,
    espContenido = productoEsperado.contenido, espEsContratipo = productoEsperado.esContratipo,
    productoPath = producto.path, etiquetaPath = etiqueta.path,
    productoJson = producto.toJson(), etiquetaJson = etiqueta.toJson(), precioJson = precio.toJson(),
    timestampMillis = timestampMillis, estado = estado.name,
    respuestaJson = respuesta?.toJson(), error = error,
)

internal fun CapturaEntity.toModelo() = Captura(
    captureId = captureId, sesionId = sesionId,
    productoEsperado = ProductoEsperado(
        descripcion = espDescripcion, codigoBarras = espCodigoBarras, sku = espSku,
        marca = espMarca, contenido = espContenido, esContratipo = espEsContratipo,
    ),
    producto = analisisDeJson(productoJson),
    etiqueta = analisisDeJson(etiquetaJson),
    precio = precioDeJson(precioJson),
    timestampMillis = timestampMillis,
    estado = runCatching { EstadoEnvio.valueOf(estado) }.getOrDefault(EstadoEnvio.PENDIENTE),
    respuesta = respuestaJson?.let { runCatching { resultadoDeJson(it) }.getOrNull() },
    error = error,
)
