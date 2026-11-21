package com.richi_mc.hackathon3b.feature.analysis

/** Métricas de nitidez y brillo en Kotlin puro (testeables en JVM). La conversión desde Bitmap está en [ImagenGris]. */
object Laplaciano {
    /**
     * Varianza del Laplaciano 3x3 (vecinos 4-conexos) sobre una imagen en gris [w] x [h]
     * almacenada por filas. Alta = nítida, baja = borrosa. Devuelve 0.0 si la imagen es menor a 3x3.
     */
    fun varianza(gris: DoubleArray, w: Int, h: Int): Double {
        if (w < 3 || h < 3 || gris.size < w * h) return 0.0
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val lap = gris[i - w] + gris[i + w] + gris[i - 1] + gris[i + 1] - 4 * gris[i]
                sum += lap
                sumSq += lap * lap
                n++
            }
        }
        val mean = sum / n
        return (sumSq / n - mean * mean).coerceAtLeast(0.0)
    }

    /** Luminancia media 0..255 de una imagen en gris. */
    fun brilloMedio(gris: DoubleArray): Double =
        if (gris.isEmpty()) 0.0 else gris.sum() / gris.size
}

/** Atajo: igual que [Laplaciano.brilloMedio]. */
fun brilloMedio(gris: DoubleArray): Double = Laplaciano.brilloMedio(gris)
