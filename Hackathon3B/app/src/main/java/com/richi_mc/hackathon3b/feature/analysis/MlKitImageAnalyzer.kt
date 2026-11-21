package com.richi_mc.hackathon3b.feature.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import android.util.Log
import com.richi_mc.hackathon3b.core.model.AnalisisImagen
import com.richi_mc.hackathon3b.core.model.LineaOcr
import com.richi_mc.hackathon3b.core.model.ImageAnalyzer
import com.richi_mc.hackathon3b.core.model.PrecioDetectado
import com.richi_mc.hackathon3b.core.model.PriceParser
import com.richi_mc.hackathon3b.core.model.Problema
import com.richi_mc.hackathon3b.core.model.TipoFoto
import com.richi_mc.hackathon3b.core.model.Umbrales
import com.richi_mc.hackathon3b.feature.labeling.MlKitLabeler
import com.richi_mc.hackathon3b.feature.labeling.problemasPorEtiquetas
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.Closeable
import kotlin.math.max

/**
 * Pipeline on-device de PLAN.md §8: nitidez, brillo, código de barras, OCR y precio.
 *
 * Crea un cliente ML Kit de código de barras y uno de OCR por instancia; llama a [close]
 * cuando ya no se use (el contenedor de la app vive lo mismo que el proceso, así que basta con eso).
 *
 * El [labeler] (opcional) lo cierra quien lo creó. Sin él no se etiqueta ni se agregan problemas por etiquetas.
 *
 * Si la imagen no se puede decodificar devuelve un análisis con nitidez/brillo 0 y los problemas
 * bloqueantes BORROSA y OSCURA (para que la UI pida retomar la foto) en lugar de lanzar excepción.
 */
