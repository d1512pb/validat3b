package com.richi_mc.hackathon3b.feature.analysis.price

import com.richi_mc.hackathon3b.core.model.CajaPx
import com.richi_mc.hackathon3b.core.model.FormaPrecio
import com.richi_mc.hackathon3b.core.model.LineaOcr
import com.richi_mc.hackathon3b.core.model.PrecioDetectado
import com.richi_mc.hackathon3b.core.model.TipoPrecio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringPriceParserTest {
    private val parser = ScoringPriceParser()

    private fun caso(prefijo: String): PrecioDetectado =
        parser.parse(casosPrecio().first { it.nombre.startsWith(prefijo) }.lineas)

    private fun assertGana(p: PrecioDetectado, valor: Double, forma: FormaPrecio) {
        assertEquals(valor, p.valor!!, 1e-9)
        assertEquals(forma, p.candidatos.first().forma)
    }

    @Test fun decimalConSigno() {
        val p = caso("\$24.90")
        assertGana(p, 24.90, FormaPrecio.DECIMAL)
        assertEquals("\$24.90", p.textoFuente)
        assertTrue(p.confianza!! >= 0.60f)
    }

    @Test fun centavosEnLaMismaLinea() = assertGana(caso("24 90"), 24.90, FormaPrecio.CENTAVOS_MISMA_LINEA)

    @Test fun centavosPegadosConSimbolosMasBajos() {
        val p = caso("2490")
        assertGana(p, 24.90, FormaPrecio.CENTAVOS_PEGADOS)
        assertTrue(p.confianza!! >= 0.60f)
    }

    @Test fun centavosEnOtraLinea() = assertGana(caso("24 / 90"), 24.90, FormaPrecio.CENTAVOS_OTRA_LINEA)

    @Test fun sigloNormalizadoComoSigno() {
        val p = caso("S24.90")
        assertGana(p, 24.90, FormaPrecio.DECIMAL)
        assertTrue(p.candidatos.first().tieneSigno)
    }

    @Test fun enteroConSigno() {
        val p = caso("\$25")
        assertGana(p, 25.0, FormaPrecio.ENTERO)
        assertTrue(p.confianza!! >= 0.60f)
    }

    @Test fun enteroSoloEnLaLineaMasAltaTieneConfianzaMedia() {
        val p = caso("25 solo")
        assertGana(p, 25.0, FormaPrecio.ENTERO)
        assertTrue("confianza=${p.confianza}", p.confianza!! in 0.30f..0.59f)
        assertTrue(p.requiereRevision)
    }

    @Test fun precioUnitarioNoEsElPrincipal() {
        val p = caso("600 ML")
        assertGana(p, 15.0, FormaPrecio.DECIMAL)
        assertTrue(p.tienePrecioUnitario)
        assertEquals(25.0, p.precioUnitario!!, 1e-9)
        assertEquals(TipoPrecio.REGULAR, p.tipo)
    }

    @Test fun antesYAhoraEsOferta() {
        val p = caso("ANTES")
        assertGana(p, 27.50, FormaPrecio.DECIMAL)
        assertEquals(TipoPrecio.OFERTA, p.tipo)
        val antes = p.candidatos.first { it.valor == 32.0 }
        assertTrue(antes.esAnterior)
    }

    @Test fun codigoDeBarrasYFechaNoDanPrecio() {
        val p = caso("codigo de barras")
        assertNull(p.valor)
        assertTrue(p.candidatos.isEmpty())
        assertEquals(TipoPrecio.DESCONOCIDO, p.tipo)
        assertEquals(0f, p.confianza!!, 0f)
    }

    @Test fun candidatosVienenOrdenadosYLimitadosATres() {
        val p = parser.parse(
            listOf(
                lin(el("\$24.90", 100, 100, 300, 80)),
                lin(el("\$18.50", 100, 200, 200, 30)),
                lin(el("\$99.00", 100, 250, 200, 25)),
                lin(el("\$12.00", 100, 300, 200, 20)),
            )
        )
        assertEquals(3, p.candidatos.size)
        assertEquals(p.candidatos.sortedByDescending { it.puntuacion }, p.candidatos)
        assertEquals(24.90, p.candidatos.first().valor, 1e-9)
    }

    @Test fun descartaUnidadesYMezclas() {
        val p = parser.parse(
            listOf(
                lin(el("6", 50, 100, 30, 30), el("X", 90, 100, 30, 30), el("355", 130, 100, 60, 30), el("ML", 200, 100, 50, 30)),
                lin(el("A12B", 50, 150, 80, 30), el("15%", 150, 150, 80, 30), el("1.5L", 250, 150, 80, 30)),
            )
        )
        assertTrue(p.candidatos.isEmpty())
        assertNull(p.valor)
    }

    @Test fun fueraDeRangoSeDescarta() {
        val p = parser.parse(listOf(lin(el("\$9999.00", 100, 100, 300, 80))))
        assertNull(p.valor)
        assertTrue(p.candidatos.isEmpty())
    }

    @Test fun tokenSinEvidenciaDaDosCandidatosYPrefiereCentavos() {
        val p = parser.parse(listOf(lin(el("2490", 100, 100, 280, 80))))
        val valores = p.candidatos.map { it.valor }
        assertTrue(24.90 in valores && 2490.0 in valores)
        assertEquals(24.90, p.candidatos.first().valor, 1e-9)
    }

    @Test fun letrasPorDigitosDentroDelToken() {
        val p = parser.parse(listOf(lin(el("\$2O.9O", 100, 100, 300, 80))))
        assertEquals(20.90, p.valor!!, 1e-9)
    }

    @Test fun espaciosAlrededorDelPunto() {
        val p = parser.parse(
            listOf(lin(el("\$24", 100, 100, 140, 80), el(".", 245, 150, 10, 30), el("90", 260, 100, 70, 80)))
        )
        assertGana(p, 24.90, FormaPrecio.DECIMAL)
    }

    @Test fun volumenComoElParserAnterior() {
        val p = parser.parse(listOf(lin(el("3", 50, 100, 30, 60), el("X", 90, 100, 30, 60), el("\$50", 130, 100, 150, 60))))
        assertEquals(TipoPrecio.VOLUMEN, p.tipo)
        assertEquals(50.0, p.valor!!, 1e-9)
    }

    // ---- Re-OCR ----

    @Test fun reOcrAportaCentavosNuevos() {
        val original = listOf(lin(el("24", 100, 100, 140, 80)))
        val recorte = listOf(lin(el("24.90", 100, 100, 300, 80)))
        val sin = parser.parse(original)
        assertEquals(24.0, sin.valor!!, 1e-9)
        val con = parser.parse(original, recorte)
        assertEquals(24.90, con.valor!!, 1e-9)
        assertEquals(FormaPrecio.DECIMAL, con.candidatos.first().forma)
        assertTrue(con.confianza!! > sin.confianza!!)
    }

    @Test fun reOcrQueCoincideSumaBonus() {
        val original = listOf(lin(el("2490", 100, 100, 280, 80)))
        val sin = parser.parse(original)
        val con = parser.parse(original, listOf(lin(el("\$24.90", 100, 100, 300, 80))))
        assertEquals(24.90, con.valor!!, 1e-9)
        assertEquals(sin.confianza!! + PesosPrecio.COINCIDE_REOCR, con.confianza!!, 1e-4f)
    }

    // ---- Sin elementos (capturas viejas, fakes) ----

    @Test fun lineasSinElementosTokenizanElTexto() {
        val p = parser.parse(listOf(LineaOcr("\$24.90", 80, 100)))
        assertEquals(24.90, p.valor!!, 1e-9)
        assertEquals(FormaPrecio.DECIMAL, p.candidatos.first().forma)
        val q = parser.parse(listOf(LineaOcr("24 90", 80, 100)))
        assertGana(q, 24.90, FormaPrecio.CENTAVOS_MISMA_LINEA)
    }

    @Test fun lineasSinElementosYCentavosEnOtraLinea() {
        val p = parser.parse(listOf(LineaOcr("24", 80, 100), LineaOcr("90", 40, 102)))
        assertGana(p, 24.90, FormaPrecio.CENTAVOS_OTRA_LINEA)
    }

    @Test fun sinAlturaUtilElRestoDeSenalesFunciona() {
        val p = parser.parse(
            listOf(
                LineaOcr("ANTES \$32.00", CajaPx(0, 0, 0, 0)),
                LineaOcr("AHORA \$27.50", CajaPx(0, 0, 0, 0)),
            )
        )
        assertEquals(27.50, p.valor!!, 1e-9)
        assertEquals(0f, p.candidatos.first().alturaRelativa, 0f)
        assertEquals(TipoPrecio.OFERTA, p.tipo)
    }

    @Test fun textoVacioNoRompe() {
        val p = parser.parse(emptyList())
        assertNull(p.valor)
        assertFalse(p.tienePrecioUnitario)
        assertEquals("", p.textoFuente)
    }

    // ---- Decisión de re-OCR ----

    @Test fun necesitaReOcrSegunConfianzaYForma() {
        val ok = caso("\$24.90")
        assertFalse(necesitaReOcr(ok, casosPrecio().first { it.nombre.startsWith("\$24.90") }.lineas))
        val solo25 = casosPrecio().first { it.nombre.startsWith("25 solo") }
        assertTrue(necesitaReOcr(parser.parse(solo25.lineas), solo25.lineas))
        val pegados = casosPrecio().first { it.nombre.startsWith("2490") }
        assertFalse(necesitaReOcr(parser.parse(pegados.lineas), pegados.lineas))
        val enteroAlto = parser.parse(listOf(lin(el("\$25", 100, 100, 160, 80)), lin(el("PRECIO", 100, 20, 100, 20))))
        assertTrue(enteroAlto.candidatos.first().forma == FormaPrecio.ENTERO)
        assertTrue(necesitaReOcr(enteroAlto, emptyList()))
    }

    @Test fun necesitaReOcrSinCandidatosPeroConDigitos() {
        val lineas = listOf(lin(el("7501055300075", 50, 100, 400, 20)))
        assertTrue(necesitaReOcr(parser.parse(lineas), lineas))
        val sinDigitos = listOf(lin(el("OFERTA", 50, 100, 400, 20)))
        assertFalse(necesitaReOcr(parser.parse(sinDigitos), sinDigitos))
    }

    @Test fun zonaReOcrAmpliaYRecorta() {
        val lineas = listOf(lin(el("\$24.90", 100, 100, 300, 80)))
        val p = parser.parse(lineas)
        val z = zonaReOcr(lineas, p, 1000, 1000)!!
        // 40 % del ancho (300) a la derecha; 50 % del alto (80) arriba y abajo.
        assertEquals(CajaPx(100, 60, 520, 220), z)
        val borde = zonaReOcr(lineas, p, 450, 200)!!
        assertEquals(CajaPx(100, 60, 450, 200), borde)
    }

    @Test fun zonaReOcrUsaLaLineaMasAltaConDigitosSiNoHayGanador() {
        val lineas = listOf(
            lin(el("7501055300075", 50, 100, 400, 20)),
            lin(el("12/11/26", 50, 300, 160, 40)),
        )
        val z = zonaReOcr(lineas, parser.parse(lineas), 1000, 1000)!!
        assertEquals(CajaPx(50, 280, 274, 360), z)
        assertNull(zonaReOcr(emptyList(), parser.parse(emptyList()), 1000, 1000))
        assertNotNull(zonaReOcr(lineas, parser.parse(lineas), 1000, 1000))
    }

    @Test fun demoDevuelveUnaLineaPorCaso() {
        val lineas = demoCasosPrecio()
        lineas.forEach(::println)
        assertEquals(10, lineas.size)
    }
}
