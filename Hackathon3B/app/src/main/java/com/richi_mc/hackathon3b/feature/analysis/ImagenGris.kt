package com.richi_mc.hackathon3b.feature.analysis

import android.graphics.Bitmap

/** Imagen en escala de grises (luminancia 0..255) por filas. */
class ImagenGris(val gris: DoubleArray, val w: Int, val h: Int) {
    fun nitidez(): Double = Laplaciano.varianza(gris, w, h)
    fun brillo(): Double = Laplaciano.brilloMedio(gris)
}

/** Reduce a [ancho] px de ancho (por defecto 512, la escala a la que se calibra [com.richi_mc.hackathon3b.core.model.Umbrales.nitidezMin]) y convierte a gris. */
fun Bitmap.aGris512(ancho: Int = 512): ImagenGris {
    val w = ancho
    val h = (height * w.toFloat() / width).toInt().coerceAtLeast(1)
    val escalado = if (width == w && height == h) this else Bitmap.createScaledBitmap(this, w, h, true)
    val px = IntArray(w * h)
    escalado.getPixels(px, 0, w, 0, 0, w, h)
    if (escalado !== this) escalado.recycle()
    val g = DoubleArray(px.size) { i ->
        val c = px[i]
        0.299 * (c shr 16 and 0xFF) + 0.587 * (c shr 8 and 0xFF) + 0.114 * (c and 0xFF)
    }
    return ImagenGris(g, w, h)
}
