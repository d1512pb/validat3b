package com.richi_mc.hackathon3b.feature.analysis

import com.richi_mc.hackathon3b.core.model.PrecioDetectado
import com.richi_mc.hackathon3b.core.model.PriceParser
import com.richi_mc.hackathon3b.core.model.TipoPrecio
import java.text.Normalizer
import java.util.Locale

/**
 * Parser de precio por reglas sobre el texto OCR de una etiqueta (Kotlin puro, sin Android).
 *
 * Idea: se extraen todos los números con forma de precio, se marca el papel de cada uno
 * (principal, precio unitario, "antes", "ahorro") según las palabras clave cercanas, y el
 * principal es el mayor de los candidatos restantes (prefiriendo los que llevan `$`).
 */
class RegexPriceParser : PriceParser {

    private enum class Rol { PRINCIPAL, UNITARIO, ANTES, AHORRO }

    private class Candidato(
        val linea: Int,
        val ini: Int,
        val fin: Int,
        val valor: Double,
        val conSigno: Boolean,
        val texto: String,
        var rol: Rol = Rol.PRINCIPAL,
    )

    override fun parse(textoOcr: String): PrecioDetectado {
        val texto = normalizar(textoOcr)
        val lineas = texto.lines().map { enmascarar(it) }

        val candidatos = ArrayList<Candidato>()
        lineas.forEachIndexed { li, l -> candidatos += candidatosEn(l, li) }

        // Palabras clave que apuntan al número que VIENE DESPUÉS (misma línea o la siguiente).
        var hayUnitario = false
        lineas.forEachIndexed { li, l ->
            for (m in UNIT_PREFIJO.findAll(l)) {
                hayUnitario = true
                asignarSiguiente(candidatos, li, m.range.last + 1, Rol.UNITARIO, siguienteLinea = true)
            }
            for (m in ANTES.findAll(l)) asignarSiguiente(candidatos, li, m.range.last + 1, Rol.ANTES, siguienteLinea = true)
            for (m in AHORRO.findAll(l)) asignarSiguiente(candidatos, li, m.range.last + 1, Rol.AHORRO, siguienteLinea = false)
        }
        // Sufijos ("$89.00 /KG", "$12 C/U") apuntan al número inmediatamente anterior.
        lineas.forEachIndexed { li, l ->
            for (m in UNIT_SUFIJO.findAll(l)) {
                hayUnitario = true
                val previo = candidatos.lastOrNull { it.linea == li && it.fin <= m.range.first }
                if (previo != null && previo.rol == Rol.PRINCIPAL &&
                    l.substring(previo.fin, m.range.first).isBlank() && m.range.first - previo.fin <= 3
                ) previo.rol = Rol.UNITARIO
            }
        }

        val principales = candidatos.filter { it.rol == Rol.PRINCIPAL }
        val unitarios = candidatos.filter { it.rol == Rol.UNITARIO }

        val elegido = elegirPrincipal(principales) ?: unitarios.firstOrNull()
        val unitario = unitarios.firstOrNull()?.valor

        val tipo = when {
            VOLUMEN.containsMatchIn(texto) -> TipoPrecio.VOLUMEN
            OFERTA.containsMatchIn(texto) -> TipoPrecio.OFERTA
            elegido != null -> TipoPrecio.REGULAR
            else -> TipoPrecio.DESCONOCIDO
        }

        return PrecioDetectado(
            valor = elegido?.valor,
            tipo = tipo,
            tienePrecioUnitario = hayUnitario,
            textoFuente = elegido?.texto.orEmpty(),
            precioUnitario = unitario,
        )
    }

    private fun elegirPrincipal(c: List<Candidato>): Candidato? {
        val conSigno = c.filter { it.conSigno }
        return (conSigno.ifEmpty { c }).maxByOrNull { it.valor }
    }

    private fun asignarSiguiente(
        todos: List<Candidato>, linea: Int, desde: Int, rol: Rol, siguienteLinea: Boolean,
    ) {
        val c = todos.firstOrNull { it.linea == linea && it.ini >= desde && it.rol == Rol.PRINCIPAL }
            ?: if (siguienteLinea) todos.firstOrNull { it.linea == linea + 1 && it.rol == Rol.PRINCIPAL } else null
        c?.rol = rol
    }

