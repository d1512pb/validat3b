package com.richi_mc.hackathon3b.core

import com.richi_mc.hackathon3b.core.data.toEntity
import com.richi_mc.hackathon3b.core.data.toModelo
import com.richi_mc.hackathon3b.core.model.AnalisisImagen
import com.richi_mc.hackathon3b.core.model.Cadena
import com.richi_mc.hackathon3b.core.model.CajaPx
import com.richi_mc.hackathon3b.core.model.Captura
import com.richi_mc.hackathon3b.core.model.CandidatoPrecio
import com.richi_mc.hackathon3b.core.model.FormaPrecio
import com.richi_mc.hackathon3b.core.model.EtiquetaImagen
import com.richi_mc.hackathon3b.core.model.FuenteDescripcion
import com.richi_mc.hackathon3b.core.model.LineaOcr
import com.richi_mc.hackathon3b.core.model.PrecioDetectado
import com.richi_mc.hackathon3b.core.model.Problema
import com.richi_mc.hackathon3b.core.model.ProductoEsperado
import com.richi_mc.hackathon3b.core.model.Sesion
import com.richi_mc.hackathon3b.core.model.TipoFoto
import com.richi_mc.hackathon3b.core.model.TipoPrecio
import com.richi_mc.hackathon3b.core.network.CapturaEntradaDto
import com.richi_mc.hackathon3b.core.network.CapturaMapper
import com.richi_mc.hackathon3b.core.network.ChequeoLocalDto
import com.richi_mc.hackathon3b.core.network.ContratoJson
import com.richi_mc.hackathon3b.core.network.DatosDispositivo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** producto_esperado con detectado + final, etiquetas_imagen, chequeos por etiquetas y persistencia. */
class CapturaMapperProductoEsperadoTest {

    private val dispositivo = DatosDispositivo("Pixel Test", "Android 15", "1.0")

    private fun et(texto: String, confianza: Float, i: Int = 0) = EtiquetaImagen(texto, confianza, i)

    private fun analisis(
        tipo: TipoFoto,
        etiquetas: List<EtiquetaImagen> = emptyList(),
        problemas: List<Problema> = emptyList(),
        codigo: String? = null,
    ) = AnalisisImagen(
        tipo, "/tmp/${tipo.contrato}.jpg", 300.0, 130.0, "LECHE 1L", 0.9f, codigo,
        codigo?.let { "EAN_13" }, codigo?.let { 0.05 }, problemas,
        etiquetas = etiquetas,
        lineasOcr = listOf(LineaOcr("LECHE ENTERA 1L", 40, 100)),
    )

    private val fachada = analisis(TipoFoto.FACHADA, listOf(et("Building", 0.884f)))

    private fun sesion(f: AnalisisImagen? = fachada) = Sesion(
        id = "S1", cadena = Cadena.TRES_B, tienda = "3B Centro", lat = 19.3, lon = -99.1, precisionM = 10f,
        fachadaPath = "/tmp/f.jpg", inicioMillis = 0L, fachadaAnalisis = f,
    )

    private val esperado = ProductoEsperado(
        descripcion = "LECHE ENTERA 1 L", codigoBarras = "7501055363056", esContratipo = true,
        precio = 25.5, descripcionDetectada = "LECHE ENTERA 1L", precioDetectado = 24.9,
        fuente = FuenteDescripcion.ETIQUETA_RACK, confianzaSugerencia = 0.8f, editadoPorUsuario = true,
    )

    private fun captura(
        producto: AnalisisImagen = analisis(TipoFoto.PRODUCTO, listOf(et("Bottle", 0.912f), et("Drink", 0.8f)), codigo = "7501055363056"),
        etiqueta: AnalisisImagen = analisis(TipoFoto.ETIQUETA, listOf(et("Text", 0.83f))),
        pe: ProductoEsperado = esperado,
        tipo: TipoPrecio = TipoPrecio.OFERTA,
    ) = Captura(
        captureId = "CAP-1", sesionId = "S1", productoEsperado = pe, producto = producto, etiqueta = etiqueta,
        precio = PrecioDetectado(24.9, tipo, true, "\$24.90", 24.9), timestampMillis = 1_795_279_325_000L,
    )

    private fun dto(c: Captura = captura(), s: Sesion = sesion()) = CapturaMapper.toDto(c, s, dispositivo)

    private fun List<ChequeoLocalDto>.unico(nombre: String, foto: String? = null) =
        single { it.nombre == nombre && it.foto == foto }

    private fun json(d: CapturaEntradaDto): JsonObject =
        Json.parseToJsonElement(ContratoJson.encodeToString(CapturaEntradaDto.serializer(), d)).jsonObject

