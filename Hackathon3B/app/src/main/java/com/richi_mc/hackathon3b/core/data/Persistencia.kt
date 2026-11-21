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
import com.richi_mc.hackathon3b.core.model.CajaPx
import com.richi_mc.hackathon3b.core.model.Cadena
import com.richi_mc.hackathon3b.core.model.CandidatoPrecio
import com.richi_mc.hackathon3b.core.model.Captura
import com.richi_mc.hackathon3b.core.model.CodigoEscaneado
import com.richi_mc.hackathon3b.core.model.EstadoEnvio
import com.richi_mc.hackathon3b.core.model.EtiquetaImagen
import com.richi_mc.hackathon3b.core.model.FormaPrecio
import com.richi_mc.hackathon3b.core.model.FuenteDescripcion
import com.richi_mc.hackathon3b.core.model.LineaOcr
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
    // Producto esperado editable (v3): precio final, valores detectados y auditoría de la sugerencia.
    val espPrecio: Double? = null,
    val espDescripcionDetectada: String? = null,
    val espPrecioDetectado: Double? = null,
    /** Nombre de [FuenteDescripcion]; null si no hubo sugerencia. */
    val espFuente: String? = null,
    val espConfianzaSugerencia: Float? = null,
    val espEditadoPorUsuario: Boolean = false,
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
    /** CodigoEscaneado (paso "Escanear código") serializado a JSON; null si no se escaneó. */
    val codigoEscaneadoJson: String? = null,
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
    /** Por reenviar: PENDIENTE y ERROR. Las FALLIDA no se reintentan solas. */
    @Query("SELECT * FROM capturas WHERE estado IN ('PENDIENTE', 'ERROR') ORDER BY timestampMillis ASC")
    suspend fun pendientes(): List<CapturaEntity>
    @Query("SELECT COUNT(*) FROM capturas WHERE estado != 'ENVIADA'") fun observarPendientes(): Flow<Int>
    @Query("SELECT COUNT(*) FROM capturas WHERE timestampMillis >= :desde AND timestampMillis < :hasta")
    suspend fun contarEntre(desde: Long, hasta: Long): Int
}

// ------------------------------------------------------------------ Database

@Database(entities = [CapturaEntity::class, SesionEntity::class], version = 3, exportSchema = false)
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
    // Agregados con Image Labeling y la sugerencia; con default para leer filas viejas.
    val etiquetas: List<EtiquetaJson> = emptyList(),
    val lineasOcr: List<LineaOcrJson> = emptyList(),
)

@Serializable
internal data class EtiquetaJson(val texto: String, val confianza: Float = 0f, val indice: Int = 0)

@Serializable
internal data class LineaOcrJson(
    val texto: String,
    val alturaPx: Int = 0,
    val top: Int = 0,
    // Plan 3: caja completa; con default para filas viejas. No se persisten elementos ni símbolos.
    val left: Int = 0,
    val right: Int = 0,
)

@Serializable
internal data class PrecioJson(
    val valor: Double? = null,
    val tipo: TipoPrecio = TipoPrecio.DESCONOCIDO,
    val tienePrecioUnitario: Boolean = false,
    val textoFuente: String = "",
    val precioUnitario: Double? = null,
    // Plan 3: con default para filas viejas (confianza null = no calculada).
    val confianza: Float? = null,
    val candidatos: List<CandidatoJson> = emptyList(),
)

/** Con `coerceInputValues`, una [forma] desconocida cae a ENTERO en vez de romper la lectura. */
@Serializable
internal data class CandidatoJson(
    val valor: Double = 0.0,
    val forma: FormaPrecio = FormaPrecio.ENTERO,
    val textoFuente: String = "",
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
    val alturaRelativa: Float = 0f,
    val tieneSigno: Boolean = false,
    val esUnitario: Boolean = false,
    val esAnterior: Boolean = false,
    val puntuacion: Float = 0f,
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
    // Campos agregados con el contrato v0.1.0 completo; con default para filas viejas.
    val validaciones: Map<String, String> = emptyMap(),
    val fotosARepetir: List<TipoFoto> = emptyList(),
    val tipoPrecio: String? = null,
    val precioUnitarioDetectado: Double? = null,
    val promocion: String? = null,
)

@Serializable
internal data class CodigoEscaneadoJson(
    val valor: String,
    val formato: String? = null,
    val checksumOk: Boolean = false,
    val timestampMillis: Long = 0L,
)

internal fun CodigoEscaneado.toJson() = JsonDb.encodeToString(
    CodigoEscaneadoJson.serializer(), CodigoEscaneadoJson(valor, formato, checksumOk, timestampMillis)
)

internal fun codigoEscaneadoDeJson(s: String): CodigoEscaneado =
    JsonDb.decodeFromString(CodigoEscaneadoJson.serializer(), s).let {
        CodigoEscaneado(it.valor, it.formato, it.checksumOk, it.timestampMillis)
    }

