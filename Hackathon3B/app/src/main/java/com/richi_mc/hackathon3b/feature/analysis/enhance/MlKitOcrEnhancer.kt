package com.richi_mc.hackathon3b.feature.analysis.enhance

import android.content.Context
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.richi_mc.hackathon3b.core.model.CajaPx
import com.richi_mc.hackathon3b.core.model.ElementoOcr
import com.richi_mc.hackathon3b.core.model.LineaOcr
import com.richi_mc.hackathon3b.core.model.OcrEnhancer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.Closeable

/**
 * Re-OCR del recorte ampliado y con más contraste de una zona de la foto guardada (PLAN3 §5).
 *
 * Tiene su propio cliente de ML Kit; llama a [close] cuando ya no se use. Cualquier fallo devuelve lista
 * vacía (salvo la cancelación, que se re-lanza).
 *
 * @param escalaMaxima ampliación máxima del recorte; bajar a 2f si resulta lento.
 * @param umbralBinario si es true, binariza el recorte en 128 tras estirar el contraste (desactivado por defecto).
 */
@Suppress("unused")
class MlKitOcrEnhancer(
    context: Context,
    private val escalaMaxima: Float = GeometriaRecorte.ESCALA_MAXIMA,
    private val umbralBinario: Boolean = false,
) : OcrEnhancer, Closeable {

    private val textRecognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun reOcr(path: String, zona: CajaPx): List<LineaOcr> =
        withContext(Dispatchers.Default) {
            val recorte = seguro { prepararRecorte(path, zona, escalaMaxima, umbralBinario) }
                ?: return@withContext emptyList()
            try {
                val texto = seguro { textRecognizer.process(InputImage.fromBitmap(recorte.bitmap, 0)).await() }
                    ?: return@withContext emptyList()
                texto.textBlocks.flatMap { it.lines }.map { linea ->
                    GeometriaRecorte.aImagenCompleta(
                        linea.aLineaOcr(), recorte.escala, recorte.origenX, recorte.origenY,
                    )
                }
            } finally {
                recorte.bitmap.recycle()
            }
        }

    override fun close() {
        textRecognizer.close()
    }

    private fun Text.Line.aLineaOcr(): LineaOcr = LineaOcr(
        texto = text,
        caja = boundingBox.aCaja(),
        elementos = elements.map { e ->
            ElementoOcr(e.text, e.boundingBox.aCaja(), e.symbols.map { it.boundingBox.aCaja() })
        },
    )

    private fun android.graphics.Rect?.aCaja(): CajaPx =
        if (this == null) CajaPx(0, 0, 0, 0) else CajaPx(left, top, right, bottom)

    private inline fun <T> seguro(bloque: () -> T): T? =
        try {
            bloque()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
}
