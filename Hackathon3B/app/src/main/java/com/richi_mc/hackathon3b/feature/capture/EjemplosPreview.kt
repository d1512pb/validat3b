package com.richi_mc.hackathon3b.feature.capture

import com.richi_mc.hackathon3b.core.model.AnalisisImagen
import com.richi_mc.hackathon3b.core.model.Cadena
import com.richi_mc.hackathon3b.core.model.Captura
import com.richi_mc.hackathon3b.core.model.EstadoEnvio
import com.richi_mc.hackathon3b.core.model.EtiquetaImagen
import com.richi_mc.hackathon3b.core.model.FuenteDescripcion
import com.richi_mc.hackathon3b.core.model.PrecioDetectado
import com.richi_mc.hackathon3b.core.model.Problema
import com.richi_mc.hackathon3b.core.model.ProductoEsperado
import com.richi_mc.hackathon3b.core.model.ResultadoServidor
import com.richi_mc.hackathon3b.core.model.Sesion
import com.richi_mc.hackathon3b.core.model.TipoFoto
import com.richi_mc.hackathon3b.core.model.TipoPrecio

/** Datos de ejemplo solo para @Preview. */
internal object EjemplosPreview {
    val sesion = Sesion(
        id = "s1", cadena = Cadena.TRES_B, tienda = "3B Lindavista",
        lat = 19.5, lon = -99.13, precisionM = 12f, fachadaPath = "", inicioMillis = 0L,
        fachadaAnalisis = analisis(
            TipoFoto.FACHADA, "TIENDAS 3B",
            etiquetas = listOf(EtiquetaImagen("Building", 0.88f, 0), EtiquetaImagen("Signage", 0.71f, 1)),
        ),
    )

    fun analisis(
        tipo: TipoFoto,
        texto: String,
        problemas: List<Problema> = emptyList(),
        codigo: String? = null,
        etiquetas: List<EtiquetaImagen> = emptyList(),
    ) = AnalisisImagen(
        tipo = tipo, path = "", nitidez = 250.0, brillo = 130.0, textoOcr = texto, confianzaOcr = 0.9f,
        codigoBarras = codigo, formatoCodigo = codigo?.let { "EAN_13" }, areaCodigo = codigo?.let { 0.05 },
        problemas = problemas, etiquetas = etiquetas,
    )

    val producto = analisis(
        TipoFoto.PRODUCTO, "LECHE ENTERA 1 L\nLALA", codigo = "7501055300075",
        etiquetas = listOf(EtiquetaImagen("Milk", 0.91f, 0), EtiquetaImagen("Drink", 0.84f, 1), EtiquetaImagen("Bottle", 0.66f, 2)),
    )
    /** Foto de una pared: sin etiquetas de producto ni texto de empaque (PRODUCTO_NO_VISIBLE, advertencia no bloqueante). */
    val productoNoVisible = analisis(
        TipoFoto.PRODUCTO, "", problemas = listOf(Problema.PRODUCTO_NO_VISIBLE),
        etiquetas = listOf(EtiquetaImagen("Wall", 0.82f, 0), EtiquetaImagen("Floor", 0.55f, 1)),
    )
    val etiqueta = analisis(
        TipoFoto.ETIQUETA, "LECHE ENTERA 1L\n\$24.90\nPRECIO POR LITRO \$24.90\nVigencia al 30 de noviembre\nSKU 1002345",
        problemas = listOf(Problema.SIN_PRECIO_UNITARIO), codigo = "7501055300075",
    )
    val etiquetaConProblemas = analisis(
        TipoFoto.ETIQUETA, "LECHE", problemas = listOf(Problema.BORROSA, Problema.SIN_PRECIO),
    )
    val precio = PrecioDetectado(24.90, TipoPrecio.REGULAR, true, "\$24.90", 24.90)
    /** Sugerida por la etiqueta del rack y sin editar. */
    val esperado = ProductoEsperado(
        descripcion = "LECHE ENTERA 1L", codigoBarras = "7501055300075",
        precio = 24.90, descripcionDetectada = "LECHE ENTERA 1L", precioDetectado = 24.90,
        fuente = FuenteDescripcion.ETIQUETA_RACK, confianzaSugerencia = 0.8f,
    )
    /** El capturista corrigió la descripción y el precio. */
    val esperadoEditado = esperado.copy(
        descripcion = "LECHE ENTERA LALA 1 L", precio = 25.50, editadoPorUsuario = true,
    )
    /** No se detectó nada: campos vacíos. */
    val esperadoVacio = ProductoEsperado(fuente = FuenteDescripcion.MANUAL)
    /** Solo una categoría de ML Kit como pista. */
    val esperadoPorCategoria = ProductoEsperado(
        descripcion = "BOTELLA", descripcionDetectada = "BOTELLA", fuente = FuenteDescripcion.CATEGORIA,
        confianzaSugerencia = 0.3f,
    )

    val captura = Captura(
        captureId = "CAP-3B-20261121-0004", sesionId = "s1", productoEsperado = esperado,
        producto = producto, etiqueta = etiqueta, precio = precio, timestampMillis = 0L,
        estado = EstadoEnvio.ENVIADA,
        respuesta = ResultadoServidor(
            decision = "revision_manual", confidenceScore = 0.64, reasonCode = "DESCRIPCION_INCOMPLETA",
            explicacion = "La descripción de la etiqueta no coincide del todo con el producto fotografiado.",
            avisos = listOf("Acércate más a la etiqueta", "Evita reflejos"),
        ),
    )
}
