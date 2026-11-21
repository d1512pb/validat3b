package com.richi_mc.hackathon3b.feature.suggestion

import com.richi_mc.hackathon3b.core.model.EtiquetaImagen
import com.richi_mc.hackathon3b.core.model.FuenteDescripcion
import com.richi_mc.hackathon3b.core.model.LineaOcr
import com.richi_mc.hackathon3b.core.model.PrecioDetectado
import com.richi_mc.hackathon3b.core.model.TipoPrecio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglasSuggestionServiceTest {
    private val precio = PrecioDetectado(24.90, TipoPrecio.REGULAR, true, "$24.90", 24.90)
    private val sinPrecio = PrecioDetectado(null, TipoPrecio.DESCONOCIDO, false, "")
    private val servicio = ReglasSuggestionService(traducir = { if (it == "Bottle") "Botella" else it })

    @Test fun catalogoTienePrioridad() {
        val s = ReglasSuggestionService(catalogo = { if (it == "123") "LECHE LALA ENTERA 1 L" else null })
            .sugerir(
                listOf(LineaOcr("OTRA COSA GRANDE", 100, 0)), emptyList(),
                listOf(EtiquetaImagen("Bottle", 0.9f, 0)), precio, "123",
            )
        assertEquals("LECHE LALA ENTERA 1 L", s.descripcion)
        assertEquals(FuenteDescripcion.CATALOGO, s.fuente)
        assertEquals(1.0f, s.confianza, 0f)
        assertEquals(24.90, s.precio!!, 0.0)
    }

    @Test fun catalogoSinCoincidenciaCaeALaSiguienteFuente() {
        val s = ReglasSuggestionService().sugerir(
            listOf(LineaOcr("LECHE LALA", 80, 0)), emptyList(), emptyList(), precio, "999",
        )
        assertEquals(FuenteDescripcion.FOTO_PRODUCTO, s.fuente)
    }

    @Test fun soloEtiquetaLegible() {
        val s = servicio.sugerir(
            emptyList(),
            listOf(
                LineaOcr("LECHE ENTERA LALA 1L", 40, 10),
                LineaOcr("$24.90", 120, 60),
                LineaOcr("PRECIO POR LITRO $24.90", 30, 120),
            ),
            emptyList(), precio, null,
        )
        assertEquals("LECHE ENTERA LALA 1L", s.descripcion)
        assertEquals(FuenteDescripcion.ETIQUETA_RACK, s.fuente)
        assertEquals(0.8f, s.confianza, 0f)
        assertEquals(24.90, s.precio!!, 0.0)
    }

    @Test fun etiquetaIgnoraLineasDebajoDelPrecioYRuido() {
        val s = servicio.sugerir(
            emptyList(),
            listOf(
                LineaOcr("Galletas Marías Gamesa", 30, 10),
                LineaOcr("Paquete familiar", 30, 40),
                LineaOcr("Tercera línea extra", 30, 70),
                LineaOcr("24.90", 100, 120),
                LineaOcr("Vigencia al 30 de noviembre", 20, 220),
                LineaOcr("SKU 1002345", 20, 250),
            ),
            emptyList(), precio, null,
        )
        assertEquals("GALLETAS MARIAS GAMESA PAQUETE FAMILIAR", s.descripcion)
    }

    @Test fun etiquetaSinPrecioEncontradoUsaTodasLasLineas() {
        val s = servicio.sugerir(
            emptyList(), listOf(LineaOcr("Atun en agua", 30, 300), LineaOcr("PRECIO 18.50", 80, 10)),
            emptyList(), sinPrecio, null,
        )
        assertEquals(FuenteDescripcion.ETIQUETA_RACK, s.fuente)
        assertEquals("ATUN EN AGUA", s.descripcion)
        assertNull(s.precio)
    }

    @Test fun soloFotoDelProducto() {
        val s = servicio.sugerir(
            listOf(
                LineaOcr("Coca-Cola", 150, 100),
                LineaOcr("Sabor original", 60, 260),
                LineaOcr("600 ml", 40, 400),
                LineaOcr("pequeño texto legal", 15, 500),
                LineaOcr("7501055300075", 20, 460),
            ),
            emptyList(), emptyList(), precio, null,
        )
        assertEquals("COCA COLA SABOR ORIGINAL 600 ML", s.descripcion)
        assertEquals(FuenteDescripcion.FOTO_PRODUCTO, s.fuente)
        assertEquals(0.6f, s.confianza, 0f)
    }

    @Test fun contenidoNetoNoSeDuplica() {
        val s = servicio.sugerir(
            listOf(LineaOcr("Leche Lala", 100, 0), LineaOcr("1 L", 50, 100), LineaOcr("Entera 1L", 60, 200)),
            emptyList(), emptyList(), precio, null,
        )
        assertEquals("LECHE LALA ENTERA 1L", s.descripcion)
    }

    @Test fun soloEtiquetasDeImagenDaCategoria() {
        val s = servicio.sugerir(
            emptyList(), emptyList(),
            listOf(EtiquetaImagen("Food", 0.5f, 1), EtiquetaImagen("Bottle", 0.9f, 0)),
            precio, null,
        )
        assertEquals("BOTELLA", s.descripcion)
        assertEquals(FuenteDescripcion.CATEGORIA, s.fuente)
        assertEquals(0.3f, s.confianza, 0f)
    }

    @Test fun nadaDaSugerenciaVacia() {
        val s = servicio.sugerir(
            listOf(LineaOcr("12345", 50, 0)), listOf(LineaOcr("$24.90", 50, 0)), emptyList(), precio, null,
        )
        assertNull(s.descripcion)
        assertNull(s.fuente)
        assertEquals(24.90, s.precio!!, 0.0)
        assertEquals(0f, s.confianza, 0f)
    }

    @Test fun limiteDe60CaracteresYSinAcentos() {
        val largo = "Camarón jalapeño crujiente súper delicioso empanizado congelado premium"
        val s = servicio.sugerir(listOf(LineaOcr(largo, 80, 0), LineaOcr("Ñoño 500 g", 70, 100)), emptyList(), emptyList(), precio, null)
        val d = s.descripcion!!
        assertTrue(d.length <= 60)
        assertTrue(d.none { it.code > 127 })
        assertTrue(d.startsWith("CAMARON JALAPENO"))
        assertTrue(d.endsWith("500 G"))
    }

    @Test fun recortaEnLimiteDePalabra() {
        val s = servicio.sugerir(
            listOf(LineaOcr("AAAAAAAAAA BBBBBBBBBB CCCCCCCCCC DDDDDDDDDD EEEEEEEEEE FFFFFFFFFF GGGGGGGGGG", 80, 0)),
            emptyList(), emptyList(), precio, null,
        )
        assertEquals("AAAAAAAAAA BBBBBBBBBB CCCCCCCCCC DDDDDDDDDD EEEEEEEEEE", s.descripcion)
    }

    @Test fun demoCumpleLasFuentesEsperadas() {
        DemoSugerencias.casos().forEach { assertEquals(it.titulo, it.esperada, it.sugerencia.fuente) }
    }
}