class MlKitImageAnalyzer(
    @Suppress("unused") context: Context,
    private val priceParser: PriceParser,
    private val labeler: MlKitLabeler? = null,
) : ImageAnalyzer, Closeable {

    private val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
            )
            .build()
    )
    private val textRecognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun analizar(path: String, tipo: TipoFoto): AnalisisImagen =
        withContext(Dispatchers.Default) {
            val bitmap = decodificar(path)
                ?: return@withContext AnalisisImagen(
                    tipo = tipo, path = path, nitidez = 0.0, brillo = 0.0, textoOcr = "",
                    confianzaOcr = null, codigoBarras = null, formatoCodigo = null,
                    problemas = listOf(Problema.BORROSA, Problema.OSCURA),
                )
            try {
                analizarBitmap(bitmap, path, tipo)
            } finally {
                bitmap.recycle()
            }
        }

    /** Precio de una imagen ya analizada (lo usa la pantalla de revisión). */
    fun precioDe(analisis: AnalisisImagen): PrecioDetectado = priceParser.parse(analisis.textoOcr)

    override fun close() {
        barcodeScanner.close()
        textRecognizer.close()
    }

    private suspend fun analizarBitmap(bitmap: Bitmap, path: String, tipo: TipoFoto): AnalisisImagen =
        coroutineScope {
            val problemas = mutableListOf<Problema>()

            // 1-2. Nitidez y brillo sobre gris a 512 px.
            val gris = bitmap.aGris512()
            val nitidez = gris.nitidez()
            val brillo = gris.brillo()
            if (nitidez < Umbrales.nitidezMin) problemas += Problema.BORROSA
            if (brillo < Umbrales.brilloMin) problemas += Problema.OSCURA
            else if (brillo > Umbrales.brilloMax) problemas += Problema.SOBREEXPUESTA

            // 3 y 6. Código de barras, OCR y etiquetado en paralelo.
            val imagen = InputImage.fromBitmap(bitmap, 0)
            val codigoJob = async { if (tipo == TipoFoto.FACHADA) null else leerCodigo(imagen, bitmap) }
            val ocrJob = async { leerTexto(imagen) }
            val etiquetasJob = async { labeler?.etiquetar(imagen).orEmpty() }
            val codigo = codigoJob.await()
            val ocr = ocrJob.await()
            val etiquetas = etiquetasJob.await()

            // 3-5. Código de barras: obligatorio en producto, opcional en etiqueta.
            if (tipo == TipoFoto.PRODUCTO) {
                when {
                    codigo == null -> problemas += Problema.SIN_CODIGO_BARRAS
                    !CodigoBarras.checksumValido(codigo.valor, codigo.formato) -> problemas += Problema.CODIGO_INVALIDO
                    codigo.area != null && codigo.area < Umbrales.areaCodigoMin -> problemas += Problema.DEMASIADO_LEJOS
                }
            }

            // 6. OCR legible (no aplica a fachada).
            if (tipo != TipoFoto.FACHADA) {
                val alfanum = ocr.texto.count { it.isLetterOrDigit() }
                val confianzaBaja = ocr.confianza != null && ocr.confianza < Umbrales.ocrConfianzaMin
                if (alfanum < Umbrales.ocrCaracteresMin || confianzaBaja) problemas += Problema.SIN_TEXTO_LEGIBLE
            }

            // 7. Precio (solo etiqueta).
            if (tipo == TipoFoto.ETIQUETA) {
                val precio = priceParser.parse(ocr.texto)
                if (precio.valor == null) problemas += Problema.SIN_PRECIO
                if (!precio.tienePrecioUnitario) problemas += Problema.SIN_PRECIO_UNITARIO
            }

            // 8. Etiquetas de imagen: producto visible, fachada de tienda, foto de pantalla.
            if (labeler != null) {
                Log.d("Etiquetas", "$tipo: " + etiquetas.joinToString { "${it.texto} ${"%.2f".format(it.confianza)}" })
            }
            for (p in problemasPorEtiquetas(tipo, etiquetas, hayCodigo = codigo != null)) {
                if (p !in problemas) problemas += p
            }

            AnalisisImagen(
                tipo = tipo, path = path, nitidez = nitidez, brillo = brillo,
                textoOcr = ocr.texto, confianzaOcr = ocr.confianza,
                codigoBarras = codigo?.valor, formatoCodigo = codigo?.formato,
                areaCodigo = codigo?.area, problemas = problemas,
                etiquetas = etiquetas, lineasOcr = ocr.lineas,
            )
        }

    private class CodigoLeido(val valor: String, val formato: String?, val area: Double?)
    private class TextoLeido(val texto: String, val confianza: Float?, val lineas: List<LineaOcr> = emptyList())

    private suspend fun leerCodigo(imagen: InputImage, bitmap: Bitmap): CodigoLeido? {
        val barcodes = seguro { barcodeScanner.process(imagen).await() } ?: return null
        val imgArea = bitmap.width.toDouble() * bitmap.height
        val leidos = barcodes.mapNotNull { b ->
            val valor = (b.rawValue ?: b.displayValue)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val formato = nombreFormato(b.format)
            val area = b.boundingBox?.let { it.width().toDouble() * it.height() / imgArea }
            CodigoLeido(valor, formato, area)
        }
        // Prefiere un código con checksum válido; entre iguales, el más grande.
        return leidos
            .sortedWith(
                compareByDescending<CodigoLeido> { CodigoBarras.checksumValido(it.valor, it.formato) }
                    .thenByDescending { it.area ?: 0.0 }
            )
            .firstOrNull()
    }

    private suspend fun leerTexto(imagen: InputImage): TextoLeido {
        val resultado = seguro { textRecognizer.process(imagen).await() } ?: return TextoLeido("", null)
        val confianzas = resultado.textBlocks
            .flatMap { it.lines }.flatMap { it.elements }
            .map { it.confidence }
        return TextoLeido(
            texto = resultado.text,
            confianza = if (confianzas.isEmpty()) null else confianzas.average().toFloat(),
            lineas = resultado.textBlocks.flatMap { it.lines }.map { l ->
                LineaOcr(l.text, l.boundingBox?.height() ?: 0, l.boundingBox?.top ?: 0)
            },
        )
    }

    /** Un fallo de ML Kit (modelo no disponible, etc.) se trata como "no se detectó nada". */
    private inline fun <T> seguro(bloque: () -> T): T? =
        try {
            bloque()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private fun nombreFormato(formato: Int): String? = when (formato) {
        Barcode.FORMAT_EAN_13 -> "EAN_13"
        Barcode.FORMAT_EAN_8 -> "EAN_8"
        Barcode.FORMAT_UPC_A -> "UPC_A"
        Barcode.FORMAT_UPC_E -> "UPC_E"
        else -> null
    }

    /** Decodifica con inSampleSize (lado mayor >= ~1600 px) y aplica la orientación EXIF. */
    private fun decodificar(path: String): Bitmap? {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, limites)
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return null
        var sample = 1
        val lado = max(limites.outWidth, limites.outHeight)
        while (lado / (sample * 2) >= Umbrales.LADO_MAYOR_PX) sample *= 2
        val opciones = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeFile(path, opciones) ?: return null
        val grados = try {
            when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (e: Exception) {
            0f
        }
        if (grados == 0f) return bmp
        val rotado = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(grados) }, true)
        if (rotado !== bmp) bmp.recycle()
        return rotado
    }
}
