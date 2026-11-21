package com.richi_mc.hackathon3b.core.network

import android.os.Build
import com.richi_mc.hackathon3b.BuildConfig
import com.richi_mc.hackathon3b.core.model.AnalisisImagen
import com.richi_mc.hackathon3b.core.model.Captura
import com.richi_mc.hackathon3b.core.model.Problema
import com.richi_mc.hackathon3b.core.model.ResultadoServidor
import com.richi_mc.hackathon3b.core.model.Sesion
import com.richi_mc.hackathon3b.core.model.TipoFoto
import com.richi_mc.hackathon3b.core.model.Umbrales
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Datos del teléfono para `metadatos_app` (parametrizable para tests JVM). */
data class DatosDispositivo(
    val dispositivo: String?,
    val sistemaOperativo: String?,
    val versionApp: String?,
    val capturistaId: String? = null,
) {
    companion object {
        fun actual() = DatosDispositivo(
            dispositivo = Build.MODEL,
            sistemaOperativo = "Android " + Build.VERSION.RELEASE,
            versionApp = BuildConfig.VERSION_NAME,
        )
    }
}

/** Convierte entre el modelo de la app y el contrato de ../contratos/. */
object CapturaMapper {
    const val OK = "ok"
    const val FALLA = "falla"
    const val DUDA = "duda"

    fun toDto(
        c: Captura,
        s: Sesion,
        dispositivo: DatosDispositivo = DatosDispositivo.actual(),
        zona: ZoneId = ZoneId.systemDefault(),
    ): CapturaEntradaDto {
        val ubicacion = if (s.lat != null && s.lon != null) {
            UbicacionDto(s.lat, s.lon, s.precisionM?.toDouble())
        } else null
        val pe = c.productoEsperado
        return CapturaEntradaDto(
            captureId = c.captureId,
            codigoBarrasCapturado = c.producto.codigoBarras ?: c.etiqueta.codigoBarras,
            productoEsperado = ProductoEsperadoDto(
                sku = pe.sku,
                descripcion = pe.descripcion,
                marca = pe.marca,
                contenido = pe.contenido,
                codigoBarras = pe.codigoBarras,
                esContratipo = pe.esContratipo,
            ),
            cadena = s.cadena.etiqueta,
            tienda = s.tienda,
            fechaHoraCaptura = formatearFecha(c.timestampMillis, zona),
            metadatosApp = MetadatosAppDto(
                dispositivo = dispositivo.dispositivo,
                sistemaOperativo = dispositivo.sistemaOperativo,
                versionApp = dispositivo.versionApp,
                capturistaId = dispositivo.capturistaId,
                ubicacion = ubicacion,
            ),
            chequeosLocales = chequeos(c, s),
        )
    }

    fun toResultado(dto: RespuestaCapturaDto) = ResultadoServidor(
        decision = dto.decision,
        confidenceScore = dto.confidenceScore,
        reasonCode = dto.reasonCode,
        explicacion = dto.explicacion,
        avisos = dto.traza?.avisos.orEmpty(),
        precioDetectado = dto.precioDetectado,
        codigoBarrasDetectado = dto.codigoBarrasDetectado,
        capaDecisiva = dto.traza?.capaDecisiva,
    )

    fun formatearFecha(millis: Long, zona: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zona).truncatedTo(ChronoUnit.SECONDS)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    // ------------------------------------------------------------- chequeos

    internal fun chequeos(c: Captura, s: Sesion): List<ChequeoLocalDto> {
        val fotos = buildList {
            s.fachadaAnalisis?.let { add(it) }
            add(c.producto)
            add(c.etiqueta)
        }
        val lista = mutableListOf<ChequeoLocalDto>()

        // nitidez e iluminación por foto
        fotos.forEach { lista += nitidez(it) }
        fotos.forEach { lista += iluminacion(it) }

        lista += codigoBarras(c.producto)
        lista += distancia(c.producto)
        lista += etiquetaPresente(c.etiqueta)
        lista += precioLeido(c)

        val codProducto = c.producto.codigoBarras
        val codEtiqueta = c.etiqueta.codigoBarras
        if (codProducto != null && codEtiqueta != null) {
            val coincide = codProducto == codEtiqueta
            lista += ChequeoLocalDto(
                nombre = "codigo_etiqueta_coincide",
                foto = null,
                resultado = if (coincide) OK else FALLA,
                valor = codEtiqueta,
                detalle = "producto=$codProducto; etiqueta=$codEtiqueta",
            )
        }
        return lista
    }

