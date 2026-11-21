package com.richi_mc.hackathon3b.core

import com.richi_mc.hackathon3b.core.network.CapturaEntradaDto
import com.richi_mc.hackathon3b.core.network.ChequeoLocalDto
import com.richi_mc.hackathon3b.core.network.ContratoJson
import com.richi_mc.hackathon3b.core.network.ErrorDto
import com.richi_mc.hackathon3b.core.network.ErroresApi
import com.richi_mc.hackathon3b.core.network.MetadatosAppDto
import com.richi_mc.hackathon3b.core.network.ProductoEsperadoDto
import com.richi_mc.hackathon3b.core.network.RespuestaCapturaDto
import com.richi_mc.hackathon3b.core.network.CapturaMapper
import com.richi_mc.hackathon3b.core.network.UbicacionDto
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContratoSerializacionTest {

    /** Los tests JVM corren con cwd = módulo `app`; los ejemplos viven en <monorepo>/contratos. */
    private fun ejemplo(nombre: String): String {
        val f = File("../../contratos/ejemplos/$nombre")
        check(f.isFile) { "No se encontró ${f.absolutePath}" }
        return f.readText()
    }

    private fun parse(s: String): JsonElement = Json.parseToJsonElement(s)

    private val esperada = CapturaEntradaDto(
        captureId = "CAP-000123",
        codigoBarrasCapturado = "7501055363056",
        productoEsperado = ProductoEsperadoDto(
            sku = "3B-000451", descripcion = "Refresco de cola", marca = "Coca-Cola",
            contenido = "600 ml", codigoBarras = "7501055363056", esContratipo = false,
        ),
        cadena = "Bodega Aurrerá",
        tienda = "Bodega Aurrerá Iztapalapa Centro",
        tiendaId = "BA-0187",
        fechaHoraCaptura = "2026-11-21T10:42:05-06:00",
        metadatosApp = MetadatosAppDto(
            dispositivo = "Samsung Galaxy A15", sistemaOperativo = "Android 15", versionApp = "1.0.0",
            capturistaId = "CAPT-017",
            ubicacion = UbicacionDto(19.3574, -99.0927, 12.0),
        ),
        chequeosLocales = listOf(
            ChequeoLocalDto("nitidez", "producto", "ok", puntaje = 0.91),
            ChequeoLocalDto("codigo_barras", "producto", "ok", valor = "7501055363056"),
        ),
    )

    @Test
    fun entrada_codificada_equivale_al_ejemplo_del_contrato() {
        val codificado = ContratoJson.encodeToString(CapturaEntradaDto.serializer(), esperada)
        assertEquals(parse(ejemplo("captura_entrada.json")), parse(codificado))
    }

    @Test
    fun entrada_del_ejemplo_se_decodifica_al_dto() {
        val dto = ContratoJson.decodeFromString(CapturaEntradaDto.serializer(), ejemplo("captura_entrada.json"))
        assertEquals(esperada, dto)
    }

    @Test
    fun nulos_no_se_serializan() {
        val dto = CapturaEntradaDto(
            captureId = "X", productoEsperado = ProductoEsperadoDto(), cadena = "3B", tienda = "T",
            fechaHoraCaptura = "2026-11-21T10:42:05-06:00",
        )
        val texto = ContratoJson.encodeToString(CapturaEntradaDto.serializer(), dto)
        assertTrue(texto, !texto.contains("null"))
        assertTrue(texto, texto.contains("\"version_contrato\":\"0.1.0\""))
        assertTrue(texto, texto.contains("\"es_contratipo\":false"))
    }

    @Test
    fun respuesta_aprobada() {
        val r = ContratoJson.decodeFromString(RespuestaCapturaDto.serializer(), ejemplo("respuesta_aprobada.json"))
        assertEquals("aprobada", r.decision)
        assertNull(r.reasonCode)
        assertTrue(r.traza!!.avisos.isEmpty())
        assertEquals("simulado", r.traza!!.capaDecisiva)
        assertEquals("regular", r.traza!!.tipoPrecio)
        assertEquals(42.5, r.precioDetectado!!, 0.0)
        val res = CapturaMapper.toResultado(r)
        assertEquals("aprobada", res.decision)
        assertEquals("simulado", res.capaDecisiva)
        assertEquals("7501055363056", res.codigoBarrasDetectado)
    }

    @Test
    fun respuesta_rechazada() {
        val r = ContratoJson.decodeFromString(RespuestaCapturaDto.serializer(), ejemplo("respuesta_rechazada.json"))
        assertEquals("rechazada", r.decision)
        assertEquals("PRODUCTO_NO_COINCIDE", r.reasonCode)
        assertEquals("falla", r.validaciones!!.producto)
        assertEquals(listOf("Revisa que sea el producto o contratipo esperado."), r.traza!!.avisos)
        assertEquals(r.traza!!.avisos, CapturaMapper.toResultado(r).avisos)
    }

    @Test
    fun respuesta_revision_manual() {
        val r = ContratoJson.decodeFromString(RespuestaCapturaDto.serializer(), ejemplo("respuesta_revision_manual.json"))
        assertEquals("revision_manual", r.decision)
        assertEquals("FACHADA_INCORRECTA", r.reasonCode)
        assertEquals("falla", r.validaciones!!.tiendaFachada)
        assertEquals(1, r.traza!!.avisos.size)
    }

    @Test
    fun respuesta_tolera_campos_desconocidos_y_faltantes() {
        val r = ContratoJson.decodeFromString(
            RespuestaCapturaDto.serializer(),
            """{"capture_id":"A","decision":"aprobada","campo_nuevo":1,"traza":{"x":2}}"""
        )
        assertEquals("aprobada", r.decision)
        assertTrue(r.traza!!.avisos.isEmpty())
    }

    @Test
    fun error_422_se_convierte_en_mensaje_legible() {
        val e = ContratoJson.decodeFromString(ErrorDto.serializer(), ejemplo("error_422.json"))
        assertEquals("CAPTURA_INVALIDA", e.error.codigo)
        val msg = ErroresApi.mensaje(422, ejemplo("error_422.json"))
        assertTrue(msg, msg.contains("El campo captura no cumple el contrato."))
        assertTrue(msg, msg.contains("CAPTURA_INVALIDA"))
        assertTrue(msg, msg.contains("producto_esperado: Field required"))
    }

    @Test
    fun error_sin_json_usa_mensaje_por_codigo_http() {
        assertTrue(ErroresApi.mensaje(500, "<html>").contains("500"))
        assertTrue(ErroresApi.mensaje(401, null).contains("401"))
    }
}
