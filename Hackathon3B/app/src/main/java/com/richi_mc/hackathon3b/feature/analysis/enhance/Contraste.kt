package com.richi_mc.hackathon3b.feature.analysis.enhance

/** Lógica de píxeles pura (sin Android) del realce previo al re-OCR (PLAN3 §5, pasos 4). */
object Contraste {

    /** Luminancia 0..255 de un píxel ARGB (Rec. 601, enteros). */
    fun luminancia(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    /**
     * Valor de luminancia por debajo del cual queda la fracción [p] (0..1) de los píxeles.
     * Usa un histograma de 256 niveles. Con la lista vacía devuelve 0.
     */
    fun percentil(luminancias: IntArray, p: Double): Int {
        if (luminancias.isEmpty()) return 0
        val histograma = IntArray(256)
        for (v in luminancias) histograma[v.coerceIn(0, 255)]++
        val objetivo = (p.coerceIn(0.0, 1.0) * luminancias.size).toLong()
        var acumulado = 0L
        for (nivel in 0..255) {
            acumulado += histograma[nivel]
            if (acumulado > objetivo) return nivel
        }
        return 255
    }

    /**
     * Estira el rango entre los percentiles [pBajo] y [pAlto] a 0..255 (lo de fuera se satura).
     * Si el rango es nulo (imagen plana) devuelve una copia sin cambios.
     * Con [umbralBinario] no nulo, el resultado se binariza: >= umbral -> 255, si no 0.
     */
    fun estirar(
        luminancias: IntArray,
        pBajo: Double = PERCENTIL_BAJO,
        pAlto: Double = PERCENTIL_ALTO,
        umbralBinario: Int? = null,
    ): IntArray {
        if (luminancias.isEmpty()) return IntArray(0)
        val bajo = percentil(luminancias, pBajo)
        val alto = percentil(luminancias, pAlto)
        val salida = IntArray(luminancias.size)
        if (alto <= bajo) {
            for (i in luminancias.indices) salida[i] = luminancias[i].coerceIn(0, 255)
        } else {
            val rango = (alto - bajo).toFloat()
            for (i in luminancias.indices) {
                salida[i] = (((luminancias[i] - bajo) * 255f) / rango).toInt().coerceIn(0, 255)
            }
        }
        if (umbralBinario != null) {
            for (i in salida.indices) salida[i] = if (salida[i] >= umbralBinario) 255 else 0
        }
        return salida
    }

    const val PERCENTIL_BAJO = 0.02
    const val PERCENTIL_ALTO = 0.98
}
