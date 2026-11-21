package com.richi_mc.hackathon3b.feature.analysis.enhance

import com.richi_mc.hackathon3b.core.model.CajaPx
import com.richi_mc.hackathon3b.core.model.ElementoOcr
import com.richi_mc.hackathon3b.core.model.LineaOcr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeometriaRecorteTest {

    @Test
    fun escala_seAcotaEntre1YMaxima() {
        // zona 80 -> línea 40 -> 120/40 = 3
        assertEquals(3f, GeometriaRecorte.escala(80), 0.001f)
        // zona 40 -> línea 20 -> 6 -> tope 3
        assertEquals(3f, GeometriaRecorte.escala(40), 0.001f)
        assertEquals(2f, GeometriaRecorte.escala(40, maxima = 2f), 0.001f)
        // zona 160 -> línea 80 -> 1.5
        assertEquals(1.5f, GeometriaRecorte.escala(160), 0.001f)
        // zona 400 -> línea 200 -> 0.6 -> mínimo 1
        assertEquals(1f, GeometriaRecorte.escala(400), 0.001f)
        assertEquals(1f, GeometriaRecorte.escala(0), 0.001f)
    }

    @Test
    fun recortarABordes_ajustaYDescartaVacias() {
        assertEquals(CajaPx(0, 10, 100, 50), GeometriaRecorte.recortarABordes(CajaPx(-20, 10, 140, 50), 100, 80))
        assertEquals(CajaPx(10, 0, 60, 80), GeometriaRecorte.recortarABordes(CajaPx(10, -5, 60, 200), 100, 80))
        assertNull(GeometriaRecorte.recortarABordes(CajaPx(200, 10, 300, 50), 100, 80))
    }

    @Test
    fun aImagenCompleta_dividePorEscalaYSumaOrigen() {
        val caja = GeometriaRecorte.aImagenCompleta(CajaPx(30, 60, 90, 120), 3f, 100, 200)
        assertEquals(CajaPx(110, 220, 130, 240), caja)
    }

    @Test
    fun aImagenCompleta_convierteLineaElementosYSimbolos() {
        val linea = LineaOcr(
            "24",
            CajaPx(0, 0, 60, 30),
            listOf(ElementoOcr("24", CajaPx(0, 0, 60, 30), listOf(CajaPx(0, 0, 30, 30), CajaPx(30, 0, 60, 30)))),
        )
        val r = GeometriaRecorte.aImagenCompleta(linea, 3f, 10, 20)
        assertEquals(CajaPx(10, 20, 30, 30), r.caja)
        assertEquals(CajaPx(10, 20, 30, 30), r.elementos[0].caja)
        assertEquals(listOf(CajaPx(10, 20, 20, 30), CajaPx(20, 20, 30, 30)), r.elementos[0].simbolos)
        assertEquals("24", r.texto)
    }
}
