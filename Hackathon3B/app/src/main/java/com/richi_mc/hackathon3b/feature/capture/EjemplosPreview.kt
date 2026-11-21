package com.richi_mc.hackathon3b.feature.capture

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

/** Datos de ejemplo solo para @Preview. */
internal object EjemplosPreview {
    val sesion = Sesion(
        id = "s1", cadena = Cadena.TRES_B, tienda = "3B Lindavista",
        lat = 19.5, lon = -99.13, precisionM = 12f, fachadaPath = "", inicioMillis = 0L,
        fachadaAnalisis = analisis(TipoFoto.FACHADA, "TIENDAS 3B"),
    )

    fun analisis(
        tipo: TipoFoto,
        texto: String,
        problemas: List<Problema> = emptyList(),
        codigo: String? = null,
    ) = AnalisisImagen(
        tipo = tipo, path = "", nitidez = 250.0, brillo = 130.0, textoOcr = texto, confianzaOcr = 0.9f,
        codigoBarras = codigo, formatoCodigo = codigo?.let { "EAN_13" }, areaCodigo = codigo?.let { 0.05 },
        problemas = problemas,
    )

    val producto = analisis(TipoFoto.PRODUCTO, "LECHE ENTERA 1 L\nLALA", codigo = "7501055300075")
    val etiqueta = analisis(
        TipoFoto.ETIQUETA, "LECHE ENTERA 1L\n\$24.90\nPRECIO POR LITRO \$24.90\nVigencia al 30 de noviembre\nSKU 1002345",
        problemas = listOf(Problema.SIN_PRECIO_UNITARIO), codigo = "7501055300075",
    )
    val etiquetaConProblemas = analisis(
        TipoFoto.ETIQUETA, "LECHE", problemas = listOf(Problema.BORROSA, Problema.SIN_PRECIO),
    )
    val precio = PrecioDetectado(24.90, TipoPrecio.REGULAR, true, "\$24.90", 24.90)
    val esperado = ProductoEsperado(descripcion = "Leche entera 1 L", codigoBarras = "7501055300075")

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
