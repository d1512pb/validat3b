package com.richi_mc.hackathon3b.core

import com.richi_mc.hackathon3b.core.model.TipoFoto
import com.richi_mc.hackathon3b.core.network.CapturaMapper
import com.richi_mc.hackathon3b.core.network.ContratoJson
import com.richi_mc.hackathon3b.core.network.DatosDispositivo
import com.richi_mc.hackathon3b.core.network.RespuestaCapturaDto
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturaMapperContratoTest {

    private fun ejemplo(nombre: String): String {
        val f = File("../../contratos/ejemplos/$nombre")
        check(f.isFile) { "No se encontró ${f.absolutePath}" }
        return f.readText()
    }

    private fun respuesta(json: String) = ContratoJson.decodeFromString(RespuestaCapturaDto.serializer(), json)

    private fun conReason(decision: String, reason: String?, validaciones: String = "{}") = CapturaMapper.toResultado(
        respuesta(
            """{"capture_id":"A","decision":"$decision","reason_code":${reason?.let { "\"$it\"" }},
            "validaciones":$validaciones}"""
        )
    )

    // ------------------------------------------------------------ dispositivo

    @Test
    fun dispositivo_no_repite_la_marca() {
        assertEquals("Samsung Galaxy A15", DatosDispositivo.nombre("Samsung", "Galaxy A15"))
        assertEquals("Google Pixel 8", DatosDispositivo.nombre("Google", "Pixel 8"))
        assertEquals("Xiaomi Redmi Note", DatosDispositivo.nombre("Xiaomi", "Redmi Note"))
        assertEquals("Samsung SM-A155M", DatosDispositivo.nombre("samsung", "Samsung SM-A155M"))
        assertEquals("motorola moto g", DatosDispositivo.nombre("Motorola", "motorola moto g"))
        assertEquals("Pixel", DatosDispositivo.nombre(null, "Pixel"))
        assertEquals("Google", DatosDispositivo.nombre("Google", ""))
        assertNull(DatosDispositivo.nombre(null, null))
    }

    // -------------------------------------------------------------- ubicación

    @Test
    fun ubicacion_valida() {
        val u = CapturaMapper.ubicacionDto(19.35, -99.09, 12f)!!
        assertEquals(12.0, u.precisionM!!, 0.0)
    }

    @Test
    fun ubicacion_nula_en_cero_cero_fuera_de_rango_o_incompleta() {
        assertNull(CapturaMapper.ubicacionDto(0.0, 0.0, 5f))
        assertNull(CapturaMapper.ubicacionDto(91.0, 10.0, 5f))
        assertNull(CapturaMapper.ubicacionDto(-90.5, 10.0, 5f))
        assertNull(CapturaMapper.ubicacionDto(10.0, 181.0, 5f))
        assertNull(CapturaMapper.ubicacionDto(10.0, -180.1, 5f))
        assertNull(CapturaMapper.ubicacionDto(Double.NaN, 10.0, 5f))
        assertNull(CapturaMapper.ubicacionDto(null, 10.0, 5f))
        assertNull(CapturaMapper.ubicacionDto(10.0, null, 5f))
    }

    @Test
    fun ubicacion_borde_valido_y_precision_negativa_es_null() {
        assertEquals(90.0, CapturaMapper.ubicacionDto(90.0, 180.0, null)!!.latitud, 0.0)
        assertNull(CapturaMapper.ubicacionDto(0.0, 10.0, null)!!.precisionM)
        assertNull(CapturaMapper.ubicacionDto(19.0, -99.0, -1f)!!.precisionM)
        assertEquals(0.0, CapturaMapper.ubicacionDto(19.0, -99.0, 0f)!!.precisionM!!, 0.0)
    }

    // -------------------------------------------------------------- respuesta

    @Test
    fun decision_desconocida_es_revision_manual() {
        val r = CapturaMapper.toResultado(respuesta("""{"capture_id":"A","decision":"quizas"}"""))
        assertEquals("revision_manual", r.decision)
    }

    @Test
    fun rechazada_del_contrato_llena_validaciones_y_fotos() {
        val r = CapturaMapper.toResultado(respuesta(ejemplo("respuesta_rechazada.json")))
        assertEquals("rechazada", r.decision)
        assertEquals("falla", r.validaciones["producto"])
        assertEquals(4, r.validaciones.size)
        assertEquals(listOf(TipoFoto.PRODUCTO), r.fotosARepetir)
        assertEquals("regular", r.tipoPrecio)
        assertEquals(listOf("Revisa que sea el producto o contratipo esperado."), r.mensajesCapturista)
    }

    @Test
    fun revision_manual_del_contrato_pide_fachada() {
        val r = CapturaMapper.toResultado(respuesta(ejemplo("respuesta_revision_manual.json")))
        assertTrue(TipoFoto.FACHADA in r.fotosARepetir)
    }

    @Test
    fun aprobada_no_pide_repetir_nada() {
        val r = CapturaMapper.toResultado(respuesta(ejemplo("respuesta_aprobada.json")))
        assertTrue(r.fotosARepetir.isEmpty())
        assertEquals(4, r.validaciones.size)
        assertEquals(42.5, r.precioDetectado!!, 0.0)
    }

    @Test
    fun aprobada_con_validacion_en_falla_tampoco_pide_repetir() {
        val r = conReason("aprobada", null, """{"producto":"falla"}""")
        assertTrue(r.fotosARepetir.isEmpty())
    }

    @Test
    fun reason_codes_a_fotos() {
        fun fotos(reason: String) = conReason("rechazada", reason).fotosARepetir
        listOf("CONTRATIPO_INVALIDO", "PRODUCTO_NO_COINCIDE", "DESCRIPCION_INCOMPLETA", "PRODUCTO_NO_VISIBLE")
            .forEach { assertEquals(it, listOf(TipoFoto.PRODUCTO), fotos(it)) }
        assertEquals(listOf(TipoFoto.FACHADA), fotos("FACHADA_INCORRECTA"))
        assertEquals(listOf(TipoFoto.ETIQUETA), fotos("SIN_ETIQUETA_PRECIO"))
        assertEquals(listOf(TipoFoto.ETIQUETA), fotos("SIN_PRECIO_UNITARIO"))
        listOf("CAPTURA_LEJANA", "IMAGEN_BORROSA", "FOTO_DE_PANTALLA", "CODIGO_NUEVO_RARO")
            .forEach { assertTrue(it, fotos(it).isEmpty()) }
    }

    @Test
    fun validaciones_en_falla_se_unen_sin_duplicados_y_en_orden() {
        val r = conReason(
            "rechazada", "SIN_ETIQUETA_PRECIO",
            """{"tienda_fachada":"falla","producto":"falla","codigo_barras":"falla","evidencia_precio":"falla"}""",
        )
        assertEquals(listOf(TipoFoto.FACHADA, TipoFoto.PRODUCTO, TipoFoto.ETIQUETA), r.fotosARepetir)
    }

    @Test
    fun reason_lejana_usa_la_validacion_en_falla() {
        val r = conReason("rechazada", "CAPTURA_LEJANA", """{"evidencia_precio":"falla","producto":"ok"}""")
        assertEquals(listOf(TipoFoto.ETIQUETA), r.fotosARepetir)
    }

    @Test
    fun codigo_barras_en_falla_apunta_a_producto() {
        val r = conReason("revision_manual", null, """{"codigo_barras":"falla"}""")
        assertEquals(listOf(TipoFoto.PRODUCTO), r.fotosARepetir)
    }

    @Test
    fun traza_llena_precio_y_promocion() {
        val r = CapturaMapper.toResultado(
            respuesta(
                """{"capture_id":"A","decision":"aprobada","traza":{"tipo_precio":"oferta",
                "precio_unitario_detectado":12.5,"promocion":"2x${'$'}50"}}"""
            )
        )
        assertEquals("oferta", r.tipoPrecio)
        assertEquals(12.5, r.precioUnitarioDetectado!!, 0.0)
        assertEquals("2x\$50", r.promocion)
    }

    @Test
    fun mensajes_capturista_cae_a_explicacion() {
        val r = CapturaMapper.toResultado(respuesta("""{"capture_id":"A","decision":"rechazada","explicacion":"Motivo."}"""))
        assertEquals(listOf("Motivo."), r.mensajesCapturista)
    }
}
