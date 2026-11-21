package com.richi_mc.hackathon3b.feature.labeling

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.richi_mc.hackathon3b.core.model.EtiquetaImagen
import com.richi_mc.hackathon3b.core.model.ImageLabelerService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/**
 * Etiquetado de imágenes con el modelo base (bundled) de ML Kit Image Labeling.
 *
 * Un fallo de ML Kit (modelo no disponible, imagen ilegible…) devuelve lista vacía en lugar de
 * lanzar; el llamador debe tratar la lista vacía como "no se pudo etiquetar", no como "no hay nada".
 */
class MlKitLabeler(private val context: Context) : ImageLabelerService, Closeable {

    private val labeler: ImageLabeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(0.5f).build()
    )

    override suspend fun etiquetar(path: String): List<EtiquetaImagen> =
        withContext(Dispatchers.IO) {
            try {
                etiquetar(InputImage.fromFilePath(context, Uri.fromFile(File(path))))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }

    /** Variante para reutilizar una imagen ya decodificada (sin releer el archivo). */
    suspend fun etiquetar(imagen: InputImage): List<EtiquetaImagen> =
        try {
            labeler.process(imagen).await()
                .map { EtiquetaImagen(it.text, it.confidence, it.index) }
                .sortedByDescending { it.confianza }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }

    override fun close() {
        labeler.close()
    }
}
