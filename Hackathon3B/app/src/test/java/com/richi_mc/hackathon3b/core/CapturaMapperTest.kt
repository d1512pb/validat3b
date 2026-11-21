package com.richi_mc.hackathon3b.core

import com.richi_mc.hackathon3b.core.model.AnalisisImagen
import com.richi_mc.hackathon3b.core.model.Cadena
import com.richi_mc.hackathon3b.core.model.Captura
import com.richi_mc.hackathon3b.core.model.PrecioDetectado
import com.richi_mc.hackathon3b.core.model.Problema
import com.richi_mc.hackathon3b.core.model.ProductoEsperado
import com.richi_mc.hackathon3b.core.model.Sesion
import com.richi_mc.hackathon3b.core.model.TipoFoto
import com.richi_mc.hackathon3b.core.model.TipoPrecio
import com.richi_mc.hackathon3b.core.model.Umbrales
import com.richi_mc.hackathon3b.core.network.CapturaMapper
import com.richi_mc.hackathon3b.core.network.ChequeoLocalDto
import com.richi_mc.hackathon3b.core.network.DatosDispositivo
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CapturaMapperTest {

    private val dispositivo = DatosDispositivo("Pixel Test", "Android 15", "1.0")

    private fun analisis(
        tipo: TipoFoto,
        nitidez: Double = 300.0,
        brillo: Double = 130.0,
        texto: String = "LECHE 1L",
        codigo: String? = null,
        area: Double? = null,
        problemas: List<Problema> = emptyList(),
    ) = AnalisisImagen(
        tipo, "/tmp/${tipo.contrato}.jpg", nitidez, brillo, texto, 0.9f, codigo,
        codigo?.let { "EAN_13" }, area, problemas,
    )

    private val sesion = Sesion(
        id = "S1", cadena = Cadena.BODEGA_AURRERA, tienda = "Bodega Aurrerá Iztapalapa Centro",
        lat = 19.3574, lon = -99.0927, precisionM = 12f, fachadaPath = "/tmp/f.jpg",
        inicioMillis = 0L, fachadaAnalisis = analisis(TipoFoto.FACHADA),
    )

    private fun captura(
        producto: AnalisisImagen = analisis(TipoFoto.PRODUCTO, codigo = "7501055363056", area = 0.05),
        etiqueta: AnalisisImagen = analisis(TipoFoto.ETIQUETA, texto = "LECHE 1L \$24.90"),
        precio: PrecioDetectado = PrecioDetectado(24.9, TipoPrecio.REGULAR, true, "\$24.90", 24.9),
    ) = Captura(
        captureId = "CAP-000123", sesionId = "S1",
        productoEsperado = ProductoEsperado(descripcion = "Leche", codigoBarras = "7501055363056", esContratipo = true),
        producto = producto, etiqueta = etiqueta, precio = precio,
        timestampMillis = 1_795_279_325_000L, // 2026-11-21T16:42:05Z
    )

    private fun List<ChequeoLocalDto>.get(nombre: String, foto: String? = null) =
        single { it.nombre == nombre && it.foto == foto }

    @Test
    fun campos_basicos() {
        val dto = CapturaMapper.toDto(captura(), sesion, dispositivo, ZoneOffset.ofHours(-6))
        assertEquals("0.1.0", dto.versionContrato)
        assertEquals("Bodega Aurrerá", dto.cadena)
        assertEquals("Bodega Aurrerá Iztapalapa Centro", dto.tienda)
        assertEquals("7501055363056", dto.codigoBarrasCapturado)
        assertEquals("2026-11-21T10:42:05-06:00", dto.fechaHoraCaptura)
        assertEquals(true, dto.productoEsperado.esContratipo)
        assertEquals("Pixel Test", dto.metadatosApp!!.dispositivo)
        val u = dto.metadatosApp!!.ubicacion!!
        assertEquals(19.3574, u.latitud, 0.0)
        assertEquals(12.0, u.precisionM!!, 0.0)
    }

    @Test
    fun sin_ubicacion_ni_fachada_analizada() {
        val s = sesion.copy(lat = null, lon = null, precisionM = null, fachadaAnalisis = null)
        val dto = CapturaMapper.toDto(captura(), s, dispositivo)
        assertNull(dto.metadatosApp!!.ubicacion)
        assertEquals(listOf("producto", "etiqueta"), dto.chequeosLocales.filter { it.nombre == "nitidez" }.map { it.foto })
    }

    @Test
    fun chequeos_caso_feliz() {
        val etq = analisis(TipoFoto.ETIQUETA, texto = "LECHE 1L \$24.90", codigo = "7501055363056")
        val ch = CapturaMapper.toDto(captura(etiqueta = etq), sesion, dispositivo).chequeosLocales
        assertEquals(listOf("fachada", "producto", "etiqueta"), ch.filter { it.nombre == "nitidez" }.map { it.foto })
        assertEquals(listOf("fachada", "producto", "etiqueta"), ch.filter { it.nombre == "iluminacion" }.map { it.foto })
        val nit = ch.get("nitidez", "producto")
        assertEquals("ok", nit.resultado)
        assertEquals(1.0, nit.puntaje!!, 0.0) // 300 / 200 -> tope 1
        assertEquals("ok", ch.get("iluminacion", "producto").resultado)
        val cb = ch.get("codigo_barras", "producto")
        assertEquals("ok", cb.resultado); assertEquals("7501055363056", cb.valor)
        assertEquals("ok", ch.get("distancia", "producto").resultado)
        assertEquals("ok", ch.get("etiqueta_presente", "etiqueta").resultado)
        val pl = ch.get("precio_leido", "etiqueta")
        assertEquals("ok", pl.resultado); assertEquals("24.90", pl.valor)
        assertNotNull(pl.detalle)
        assertEquals("ok", ch.get("codigo_etiqueta_coincide").resultado)
    }

    @Test
    fun chequeos_fallas() {
        val prod = analisis(
            TipoFoto.PRODUCTO, nitidez = Umbrales.nitidezMin / 2, brillo = 10.0, codigo = null,
            problemas = listOf(Problema.BORROSA, Problema.OSCURA, Problema.SIN_CODIGO_BARRAS),
        )
        val etq = analisis(
            TipoFoto.ETIQUETA, texto = "", codigo = "7501055363056",
            problemas = listOf(Problema.SIN_TEXTO_LEGIBLE, Problema.SIN_PRECIO),
        )
        val precio = PrecioDetectado(null, TipoPrecio.DESCONOCIDO, false, "")
        val ch = CapturaMapper.toDto(captura(prod, etq, precio), sesion, dispositivo).chequeosLocales
        val nit = ch.get("nitidez", "producto")
        assertEquals("falla", nit.resultado)
        assertEquals(0.25, nit.puntaje!!, 1e-9) // (min/2) / (2*min)
        assertEquals("falla", ch.get("iluminacion", "producto").resultado)
        assertEquals("falla", ch.get("codigo_barras", "producto").resultado)
        assertEquals("duda", ch.get("distancia", "producto").resultado)
        assertEquals("falla", ch.get("etiqueta_presente", "etiqueta").resultado)
        assertEquals("falla", ch.get("precio_leido", "etiqueta").resultado)
        // Solo la etiqueta tiene código: no se compara.
        assertEquals(0, ch.count { it.nombre == "codigo_etiqueta_coincide" })
        assertEquals("7501055363056", CapturaMapper.toDto(captura(prod, etq, precio), sesion, dispositivo).codigoBarrasCapturado)
    }

    @Test
    fun duda_sin_precio_unitario_y_codigos_distintos() {
        val etq = analisis(TipoFoto.ETIQUETA, codigo = "7501055300075")
        val precio = PrecioDetectado(24.9, TipoPrecio.OFERTA, false, "\$24.90")
        val ch = CapturaMapper.toDto(captura(etiqueta = etq, precio = precio), sesion, dispositivo).chequeosLocales
        assertEquals("duda", ch.get("precio_leido", "etiqueta").resultado)
        assertEquals("falla", ch.get("codigo_etiqueta_coincide").resultado)
    }

    @Test
    fun distancia_lejos() {
        val prod = analisis(TipoFoto.PRODUCTO, codigo = "7501055363056", area = 0.005, problemas = listOf(Problema.DEMASIADO_LEJOS))
        val d = CapturaMapper.toDto(captura(producto = prod), sesion, dispositivo).chequeosLocales.get("distancia", "producto")
        assertEquals("falla", d.resultado)
        assertEquals(0.125, d.puntaje!!, 1e-9) // 0.005 / (2*0.02)
    }

    @Test
    fun confiabilidad_local_se_manda_como_chequeo() {
        val c = CapturaMapper.toDto(captura(), sesion, dispositivo).chequeosLocales.get("confiabilidad_local")
        assertEquals("ok", c.resultado)
        assertEquals("alta", c.valor)
        assertNotNull(c.puntaje)
        assertEquals(true, c.detalle!!.startsWith("N="))
    }

    @Test
    fun sin_precio_no_hay_confiabilidad_local() {
        val sinPrecio = PrecioDetectado(null, TipoPrecio.DESCONOCIDO, false, "")
        val lista = CapturaMapper.toDto(captura(precio = sinPrecio), sesion, dispositivo).chequeosLocales
        assertEquals(0, lista.count { it.nombre == "confiabilidad_local" })
    }
}
