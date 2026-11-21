package com.richi_mc.hackathon3b.feature.labeling

import com.richi_mc.hackathon3b.core.model.EtiquetaImagen
import com.richi_mc.hackathon3b.core.model.Problema
import com.richi_mc.hackathon3b.core.model.TipoFoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProblemasPorEtiquetasTest {
    private fun e(texto: String, c: Float) = EtiquetaImagen(texto, c, 0)

    @Test fun fotoDePared_sinCodigo_productoNoVisible() {
        val r = problemasPorEtiquetas(TipoFoto.PRODUCTO, listOf(e("Wall", 0.9f), e("Room", 0.7f)), hayCodigo = false)
        assertEquals(listOf(Problema.PRODUCTO_NO_VISIBLE), r)
    }

    @Test fun fotoDePared_conCodigo_nada() {
        val r = problemasPorEtiquetas(TipoFoto.PRODUCTO, listOf(e("Wall", 0.9f), e("Room", 0.7f)), hayCodigo = true)
        assertEquals(emptyList<Problema>(), r)
    }

    @Test fun productoConBotella_nada() {
        assertEquals(emptyList<Problema>(), problemasPorEtiquetas(TipoFoto.PRODUCTO, listOf(e("Bottle", 0.9f)), false))
    }

    @Test fun productoConBotellaBajaConfianza_noVisible() {
        assertEquals(
            listOf(Problema.PRODUCTO_NO_VISIBLE),
            problemasPorEtiquetas(TipoFoto.PRODUCTO, listOf(e("Bottle", 0.55f)), false),
        )
    }

    @Test fun comparacionSinMayusculas() {
        assertEquals(emptyList<Problema>(), problemasPorEtiquetas(TipoFoto.PRODUCTO, listOf(e("bOTTLE", 0.9f)), false))
    }

    @Test fun listaVacia_nada() {
        TipoFoto.values().forEach {
            assertEquals(emptyList<Problema>(), problemasPorEtiquetas(it, emptyList(), false))
        }
    }

    @Test fun fachadaConEdificio_nada() {
        assertEquals(emptyList<Problema>(), problemasPorEtiquetas(TipoFoto.FACHADA, listOf(e("Building", 0.8f)), false))
    }

    @Test fun fachadaConComida_noParece() {
        assertEquals(
            listOf(Problema.FACHADA_NO_PARECE_TIENDA),
            problemasPorEtiquetas(TipoFoto.FACHADA, listOf(e("Food", 0.8f)), false),
        )
    }

    @Test fun monitor_posibleFotoDePantalla() {
        val r = problemasPorEtiquetas(TipoFoto.PRODUCTO, listOf(e("Monitor", 0.8f), e("Bottle", 0.9f)), false)
        assertEquals(listOf(Problema.POSIBLE_FOTO_DE_PANTALLA), r)
    }

    @Test fun monitorBajaConfianza_nada() {
        assertEquals(emptyList<Problema>(), problemasPorEtiquetas(TipoFoto.ETIQUETA, listOf(e("Monitor", 0.6f)), false))
    }

    private val etiquetasJabon = listOf(e("Hand", 0.9f), e("Finger", 0.8f), e("Flower", 0.7f))

    @Test fun jabonEnLaMano_conTextoDeEmpaque_nada() {
        val ocr = "Contenido Neto 150 g\nRosa\nVenus\nJabón de Tocador"
        assertEquals(
            emptyList<Problema>(),
            problemasPorEtiquetas(TipoFoto.PRODUCTO, etiquetasJabon, hayCodigo = false, textoOcr = ocr),
        )
    }

    @Test fun jabonEnLaMano_sinTextoOcr_siguePidiendoProducto() {
        assertEquals(
            listOf(Problema.PRODUCTO_NO_VISIBLE),
            problemasPorEtiquetas(TipoFoto.PRODUCTO, etiquetasJabon, hayCodigo = false),
        )
    }

    @Test fun paredSinTexto_siguePidiendoProducto() {
        val r = problemasPorEtiquetas(TipoFoto.PRODUCTO, listOf(e("Wall", 0.9f)), false, textoOcr = "")
        assertEquals(listOf(Problema.PRODUCTO_NO_VISIBLE), r)
    }

    @Test fun ocrBasura_siguePidiendoProducto() {
        val r = problemasPorEtiquetas(TipoFoto.PRODUCTO, listOf(e("Wall", 0.9f)), false, textoOcr = "a 1 ,")
        assertEquals(listOf(Problema.PRODUCTO_NO_VISIBLE), r)
    }

    @Test fun textoDeEmpaque_noAfectaFachada() {
        assertEquals(
            listOf(Problema.FACHADA_NO_PARECE_TIENDA),
            problemasPorEtiquetas(TipoFoto.FACHADA, listOf(e("Food", 0.8f)), false, textoOcr = "Rosa Venus Jabón de Tocador"),
        )
    }

    @Test fun pareceTextoDeEmpaque_contenidoNeto() {
        assertTrue(pareceTextoDeEmpaque("150g"))
        assertTrue(pareceTextoDeEmpaque("1 L"))
        assertTrue(pareceTextoDeEmpaque("600 ml"))
        assertTrue(pareceTextoDeEmpaque("Contenido Neto 150 g"))
        assertTrue(pareceTextoDeEmpaque("NET 1,5 KG"))
    }

    @Test fun pareceTextoDeEmpaque_textoSustancial() {
        assertTrue(pareceTextoDeEmpaque("Rosa\nVenus\nJabón de Tocador"))
    }

    @Test fun pareceTextoDeEmpaque_cortoOVacio() {
        assertFalse(pareceTextoDeEmpaque(""))
        assertFalse(pareceTextoDeEmpaque("   \n "))
        assertFalse(pareceTextoDeEmpaque("a 1 ,"))
        assertFalse(pareceTextoDeEmpaque("Rosa"))
        assertFalse(pareceTextoDeEmpaque("Rosa Ven"))
        assertFalse(pareceTextoDeEmpaque("ab cd ef gh ij kl mn"))
    }

    @Test fun traduccion() {
        assertEquals("Botella", TraduccionEtiquetas.es("Bottle"))
        assertEquals("Zzz", TraduccionEtiquetas.es("Zzz"))
    }
}