internal fun AnalisisImagen.toJson() = JsonDb.encodeToString(
    AnalisisJson.serializer(),
    AnalisisJson(
        tipo, path, nitidez, brillo, textoOcr, confianzaOcr, codigoBarras, formatoCodigo, areaCodigo, problemas,
        etiquetas = etiquetas.map { EtiquetaJson(it.texto, it.confianza, it.indice) },
        lineasOcr = lineasOcr.map { LineaOcrJson(it.texto, it.alturaPx, it.top, it.caja.left, it.caja.right) },
    )
)

internal fun analisisDeJson(s: String): AnalisisImagen = JsonDb.decodeFromString(AnalisisJson.serializer(), s).let {
    AnalisisImagen(
        it.tipo, it.path, it.nitidez, it.brillo, it.textoOcr, it.confianzaOcr,
        it.codigoBarras, it.formatoCodigo, it.areaCodigo, it.problemas,
        etiquetas = it.etiquetas.map { e -> EtiquetaImagen(e.texto, e.confianza, e.indice) },
        lineasOcr = it.lineasOcr.map { l -> LineaOcr(l.texto, CajaPx(l.left, l.top, l.right, l.top + l.alturaPx)) },
    )
}

internal fun PrecioDetectado.toJson() = JsonDb.encodeToString(
    PrecioJson.serializer(),
    PrecioJson(
        valor, tipo, tienePrecioUnitario, textoFuente, precioUnitario, confianza,
        candidatos.map {
            CandidatoJson(
                it.valor, it.forma, it.textoFuente,
                it.caja.left, it.caja.top, it.caja.right, it.caja.bottom,
                it.alturaRelativa, it.tieneSigno, it.esUnitario, it.esAnterior, it.puntuacion,
            )
        },
    )
)

internal fun precioDeJson(s: String): PrecioDetectado = JsonDb.decodeFromString(PrecioJson.serializer(), s).let {
    PrecioDetectado(
        it.valor, it.tipo, it.tienePrecioUnitario, it.textoFuente, it.precioUnitario, it.confianza,
        it.candidatos.map { c ->
            CandidatoPrecio(
                c.valor, c.forma, c.textoFuente, CajaPx(c.left, c.top, c.right, c.bottom),
                c.alturaRelativa, c.tieneSigno, c.esUnitario, c.esAnterior, c.puntuacion,
            )
        },
    )
}

internal fun ResultadoServidor.toJson() = JsonDb.encodeToString(
    ResultadoJson.serializer(),
    ResultadoJson(
        decision, confidenceScore, reasonCode, explicacion, avisos,
        precioDetectado, codigoBarrasDetectado, capaDecisiva,
        validaciones, fotosARepetir, tipoPrecio, precioUnitarioDetectado, promocion,
    )
)

internal fun resultadoDeJson(s: String): ResultadoServidor =
    JsonDb.decodeFromString(ResultadoJson.serializer(), s).let {
        ResultadoServidor(
            it.decision, it.confidenceScore, it.reasonCode, it.explicacion, it.avisos,
            it.precioDetectado, it.codigoBarrasDetectado, it.capaDecisiva,
            it.validaciones, it.fotosARepetir, it.tipoPrecio, it.precioUnitarioDetectado, it.promocion,
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
    espPrecio = productoEsperado.precio, espDescripcionDetectada = productoEsperado.descripcionDetectada,
    espPrecioDetectado = productoEsperado.precioDetectado, espFuente = productoEsperado.fuente?.name,
    espConfianzaSugerencia = productoEsperado.confianzaSugerencia,
    espEditadoPorUsuario = productoEsperado.editadoPorUsuario,
    productoPath = producto.path, etiquetaPath = etiqueta.path,
    productoJson = producto.toJson(), etiquetaJson = etiqueta.toJson(), precioJson = precio.toJson(),
    timestampMillis = timestampMillis, estado = estado.name,
    respuestaJson = respuesta?.toJson(), error = error,
    codigoEscaneadoJson = codigoEscaneado?.toJson(),
)

internal fun CapturaEntity.toModelo() = Captura(
    captureId = captureId, sesionId = sesionId,
    productoEsperado = ProductoEsperado(
        descripcion = espDescripcion, codigoBarras = espCodigoBarras, sku = espSku,
        marca = espMarca, contenido = espContenido, esContratipo = espEsContratipo,
        precio = espPrecio, descripcionDetectada = espDescripcionDetectada, precioDetectado = espPrecioDetectado,
        fuente = espFuente?.let { n -> runCatching { FuenteDescripcion.valueOf(n) }.getOrNull() },
        confianzaSugerencia = espConfianzaSugerencia, editadoPorUsuario = espEditadoPorUsuario,
    ),
    producto = analisisDeJson(productoJson),
    etiqueta = analisisDeJson(etiquetaJson),
    precio = precioDeJson(precioJson),
    timestampMillis = timestampMillis,
    estado = runCatching { EstadoEnvio.valueOf(estado) }.getOrDefault(EstadoEnvio.PENDIENTE),
    respuesta = respuestaJson?.let { runCatching { resultadoDeJson(it) }.getOrNull() },
    error = error,
    codigoEscaneado = codigoEscaneadoJson?.let { runCatching { codigoEscaneadoDeJson(it) }.getOrNull() },
)
