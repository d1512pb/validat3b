package com.richi_mc.hackathon3b.core.network

import android.os.Build
import com.richi_mc.hackathon3b.BuildConfig
import com.richi_mc.hackathon3b.core.model.AnalisisImagen
import com.richi_mc.hackathon3b.core.model.Captura
import com.richi_mc.hackathon3b.core.model.CodigoEscaneado
import com.richi_mc.hackathon3b.core.model.Decision
import com.richi_mc.hackathon3b.core.model.EtiquetaImagen
import com.richi_mc.hackathon3b.core.model.Problema
import com.richi_mc.hackathon3b.core.model.ResultadoServidor
import com.richi_mc.hackathon3b.core.model.Sesion
import com.richi_mc.hackathon3b.core.model.TipoFoto
import com.richi_mc.hackathon3b.core.model.Umbrales
import com.richi_mc.hackathon3b.feature.labeling.GruposEtiquetas
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
            dispositivo = nombre(Build.MANUFACTURER, Build.MODEL),
            sistemaOperativo = "Android " + Build.VERSION.RELEASE,
            versionApp = BuildConfig.VERSION_NAME,
        )

        /** "Fabricante Modelo" sin repetir la marca si el modelo ya empieza con ella (ignora mayúsculas). */
        fun nombre(fabricante: String?, modelo: String?): String? {
            val f = fabricante?.trim().orEmpty()
            val m = modelo?.trim().orEmpty()
            return when {
                f.isEmpty() && m.isEmpty() -> null
                f.isEmpty() -> m
                m.isEmpty() -> f
                m.startsWith(f, ignoreCase = true) -> m
                else -> "$f $m"
            }
        }
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
        val ubicacion = ubicacionDto(s.lat, s.lon, s.precisionM)
        val pe = c.productoEsperado
        return CapturaEntradaDto(
            captureId = c.captureId,
            codigoBarrasCapturado = c.codigoBarrasFinal,
            productoEsperado = ProductoEsperadoDto(
                sku = pe.sku,
                descripcion = pe.descripcion,
                marca = pe.marca,
                contenido = pe.contenido,
                codigoBarras = pe.codigoBarras,
                esContratipo = pe.esContratipo,
                precio = pe.precio,
                editadoPorUsuario = pe.editadoPorUsuario,
                descripcionDetectada = pe.descripcionDetectada,
                precioDetectado = pe.precioDetectado,
                tipoPrecio = c.precio.tipo.name,
                fuenteDescripcion = pe.fuente?.name,
                confianzaSugerencia = pe.confianzaSugerencia?.let { redondear2(it.toDouble()) },
                confianzaPrecio = c.precio.confianza?.let { redondear2(it.toDouble()) },
                precioCandidatos = c.precio.candidatos.takeIf { it.isNotEmpty() }?.map {
                    CandidatoPrecioDto(redondear2(it.valor), it.forma.name, redondear2(it.puntuacion.toDouble()))
                },
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
            etiquetasImagen = etiquetasImagen(c, s),
        )
    }

    /** `etiquetas_imagen`: null si ninguna de las tres fotos tiene etiquetas. */
    internal fun etiquetasImagen(c: Captura, s: Sesion): EtiquetasImagenDto? {
        val dto = EtiquetasImagenDto(
            fachada = s.fachadaAnalisis?.etiquetas.orEmpty().map(::etiquetaDto),
            producto = c.producto.etiquetas.map(::etiquetaDto),
            etiqueta = c.etiqueta.etiquetas.map(::etiquetaDto),
        )
        return dto.takeIf { it.fachada.isNotEmpty() || it.producto.isNotEmpty() || it.etiqueta.isNotEmpty() }
    }

    private fun etiquetaDto(e: EtiquetaImagen) = EtiquetaImagenDto(e.texto, redondear2(e.confianza.toDouble()))

    private fun redondear2(v: Double) = Math.round(v * 100.0) / 100.0

    /** Solo con lat/lon presentes, en rango y distintas de (0, 0); `precision_m` solo si es >= 0. */
    fun ubicacionDto(lat: Double?, lon: Double?, precisionM: Float?): UbicacionDto? {
        if (lat == null || lon == null) return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        if (lat == 0.0 && lon == 0.0) return null
        val precision = precisionM?.toDouble()?.takeIf { it >= 0.0 }
        return UbicacionDto(lat, lon, precision)
    }

    fun toResultado(dto: RespuestaCapturaDto): ResultadoServidor {
        val decision = Decision.normalizar(dto.decision)
        val v = dto.validaciones
        val validaciones = buildMap {
            v?.tiendaFachada?.let { put("tienda_fachada", it) }
            v?.producto?.let { put("producto", it) }
            v?.codigoBarras?.let { put("codigo_barras", it) }
            v?.evidenciaPrecio?.let { put("evidencia_precio", it) }
        }
        return ResultadoServidor(
            decision = decision,
            confidenceScore = dto.confidenceScore,
            reasonCode = dto.reasonCode,
            explicacion = dto.explicacion,
            avisos = dto.traza?.avisos.orEmpty(),
            precioDetectado = dto.precioDetectado,
            codigoBarrasDetectado = dto.codigoBarrasDetectado,
            capaDecisiva = dto.traza?.capaDecisiva,
            validaciones = validaciones,
            fotosARepetir = fotosARepetir(decision, dto.reasonCode, validaciones),
            tipoPrecio = dto.traza?.tipoPrecio,
            precioUnitarioDetectado = dto.traza?.precioUnitarioDetectado,
            promocion = dto.traza?.promocion,
        )
    }

    /**
     * Fotos a repetir (contratos.md §4.2/§4.3): las de validaciones en "falla" unidas con las del
     * reason_code, sin duplicados y en orden fachada, producto, etiqueta. Vacío si se aprobó.
     */
    fun fotosARepetir(decision: String, reasonCode: String?, validaciones: Map<String, String>): List<TipoFoto> {
        if (decision == Decision.APROBADA) return emptyList()
        val fotos = mutableSetOf<TipoFoto>()
        validaciones.forEach { (clave, estado) ->
            if (estado != FALLA) return@forEach
            when (clave) {
                "tienda_fachada" -> fotos += TipoFoto.FACHADA
                "producto", "codigo_barras" -> fotos += TipoFoto.PRODUCTO
                "evidencia_precio" -> fotos += TipoFoto.ETIQUETA
            }
        }
        when (reasonCode?.trim()?.uppercase(Locale.ROOT)) {
            "CONTRATIPO_INVALIDO", "PRODUCTO_NO_COINCIDE", "DESCRIPCION_INCOMPLETA", "PRODUCTO_NO_VISIBLE" ->
                fotos += TipoFoto.PRODUCTO
            "FACHADA_INCORRECTA" -> fotos += TipoFoto.FACHADA
            "SIN_ETIQUETA_PRECIO", "SIN_PRECIO_UNITARIO" -> fotos += TipoFoto.ETIQUETA
            // CAPTURA_LEJANA / IMAGEN_BORROSA / FOTO_DE_PANTALLA y desconocidos: solo las validaciones.
        }
        return TipoFoto.entries.filter { it in fotos }
    }

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

        val escaneado = c.codigoEscaneado
        lista += if (escaneado != null) codigoEscaneadoChequeo(escaneado) else codigoBarras(c.producto)
        lista += distancia(c.producto, escaneadoValido = escaneado?.checksumOk == true)
        lista += etiquetaPresente(c.etiqueta)
        lista += precioLeido(c)

        // Chequeos por etiquetas de imagen: solo para las fotos que tienen etiquetas.
        s.fachadaAnalisis?.let { lista += fachadaTienda(it) }
        lista += productoVisible(c.producto)
        fotos.forEach { lista += fotoPantalla(it) }

        val codProducto = c.producto.codigoBarras
        val codEtiqueta = c.etiqueta.codigoBarras
        if (escaneado != null && codProducto != null) {
            val coincide = mismoCodigo(escaneado.valor, codProducto)
            lista += ChequeoLocalDto(
                nombre = "codigo_foto_coincide",
                foto = null,
                resultado = if (coincide) OK else FALLA,
                valor = escaneado.valor,
                detalle = "escaneado=${escaneado.valor}; foto=$codProducto",
            )
        }
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

    /** Código leído en vivo: no viene de una de las 3 fotos, por eso `foto = null`. */
    private fun codigoEscaneadoChequeo(e: CodigoEscaneado) = ChequeoLocalDto(
        nombre = "codigo_barras",
        foto = null,
        resultado = if (e.checksumOk) OK else FALLA,
        valor = e.valor,
        detalle = "escaneado en vivo; formato ${e.formato ?: "desconocido"}",
    )

    /** Compara códigos ignorando ceros a la izquierda (UPC-A vs EAN-13). */
    internal fun mismoCodigo(a: String, b: String) = a.trim().trimStart('0') == b.trim().trimStart('0')

    private fun distancia(a: AnalisisImagen, escaneadoValido: Boolean = false): ChequeoLocalDto {
        val area = a.areaCodigo
        val min = Umbrales.areaCodigoMin
        val lejos = Problema.DEMASIADO_LEJOS in a.problemas
        return when {
            lejos && escaneadoValido -> ChequeoLocalDto(
                nombre = "distancia",
                foto = TipoFoto.PRODUCTO.contrato,
                resultado = DUDA,
                puntaje = area?.let { if (min <= 0.0) 1.0 else (it / (2 * min)).coerceIn(0.0, 1.0) },
                detalle = "el código se ve pequeño en la foto, pero se obtuvo un escaneo válido en vivo",
            )
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

    private fun List<EtiquetaImagen>.mejorDelGrupo(grupo: Set<String>): EtiquetaImagen? =
        filter { it.texto.lowercase() in grupo }.maxByOrNull { it.confianza }

    /** `producto_visible` (foto del producto): duda (advertencia, no bloquea) si no hay etiquetas de
     * producto ni texto de empaque (PRODUCTO_NO_VISIBLE). */
    internal fun productoVisible(a: AnalisisImagen): List<ChequeoLocalDto> {
        if (a.etiquetas.isEmpty()) return emptyList()
        val mejor = a.etiquetas.mejorDelGrupo(GruposEtiquetas.producto)
        val principal = a.etiquetas.first()
        val noVisible = Problema.PRODUCTO_NO_VISIBLE in a.problemas
        val base = "etiqueta principal ${principal.texto} ${num(principal.confianza * 100.0, 0)}%"
        return listOf(
            ChequeoLocalDto(
                nombre = "producto_visible",
                foto = TipoFoto.PRODUCTO.contrato,
                resultado = if (noVisible) DUDA else OK,
                puntaje = mejor?.confianza?.toDouble()?.coerceIn(0.0, 1.0),
                valor = principal.texto,
                detalle = if (noVisible) "$base; sin etiqueta de producto ni texto de empaque (advertencia)" else base,
            )
        )
    }

    /** `fachada_tienda` (foto de la fachada): duda (advertencia) si no parece una tienda. */
    internal fun fachadaTienda(a: AnalisisImagen): List<ChequeoLocalDto> {
        if (a.etiquetas.isEmpty()) return emptyList()
        val mejor = a.etiquetas.mejorDelGrupo(GruposEtiquetas.fachada)
        val principal = a.etiquetas.first()
        return listOf(
            ChequeoLocalDto(
                nombre = "fachada_tienda",
                foto = TipoFoto.FACHADA.contrato,
                resultado = if (Problema.FACHADA_NO_PARECE_TIENDA in a.problemas) DUDA else OK,
                puntaje = mejor?.confianza?.toDouble()?.coerceIn(0.0, 1.0),
                valor = principal.texto,
                detalle = "etiqueta principal ${principal.texto} ${num(principal.confianza * 100.0, 0)}%",
            )
        )
    }

    /** `pantalla` (por foto): duda si el etiquetador ve un monitor, pantalla o teléfono (POSIBLE_FOTO_DE_PANTALLA). */
    internal fun fotoPantalla(a: AnalisisImagen): List<ChequeoLocalDto> {
        if (a.etiquetas.isEmpty()) return emptyList()
        val mejor = a.etiquetas.mejorDelGrupo(GruposEtiquetas.pantalla)
        val sospechosa = Problema.POSIBLE_FOTO_DE_PANTALLA in a.problemas
        return listOf(
            ChequeoLocalDto(
                nombre = "pantalla",
                foto = a.tipo.contrato,
                resultado = if (sospechosa) DUDA else OK,
                puntaje = mejor?.confianza?.toDouble()?.coerceIn(0.0, 1.0),
                valor = (mejor ?: a.etiquetas.first()).texto,
                detalle = if (sospechosa) "parece una foto de una pantalla" else "no parece una foto de una pantalla",
            )
        )
    }

    private fun num(v: Double, decimales: Int) = String.format(Locale.ROOT, "%.${decimales}f", v)
}