    // ------------------------------------------------------- producto_esperado

    @Test
    fun producto_esperado_manda_final_y_detectado() {
        val p = dto().productoEsperado
        assertEquals("LECHE ENTERA 1 L", p.descripcion)
        assertEquals(25.5, p.precio!!, 0.0)
        assertEquals(true, p.editadoPorUsuario)
        assertEquals("LECHE ENTERA 1L", p.descripcionDetectada)
        assertEquals(24.9, p.precioDetectado!!, 0.0)
        assertEquals("OFERTA", p.tipoPrecio)
        assertEquals("ETIQUETA_RACK", p.fuenteDescripcion)
        assertEquals(0.8, p.confianzaSugerencia!!, 1e-6)
        assertEquals("7501055363056", p.codigoBarras)
        assertEquals(true, p.esContratipo)
    }

    @Test
    fun producto_esperado_json_trae_los_campos_nuevos() {
        val pe = json(dto())["producto_esperado"]!!.jsonObject
        listOf(
            "descripcion", "precio", "editado_por_usuario", "descripcion_detectada", "precio_detectado",
            "tipo_precio", "fuente_descripcion", "confianza_sugerencia", "codigo_barras", "es_contratipo",
        ).forEach { assertTrue("falta $it en $pe", it in pe) }
        assertEquals("LECHE ENTERA 1L", pe["descripcion_detectada"]!!.jsonPrimitive.content)
        assertEquals("25.5", pe["precio"]!!.jsonPrimitive.content)
    }

    @Test
    fun sin_precio_ni_sugerencia_no_manda_nulos() {
        val pe = ProductoEsperado(descripcion = "ALGO", fuente = FuenteDescripcion.MANUAL)
        val texto = ContratoJson.encodeToString(CapturaEntradaDto.serializer(), dto(captura(pe = pe)))
        assertFalse(texto, texto.contains("null"))
        val p = dto(captura(pe = pe)).productoEsperado
        assertNull(p.precio); assertNull(p.descripcionDetectada); assertNull(p.confianzaSugerencia)
        assertEquals("MANUAL", p.fuenteDescripcion)
        assertEquals(false, p.editadoPorUsuario)
    }

    // ------------------------------------------------- confianza y candidatos del precio

    private fun candidato(valor: Double, forma: FormaPrecio, punt: Float) = CandidatoPrecio(
        valor, forma, "\$$valor", CajaPx(10, 20, 110, 80), 0.9f, true, false, false, punt,
    )

    private fun capturaConPrecio(precio: PrecioDetectado) = captura().copy(precio = precio)

    @Test
    fun manda_confianza_y_candidatos_del_precio_con_dos_decimales() {
        val precio = PrecioDetectado(
            24.9, TipoPrecio.REGULAR, false, "\$24.90", confianza = 0.8249f,
            candidatos = listOf(
                candidato(24.9, FormaPrecio.CENTAVOS_MISMA_LINEA, 0.8249f),
                candidato(2490.0, FormaPrecio.ENTERO, 0.314f),
            ),
        )
        val p = dto(capturaConPrecio(precio)).productoEsperado
        assertEquals(0.82, p.confianzaPrecio!!, 0.0)
        val cands = p.precioCandidatos!!
        assertEquals(2, cands.size)
        assertEquals(24.9, cands[0].valor, 0.0)
        assertEquals("CENTAVOS_MISMA_LINEA", cands[0].forma)
        assertEquals(0.82, cands[0].puntuacion, 0.0)
        assertEquals("ENTERO", cands[1].forma)
        assertEquals(0.31, cands[1].puntuacion, 0.0)

        val pe = json(dto(capturaConPrecio(precio)))["producto_esperado"]!!.jsonObject
        assertEquals("0.82", pe["confianza_precio"]!!.jsonPrimitive.content)
        val primero = pe["precio_candidatos"]!!.jsonArray.first().jsonObject
        assertEquals(setOf("valor", "forma", "puntuacion"), primero.keys)
        assertEquals("CENTAVOS_MISMA_LINEA", primero["forma"]!!.jsonPrimitive.content)
    }

    @Test
    fun sin_confianza_ni_candidatos_no_manda_los_campos_del_precio() {
        val d = dto() // PrecioDetectado sin confianza ni candidatos
        assertNull(d.productoEsperado.confianzaPrecio)
        assertNull(d.productoEsperado.precioCandidatos)
        val pe = json(d)["producto_esperado"]!!.jsonObject
        assertFalse("confianza_precio" in pe)
        assertFalse("precio_candidatos" in pe)
    }

