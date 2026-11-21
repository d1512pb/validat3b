package com.richi_mc.hackathon3b.feature.suggestion

import com.richi_mc.hackathon3b.core.model.EtiquetaImagen
import com.richi_mc.hackathon3b.core.model.FuenteDescripcion
import com.richi_mc.hackathon3b.core.model.LineaOcr
import com.richi_mc.hackathon3b.core.model.PrecioDetectado
import com.richi_mc.hackathon3b.core.model.Sugerencia
import com.richi_mc.hackathon3b.core.model.SuggestionService
import java.text.Normalizer
import java.util.Locale

/**
 * Sugerencia de descripción por cascada de reglas (PLAN2 §5). Kotlin puro, sin Android.
 *
 * @param traducir traductor de categorías de ML Kit (inglés → español); en integración se inyecta el de W7.
 * @param catalogo descripción oficial del SKU por código de barras; null si no se conoce (no hay catálogo local).
 */
class ReglasSuggestionService(
    private val traducir: (String) -> String = { it },
    private val catalogo: (String) -> String? = { null },
) : SuggestionService {

    override fun sugerir(
        lineasProducto: List<LineaOcr>,
        lineasEtiqueta: List<LineaOcr>,
        etiquetasProducto: List<EtiquetaImagen>,
        precio: PrecioDetectado,
        codigoBarras: String?,
    ): Sugerencia {
        fun resultado(descripcion: String, fuente: FuenteDescripcion, confianza: Float) =
            Sugerencia(descripcion, fuente, precio.valor, confianza)

        // 1. Catálogo
        if (codigoBarras != null) {
            val oficial = catalogo(codigoBarras)?.let { armar(it, null) }
            if (oficial != null) return resultado(oficial, FuenteDescripcion.CATALOGO, 1.0f)
        }

        // 2. Etiqueta del rack
        descripcionDeEtiqueta(lineasEtiqueta, lineasProducto, precio)?.let {
            return resultado(it, FuenteDescripcion.ETIQUETA_RACK, 0.8f)
        }

        // 3. Foto del producto
        descripcionDeProducto(lineasProducto, lineasEtiqueta)?.let {
            return resultado(it, FuenteDescripcion.FOTO_PRODUCTO, 0.6f)
        }

        // 4. Categoría de ML Kit
        etiquetasProducto.maxByOrNull { it.confianza }?.let { etiqueta ->
            val texto = armar(traducir(etiqueta.texto), null)
            if (texto != null) return resultado(texto, FuenteDescripcion.CATEGORIA, 0.3f)
        }

        // 5. Nada
        return Sugerencia(null, null, precio.valor, 0f)
    }

    // ---- Etiqueta del rack ----

    private fun descripcionDeEtiqueta(
        lineasEtiqueta: List<LineaOcr>,
        lineasProducto: List<LineaOcr>,
        precio: PrecioDetectado,
    ): String? {
        val topPrecio = lineaDelPrecio(lineasEtiqueta, precio)?.top
        val candidatas = lineasEtiqueta
            .filter { topPrecio == null || it.top < topPrecio }
            .filter { !esLineaDePrecio(it.texto) }
            .mapNotNull { linea -> limpiar(linea.texto)?.let { linea to it } }
            .filter { (_, limpio) -> palabrasDeLetras(limpio) >= 2 }
            .sortedBy { it.first.top }
            .take(2)
        if (candidatas.isEmpty()) return null

        val nucleo = candidatas.joinToString(" ") { it.second }
        val fuentesContenido = lineasEtiqueta.filter { !esLineaDePrecio(it.texto) } + lineasProducto
        return armar(nucleo, contenidoNeto(fuentesContenido))
    }

    /** Línea del precio principal: la de mayor altura que contiene el texto fuente o el valor. */
    private fun lineaDelPrecio(lineas: List<LineaOcr>, precio: PrecioDetectado): LineaOcr? {
        val fuente = precio.textoFuente.trim()
        val variantes = buildList {
            if (fuente.isNotEmpty()) add(fuente)
            precio.valor?.let {
                val s = String.format(Locale.US, "%.2f", it)
                add(s); add(s.replace('.', ','))
            }
        }
        val coinciden = lineas.filter { l -> variantes.any { l.texto.contains(it) } }
        val sinUnitario = coinciden.filterNot { PALABRAS_UNITARIO.containsMatchIn(it.texto.mayusSinAcentos()) }
        return (sinUnitario.ifEmpty { coinciden })
            .sortedWith(compareByDescending<LineaOcr> { it.alturaPx }.thenBy { it.top })
            .firstOrNull()
    }

    private fun esLineaDePrecio(texto: String): Boolean {
        val t = texto.mayusSinAcentos()
        if ('$' in t) return true
        if (PALABRAS_PRECIO.containsMatchIn(t)) return true
        if (CODIGO_LARGO.containsMatchIn(t)) return true
        // El contenido neto ("1.50 L") no cuenta como patrón de precio.
        return PATRON_PRECIO.containsMatchIn(CONTENIDO_NETO.replace(t, " "))
    }

    // ---- Foto del producto ----

    private fun descripcionDeProducto(lineasProducto: List<LineaOcr>, lineasEtiqueta: List<LineaOcr>): String? {
        val validas = lineasProducto
            .mapNotNull { linea -> limpiar(linea.texto)?.let { linea to it } }
            .filter { (_, limpio) -> limpio.count { it.isLetter() } >= 2 && !soloContenido(limpio) }
        if (validas.isEmpty()) return null

        val porAltura = validas.sortedByDescending { it.first.alturaPx }
        val maxAltura = porAltura.first().first.alturaPx
        val elegidas = porAltura.take(3).filterIndexed { i, (linea, _) ->
            i < 2 || linea.alturaPx >= maxAltura * 0.6
        }.sortedBy { it.first.top }

        val nucleo = elegidas.joinToString(" ") { it.second }
        // Contenido neto: de la foto del producto; si no aparece, de la etiqueta (sin líneas de precio).
        val contenido = contenidoNeto(lineasProducto)
            ?: contenidoNeto(lineasEtiqueta.filter { !esLineaDePrecio(it.texto) })
        return armar(nucleo, contenido)
    }

    private fun soloContenido(limpio: String): Boolean {
        val m = CONTENIDO_NETO.find(limpio) ?: return false
        return limpio.replaceRange(m.range, "").isBlank()
    }

    // ---- Utilidades ----

    private fun palabrasDeLetras(limpio: String) =
        limpio.split(' ').count { p -> p.count { it.isLetter() } >= 2 }

    private fun contenidoNeto(lineas: List<LineaOcr>): String? {
        for (linea in lineas) {
            val m = CONTENIDO_NETO.find(linea.texto.mayusSinAcentos()) ?: continue
            return m.value
        }
        return null
    }

    /** Une núcleo + contenido neto (si no estaba ya), respetando el máximo de 60 caracteres. */
    private fun armar(nucleo: String, contenido: String?): String? {
        val limpio = limpiar(nucleo) ?: return null
        val neto = contenido?.let { normalizarContenido(it) }
        val completo = recortar(limpio, MAX_CHARS)
        if (neto == null || (completo != null && canonico(completo).contains(canonico(neto)))) return completo
        val base = recortar(limpio, MAX_CHARS - neto.length - 1) ?: return recortar(neto, MAX_CHARS)
        return "$base $neto"
    }

    /** "600ML" -> "600 ML"; "1,5 LT" -> "1,5 LT". */
    private fun normalizarContenido(c: String): String =
        CONTENIDO_NETO.find(c.mayusSinAcentos())?.let { m ->
            "${m.groupValues[0].takeWhile { it.isDigit() || it == '.' || it == ',' }} ${m.groupValues[2]}"
        } ?: c.trim()

    /** Forma comparable: "1L", "1 L", "1 LT" -> "1 L". */
    private fun canonico(s: String): String =
        CONTENIDO_NETO.replace(s) { m ->
            val unidad = when (m.groupValues[2]) { "LT" -> "L"; "GR" -> "G"; "PZS" -> "PZ"; else -> m.groupValues[2] }
            val numero = m.value.takeWhile { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')
            "$numero $unidad"
        }

    private companion object {
        const val MAX_CHARS = 60
        val PATRON_PRECIO = Regex("""\d+[.,]\d{2}""")
        val CONTENIDO_NETO = Regex("""(?<![0-9.,])\d+([.,]\d+)?\s?(ML|LT|L|GR|G|KG|PZS|PZ)\b""")
        val PALABRAS_PRECIO = Regex("""\bPRECIO\b|UNITARIO|\bPOR\s+(KG|KILO|LITRO|LT|L)\b|\bAHORRA|VIGENCIA|\bSKU\b""")
        val PALABRAS_UNITARIO = Regex("""UNITARIO|\bPOR\s+(KG|KILO|LITRO|LT|L)\b""")
        val CODIGO_LARGO = Regex("""\d{7,}""")

        fun String.mayusSinAcentos(): String =
            Normalizer.normalize(uppercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

        /**
         * Mayúsculas, sin acentos, solo letras/dígitos/espacios y `. , % /`, espacios colapsados.
         * Devuelve null si queda vacío o solo números.
         */
        fun limpiar(texto: String): String? {
            val t = texto.mayusSinAcentos()
                .map { if (it in 'A'..'Z' || it in '0'..'9' || it in " .,%/") it else ' ' }
                .joinToString("")
                .replace(Regex("\\s+"), " ")
                .trim()
                .trim('.', ',', '/')
                .trim()
            if (t.isEmpty() || t.none { it.isLetter() }) return null
            return t
        }

        /** Corta en límite de palabra; null si queda vacío. */
        fun recortar(s: String, max: Int): String? {
            if (max <= 0) return null
            if (s.length <= max) return s
            val corte = if (s[max] == ' ') max else s.lastIndexOf(' ', max).takeIf { it > 0 } ?: max
            return s.substring(0, corte).trim().ifEmpty { null }
        }
    }
}
