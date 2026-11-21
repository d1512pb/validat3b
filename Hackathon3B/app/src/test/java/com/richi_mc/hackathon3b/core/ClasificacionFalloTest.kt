package com.richi_mc.hackathon3b.core

import com.richi_mc.hackathon3b.core.data.clasificarFallo
import com.richi_mc.hackathon3b.core.data.textoSalud
import com.richi_mc.hackathon3b.core.model.EstadoEnvio
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClasificacionFalloTest {

    private fun error(codigo: String, mensaje: String, detalles: String = "[]") =
        """{"error":{"codigo":"$codigo","mensaje":"$mensaje","detalles":$detalles}}"""

    @Test
    fun http_401_es_fallida_con_aviso_de_clave() {
        val (estado, msg) = clasificarFallo(401, error("NO_AUTORIZADO", "Falta la clave."))
        assertEquals(EstadoEnvio.FALLIDA, estado)
        assertTrue(msg, msg.startsWith("Revisa la clave de acceso del servidor."))
        assertTrue(msg, msg.contains("Falta la clave. (NO_AUTORIZADO)"))
    }

    @Test
    fun http_422_es_fallida_con_detalles() {
        val (estado, msg) = clasificarFallo(
            422, error("CAPTURA_INVALIDA", "No cumple el contrato.", """["producto_esperado: Field required"]""")
        )
        assertEquals(EstadoEnvio.FALLIDA, estado)
        assertTrue(msg, msg.startsWith("Error de la app al armar la captura, queda para revisión manual."))
        assertTrue(msg, msg.contains("No cumple el contrato. (CAPTURA_INVALIDA): producto_esperado: Field required"))
    }

    @Test
    fun http_400_y_415_son_fallidas_con_su_prefijo() {
        val (e400, m400) = clasificarFallo(400, error("CAPTURA_NO_ES_JSON", "No es JSON."))
        assertEquals(EstadoEnvio.FALLIDA, e400)
        assertTrue(m400.startsWith("Error de la app al armar la captura"))
        val (e415, m415) = clasificarFallo(415, error("TIPO_NO_SOPORTADO", "Tipo no soportado."))
        assertEquals(EstadoEnvio.FALLIDA, e415)
        assertTrue(m415.startsWith("Formato de foto no aceptado, queda para revisión manual."))
    }

    @Test
    fun codigo_desconocido_usa_solo_el_mensaje() {
        val (estado, msg) = clasificarFallo(400, error("FOTO_INVALIDA", "La foto está dañada."))
        assertEquals(EstadoEnvio.FALLIDA, estado)
        assertTrue(msg, msg.endsWith("La foto está dañada."))
        assertFalse(msg, msg.contains("FOTO_INVALIDA"))
    }

    @Test
    fun otro_4xx_es_fallida_y_sin_error_dto_menciona_el_codigo_http() {
        val (estado, msg) = clasificarFallo(404, "<html>no</html>")
        assertEquals(EstadoEnvio.FALLIDA, estado)
        assertTrue(msg, msg.contains("404"))
    }

    @Test
    fun http_5xx_es_error_reintentable() {
        val (e500, m500) = clasificarFallo(500, "boom")
        assertEquals(EstadoEnvio.ERROR, e500)
        assertEquals("El servidor no respondió bien (HTTP 500), se reintentará", m500)
        val (e503, m503) = clasificarFallo(503, null)
        assertEquals(EstadoEnvio.ERROR, e503)
        assertTrue(m503.contains("503"))
        // Aunque traiga un ErrorDto, un 5xx sigue siendo reintentable.
        assertEquals(EstadoEnvio.ERROR, clasificarFallo(502, error("X", "y")).first)
    }

    @Test
    fun http_413_es_error_si_sigue_fallando_tras_recomprimir() {
        val (estado, msg) = clasificarFallo(413, error("FOTO_MUY_GRANDE", "Foto muy grande."))
        assertEquals(EstadoEnvio.ERROR, estado)
        assertTrue(msg, msg.contains("413"))
    }

    @Test
    fun texto_de_salud() {
        val obj = Json.parseToJsonElement("""{"estado":"ok","version":"0.1.0"}""").jsonObject
        assertEquals("Servidor en línea, modo simulado, estado: ok, versión 0.1.0", textoSalud(true, obj))
        assertEquals("Servidor en línea", textoSalud(false, Json.parseToJsonElement("{}").jsonObject))
        assertEquals("Servidor en línea", textoSalud(false, null))
    }
}
