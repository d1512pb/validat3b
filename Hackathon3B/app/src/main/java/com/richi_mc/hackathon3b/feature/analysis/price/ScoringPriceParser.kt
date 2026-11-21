package com.richi_mc.hackathon3b.feature.analysis.price

import com.richi_mc.hackathon3b.core.model.CajaPx
import com.richi_mc.hackathon3b.core.model.CandidatoPrecio
import com.richi_mc.hackathon3b.core.model.LineaOcr
import com.richi_mc.hackathon3b.core.model.PrecioDetectado
import com.richi_mc.hackathon3b.core.model.PriceParser
import com.richi_mc.hackathon3b.core.model.TipoPrecio
import com.richi_mc.hackathon3b.core.model.Umbrales

/**
 * Parser de precio por puntuación (PLAN3). Junta todos los números que podrían ser el precio
 * ([Etiqueta]), puntúa cada uno con [PesosPrecio] y elige el de mayor puntuación; esa
 * puntuación es la confianza. Kotlin puro, sin Android.
 */
class ScoringPriceParser : PriceParser {

    override fun parse(lineas: List<LineaOcr>, lineasReOcr: List<LineaOcr>): PrecioDetectado {
        val etiqueta = Etiqueta(lineas)
        val cands = etiqueta.candidatos.toMutableList()
        if (lineasReOcr.isNotEmpty()) incorporarReOcr(etiqueta, cands, Etiqueta(lineasReOcr))

        cands.forEach { it.puntuacion = puntuar(it) }
        val unitarios = cands.filter { it.esUnitario }.sortedByDescending { it.puntuacion }
        val hayAnterior = cands.any { it.esAnterior }

        // Fusión: mismo valor leído en dos formas/lugares → se queda el mejor y suma un poco.
        val fusionados = cands.groupBy { claveValor(it.valor) }.values.map { grupo ->
            val mejor = grupo.maxWith(compararCand)
            if (grupo.size > 1) mejor.puntuacion = (mejor.puntuacion + PesosPrecio.FUSION).coerceIn(0f, 1f)
            mejor
        }.sortedWith(compararCand.reversed())

        val ganador = fusionados.firstOrNull()
        var confianza = ganador?.puntuacion ?: 0f
        val segundo = fusionados.getOrNull(1)
        if (ganador != null && segundo != null && ganador.puntuacion - segundo.puntuacion < PesosPrecio.MARGEN_AMBIGUEDAD) {
            confianza = (confianza + PesosPrecio.AMBIGUEDAD).coerceIn(0f, 1f)
        }
        val valor = ganador?.valor?.takeIf { confianza >= Umbrales.confianzaPrecioMin }

        val texto = etiqueta.textoNormalizado
        val tipo = when {
            RegexPrecio.VOLUMEN.containsMatchIn(texto) -> TipoPrecio.VOLUMEN
            hayAnterior && ganador != null && !ganador.esAnterior -> TipoPrecio.OFERTA
            RegexPrecio.OFERTA.containsMatchIn(texto) -> TipoPrecio.OFERTA
            valor != null -> TipoPrecio.REGULAR
            else -> TipoPrecio.DESCONOCIDO
        }

        return PrecioDetectado(
            valor = valor,
            tipo = tipo,
            tienePrecioUnitario = unitarios.isNotEmpty(),
            textoFuente = ganador?.textoFuente.orEmpty(),
            precioUnitario = unitarios.firstOrNull()?.valor,
            confianza = confianza,
            candidatos = fusionados.take(PesosPrecio.MAX_CANDIDATOS).map { it.aCandidato() },
        )
    }

    private val compararCand = compareBy<Cand>({ it.puntuacion }, { it.alturaRelativa }, { -it.linea })

