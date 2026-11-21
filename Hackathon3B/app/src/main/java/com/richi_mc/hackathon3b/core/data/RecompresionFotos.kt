package com.richi_mc.hackathon3b.core.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * Recompresión de emergencia para reintentar tras un 413 (contratos.md §5): lado mayor 1280 px, JPEG q75,
 * respetando la orientación EXIF. `ImageUtils` no sirve porque usa constantes fijas (1600 px, q85).
 */
internal object RecompresionFotos {
    const val LADO_MAYOR_PX = 1280
    const val CALIDAD_JPEG = 75

    /** Escribe una copia reducida de [src] en [destDir] y la devuelve. No borra [src]. Llamar fuera del hilo principal. */
    fun recomprimir(src: File, destDir: File): File {
        destDir.mkdirs()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "No se pudo leer la imagen" }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= LADO_MAYOR_PX) sample *= 2

        val decoded = BitmapFactory.decodeFile(
            src.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: error("No se pudo decodificar la imagen")

        val orientation = ExifInterface(src.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val scale = LADO_MAYOR_PX.toFloat() / max(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
            }
            if (scale < 1f) postScale(scale, scale)
        }
        val transformed = if (matrix.isIdentity) decoded else
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (transformed !== decoded) decoded.recycle()

        val out = File(destDir, "${UUID.randomUUID()}.jpg")
        try {
            out.outputStream().use { transformed.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, it) }
        } catch (e: Exception) {
            out.delete()
            throw e
        } finally {
            transformed.recycle()
        }
        return out
    }
}
