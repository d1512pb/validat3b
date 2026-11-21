package com.richi_mc.hackathon3b.feature.analysis

import com.richi_mc.hackathon3b.core.model.TipoPrecio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexPriceParserTest {
    private val parser = RegexPriceParser()

    @Test fun signoYPunto() {
        val p = parser.parse("\$24.90")
        assertEquals(24.90, p.valor!!, 1e-9)
        assertEquals(TipoPrecio.REGULAR, p.tipo)
        assertEquals("\$24.90", p.textoFuente)
        assertFalse(p.tienePrecioUnitario)
    }

    @Test fun signoConEspacio() = assertEquals(24.90, parser.parse("\$ 24.90").valor!!, 1e-9)

    @Test fun centavosSeparadosPorOcrConEspacio() {
        assertEquals(24.90, parser.parse("\$ 24 90").valor!!, 1e-9)
        assertEquals(24.90, parser.parse("\$24 90").valor!!, 1e-9)
    }

    @Test fun comaDecimalSinSigno() = assertEquals(24.90, parser.parse("24,90").valor!!, 1e-9)

    @Test fun miles() {
        assertEquals(1299.0, parser.parse("\$1,299.00").valor!!, 1e-9)
        assertEquals(1299.0, parser.parse("PANTALLA 1,299.00").valor!!, 1e-9)
    }

    @Test fun precioYPrecioUnitarioEnLaMismaLinea() {
        val p = parser.parse("LECHE 1L \$24.90 PRECIO POR LITRO \$24.90")
        assertEquals(24.90, p.valor!!, 1e-9)
        assertTrue(p.tienePrecioUnitario)
        assertEquals(24.90, p.precioUnitario!!, 1e-9)
        assertEquals(TipoPrecio.REGULAR, p.tipo)
    }

    @Test fun precioUnitarioDistintoEnLineas() {
        val p = parser.parse("ARROZ 900G\n\$32.50\nPRECIO UNITARIO\n\$36.11 POR KG")
        assertEquals(32.50, p.valor!!, 1e-9)
        assertEquals(36.11, p.precioUnitario!!, 1e-9)
        assertTrue(p.tienePrecioUnitario)
    }

    @Test fun ofertaConAntes() {
        val p = parser.parse("OFERTA \$19.90 ANTES \$24.90")
        assertEquals(19.90, p.valor!!, 1e-9)
        assertEquals(TipoPrecio.OFERTA, p.tipo)
    }

    @Test fun ahorraNoEsElPrecio() {
        val p = parser.parse("AHORRA \$5.00 \$19.90")
        assertEquals(19.90, p.valor!!, 1e-9)
        assertEquals(TipoPrecio.OFERTA, p.tipo)
    }

    @Test fun ofertaConAcentosYMinusculas() {
        assertEquals(TipoPrecio.OFERTA, parser.parse("Promoción \$15.50").tipo)
        assertEquals(TipoPrecio.OFERTA, parser.parse("precio especial \$15.50").tipo)
    }

    @Test fun volumenDosPorPrecio() {
        val p = parser.parse("2X\$50")
        assertEquals(TipoPrecio.VOLUMEN, p.tipo)
        assertEquals(50.0, p.valor!!, 1e-9)
    }

    @Test fun volumenOtrasFormas() {
        assertEquals(TipoPrecio.VOLUMEN, parser.parse("2 X 1 EN SHAMPOO").tipo)
        assertEquals(TipoPrecio.VOLUMEN, parser.parse("3 X \$45.00").tipo)
        assertEquals(TipoPrecio.VOLUMEN, parser.parse("2do al 50%").tipo)
    }

    @Test fun llevaPagaSinPrecio() {
        val p = parser.parse("LLEVA 3 PAGA 2")
        assertEquals(TipoPrecio.VOLUMEN, p.tipo)
        assertNull(p.valor)
    }

    @Test fun precioPorKilo() {
        val p = parser.parse("\$ 89.00 /KG")
        assertTrue(p.tienePrecioUnitario)
        assertEquals(89.0, p.precioUnitario!!, 1e-9)
        assertEquals(89.0, p.valor!!, 1e-9)
    }

    @Test fun textoSinPrecio() {
        val p = parser.parse("LECHE ENTERA DEL VALLE\nCADUCA PRONTO")
        assertNull(p.valor)
        assertEquals(TipoPrecio.DESCONOCIDO, p.tipo)
        assertFalse(p.tienePrecioUnitario)
        assertEquals("", p.textoFuente)
    }

    @Test fun textoVacio() {
        assertNull(parser.parse("").valor)
        assertEquals(TipoPrecio.DESCONOCIDO, parser.parse("").tipo)
    }

    @Test fun codigoDeBarrasNoEsPrecio() {
        assertNull(parser.parse("7501055300075").valor)
        assertNull(parser.parse("SKU 1234567 7501055300075.00").valor)
        assertEquals(24.90, parser.parse("7501055300075\n\$24.90").valor!!, 1e-9)
    }

    @Test fun fechaNoEsPrecio() {
        assertNull(parser.parse("CAD 12/05/2026").valor)
        assertNull(parser.parse("12.05.2026").valor)
        assertEquals(24.90, parser.parse("CAD 12/05/2026 \$24.90").valor!!, 1e-9)
    }

    @Test fun ante_duda_gana_el_mayor_con_signo() {
        assertEquals(24.90, parser.parse("\$19.90 \$24.90").valor!!, 1e-9)
        // un número sin signo no le gana a uno con signo
        assertEquals(19.90, parser.parse("99.99 \$19.90").valor!!, 1e-9)
    }

    @Test fun pesoNoEsPrecio() {
        assertNull(parser.parse("CONTENIDO 1.50 L").valor)
    }
}
