package com.richi_mc.hackathon3b.core

import com.richi_mc.hackathon3b.core.data.toEntity
import com.richi_mc.hackathon3b.core.data.toModelo
import com.richi_mc.hackathon3b.core.model.AnalisisImagen
import com.richi_mc.hackathon3b.core.model.BorradorCaptura
import com.richi_mc.hackathon3b.core.model.Cadena
import com.richi_mc.hackathon3b.core.model.Captura
import com.richi_mc.hackathon3b.core.model.CodigoEscaneado
import com.richi_mc.hackathon3b.core.model.PrecioDetectado
import com.richi_mc.hackathon3b.core.model.Problema
import com.richi_mc.hackathon3b.core.model.ProductoEsperado
import com.richi_mc.hackathon3b.core.model.Sesion
import com.richi_mc.hackathon3b.core.model.TipoFoto
import com.richi_mc.hackathon3b.core.model.TipoPrecio
import com.richi_mc.hackathon3b.core.network.CapturaMapper
import com.richi_mc.hackathon3b.core.network.ChequeoLocalDto
import com.richi_mc.hackathon3b.core.network.DatosDispositivo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Código escaneado en vivo: chequeos del contrato, código final y persistencia. */
class CapturaMapperEscaneoTest {

    private val dispositivo = DatosDispositivo("Pixel Test", "Android 15", "1.0")

    private fun analisis(
        tipo: TipoFoto,
        codigo: String? = null,
        area: Double? = null,
        problemas: List<Problema> = emptyList(),
    ) = AnalisisImagen(
        tipo, "/tmp/${tipo.contrato}.jpg", 300.0, 130.0, "LECHE 1L", 0.9f, codigo,
        codigo?.let { "EAN_13" }, area, problemas,
    )

    private val sesion = Sesion(
        id = "S1", cadena = Cadena.TRES_B, tienda = "3B Centro", lat = 19.3, lon = -99.1, precisionM = 10f,
        fachadaPath = "/tmp/f.jpg", inicioMillis = 0L, fachadaAnalisis = analisis(TipoFoto.FACHADA),
    )

    private fun escaneo(valor: String = "7501055363056", ok: Boolean = true, formato: String? = "EAN_13") =
        CodigoEscaneado(valor, formato, ok, 1L)

    private fun captura(producto: AnalisisImagen, escaneado: CodigoEscaneado?, etiqueta: AnalisisImagen = analisis(TipoFoto.ETIQUETA)) =
        Captura(
            captureId = "CAP-1", sesionId = "S1", productoEsperado = ProductoEsperado(),
            producto = producto, etiqueta = etiqueta,
            precio = PrecioDetectado(24.9, TipoPrecio.REGULAR, true, "\$24.90", 24.9),
            timestampMillis = 1_795_279_325_000L, codigoEscaneado = escaneado,
        )

    private fun chequeos(c: Captura) = CapturaMapper.toDto(c, sesion, dispositivo).chequeosLocales

    private fun List<ChequeoLocalDto>.unico(nombre: String, foto: String? = null) =
        single { it.nombre == nombre && it.foto == foto }

    @Test
    fun escaneo_valido_sin_codigo_en_foto() {
        val prod = analisis(TipoFoto.PRODUCTO, problemas = listOf(Problema.SIN_CODIGO_BARRAS))
        val c = captura(prod, escaneo())
        val dto = CapturaMapper.toDto(c, sesion, dispositivo)
        assertEquals("7501055363056", dto.codigoBarrasCapturado)
        val cb = dto.chequeosLocales.unico("codigo_barras") // foto = null
        assertEquals("ok", cb.resultado)
        assertEquals("7501055363056", cb.valor)
        assertTrue(cb.detalle!!.contains("escaneado en vivo"))
        assertTrue(cb.detalle!!.contains("EAN_13"))
        assertTrue(dto.chequeosLocales.none { it.nombre == "codigo_barras" && it.foto == "producto" })
        assertTrue(dto.chequeosLocales.none { it.nombre == "codigo_foto_coincide" })
    }

    @Test
    fun escaneo_con_checksum_invalido_falla_y_codigo_final_prefiere_la_foto() {
        val prod = analisis(TipoFoto.PRODUCTO, codigo = "7501055300075", area = 0.05)
        val c = captura(prod, escaneo("1234567890123", ok = false))
        assertEquals("7501055300075", c.codigoBarrasFinal)
        assertEquals("falla", chequeos(c).unico("codigo_barras").resultado)
    }

    @Test
    fun codigo_foto_coincide_ignora_ceros_a_la_izquierda() {
        val prod = analisis(TipoFoto.PRODUCTO, codigo = "7501055363056", area = 0.05)
        val ok = chequeos(captura(prod, escaneo("07501055363056"))).unico("codigo_foto_coincide")
        assertEquals("ok", ok.resultado)
        val mal = chequeos(captura(prod, escaneo("7501055300075"))).unico("codigo_foto_coincide")
        assertEquals("falla", mal.resultado)
    }

    @Test
    fun distancia_lejos_con_escaneo_valido_es_duda() {
        val prod = analisis(
            TipoFoto.PRODUCTO, codigo = "7501055363056", area = 0.005, problemas = listOf(Problema.DEMASIADO_LEJOS),
        )
        val d = chequeos(captura(prod, escaneo())).unico("distancia", "producto")
        assertEquals("duda", d.resultado)
        assertTrue(d.detalle!!.contains("escaneo"))
        // Sin escaneo sigue siendo falla.
        assertEquals("falla", chequeos(captura(prod, null)).unico("distancia", "producto").resultado)
        // Con escaneo inválido tampoco se perdona.
        assertEquals("falla", chequeos(captura(prod, escaneo(ok = false))).unico("distancia", "producto").resultado)
    }

    @Test
    fun sin_escaneo_se_mantiene_el_chequeo_de_la_foto() {
        val prod = analisis(TipoFoto.PRODUCTO, codigo = "7501055363056", area = 0.05)
        val ch = chequeos(captura(prod, null))
        assertEquals("ok", ch.unico("codigo_barras", "producto").resultado)
        assertTrue(ch.none { it.nombre == "codigo_foto_coincide" })
    }

    @Test
    fun problemas_efectivos_y_requiere_escaneo_en_el_borrador() {
        val prod = analisis(TipoFoto.PRODUCTO, problemas = listOf(Problema.SIN_CODIGO_BARRAS, Problema.BORROSA))
        val b = BorradorCaptura(producto = prod)
        assertTrue(b.requiereEscaneo)
        assertEquals(listOf(Problema.SIN_CODIGO_BARRAS, Problema.BORROSA), b.problemasProducto)
        val conEscaneo = b.copy(codigoEscaneado = escaneo())
        assertEquals(listOf(Problema.BORROSA), conEscaneo.problemasProducto)
    }

    @Test
    fun persistencia_guarda_y_lee_el_codigo_escaneado() {
        val prod = analisis(TipoFoto.PRODUCTO, codigo = "7501055363056", area = 0.05)
        val e = escaneo(formato = "UPC_A")
        val leida = captura(prod, e).toEntity().toModelo()
        assertEquals(e, leida.codigoEscaneado)
        assertNull(captura(prod, null).toEntity().toModelo().codigoEscaneado)
    }
}