    private fun puntuar(c: Cand): Float {
        var p = PesosPrecio.ALTURA * c.alturaRelativa
        if (c.alturaRelativa >= PesosPrecio.UMBRAL_LINEA_ALTA) p += PesosPrecio.LINEA_MAS_ALTA
        if (c.tieneSigno) p += PesosPrecio.SIGNO
        if (c.evidencia) p += PesosPrecio.CENTAVOS
        if (c.cercaClave) p += PesosPrecio.PALABRA_CERCANA
        if (c.valor >= PesosPrecio.RANGO_TIPICO_MIN && c.valor <= PesosPrecio.RANGO_TIPICO_MAX) p += PesosPrecio.RANGO_TIPICO
        if (c.coincideReOcr) p += PesosPrecio.COINCIDE_REOCR
        if (c.esUnitario) p += PesosPrecio.UNITARIO
        if (c.esAnterior) p += PesosPrecio.ANTERIOR
        if (c.pegadosSinEvidencia) p += PesosPrecio.PEGADOS_SIN_EVIDENCIA
        if (c.enteroSinEvidencia) p += PesosPrecio.ENTERO_SIN_EVIDENCIA
        return p.coerceIn(0f, 1f)
    }

    /**
     * Re-OCR del recorte (PLAN3 §5.A.6): un valor que coincide con uno original lo refuerza; un valor
     * nuevo entra como candidato con la altura de la línea original que más se solape.
     */
    private fun incorporarReOcr(original: Etiqueta, cands: MutableList<Cand>, recorte: Etiqueta) {
        val valoresRecorte = recorte.candidatos.map { claveValor(it.valor) }.toSet()
        val valoresOriginales = cands.map { claveValor(it.valor) }.toSet()
        cands.forEach { if (claveValor(it.valor) in valoresRecorte) it.coincideReOcr = true }

        for (r in recorte.candidatos) {
            if (claveValor(r.valor) in valoresOriginales) continue
            val idx = lineaMasSolapada(original.lineas, r.caja)
            val linea = idx?.let { original.lineas[it] }
            val altura = if (linea != null && original.alturaMax > 0) {
                (linea.caja.alto.toFloat() / original.alturaMax).coerceIn(0f, 1f)
            } else 0f
            val enLinea = idx?.let { i -> cands.filter { it.linea == i } }.orEmpty()
            cands += Cand(
                valor = r.valor, forma = r.forma, textoFuente = r.textoFuente, caja = r.caja,
                alturaRelativa = altura, tieneSigno = r.tieneSigno, evidencia = r.evidencia,
                enteroSinEvidencia = r.enteroSinEvidencia, linea = idx ?: -1, pos = r.pos,
                esUnitario = r.esUnitario || enLinea.any { it.esUnitario },
                esAnterior = r.esAnterior || enLinea.any { it.esAnterior },
                cercaClave = idx?.let { original.cercaClave(it) } ?: false,
            )
        }
    }

    private fun lineaMasSolapada(lineas: List<LineaOcr>, caja: CajaPx): Int? {
        if (lineas.isEmpty()) return null
        fun solape(a: CajaPx, b: CajaPx): Long {
            val w = minOf(a.right, b.right) - maxOf(a.left, b.left)
            val h = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
            return if (w > 0 && h > 0) w.toLong() * h else 0L
        }
        fun solapeV(a: CajaPx, b: CajaPx): Int = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        val porArea = lineas.indices.maxByOrNull { solape(lineas[it].caja, caja) }
        if (porArea != null && solape(lineas[porArea].caja, caja) > 0) return porArea
        val porVertical = lineas.indices.maxByOrNull { solapeV(lineas[it].caja, caja) }
        return porVertical?.takeIf { solapeV(lineas[it].caja, caja) > 0 }
    }

    private fun Cand.aCandidato() = CandidatoPrecio(
        valor = valor, forma = forma, textoFuente = textoFuente, caja = caja,
        alturaRelativa = alturaRelativa, tieneSigno = tieneSigno, esUnitario = esUnitario,
        esAnterior = esAnterior, puntuacion = puntuacion,
    )
}
