package com.richi_mc.hackathon3b.feature.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.richi_mc.hackathon3b.core.model.Umbrales
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.max

/** Utilidades de imagen para las fotos capturadas. Reutilizables (p. ej. importar de galería). */
object ImageUtils {

    /**
     * Decodifica [src], aplica la rotación/espejo EXIF, reescala para que el lado mayor sea
     * como máximo [Umbrales.LADO_MAYOR_PX] y lo comprime a JPEG [Umbrales.CALIDAD_JPEG] en
     * `destDir/<uuid>.jpg`. No borra [src]. Llamar fuera del hilo principal.
     */
    fun guardarReescalada(src: File, destDir: File): File {
        destDir.mkdirs()
        val maxSide = Umbrales.LADO_MAYOR_PX

        // 1) Dimensiones sin cargar píxeles, para submuestrear al decodificar.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "No se pudo leer la imagen" }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2

        val decoded = BitmapFactory.decodeFile(
            src.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: error("No se pudo decodificar la imagen")

        // 2) Rotación EXIF + reescalado en una sola transformación.
        // Formatos sin EXIF legible (o EXIF corrupto) se tratan como orientación normal.
        val orientation = runCatching {
            ExifInterface(src.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val scale = maxSide.toFloat() / max(decoded.width, decoded.height)
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

        // 3) JPEG a destino (sin EXIF: la rotación ya está aplicada a los píxeles).
        val out = File(destDir, "${UUID.randomUUID()}.jpg")
        try {
            out.outputStream().use { transformed.compress(Bitmap.CompressFormat.JPEG, Umbrales.CALIDAD_JPEG, it) }
        } catch (e: Exception) {
            out.delete()
            throw e
        } finally {
            transformed.recycle()
        }
        return out
    }

    /**
     * Copia la imagen del [uri] (selector de fotos / galería) a un temporal y la guarda con
     * [guardarReescalada] en [destDir] (siempre JPEG, lado mayor acotado, rotación EXIF aplicada).
     * Acepta JPEG, PNG, WebP y HEIC/HEIF (BitmapFactory los decodifica en API 29+).
     * La copia es byte a byte, así que el temporal conserva los metadatos EXIF originales.
     * Lanza [IOException] con mensaje en español si no se puede leer o no es una imagen válida.
     * Llamar fuera del hilo principal.
     */
    fun importarDesdeUri(context: Context, uri: Uri, destDir: File): File {
        // Extensión neutra: BitmapFactory/ExifInterface detectan el formato por contenido, no por nombre.
        val tmp = File.createTempFile("import_", ".img", context.cacheDir)
        try {
            val input = try {
                context.contentResolver.openInputStream(uri)
            } catch (e: Exception) {
                throw IOException("No se pudo abrir la imagen seleccionada.", e)
            } ?: throw IOException("No se pudo abrir la imagen seleccionada.")
            input.use { origen -> tmp.outputStream().use { origen.copyTo(it) } }
            if (tmp.length() == 0L) throw IOException("La imagen seleccionada está vacía.")
            return try {
                guardarReescalada(tmp, destDir)
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                throw IOException("No se pudo procesar la imagen: elige otra foto (JPG, PNG, WebP o HEIC).", e)
            }
        } finally {
            tmp.delete()
        }
    }
}