    @Test
    fun persistencia_ida_y_vuelta_de_confianza_y_candidatos() {
        val precio = PrecioDetectado(
            24.9, TipoPrecio.OFERTA, true, "\$24.90", 12.0, 0.45f,
            listOf(candidato(24.9, FormaPrecio.DECIMAL, 0.45f), candidato(25.0, FormaPrecio.ENTERO, 0.2f)),
        )
        val v = capturaConPrecio(precio).toEntity().toModelo()
        assertEquals(precio, v.precio)
    }

    @Test
    fun precio_viejo_sin_confianza_ni_candidatos_se_lee_con_defaults() {
        val viejo = """{"valor":24.9,"tipo":"REGULAR","tienePrecioUnitario":true,"textoFuente":"$24.90"}"""
        val m = captura().toEntity().copy(precioJson = viejo).toModelo()
        assertEquals(24.9, m.precio.valor!!, 0.0)
        assertNull(m.precio.confianza)
        assertTrue(m.precio.candidatos.isEmpty())
        assertFalse(m.precio.requiereRevision)
    }

    @Test
    fun candidato_con_forma_desconocida_cae_a_entero() {
        val raro = """{"valor":24.9,"candidatos":[{"valor":24.9,"forma":"NUEVA_FORMA"}]}"""
        val m = captura().toEntity().copy(precioJson = raro).toModelo()
        assertEquals(FormaPrecio.ENTERO, m.precio.candidatos.single().forma)
    }

    @Test
    fun linea_ocr_conserva_la_caja_completa_y_filas_viejas_siguen_leyendose() {
        val a = analisis(TipoFoto.ETIQUETA).copy(
            lineasOcr = listOf(LineaOcr("\$24.90", CajaPx(30, 100, 400, 190))),
        )
        val v = captura(etiqueta = a).toEntity().toModelo()
        assertEquals(CajaPx(30, 100, 400, 190), v.etiqueta.lineasOcr.single().caja)
        val viejo = """{"tipo":"ETIQUETA","path":"/e.jpg","nitidez":1.0,"brillo":1.0,"textoOcr":"X",""" +
            """"lineasOcr":[{"texto":"A","alturaPx":40,"top":100}]}"""
        val m = captura().toEntity().copy(etiquetaJson = viejo).toModelo()
        assertEquals(CajaPx(0, 100, 0, 140), m.etiqueta.lineasOcr.single().caja)
    }

    // -------------------------------------------------------- etiquetas_imagen

    @Test
    fun etiquetas_imagen_por_foto_con_confianza_redondeada() {
        val e = dto().etiquetasImagen!!
        assertEquals("Building", e.fachada.single().etiqueta)
        assertEquals(0.88, e.fachada.single().confianza, 0.0)
        assertEquals(listOf("Bottle", "Drink"), e.producto.map { it.etiqueta })
        assertEquals(0.91, e.producto.first().confianza, 0.0)
        assertEquals(0.83, e.etiqueta.single().confianza, 0.0)

        val raiz = json(dto())["etiquetas_imagen"]!!.jsonObject
        assertEquals(setOf("fachada", "producto", "etiqueta"), raiz.keys)
        val primera = raiz["producto"]!!.jsonArray.first().jsonObject
        assertEquals("Bottle", primera["etiqueta"]!!.jsonPrimitive.content)
        assertEquals("0.91", primera["confianza"]!!.jsonPrimitive.content)
    }

    @Test
    fun etiquetas_imagen_se_omite_si_las_tres_listas_estan_vacias() {
        val c = captura(analisis(TipoFoto.PRODUCTO), analisis(TipoFoto.ETIQUETA))
        val d = dto(c, sesion(analisis(TipoFoto.FACHADA)))
        assertNull(d.etiquetasImagen)
        assertFalse("etiquetas_imagen" in json(d))
        // Sin fachada analizada tampoco aporta.
        assertNull(dto(c, sesion(null)).etiquetasImagen)
    }

    @Test
    fun etiquetas_imagen_parcial_manda_listas_vacias_para_las_demas() {
        val c = captura(analisis(TipoFoto.PRODUCTO, listOf(et("Wall", 0.5f))), analisis(TipoFoto.ETIQUETA))
        val e = dto(c, sesion(null)).etiquetasImagen!!
        assertTrue(e.fachada.isEmpty()); assertTrue(e.etiqueta.isEmpty())
        assertEquals(1, e.producto.size)
    }

    // ------------------------------------------------------ chequeos por etiquetas

