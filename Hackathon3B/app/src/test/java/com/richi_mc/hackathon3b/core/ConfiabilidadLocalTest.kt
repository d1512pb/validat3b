package com.richi_mc.hackathon3b.core

import com.richi_mc.hackathon3b.core.model.AnalisisImagen
import com.richi_mc.hackathon3b.core.model.BandaIcl
import com.richi_mc.hackathon3b.core.model.CajaPx
import com.richi_mc.hackathon3b.core.model.CandidatoPrecio
import com.richi_mc.hackathon3b.core.model.ConfiabilidadLocal
import com.richi_mc.hackathon3b.core.model.FormaPrecio
import com.richi_mc.hackathon3b.core.model.PrecioDetectado
import com.richi_mc.hackathon3b.core.model.TipoFoto
import com.richi_mc.hackathon3b.core.model.TipoPrecio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Ejemplos de metodologia.md §5 y casos límite del ICL. */
class ConfiabilidadLocalTest {

    private fun foto(
        tipo: TipoFoto,
        nitidez: Double = 220.0,
        confianza: Float? = 0.92f,
        texto: String = "LECHE ENTERA LALA 1 LITRO 7501055",
        area: Double? = null,
    ) = AnalisisImagen(
        tipo, "/tmp/${tipo.contrato}.jpg", nitidez, 130.0, texto, confianza,
        null, null, area, emptyList(),
    )

    private fun candidato(valor: Double, unitario: Boolean, signo: Boolean = true) = CandidatoPrecio(
        valor, FormaPrecio.DECIMAL, "\$$valor", CajaPx(0, 0, 10, 10), 1f, signo, unitario, false,
    )

    private val precioBueno = PrecioDetectado(42.5, TipoPrecio.REGULAR, true, "\$42.50", 42.5)

    @Test
    fun captura_buena_da_banda_alta() {
        val i = ConfiabilidadLocal.calcular(
            fachada = foto(TipoFoto.FACHADA),
            producto = foto(TipoFoto.PRODUCTO, confianza = 0.88f, area = 0.05),
            etiqueta = foto(TipoFoto.ETIQUETA, texto = "LECHE ENTERA 1 L \$42.50 \$42.50 POR LITRO"),
            precio = precioBueno,
            escaneoValido = false,
        )
        assertNotNull(i!!)
        assertEquals(1.0, i.n, 1e-9)
        assertEquals(1.0, i.d, 1e-9)
        assertEquals(0.995, i.o, 0.001)
        // 1 L: valor == precioUnitario no es "solo unitario".
        assertEquals(1.0, i.p, 1e-9)
        assertEquals(0.999, i.icl, 0.001)
        assertEquals(BandaIcl.ALTA, i.banda)
        assertNull(i.masDebil)
    }

    @Test
    fun ejemplo_regular_da_73_y_banda_media() {
        val icl = ConfiabilidadLocal.combinar(n = 0.65, d = 0.75, o = 0.9, p = 0.6, t = 1.0)
        assertEquals(0.73, icl, 0.005)
        assertEquals(BandaIcl.MEDIA, ConfiabilidadLocal.banda(icl))
    }

    @Test
    fun ejemplo_debil_da_59_y_banda_baja() {
        val icl = ConfiabilidadLocal.combinar(n = 0.55, d = 0.5, o = 0.7, p = 0.6, t = 0.7)
        assertEquals(0.593, icl, 0.005)
        assertEquals(BandaIcl.BAJA, ConfiabilidadLocal.banda(icl))
    }

    @Test
    fun todo_en_el_umbral_da_alrededor_de_54() {
        val icl = ConfiabilidadLocal.combinar(0.5, 0.5, 0.5, 0.6, 0.7)
        assertEquals(0.541, icl, 0.005)
    }

    @Test
    fun captura_debil_sugiere_retomar_la_etiqueta() {
        val i = ConfiabilidadLocal.calcular(
            fachada = foto(TipoFoto.FACHADA, nitidez = 300.0),
            producto = foto(TipoFoto.PRODUCTO, nitidez = 300.0, area = null),
            etiqueta = foto(TipoFoto.ETIQUETA, nitidez = 110.0, confianza = 0.6f, texto = "2X\$50"),
            precio = PrecioDetectado(50.0, TipoPrecio.VOLUMEN, false, "2x\$50"),
            escaneoValido = true,
        )!!
        assertEquals(0.55, i.n, 1e-9)
        assertEquals(0.5, i.d, 1e-9)
        assertEquals(0.6, i.p, 1e-9)
        assertEquals(0.7, i.t, 1e-9)
        assertEquals(BandaIcl.BAJA, i.banda)
        assertEquals(TipoFoto.ETIQUETA, i.masDebil)
    }

    @Test
    fun precio_sin_signo_y_solo_unitario() {
        val sinSigno = PrecioDetectado(42.5, TipoPrecio.REGULAR, true, "42.50")
        assertEquals(0.8, ConfiabilidadLocal.puntajePrecio(sinSigno), 1e-9)

        val soloUnitario = PrecioDetectado(
            89.0, TipoPrecio.REGULAR, true, "\$89.00/kg", 89.0,
            candidatos = listOf(candidato(89.0, unitario = true)),
        )
        assertEquals(0.6, ConfiabilidadLocal.puntajePrecio(soloUnitario), 1e-9)

        val sinUnitario = precioBueno.copy(tienePrecioUnitario = false, precioUnitario = null)
        assertEquals(0.6, ConfiabilidadLocal.puntajePrecio(sinUnitario), 1e-9)
    }

    @Test
    fun distancia_con_codigo_pequeno() {
        // DEMASIADO_LEJOS ya no bloquea: con 1 % el puntaje baja de 0.5.
        assertEquals(0.25, ConfiabilidadLocal.puntajeDistancia(0.01, escaneoValido = false), 1e-9)
        // Con escaneo válido no baja de 0.5.
        assertEquals(0.5, ConfiabilidadLocal.puntajeDistancia(0.01, escaneoValido = true), 1e-9)
        assertEquals(0.75, ConfiabilidadLocal.puntajeDistancia(0.03, escaneoValido = false), 1e-9)
    }

    @Test
    fun sin_precio_no_aplica() {
        val sinPrecio = PrecioDetectado(null, TipoPrecio.DESCONOCIDO, false, "")
        assertNull(
            ConfiabilidadLocal.calcular(
                null, foto(TipoFoto.PRODUCTO, area = 0.05), foto(TipoFoto.ETIQUETA), sinPrecio, false,
            )
        )
    }

    @Test
    fun nitidez_usa_la_peor_foto() {
        val i = ConfiabilidadLocal.calcular(
            fachada = foto(TipoFoto.FACHADA, nitidez = 130.0),
            producto = foto(TipoFoto.PRODUCTO, area = 0.05),
            etiqueta = foto(TipoFoto.ETIQUETA, texto = "\$42.50 LECHE ENTERA 1L POR LITRO"),
            precio = precioBueno,
            escaneoValido = false,
        )!!
        assertEquals(0.65, i.n, 1e-9)
        assertEquals(TipoFoto.FACHADA, i.masDebil)
    }
}
