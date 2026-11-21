package com.richi_mc.hackathon3b.feature.capture

import com.richi_mc.hackathon3b.core.model.FuenteDescripcion
import com.richi_mc.hackathon3b.core.model.ProductoEsperado
import com.richi_mc.hackathon3b.core.model.Sugerencia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CamposEsperadoTest {

    private fun sugerencia(d: String? = "LECHE ENTERA 1L", precio: Double? = 24.9, f: FuenteDescripcion? = FuenteDescripcion.ETIQUETA_RACK) =
        Sugerencia(d, f, precio, if (d == null) 0f else 0.8f)

    private val prellenado = CamposEsperado().conSugerencia(sugerencia())

    @Test
    fun la_sugerencia_prellena_los_campos() {
        assertEquals("LECHE ENTERA 1L", prellenado.descripcion)
        assertEquals("24.90", prellenado.precioTexto)
        assertEquals(24.9, prellenado.precio!!, 0.0)
        assertFalse(prellenado.descripcionEditada)
        assertFalse(prellenado.precioEditado)
        assertFalse(prellenado.editadoPorUsuario)
    }

    @Test
    fun sin_sugerencia_queda_vacio_y_fuente_manual() {
        val c = CamposEsperado().conSugerencia(sugerencia(null, null, null))
        assertEquals("", c.descripcion)
        assertEquals("", c.precioTexto)
        assertNull(c.fuente)
        assertEquals(FuenteDescripcion.MANUAL, c.aProductoEsperado().fuente)
        assertNull(c.aProductoEsperado().descripcion)
        assertNull(c.aProductoEsperado().precio)
    }

    @Test
    fun editar_marca_editado_y_restaurar_lo_desmarca() {
        val e = prellenado.conDescripcion("leche lala 1 l").conPrecio("25,5")
        assertEquals("LECHE LALA 1 L", e.descripcion)
        assertEquals(25.5, e.precio!!, 0.0)
        assertTrue(e.descripcionEditada); assertTrue(e.precioEditado); assertTrue(e.editadoPorUsuario)
        val r = e.restaurarDescripcion().restaurarPrecio()
        assertEquals("LECHE ENTERA 1L", r.descripcion)
        assertEquals("24.90", r.precioTexto)
        assertFalse(r.editadoPorUsuario)
    }

    @Test
    fun recalcular_solo_actualiza_los_campos_no_editados() {
        val e = prellenado.conDescripcion("MI DESCRIPCION")  // precio sin editar
        val nueva = Sugerencia("OTRA COSA 500 ML", FuenteDescripcion.FOTO_PRODUCTO, 30.0, 0.6f)
        val r = e.conSugerencia(nueva)
        // Descripción editada: se conserva, pero el detectado se actualiza.
        assertEquals("MI DESCRIPCION", r.descripcion)
        assertEquals("OTRA COSA 500 ML", r.descripcionDetectada)
        assertEquals(FuenteDescripcion.FOTO_PRODUCTO, r.fuente)
        assertTrue(r.descripcionEditada)
        // Precio sin editar: toma el nuevo.
        assertEquals("30.00", r.precioTexto)
        assertEquals(30.0, r.precioDetectado!!, 0.0)
        assertFalse(r.precioEditado)
    }

    @Test
    fun recalcular_conserva_el_precio_editado() {
        val e = prellenado.conPrecio("19.5")
        val r = e.conSugerencia(sugerencia(precio = 31.0))
        assertEquals("19.5", r.precioTexto)
        assertEquals(31.0, r.precioDetectado!!, 0.0)
        assertEquals("LECHE ENTERA 1L", r.descripcion)
    }

    @Test
    fun descripcion_en_mayusculas_maximo_60_y_una_linea() {
        val c = CamposEsperado().conDescripcion("a".repeat(80) + "\nb")
        assertEquals(60, c.descripcion.length)
        assertEquals("A".repeat(60), c.descripcion)
        assertEquals("A B", CamposEsperado().conDescripcion("a\nb").descripcion)
    }

    @Test
    fun precio_acepta_coma_o_punto_solo_digitos_y_un_separador() {
        assertEquals("24,9", CamposEsperado.normalizarPrecioTexto("24,9"))
        assertEquals("24.9", CamposEsperado.normalizarPrecioTexto("2a4.9$"))
        assertEquals("24.9", CamposEsperado.normalizarPrecioTexto("24.9.,"))
        assertEquals("24.99", CamposEsperado.normalizarPrecioTexto("24.999"))
        assertEquals(24.9, CamposEsperado.parsearPrecio("24,9")!!, 0.0)
        assertEquals(0.5, CamposEsperado.parsearPrecio(".5")!!, 0.0)
        assertNull(CamposEsperado.parsearPrecio("."))
        assertNull(CamposEsperado.parsearPrecio(""))
    }

    @Test
    fun confirmar_arma_el_producto_con_final_detectado_y_fuente() {
        val c = prellenado.conDescripcion("Leche entera 1 L").conPrecio("25.5")
        val pe = c.aProductoEsperado(
            ProductoEsperado(sku = "S1", marca = "Lala", codigoBarras = "7501055363056", esContratipo = true),
        )
        assertEquals("LECHE ENTERA 1 L", pe.descripcion)
        assertEquals(25.5, pe.precio!!, 0.0)
        assertEquals("LECHE ENTERA 1L", pe.descripcionDetectada)
        assertEquals(24.9, pe.precioDetectado!!, 0.0)
        assertEquals(FuenteDescripcion.ETIQUETA_RACK, pe.fuente)
        assertEquals(0.8f, pe.confianzaSugerencia!!, 0f)
        assertTrue(pe.editadoPorUsuario)
        assertEquals("7501055363056", pe.codigoBarras)
        assertTrue(pe.esContratipo)
        assertEquals("S1", pe.sku); assertEquals("Lala", pe.marca)
    }

    @Test
    fun escribir_desde_vacio_es_fuente_manual_y_editado() {
        val c = CamposEsperado().conSugerencia(sugerencia(null, null, null)).conDescripcion("Atun")
        val pe = c.aProductoEsperado()
        assertEquals(FuenteDescripcion.MANUAL, pe.fuente)
        assertNull(pe.confianzaSugerencia)
        assertTrue(pe.editadoPorUsuario)
    }

    @Test
    fun sin_cambios_no_es_editado_y_desde_borrador_conserva_detectado_y_editado() {
        val pe = prellenado.aProductoEsperado()
        assertFalse(pe.editadoPorUsuario)
        val vuelta = CamposEsperado.desde(prellenado.conDescripcion("X").aProductoEsperado())
        assertEquals("X", vuelta.descripcion)
        assertEquals("LECHE ENTERA 1L", vuelta.descripcionDetectada)
        assertEquals(FuenteDescripcion.ETIQUETA_RACK, vuelta.fuente)
        assertTrue(vuelta.descripcionEditada)
        assertFalse(vuelta.precioEditado)
    }

    @Test
    fun sufijo_forzado_se_quita_de_la_descripcion_final() {
        val pe = ProductoEsperado(descripcion = "LECHE 1 L -APR")
        val (limpio, sufijo) = RevisionViewModel.separarSufijoForzado(pe)
        assertEquals("LECHE 1 L", limpio.descripcion)
        assertEquals("-APR", sufijo)
        assertNull(RevisionViewModel.separarSufijoForzado(ProductoEsperado(descripcion = "LECHE")).second)
        assertEquals("-REV", RevisionViewModel.separarSufijoForzado(ProductoEsperado(descripcion = "-REV")).second)
    }
}
