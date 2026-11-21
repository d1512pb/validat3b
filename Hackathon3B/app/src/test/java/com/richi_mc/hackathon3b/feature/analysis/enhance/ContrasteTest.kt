package com.richi_mc.hackathon3b.feature.analysis.enhance

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ContrasteTest {

    @Test
    fun luminancia_negroBlancoYGris() {
        assertEquals(0, Contraste.luminancia(0xFF000000.toInt()))
        assertEquals(255, Contraste.luminancia(0xFFFFFFFF.toInt()))
        assertEquals(128, Contraste.luminancia(0xFF808080.toInt()))
    }

    @Test
    fun percentil_sobreRampa() {
        val rampa = IntArray(100) { it }
        assertEquals(2, Contraste.percentil(rampa, 0.02))
        assertEquals(98, Contraste.percentil(rampa, 0.98))
        assertEquals(0, Contraste.percentil(IntArray(0), 0.5))
    }

    @Test
    fun estirar_rangoEstrechoOcupaTodo() {
        // 100 valores entre 100 y 149.
        val entrada = IntArray(100) { 100 + it / 2 }
        val salida = Contraste.estirar(entrada)
        assertEquals(0, salida.min())
        assertEquals(255, salida.max())
        // Monótono no decreciente.
        for (i in 1 until salida.size) assert(salida[i] >= salida[i - 1])
    }

    @Test
    fun estirar_imagenPlanaNoCambia() {
        val entrada = IntArray(50) { 90 }
        assertArrayEquals(entrada, Contraste.estirar(entrada))
    }

    @Test
    fun estirar_saturaLosExtremos() {
        // 1 % muy oscuro y 1 % muy claro quedan fuera de los percentiles 2/98.
        val entrada = IntArray(100) { 100 + it }
        entrada[0] = 0
        entrada[99] = 255
        val salida = Contraste.estirar(entrada)
        assertEquals(0, salida[0])
        assertEquals(255, salida[99])
    }

    @Test
    fun estirar_umbralBinarioDaSoloBlancoYNegro() {
        val entrada = IntArray(100) { 50 + it }
        val salida = Contraste.estirar(entrada, umbralBinario = 128)
        assert(salida.all { it == 0 || it == 255 })
        assertEquals(0, salida.first())
        assertEquals(255, salida.last())
    }
}
