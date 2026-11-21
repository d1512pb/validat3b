package com.richi_mc.hackathon3b.core.model

/** Umbrales del análisis on-device. Calibrar con el set de desarrollo (PLAN.md §8). */
object Umbrales {
    /** Varianza del Laplaciano a 512 px de ancho por debajo de la cual la foto es BORROSA. */
    var nitidezMin = 100.0
    var brilloMin = 40.0
    var brilloMax = 220.0
    /** Fracción mínima del área de la imagen que debe ocupar el código (DEMASIADO_LEJOS). */
    var areaCodigoMin = 0.02
    /** Caracteres mínimos de OCR para no marcar SIN_TEXTO_LEGIBLE. */
    var ocrCaracteresMin = 8
    var ocrConfianzaMin = 0.5f
    /** Confianza del precio: ≥ alta se prellena normal; entre min y alta, chip "Revisa el precio"; < min, sin precio. */
    var confianzaPrecioAlta = 0.60f
    var confianzaPrecioMin = 0.30f
    /** Alto mínimo (fracción del alto de la imagen) de la línea más alta con dígitos (ETIQUETA_LEJOS). */
    var alturaEtiquetaMin = 0.04
    /** Lado mayor y calidad JPEG al guardar fotos (recomendación del contrato). */
    const val LADO_MAYOR_PX = 1600
    const val CALIDAD_JPEG = 85
    /** Precisión GPS considerada buena (chip success vs warning). */
    const val GPS_BUENO_M = 20f
}