    @Test
    fun chequeos_ok_con_etiquetas() {
        val ch = dto().chequeosLocales
        val pv = ch.unico("producto_visible", "producto")
        assertEquals("ok", pv.resultado)
        assertEquals("Bottle", pv.valor)
        assertEquals(0.912, pv.puntaje!!, 1e-3)
        val ft = ch.unico("fachada_tienda", "fachada")
        assertEquals("ok", ft.resultado)
        assertEquals("Building", ft.valor)
        assertEquals("ok", ch.unico("pantalla", "producto").resultado)
        assertEquals("ok", ch.unico("pantalla", "etiqueta").resultado)
        assertEquals("ok", ch.unico("pantalla", "fachada").resultado)
    }

    @Test
    fun producto_no_visible_y_fachada_dudosa_son_duda() {
        val prod = analisis(TipoFoto.PRODUCTO, listOf(et("Wall", 0.8f)), listOf(Problema.PRODUCTO_NO_VISIBLE), "7501055363056")
        val fach = analisis(TipoFoto.FACHADA, listOf(et("Wall", 0.7f)), listOf(Problema.FACHADA_NO_PARECE_TIENDA))
        val ch = dto(captura(producto = prod), sesion(fach)).chequeosLocales
        val pv = ch.unico("producto_visible", "producto")
        assertEquals("duda", pv.resultado)
        assertNull(pv.puntaje) // ninguna etiqueta del grupo producto
        assertEquals("Wall", pv.valor)
        assertEquals("duda", ch.unico("fachada_tienda", "fachada").resultado)
    }

    @Test
    fun foto_de_pantalla_es_duda_con_puntaje_de_la_etiqueta_de_pantalla() {
        val prod = analisis(
            TipoFoto.PRODUCTO, listOf(et("Bottle", 0.9f), et("Mobile phone", 0.85f)),
            listOf(Problema.POSIBLE_FOTO_DE_PANTALLA), "7501055363056",
        )
        val p = dto(captura(producto = prod)).chequeosLocales.unico("pantalla", "producto")
        assertEquals("duda", p.resultado)
        assertEquals(0.85, p.puntaje!!, 1e-6)
        assertEquals("Mobile phone", p.valor)
    }

    @Test
    fun sin_etiquetas_no_hay_chequeos_de_etiquetas() {
        val c = captura(analisis(TipoFoto.PRODUCTO, codigo = "7501055363056"), analisis(TipoFoto.ETIQUETA))
        val ch = dto(c, sesion(analisis(TipoFoto.FACHADA))).chequeosLocales
        listOf("producto_visible", "fachada_tienda", "pantalla").forEach { n ->
            assertEquals(n, 0, ch.count { it.nombre == n })
        }
    }

    @Test
    fun chequeos_de_etiquetas_solo_para_las_fotos_que_tienen() {
        val c = captura(analisis(TipoFoto.PRODUCTO, listOf(et("Bottle", 0.9f))), analisis(TipoFoto.ETIQUETA))
        val ch = dto(c, sesion(null)).chequeosLocales
        assertEquals(listOf("producto"), ch.filter { it.nombre == "pantalla" }.map { it.foto })
        assertEquals(1, ch.count { it.nombre == "producto_visible" })
        assertEquals(0, ch.count { it.nombre == "fachada_tienda" })
    }

    // ------------------------------------------------------------- persistencia

    @Test
    fun persistencia_ida_y_vuelta_de_producto_esperado_etiquetas_y_lineas() {
        val c = captura()
        val e = c.toEntity()
        assertEquals("ETIQUETA_RACK", e.espFuente)
        val v = e.toModelo()
        assertEquals(c.productoEsperado, v.productoEsperado)
        assertEquals(c.producto.etiquetas, v.producto.etiquetas)
        assertEquals(c.producto.lineasOcr, v.producto.lineasOcr)
        assertEquals(c.etiqueta.etiquetas, v.etiqueta.etiquetas)
    }

    @Test
    fun persistencia_de_sesion_conserva_etiquetas_de_la_fachada() {
        val s = sesion()
        assertEquals(fachada.etiquetas, s.toEntity().toModelo().fachadaAnalisis!!.etiquetas)
    }

    @Test
    fun fila_vieja_sin_etiquetas_ni_lineas_se_lee_con_defaults() {
        val viejo = """{"tipo":"PRODUCTO","path":"/p.jpg","nitidez":200.0,"brillo":120.0,"textoOcr":"X"}"""
        val e = captura().toEntity().copy(productoJson = viejo, espFuente = "DESCONOCIDA")
        val m = e.toModelo()
        assertTrue(m.producto.etiquetas.isEmpty())
        assertTrue(m.producto.lineasOcr.isEmpty())
        assertNull(m.productoEsperado.fuente) // valor desconocido → null, no truena
        assertNotNull(m.productoEsperado.descripcion)
    }
}
