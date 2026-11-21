package com.richi_mc.hackathon3b.feature.capture

import com.richi_mc.hackathon3b.core.model.FuenteDescripcion
import com.richi_mc.hackathon3b.core.model.ProductoEsperado
import com.richi_mc.hackathon3b.core.model.Sugerencia
import java.util.Locale

/**
 * Estado editable de la tarjeta "Producto esperado" (pantalla de Revisión). Lógica pura, sin Android,
 * para probarla en JVM.
 *
 * Guarda lo detectado ([descripcionDetectada], [precioDetectado]) aparte de lo que el capturista
 * ve y edita ([descripcion], [precioTexto]). "Editado" se deduce comparando valor final vs detectado,
 * así sobrevive a recrear el ViewModel y "restaurar" siempre deja el campo sin editar.
 */
data class CamposEsperado(
    val descripcion: String = "",
    val descripcionDetectada: String? = null,
    /** Texto del campo de precio (acepta coma o punto mientras se escribe). */
    val precioTexto: String = "",
    val precioDetectado: Double? = null,
    /** Fuente de la descripción detectada; null si no se detectó nada. */
    val fuente: FuenteDescripcion? = null,
    val confianza: Float? = null,
    val codigoCatalogo: String = "",
    val esContratipo: Boolean = false,
) {
    val precio: Double? get() = parsearPrecio(precioTexto)

    val descripcionEditada: Boolean
        get() = descripcion.trim() != descripcionDetectada.orEmpty().trim()

    val precioEditado: Boolean
        get() = !mismoPrecio(precio, precioDetectado)

    /** true si editó descripción o precio y el valor final difiere del detectado. */
    val editadoPorUsuario: Boolean get() = descripcionEditada || precioEditado

    /** Fuente efectiva de lo que se manda: MANUAL si no hubo sugerencia de descripción. */
    val fuenteEfectiva: FuenteDescripcion
        get() = if (descripcionDetectada == null) FuenteDescripcion.MANUAL else fuente ?: FuenteDescripcion.MANUAL

    /**
     * Aplica una sugerencia nueva (p. ej. tras retomar la etiqueta): los valores detectados siempre se
     * actualizan, pero el campo visible solo cambia si el usuario no lo había editado.
     */
    fun conSugerencia(s: Sugerencia): CamposEsperado {
        val nuevaDesc = s.descripcion?.trim()?.ifBlank { null }
        return copy(
            descripcion = if (descripcionEditada) descripcion else nuevaDesc.orEmpty(),
            descripcionDetectada = nuevaDesc,
            fuente = if (nuevaDesc == null) null else s.fuente,
            confianza = if (nuevaDesc == null) null else s.confianza,
            precioTexto = if (precioEditado) precioTexto else textoPrecio(s.precio),
            precioDetectado = s.precio,
        )
    }

    fun conDescripcion(texto: String) = copy(descripcion = normalizarDescripcion(texto))
    fun conPrecio(texto: String) = copy(precioTexto = normalizarPrecioTexto(texto))
    fun restaurarDescripcion() = copy(descripcion = descripcionDetectada.orEmpty())
    fun restaurarPrecio() = copy(precioTexto = textoPrecio(precioDetectado))

    /** Producto esperado para el borrador / la captura; conserva sku, marca y contenido de [base]. */
    fun aProductoEsperado(base: ProductoEsperado = ProductoEsperado()) = base.copy(
        descripcion = descripcion.trim().ifBlank { null },
        codigoBarras = codigoCatalogo.ifBlank { null },
        esContratipo = esContratipo,
        precio = precio,
        descripcionDetectada = descripcionDetectada,
        precioDetectado = precioDetectado,
        fuente = fuenteEfectiva,
        confianzaSugerencia = confianza.takeIf { descripcionDetectada != null },
        editadoPorUsuario = editadoPorUsuario,
    )

    companion object {
        const val MAX_DESCRIPCION = 60
        private const val MAX_PRECIO_CARACTERES = 9

        /** Estado inicial desde el borrador (al volver a la pantalla conserva lo editado y lo detectado). */
        fun desde(pe: ProductoEsperado) = CamposEsperado(
            descripcion = pe.descripcion.orEmpty(),
            descripcionDetectada = pe.descripcionDetectada,
            precioTexto = textoPrecio(pe.precio),
            precioDetectado = pe.precioDetectado,
            fuente = pe.fuente?.takeIf { pe.descripcionDetectada != null && it != FuenteDescripcion.MANUAL },
            confianza = pe.confianzaSugerencia,
            codigoCatalogo = pe.codigoBarras.orEmpty(),
            esContratipo = pe.esContratipo,
        )

        /** Mayúsculas, sin saltos de línea, máximo [MAX_DESCRIPCION] caracteres. */
        fun normalizarDescripcion(t: String): String =
            t.replace('\n', ' ').uppercase(Locale.ROOT).take(MAX_DESCRIPCION)

        /** Solo dígitos y un separador decimal (coma o punto); máximo 2 decimales. */
        fun normalizarPrecioTexto(t: String): String {
            val sb = StringBuilder()
            var separador = false
            var decimales = 0
            for (c in t) {
                when {
                    c in '0'..'9' -> {
                        if (separador) { if (decimales >= 2) continue; decimales++ }
                        sb.append(c)
                    }
                    (c == '.' || c == ',') && !separador -> { separador = true; sb.append(c) }
                }
            }
            return sb.toString().take(MAX_PRECIO_CARACTERES)
        }

        /** "24,90" o "24.90" → 24.9; vacío o solo separador → null. */
        fun parsearPrecio(t: String): Double? =
            t.trim().replace(',', '.').takeIf { it.isNotEmpty() && it != "." }?.toDoubleOrNull()

        fun textoPrecio(v: Double?): String = v?.let { String.format(Locale.US, "%.2f", it) }.orEmpty()

        private fun mismoPrecio(a: Double?, b: Double?): Boolean =
            if (a == null || b == null) a == b else Math.abs(a - b) < 0.005
    }
}
