package com.richi_mc.hackathon3b.core.model

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** Banda de decisión del índice de confiabilidad local (metodologia.md §4). */
enum class BandaIcl { ALTA, MEDIA, BAJA }

/**
 * Índice de confiabilidad local (ICL) y sus cinco subpuntajes, cada uno en 0..1 (metodologia.md).
 * [masDebil] = la foto que más baja el índice (para sugerir retomarla); null si nada resta.
 */
data class IndiceConfiabilidad(
    val n: Double,
    val d: Double,
    val o: Double,
    val p: Double,
    val t: Double,
    val icl: Double,
    val banda: BandaIcl,
    val masDebil: TipoFoto?,
)

/**
 * Cálculo del ICL (metodologia.md). Mide si la evidencia es legible y el precio está bien leído;
 * no valida producto ni tienda. Pesos, anclas y cortes son el punto de partida de la §6 (recalibrar).
 */
object ConfiabilidadLocal {
    const val PESO_N = 0.25
    const val PESO_D = 0.20
    const val PESO_O = 0.20
    const val PESO_P = 0.25
    const val PESO_T = 0.10

    const val CORTE_ALTA = 0.80
    const val CORTE_MEDIA = 0.60

    private const val PISO = 0.01
    /** Pérdida mínima (peso · −ln) para considerar que una foto "resta". */
    private const val PERDIDA_MIN = 0.01

    /**
     * ICL de una captura. null si no hay precio (tipo DESCONOCIDO o sin valor): esa captura ya
     * está bloqueada y el índice no aplica.
     */
    fun calcular(
        fachada: AnalisisImagen?,
        producto: AnalisisImagen,
        etiqueta: AnalisisImagen,
        precio: PrecioDetectado,
        escaneoValido: Boolean,
    ): IndiceConfiabilidad? {
        val t = puntajeTipo(precio.tipo) ?: return null
        if (precio.valor == null) return null

        val fotos = listOfNotNull(fachada, producto, etiqueta)
        val masBorrosa = fotos.minBy { it.nitidez }
        val n = puntajeNitidez(masBorrosa.nitidez)
        val d = puntajeDistancia(producto.areaCodigo, escaneoValido)
        val o = 0.6 * puntajeOcr(etiqueta) + 0.4 * puntajeOcr(producto)
        val p = puntajePrecio(precio)
        val icl = combinar(n, d, o, p, t)

        // Pérdida por foto: N va a la más borrosa; D al producto; O, P y T a la etiqueta.
        val perdidas = mutableMapOf<TipoFoto, Double>()
        perdidas.merge(masBorrosa.tipo, perdida(PESO_N, n), Double::plus)
        perdidas.merge(TipoFoto.PRODUCTO, perdida(PESO_D, d), Double::plus)
        perdidas.merge(
            TipoFoto.ETIQUETA, perdida(PESO_O, o) + perdida(PESO_P, p) + perdida(PESO_T, t), Double::plus,
        )
        val masDebil = perdidas.maxBy { it.value }.takeIf { it.value >= PERDIDA_MIN }?.key

        return IndiceConfiabilidad(n, d, o, p, t, icl, banda(icl), masDebil)
    }

    /** Media geométrica ponderada: ICL = N^0.25 · D^0.20 · O^0.20 · P^0.25 · T^0.10. */
    fun combinar(n: Double, d: Double, o: Double, p: Double, t: Double): Double = exp(
        PESO_N * ln(piso(n)) + PESO_D * ln(piso(d)) + PESO_O * ln(piso(o)) +
            PESO_P * ln(piso(p)) + PESO_T * ln(piso(t))
    )

    fun banda(icl: Double): BandaIcl = when {
        icl >= CORTE_ALTA -> BandaIcl.ALTA
        icl >= CORTE_MEDIA -> BandaIcl.MEDIA
        else -> BandaIcl.BAJA
    }

    /** En el umbral de borrosa vale 0.5; con el doble o más vale 1. */
    fun puntajeNitidez(nitidez: Double): Double {
        val min = Umbrales.nitidezMin
        return if (min <= 0.0) 1.0 else clamp(nitidez / (2 * min))
    }

    /** En el área mínima vale 0.5; con el doble o más vale 1. Sin código en la foto: 0.5. */
    fun puntajeDistancia(areaCodigo: Double?, escaneoValido: Boolean): Double {
        val min = Umbrales.areaCodigoMin
        if (areaCodigo == null || min <= 0.0) return 0.5
        val area = clamp(areaCodigo / (2 * min))
        // Con un escaneo válido, el producto ya está identificado: la distancia no baja de 0.5.
        return if (escaneoValido) maxOf(area, 0.5) else area
    }

    /** √(s_conf · s_texto): confianza del OCR y cantidad de letras y números leídos. */
    fun puntajeOcr(a: AnalisisImagen): Double {
        val conf = a.confianzaOcr?.toDouble() ?: Umbrales.ocrConfianzaMin.toDouble()
        val sConf = clamp(0.5 + 1.25 * (conf - Umbrales.ocrConfianzaMin))
        val alfanumericos = a.textoOcr.count { it.isLetterOrDigit() }
        val min = Umbrales.ocrCaracteresMin
        val sTexto = if (min <= 0) 1.0 else clamp(alfanumericos / (2.0 * min))
        return sqrt(sConf * sTexto)
    }

    /**
     * Base (con $ / sin $ / solo unitario) × factor (precio unitario visible o no).
     * "Solo unitario" = el candidato ganador es un precio por kg/L (no se encontró el principal).
     * No se usa `valor == precioUnitario`: en un producto de 1 L ambos coinciden legítimamente.
     */
    fun puntajePrecio(p: PrecioDetectado): Double {
        val valor = p.valor ?: return 0.0
        val ganador = p.candidatos.firstOrNull()
        val soloUnitario = ganador != null && ganador.esUnitario && Math.abs(ganador.valor - valor) < 0.005
        val base = when {
            soloUnitario -> 0.6
            '$' in p.textoFuente || ganador?.tieneSigno == true -> 1.0
            else -> 0.8
        }
        val factor = if (p.tienePrecioUnitario) 1.0 else 0.6
        return base * factor
    }

    fun puntajeTipo(tipo: TipoPrecio): Double? = when (tipo) {
        TipoPrecio.REGULAR -> 1.0
        TipoPrecio.OFERTA -> 0.8
        TipoPrecio.VOLUMEN -> 0.7
        TipoPrecio.DESCONOCIDO -> null
    }

    private fun perdida(peso: Double, x: Double) = -peso * ln(piso(x))
    private fun piso(x: Double) = x.coerceAtLeast(PISO)
    private fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
}
