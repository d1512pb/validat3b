package com.richi_mc.hackathon3b.feature.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodigoBarrasTest {
    @Test fun ean13Validos() {
        assertTrue(CodigoBarras.checksumValido("7501055300075", "EAN_13"))
        assertTrue(CodigoBarras.checksumValido("7501055363056", "EAN_13"))
        assertTrue(CodigoBarras.checksumValido("4006381333931", null))
    }

    @Test fun ean13Invalidos() {
        assertFalse(CodigoBarras.checksumValido("7501055300076", "EAN_13"))
        assertFalse(CodigoBarras.checksumValido("7501055363055", "EAN_13"))
        assertFalse(CodigoBarras.checksumValido("750105530007", "EAN_13")) // longitud
    }

    @Test fun upcA() {
        assertTrue(CodigoBarras.checksumValido("036000291452", "UPC_A"))
        assertTrue(CodigoBarras.checksumValido("036000291452", null))
        assertFalse(CodigoBarras.checksumValido("036000291453", "UPC_A"))
    }

    @Test fun ean8() {
        assertTrue(CodigoBarras.checksumValido("96385074", "EAN_8"))
        assertTrue(CodigoBarras.checksumValido("73513537", "EAN_8"))
        assertFalse(CodigoBarras.checksumValido("96385075", "EAN_8"))
    }

    @Test fun upcE() {
        assertEquals("042100005264", CodigoBarras.expandirUpcE("04252614"))
        assertTrue(CodigoBarras.checksumValido("04252614", "UPC_E"))
        assertFalse(CodigoBarras.checksumValido("04252615", "UPC_E"))
        // Sistema numérico 2..9 no es UPC-E válido.
        assertFalse(CodigoBarras.checksumValido("24252614", "UPC_E"))
    }

    @Test fun noNumericoOVacio() {
        assertFalse(CodigoBarras.checksumValido("", "EAN_13"))
        assertFalse(CodigoBarras.checksumValido("75010553000AB", "EAN_13"))
        assertFalse(CodigoBarras.checksumValido("12345", null))
    }
}