    private fun nitidez(a: AnalisisImagen): ChequeoLocalDto {
        val min = Umbrales.nitidezMin
        val puntaje = if (min <= 0.0) 1.0 else (a.nitidez / (2 * min)).coerceIn(0.0, 1.0)
        return ChequeoLocalDto(
            nombre = "nitidez",
            foto = a.tipo.contrato,
            resultado = if (Problema.BORROSA in a.problemas) FALLA else OK,
            puntaje = puntaje,
            detalle = "varianza del Laplaciano ${num(a.nitidez, 1)}",
        )
    }

    private fun iluminacion(a: AnalisisImagen): ChequeoLocalDto {
        val estado = when {
            Problema.OSCURA in a.problemas -> "oscura"
            Problema.SOBREEXPUESTA in a.problemas -> "sobreexpuesta"
            else -> null
        }
        return ChequeoLocalDto(
            nombre = "iluminacion",
            foto = a.tipo.contrato,
            resultado = if (estado == null) OK else FALLA,
            detalle = "brillo medio ${num(a.brillo, 1)}" + (estado?.let { " ($it)" } ?: ""),
        )
    }

    private fun codigoBarras(a: AnalisisImagen): ChequeoLocalDto {
        val falla = Problema.SIN_CODIGO_BARRAS in a.problemas ||
            Problema.CODIGO_INVALIDO in a.problemas || a.codigoBarras == null
        return ChequeoLocalDto(
            nombre = "codigo_barras",
            foto = TipoFoto.PRODUCTO.contrato,
            resultado = if (falla) FALLA else OK,
            valor = a.codigoBarras,
            detalle = a.formatoCodigo,
        )
    }

    private fun distancia(a: AnalisisImagen): ChequeoLocalDto {
        val area = a.areaCodigo
        val min = Umbrales.areaCodigoMin
        val lejos = Problema.DEMASIADO_LEJOS in a.problemas
        return when {
            area == null -> ChequeoLocalDto(
                nombre = "distancia", foto = TipoFoto.PRODUCTO.contrato, resultado = DUDA,
                detalle = "sin código de barras para medir la distancia",
            )
            else -> ChequeoLocalDto(
                nombre = "distancia",
                foto = TipoFoto.PRODUCTO.contrato,
                resultado = if (lejos) FALLA else OK,
                puntaje = if (min <= 0.0) 1.0 else (area / (2 * min)).coerceIn(0.0, 1.0),
                detalle = "el código ocupa ${num(area * 100, 1)}% de la imagen",
            )
        }
    }

    private fun etiquetaPresente(a: AnalisisImagen): ChequeoLocalDto {
        val legible = a.textoOcr.isNotBlank() && Problema.SIN_TEXTO_LEGIBLE !in a.problemas
        return ChequeoLocalDto(
            nombre = "etiqueta_presente",
            foto = TipoFoto.ETIQUETA.contrato,
            resultado = if (legible) OK else FALLA,
            puntaje = a.confianzaOcr?.toDouble()?.coerceIn(0.0, 1.0),
            detalle = "${a.textoOcr.trim().length} caracteres de texto",
        )
    }

    private fun precioLeido(c: Captura): ChequeoLocalDto {
        val p = c.precio
        val valor = p.valor
        if (valor == null) {
            return ChequeoLocalDto(
                nombre = "precio_leido", foto = TipoFoto.ETIQUETA.contrato, resultado = FALLA,
                detalle = "no se encontró un precio en la etiqueta",
            )
        }
        val unitario = if (p.tienePrecioUnitario) "con precio unitario" else "sin precio unitario"
        return ChequeoLocalDto(
            nombre = "precio_leido",
            foto = TipoFoto.ETIQUETA.contrato,
            resultado = if (p.tienePrecioUnitario) OK else DUDA,
            valor = num(valor, 2),
            detalle = "tipo ${p.tipo.name.lowercase(Locale.ROOT)}, $unitario",
        )
    }

    private fun num(v: Double, decimales: Int) = String.format(Locale.ROOT, "%.${decimales}f", v)
}