    private fun candidatosEn(linea: String, li: Int): List<Candidato> {
        val res = ArrayList<Candidato>()
        for (m in CON_SIGNO.findAll(linea)) {
            val valor = parsearNumero(m.groupValues[1]) ?: continue
            res += Candidato(li, m.range.first, m.range.last + 1, valor, true, m.value.trim())
        }
        for (m in SIN_SIGNO.findAll(linea)) {
            if (res.any { m.range.first < it.fin && it.ini <= m.range.last }) continue
            val valor = parsearNumero(m.groupValues[1]) ?: continue
            res += Candidato(li, m.range.first, m.range.last + 1, valor, false, m.value.trim())
        }
        return res.sortedBy { it.ini }
    }

    private fun parsearNumero(s: String): Double? {
        val limpio = when {
            THOUSANDS.matches(s) -> s.replace(",", "")
            SPACED.matches(s) -> s.replace(Regex("\\s+"), ".")
            else -> s.replace(',', '.')
        }
        val v = limpio.toDoubleOrNull() ?: return null
        return if (v > 0.0 && v < 100_000.0) v else null
    }

    /** Mayúsculas, sin acentos, sin saltos de línea raros. */
    private fun normalizar(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .uppercase(Locale.ROOT)
            .replace("\r", "")

    /** Sustituye por espacios (misma longitud) códigos de barras/SKU largos y fechas. */
    private fun enmascarar(linea: String): String =
        linea.replace(FECHA) { " ".repeat(it.value.length) }
            .replace(DIGITOS_LARGOS) { " ".repeat(it.value.length) }

    private companion object {
        val CON_SIGNO = Regex(
            """\$\s*(\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d{1,4}[ \t]\d{2}(?!\d)|\d{1,5}[.,]\d{2}(?!\d)|\d{1,5})(?!\d)"""
        )
        val SIN_SIGNO = Regex(
            """(?<![\d.,/\-])(\d{1,3}(?:,\d{3})+\.\d{2}|\d{1,4}[.,]\d{2})(?!\d)(?![.,]\d)(?!\s?%)(?!\s?(?:KG|GR?|MG|ML|LTS?|L|CM|MM|OZ|PZAS?)\b)"""
        )
        val THOUSANDS = Regex("""\d{1,3}(,\d{3})+(\.\d+)?""")
        val SPACED = Regex("""\d+\s+\d{2}""")

        val DIGITOS_LARGOS = Regex("""\d{7,}""")
        val FECHA = Regex("""\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{1,2}\.\d{1,2}\.\d{2,4}""")

        const val MEDIDA = """KG|KILO|KILOS|LT|LTS|LTR|LITRO|LITROS|L|G|GR|ML|PZA|PZ|PIEZA"""
        val UNIT_PREFIJO = Regex("""PRECIO\s+UNITARIO|\bP\.?\s?UNIT\w*\.?|PRECIO\s+POR""")
        val UNIT_SUFIJO = Regex(
            """\bPOR\s+(?:100\s?)?(?:$MEDIDA)\b|/\s?(?:$MEDIDA)\b|\bC\s?/\s?U\b|\bX\s?(?:KG|KILO|LT|LTS|L)\b"""
        )
        val ANTES = Regex("""\bANTES\b|\bANTERIOR\b""")
        val AHORRO = Regex("""\bAHORRA\w*|\bDESCUENTO\b""")

        val OFERTA = Regex(
            """\bOFERTA\w*|\bAHORRA\w*|PRECIO\s+ESPECIAL|\bANTES\b|\bPROMOCION\b|\bPROMO\b|\bREBAJA\w*"""
        )
        val VOLUMEN = Regex(
            """\b[2-9]\s?X\s?\$|\b[2-9]\s?X\s?[1-9]\b|\bLLEVA\w*\b.*\bPAGA\w*|\b[2-9]\s?POR\s?[1-9]\b|\b2\s?DO\s+(?:AL|A|CON)\s+\d+"""
        )
    }
}
