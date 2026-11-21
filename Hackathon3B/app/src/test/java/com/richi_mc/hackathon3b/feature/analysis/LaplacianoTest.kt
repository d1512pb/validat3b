package com.richi_mc.hackathon3b.feature.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaplacianoTest {
    @Test fun imagenConstanteTieneVarianzaCero() {
        val g = DoubleArray(64 * 64) { 128.0 }
        assertEquals(0.0, Laplaciano.varianza(g, 64, 64), 1e-9)
    }

    @Test fun tableroTieneVarianzaAlta() {
        val w = 64
        val g = DoubleArray(w * w) { i -> if ((i % w + i / w) % 2 == 0) 0.0 else 255.0 }
        assertTrue(Laplaciano.varianza(g, w, w) > 100_000.0)
    }

    @Test fun degradadoSuaveEsMenosNitidoQueRuido() {
        val w = 64
        val suave = DoubleArray(w * w) { i -> (i % w) * 2.0 }
        val tablero = DoubleArray(w * w) { i -> if ((i % w + i / w) % 2 == 0) 0.0 else 255.0 }
        assertTrue(Laplaciano.varianza(suave, w, w) < Laplaciano.varianza(tablero, w, w))
        assertEquals(0.0, Laplaciano.varianza(suave, w, w), 1e-9) // Laplaciano de una rampa lineal = 0
    }

    @Test fun imagenMuyPequenaDevuelveCero() {
        assertEquals(0.0, Laplaciano.varianza(DoubleArray(4), 2, 2), 0.0)
    }

    @Test fun brilloMedio() {
        assertEquals(100.0, brilloMedio(doubleArrayOf(50.0, 150.0)), 1e-9)
        assertEquals(0.0, brilloMedio(DoubleArray(0)), 0.0)
    }
}
