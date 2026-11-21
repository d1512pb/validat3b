package com.richi_mc.hackathon3b.feature.analysis.price

/**
 * Pesos y umbrales del [ScoringPriceParser] (PLAN3 §4). Todo vive aquí para ajustarlo con el
 * set de desarrollo sin tocar la lógica. Los umbrales de confianza para la UI están en
 * `Umbrales.confianzaPrecioAlta/Min`.
 */
object PesosPrecio {
    // ---- Señales positivas ----
    /** × alturaRelativa (0..1): la señal más fuerte, el precio suele ser lo más grande. */
    var ALTURA = 0.35f
    /** Si alturaRelativa >= [UMBRAL_LINEA_ALTA]: permite que un ENTERO sin `$` gane. */
    var LINEA_MAS_ALTA = 0.10f
    var UMBRAL_LINEA_ALTA = 0.9f
    /** `$` (o `S`/`§` normalizado). */
    var SIGNO = 0.15f
    /** Centavos con evidencia (DECIMAL, MISMA_LINEA, OTRA_LINEA, PEGADOS con símbolos más bajos). */
    var CENTAVOS = 0.15f
    /** PRECIO / AHORA / OFERTA / `$` suelto en la misma línea o la anterior. */
    var PALABRA_CERCANA = 0.10f
    /** Valor en el rango típico de anaquel. */
    var RANGO_TIPICO = 0.10f
    var RANGO_TIPICO_MIN = 5.0
    var RANGO_TIPICO_MAX = 500.0
    /** El recorte re-leído por OCR coincide con el valor. */
    var COINCIDE_REOCR = 0.10f

    // ---- Señales negativas ----
    var UNITARIO = -0.40f
    var ANTERIOR = -0.30f
    var PEGADOS_SIN_EVIDENCIA = -0.10f
    var ENTERO_SIN_EVIDENCIA = -0.15f

    // ---- Elección ----
    /** Dos candidatos del mismo valor se fusionan y el ganador suma esto. */
    var FUSION = 0.05f
    /** Si el segundo (valor distinto) está a menos de [MARGEN_AMBIGUEDAD] del primero. */
    var AMBIGUEDAD = -0.15f
    var MARGEN_AMBIGUEDAD = 0.05f

    // ---- Candidatos ----
    var VALOR_MIN = 1.0
    var VALOR_MAX = 5000.0
    /** Centavos "más bajos": altura < RATIO × altura del entero. */
    var RATIO_CENTAVOS = 0.8f
    /** Tolerancia horizontal (px) para CENTAVOS_OTRA_LINEA. */
    var TOLERANCIA_PX = 10
    /** Máximo de candidatos que se guardan en `PrecioDetectado.candidatos`. */
    var MAX_CANDIDATOS = 3

    // ---- Re-OCR ----
    /** Por debajo de esta confianza conviene re-leer el recorte. */
    var CONFIANZA_REOCR = 0.70f
    var ZONA_EXTRA_DERECHA = 0.40f
    var ZONA_EXTRA_VERTICAL = 0.50f
}
