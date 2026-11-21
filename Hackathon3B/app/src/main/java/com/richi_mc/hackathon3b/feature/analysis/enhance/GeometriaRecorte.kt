package com.richi_mc.hackathon3b.feature.analysis.enhance

import com.richi_mc.hackathon3b.core.model.CajaPx
import com.richi_mc.hackathon3b.core.model.ElementoOcr
import com.richi_mc.hackathon3b.core.model.LineaOcr
import kotlin.math.roundToInt

/** Geometría pura (sin Android) del recorte ampliado: escala, recorte a bordes y vuelta a la imagen completa. */
object GeometriaRecorte {

    /** Alto objetivo, en px, de una línea de texto dentro del recorte ampliado. */
    const val ALTO_LINEA_OBJETIVO_PX = 120f

    /** Ampliación máxima; bajarla a 2f si el re-OCR resulta lento. */
    const val ESCALA_MAXIMA = 3f

    /**
     * Escala = clamp(120 / alto de línea estimado, 1, [maxima]). La zona viene ampliada 50 % arriba y
     * abajo, así que la línea mide aproximadamente `zonaAlto / 2`.
     */
    fun escala(zonaAlto: Int, maxima: Float = ESCALA_MAXIMA): Float {
        val altoLinea = zonaAlto / 2f
        if (altoLinea <= 0f) return 1f
        return (ALTO_LINEA_OBJETIVO_PX / altoLinea).coerceIn(1f, maxima)
    }

    /** Recorta [zona] a los bordes de una imagen de [ancho] x [alto]; null si queda vacía. */
    fun recortarABordes(zona: CajaPx, ancho: Int, alto: Int): CajaPx? {
        val c = CajaPx(
            left = zona.left.coerceIn(0, ancho),
            top = zona.top.coerceIn(0, alto),
            right = zona.right.coerceIn(0, ancho),
            bottom = zona.bottom.coerceIn(0, alto),
        )
        return if (c.ancho <= 0 || c.alto <= 0) null else c
    }

    /** Caja en coordenadas del recorte ampliado -> coordenadas de la imagen completa. */
    fun aImagenCompleta(caja: CajaPx, escala: Float, origenX: Int, origenY: Int): CajaPx = CajaPx(
        left = (caja.left / escala).roundToInt() + origenX,
        top = (caja.top / escala).roundToInt() + origenY,
        right = (caja.right / escala).roundToInt() + origenX,
        bottom = (caja.bottom / escala).roundToInt() + origenY,
    )

    /** Convierte línea, elementos y símbolos a coordenadas de la imagen completa. */
    fun aImagenCompleta(linea: LineaOcr, escala: Float, origenX: Int, origenY: Int): LineaOcr = LineaOcr(
        texto = linea.texto,
        caja = aImagenCompleta(linea.caja, escala, origenX, origenY),
        elementos = linea.elementos.map { e ->
            ElementoOcr(
                texto = e.texto,
                caja = aImagenCompleta(e.caja, escala, origenX, origenY),
                simbolos = e.simbolos.map { aImagenCompleta(it, escala, origenX, origenY) },
            )
        },
    )
}
