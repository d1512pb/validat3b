package com.richi_mc.hackathon3b.feature.analysis.price

import com.richi_mc.hackathon3b.core.model.CajaPx
import com.richi_mc.hackathon3b.core.model.FormaPrecio
import com.richi_mc.hackathon3b.core.model.LineaOcr
import com.richi_mc.hackathon3b.core.model.PrecioDetectado

/**
 * Decisiones puras (Kotlin, sin Android) para el re-OCR del recorte de la zona del precio
 * (PLAN3 §5.A). La integración las usa para decidir si correr el [com.richi_mc.hackathon3b.core.model.OcrEnhancer]
 * y sobre qué zona.
 */

private fun contieneDigitos(l: LineaOcr) = l.texto.any { it.isDigit() }

/**
 * true si conviene re-leer el recorte: confianza < [PesosPrecio.CONFIANZA_REOCR]; el ganador es ENTERO
 * o CENTAVOS_PEGADOS sin evidencia; o no hubo candidatos pero existe una línea con dígitos.
 */
fun necesitaReOcr(precio: PrecioDetectado, lineas: List<LineaOcr>): Boolean {
    val ganador = precio.candidatos.firstOrNull() ?: return lineas.any(::contieneDigitos)
    if ((precio.confianza ?: 1f) < PesosPrecio.CONFIANZA_REOCR) return true
    return when (ganador.forma) {
        FormaPrecio.ENTERO -> true
        FormaPrecio.CENTAVOS_PEGADOS -> !pegadosConEvidencia(lineas, ganador.caja)
        else -> false
    }
}

/** Busca el elemento cuya caja es la del candidato y mira la altura de sus símbolos. */
private fun pegadosConEvidencia(lineas: List<LineaOcr>, caja: CajaPx): Boolean {
    val el = lineas.asSequence().flatMap { it.elementos.asSequence() }.firstOrNull { it.caja == caja }
        ?: return false
    return evidenciaSimbolos(el.simbolos, el.texto.count { it.isDigit() })
}

/**
 * Zona a recortar: caja del ganador (o de la línea más alta con dígitos), ampliada
 * [PesosPrecio.ZONA_EXTRA_DERECHA] del ancho a la derecha y [PesosPrecio.ZONA_EXTRA_VERTICAL] del alto
 * arriba y abajo (donde quedan los centavos en superíndice), recortada a los bordes. null si no hay nada.
 */
fun zonaReOcr(lineas: List<LineaOcr>, precio: PrecioDetectado, anchoImg: Int, altoImg: Int): CajaPx? {
    if (anchoImg <= 0 || altoImg <= 0) return null
    val base = precio.candidatos.firstOrNull()?.caja
        ?: lineas.filter(::contieneDigitos).maxByOrNull { it.alturaPx }?.caja
        ?: return null
    // Sin ancho útil (líneas con el constructor de compatibilidad): toda la franja horizontal.
    val sinAncho = base.ancho <= 0
    val left = if (sinAncho) 0 else base.left
    val right = if (sinAncho) anchoImg else base.right + (base.ancho * PesosPrecio.ZONA_EXTRA_DERECHA).toInt()
    val extraV = (base.alto * PesosPrecio.ZONA_EXTRA_VERTICAL).toInt()
    val zona = CajaPx(
        left = left.coerceIn(0, anchoImg),
        top = (base.top - extraV).coerceIn(0, altoImg),
        right = right.coerceIn(0, anchoImg),
        bottom = (base.bottom + extraV).coerceIn(0, altoImg),
    )
    return zona.takeIf { it.ancho > 0 && it.alto > 0 }
}
