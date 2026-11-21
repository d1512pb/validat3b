package com.richi_mc.hackathon3b.feature.analysis.enhance

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.richi_mc.hackathon3b.core.model.CajaPx
import kotlin.math.roundToInt

/** Recorte ya ampliado y realzado, con lo necesario para volver a coordenadas de la imagen completa. */
class RecorteMejorado(val bitmap: Bitmap, val escala: Float, val origenX: Int, val origenY: Int)

/** Umbral fijo opcional (blanco y negro); desactivado por defecto, se activa solo si el gris lee peor. */
const val UMBRAL_BINARIO = 128

/** Lado mayor máximo del recorte ampliado, para no reventar memoria. */
private const val LADO_MAXIMO_PX = 4096

/**
 * Decodifica la foto en [path] (sin inSampleSize), recorta [zona], la amplía, la pasa a grises y estira el
 * contraste. null si no se puede decodificar o la zona queda fuera de la imagen. Quien llama debe reciclar
 * `bitmap`.
 */
fun prepararRecorte(
    path: String,
    zona: CajaPx,
    escalaMaxima: Float = GeometriaRecorte.ESCALA_MAXIMA,
    umbralBinario: Boolean = false,
): RecorteMejorado? {
    val completa = BitmapFactory.decodeFile(path) ?: return null
    var recortado: Bitmap? = null
    var ampliado: Bitmap? = null
    try {
        val caja = GeometriaRecorte.recortarABordes(zona, completa.width, completa.height) ?: return null
        recortado = Bitmap.createBitmap(completa, caja.left, caja.top, caja.ancho, caja.alto)
        var escala = GeometriaRecorte.escala(zona.alto, escalaMaxima)
        val ladoMayor = maxOf(caja.ancho, caja.alto) * escala
        if (ladoMayor > LADO_MAXIMO_PX) escala = (escala * LADO_MAXIMO_PX / ladoMayor).coerceAtLeast(1f)

        ampliado = if (escala > 1f) {
            Bitmap.createScaledBitmap(
                recortado,
                (caja.ancho * escala).roundToInt().coerceAtLeast(1),
                (caja.alto * escala).roundToInt().coerceAtLeast(1),
                true,
            )
        } else recortado

        val w = ampliado.width
        val h = ampliado.height
        val pixeles = IntArray(w * h)
        ampliado.getPixels(pixeles, 0, w, 0, 0, w, h)
        val lum = IntArray(pixeles.size) { Contraste.luminancia(pixeles[it]) }
        val estirado = Contraste.estirar(lum, umbralBinario = if (umbralBinario) UMBRAL_BINARIO else null)
        for (i in pixeles.indices) pixeles[i] = Color.rgb(estirado[i], estirado[i], estirado[i])

        val salida = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        salida.setPixels(pixeles, 0, w, 0, 0, w, h)
        return RecorteMejorado(salida, w.toFloat() / caja.ancho, caja.left, caja.top)
    } finally {
        if (ampliado != null && ampliado !== recortado) ampliado.recycle()
        recortado?.recycle()
        completa.recycle()
    }
}

/** Solo el bitmap mejorado (para la demo); quien llama lo recicla. */
fun recorteMejorado(path: String, zona: CajaPx): Bitmap? = prepararRecorte(path, zona)?.bitmap
