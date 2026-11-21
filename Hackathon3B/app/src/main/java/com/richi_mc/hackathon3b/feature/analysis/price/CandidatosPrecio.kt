package com.richi_mc.hackathon3b.feature.analysis.price

import com.richi_mc.hackathon3b.core.model.CajaPx
import com.richi_mc.hackathon3b.core.model.FormaPrecio
import com.richi_mc.hackathon3b.core.model.LineaOcr
import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Generación de candidatos de precio (PLAN3 §3). Kotlin puro: toma líneas con geometría y
 * devuelve todos los números que podrían ser el precio, con su contexto, sin puntuar.
 */

/** Candidato interno (mutable en las banderas de contexto que se resuelven después de detectar). */
internal class Cand(
    val valor: Double,
    val forma: FormaPrecio,
    /** Texto original (sin normalizar) del OCR; lo usa ReglasSuggestionService para ubicar la línea. */
    val textoFuente: String,
    val caja: CajaPx,
    val alturaRelativa: Float,
    val tieneSigno: Boolean,
    /** Los centavos tienen evidencia (forma DECIMAL/MISMA/OTRA_LINEA o PEGADOS con símbolos más bajos). */
    val evidencia: Boolean,
    /** ENTERO generado de un token de 3–4 dígitos sin evidencia de altura (competidor de PEGADOS). */
    val enteroSinEvidencia: Boolean,
    val linea: Int,
    val pos: Int,
    var esUnitario: Boolean = false,
    var esAnterior: Boolean = false,
    var cercaClave: Boolean = false,
    var coincideReOcr: Boolean = false,
    var puntuacion: Float = 0f,
) {
    val pegadosSinEvidencia: Boolean get() = forma == FormaPrecio.CENTAVOS_PEGADOS && !evidencia
}

/** Mayúsculas y sin acentos (misma normalización que el parser anterior). */
internal fun normalizarTexto(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .uppercase(Locale.ROOT)
        .replace("\r", "")

internal fun claveValor(v: Double): Long = (v * 100.0).roundToLong()

internal object RegexPrecio {
    val FECHA = Regex("""\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{1,2}\.\d{1,2}\.\d{2,4}""")

    val OFERTA = Regex(
        """\bOFERTA\w*|\bAHORRA\w*|PRECIO\s+ESPECIAL|\bANTES\b|\bPROMOCION\b|\bPROMO\b|\bREBAJA\w*"""
    )
    val VOLUMEN = Regex(
        """\b[2-9]\s?X\s?\$|\b[2-9]\s?X\s?[1-9]\b|\bLLEVA\w*\b.*\bPAGA\w*|\b[2-9]\s?POR\s?[1-9]\b|\b2\s?DO\s+(?:AL|A|CON)\s+\d+"""
    )

    const val MEDIDA = """KG|KILO|KILOS|LT|LTS|LTR|LITRO|LITROS|L|G|GR|ML|PZA|PZ|PIEZA"""
    val UNIT_PREFIJO = Regex("""PRECIO\s+UNITARIO|\bP\.?\s?UNIT\w*\.?|PRECIO\s+POR""")
    val UNIT_SUFIJO = Regex(
        """\bPOR\s+(?:100\s?)?(?:$MEDIDA)\b|\bPOR\s+100\b|/\s?(?:$MEDIDA)\b|\bC\s?/\s?U\b|\bX\s?(?:KG|KILO|LT|LTS|L)\b"""
    )

    val ANTERIOR = Regex(
        """\bANTES\b|\bANTERIOR\b|\bAHORRA\w*|\bAHORRO\w*|\bDESCUENTO\b|\bPRECIO\s+REGULAR\b"""
    )
    val OFERTA_O_AHORA = Regex("""\bOFERTA\w*|\bAHORA\b""")
    val DETIENE_ANTERIOR = Regex("""\bAHORA\b|\bOFERTA\w*|\bPROMO\w*|PRECIO\s+ESPECIAL""")
    val CLAVE_CERCANA = Regex("""\bPRECIO\b|\bAHORA\b|\bOFERTA\w*""")

    val UNIDAD_TOKEN = Regex(
        """^\d+(?:[.,]\d+)?(?:ML|L|LT|LTS|LTR|LITRO|LITROS|G|GR|GRS|KG|KGS|KILO|KILOS|MG|PZ|PZS|PZA|PZAS|CM|MM|OZ|%|X.*)$"""
    )
    val UNIDADES_SUELTAS = setOf(
        "ML", "L", "LT", "LTS", "LTR", "LITRO", "LITROS", "G", "GR", "GRS", "KG", "KGS", "KILO", "KILOS",
        "MG", "PZ", "PZS", "PZA", "PZAS", "PIEZA", "PIEZAS", "CM", "MM", "OZ", "%",
    )
    val UNIDAD_POR_X = setOf("KG", "KILO", "LT", "LTS", "L")

    val DECIMAL = Regex("""^\d{1,4}[.,]\d{2}$""")
    val DECIMAL_MILES = Regex("""^\d{1,2},\d{3}\.\d{2}$""")
    val ENTERO_MILES = Regex("""^\d{1,2},\d{3}$""")
    val SOLO_DIGITOS = Regex("""^\d+$""")
}

// ---- Tokens ----

internal class Tok(val raw: String, val caja: CajaPx, val simbolos: List<CajaPx>) {
    val norm: String = normalizarTexto(raw)
}

private fun union(a: CajaPx, b: CajaPx) =
    CajaPx(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))

private fun fusionar(a: Tok, b: Tok) = Tok(a.raw + b.raw, union(a.caja, b.caja), a.simbolos + b.simbolos)

/** Tokeniza [LineaOcr.texto] por espacios repartiendo la caja de la línea en proporción a los caracteres. */
private fun tokenizarTexto(l: LineaOcr): List<Tok> {
    val len = l.texto.length.coerceAtLeast(1)
    val c = l.caja
    return Regex("""\S+""").findAll(l.texto).map { m ->
        val left = c.left + (c.ancho.toLong() * m.range.first / len).toInt()
        val right = c.left + (c.ancho.toLong() * (m.range.last + 1) / len).toInt()
        Tok(m.value, CajaPx(left, c.top, right, c.bottom), emptyList())
    }.toList()
}

/** "25.00/KG" → "25.00" + "/KG" para que el sufijo de unitario se vea como palabra aparte. */
private fun dividirSufijos(toks: List<Tok>): List<Tok> = toks.flatMap { t ->
    val m = Regex("""^(.*\d)(/[A-Za-z].*)$""").matchEntire(t.raw)
    if (m == null) listOf(t) else {
        val a = m.groupValues[1]
        val c = t.caja
        val corte = c.left + (c.ancho.toLong() * a.length / t.raw.length).toInt()
        listOf(
            Tok(a, CajaPx(c.left, c.top, corte, c.bottom), t.simbolos.take(a.length)),
            Tok(m.groupValues[2], CajaPx(corte, c.top, c.right, c.bottom), t.simbolos.drop(a.length)),
        )
    }
}

/** Quita espacios alrededor de `.` y `,` entre dígitos ("24 . 90" → "24.90"). */
private fun unirSeparadores(toks: List<Tok>): List<Tok> {
    val res = ArrayList<Tok>()
    var i = 0
    while (i < toks.size) {
        var cur = toks[i]; i++
        while (i < toks.size) {
            val n = toks[i]
            val finDigito = cur.raw.last().isDigit()
            val finSep = cur.raw.length > 1 && cur.raw.last() in ".," && cur.raw[cur.raw.length - 2].isDigit()
            val nInicioDigito = n.raw.first().isDigit()
            val nEsSep = n.raw == "." || n.raw == ","
            val nInicioSep = n.raw.length > 1 && n.raw[0] in ".," && n.raw[1].isDigit()
            val sig = toks.getOrNull(i + 1)
            when {
                finDigito && nEsSep && sig != null && sig.raw.first().isDigit() -> {
                    cur = fusionar(fusionar(cur, n), sig); i += 2
                }
                finDigito && nInicioSep -> { cur = fusionar(cur, n); i++ }
                finSep && nInicioDigito -> { cur = fusionar(cur, n); i++ }
                else -> break
            }
        }
        res += cur
    }
    return res
}

// ---- Números ----

internal enum class TipoNum { DECIMAL, CORTO, LARGO, MILES }

internal class Num(
    val tok: Tok,
    val idx: Int,
    val tipo: TipoNum,
    /** Dígitos del entero (CORTO/LARGO/MILES). */
    val digitos: String,
    /** Valor ya resuelto (DECIMAL). */
    val valorDecimal: Double?,
    var signo: Boolean,
) {
    var usado = false
}

private fun esSignoSuelto(t: Tok) = t.norm == "$" || t.norm == "§"

private fun limpiarBordes(s: String) = s.trim { !it.isLetterOrDigit() && it != '$' && it != '§' && it != '%' }

/** Intenta leer el token como número de precio; null si no lo es o debe descartarse (PLAN3 §3, paso 1 y 3). */
private fun numerar(tok: Tok, idx: Int): Num? {
    var s = limpiarBordes(tok.norm)
    var signo = false
    if (s.startsWith("$") || s.startsWith("§")) {
        signo = true; s = s.substring(1)
    } else if (s.length > 1 && s[0] == 'S' && s[1].isDigit()) {
        signo = true; s = s.substring(1)
    }
    if (s.isEmpty() || s.none { it.isDigit() }) return null
    if (RegexPrecio.UNIDAD_TOKEN.matches(s)) return null

    // O→0, I/L→1, B→8 (solo entre dígitos), siempre que haya al menos tantos dígitos como letras cambiadas.
    val chars = s.toCharArray()
    var cambios = 0
    for (i in chars.indices) {
        val nuevo = when (chars[i]) {
            'O' -> '0'
            'I', 'L' -> '1'
            'B' -> if (i > 0 && i < chars.lastIndex && s[i - 1].isDigit() && s[i + 1].isDigit()) '8' else chars[i]
            else -> chars[i]
        }
        if (nuevo != chars[i]) { chars[i] = nuevo; cambios++ }
    }
    val n = String(chars)
    if (cambios > s.count { it.isDigit() }) return null

    return when {
        RegexPrecio.DECIMAL.matches(n) -> Num(tok, idx, TipoNum.DECIMAL, "", n.replace(',', '.').toDouble(), signo)
        RegexPrecio.DECIMAL_MILES.matches(n) -> Num(tok, idx, TipoNum.DECIMAL, "", n.replace(",", "").toDouble(), signo)
        RegexPrecio.ENTERO_MILES.matches(n) -> Num(tok, idx, TipoNum.MILES, n.replace(",", ""), null, signo)
        RegexPrecio.SOLO_DIGITOS.matches(n) -> when {
            n.length >= 6 -> null
            n.length == 5 -> Num(tok, idx, TipoNum.LARGO, n, null, signo)
            else -> Num(tok, idx, TipoNum.CORTO, n, null, signo)
        }
        else -> null
    }
}

internal class LineaInfo(val idx: Int, val linea: LineaOcr, val toks: List<Tok>) {
    /** Tokens normalizados unidos por espacios (base de las palabras clave). */
    val texto: String = toks.joinToString(" ") { it.norm }
    val offsets: IntArray = IntArray(toks.size).also { arr ->
        var p = 0
        toks.forEachIndexed { i, t -> arr[i] = p; p += t.norm.length + 1 }
    }
    val nums: Array<Num?> = arrayOfNulls(toks.size)
    val cands = ArrayList<Cand>()

    val soloSignos: Int = toks.count { esSignoSuelto(it) }
    val tieneSignoSuelto: Boolean get() = soloSignos > 0

    fun construirNums() {
        val enFecha = RegexPrecio.FECHA.findAll(texto).map { it.range }.toList()
        for (i in toks.indices) {
            val t = toks[i]
            if (esSignoSuelto(t)) continue
            if (enFecha.any { offsets[i] in it }) continue
            var num = numerar(t, i) ?: continue
            // Signo suelto en el token anterior: "$ 24.90" o "S 24".
            val prev = toks.getOrNull(i - 1)
            if (!num.signo && prev != null && (esSignoSuelto(prev) || prev.norm == "S")) num.signo = true

            val prevN = prev?.norm?.let { limpiarBordes(it) }
            val sigN = toks.getOrNull(i + 1)?.norm?.let { limpiarBordes(it) }
            val sigSig = toks.getOrNull(i + 2)?.norm?.let { limpiarBordes(it) }
            if (sigN in RegexPrecio.UNIDADES_SUELTAS) continue
            if (prev?.norm == "/" || prev?.norm == "-" || toks.getOrNull(i + 1)?.norm.let { it == "/" || it == "-" }) continue
            if (num.tipo == TipoNum.CORTO && !num.signo) {
                // Cantidades: "6 X 355" (salvo "X KG": eso es precio unitario).
                if (sigN == "X" && sigSig !in RegexPrecio.UNIDAD_POR_X) continue
                if (prevN == "X") continue
            }
            nums[i] = num
        }
    }

    fun soloEsteNumero(n: Num): Boolean =
        toks.indices.count { !esSignoSuelto(toks[it]) } == 1 && nums[n.idx] === n
}

// ---- Detección ----

internal class Etiqueta(val lineas: List<LineaOcr>) {
    val alturaMax: Int = lineas.maxOfOrNull { it.caja.alto }?.coerceAtLeast(0) ?: 0
    val infos: List<LineaInfo> = lineas.mapIndexed { i, l ->
        val base = if (l.elementos.isNotEmpty()) {
            l.elementos.filter { it.texto.isNotBlank() }.map { Tok(it.texto.trim(), it.caja, it.simbolos) }
        } else tokenizarTexto(l)
        LineaInfo(i, l, unirSeparadores(dividirSufijos(base)))
    }
    val textoNormalizado: String = infos.joinToString("\n") { it.texto }
    val candidatos: List<Cand>

    init {
        infos.forEach { it.construirNums() }
        detectarDecimales()
        detectarCentavosMismaLinea()
        detectarCentavosOtraLinea()
        detectarPegadosYEnteros()
        marcarContexto()
        candidatos = infos.flatMap { it.cands }
    }

    private fun alturaRel(c: CajaPx): Float =
        if (alturaMax > 0) (c.alto.toFloat() / alturaMax).coerceIn(0f, 1f) else 0f

    private fun enRango(v: Double) = v >= PesosPrecio.VALOR_MIN && v <= PesosPrecio.VALOR_MAX

    private fun agregar(
        info: LineaInfo, n: Num, valor: Double, forma: FormaPrecio, texto: String, caja: CajaPx,
        evidencia: Boolean, enteroSinEvidencia: Boolean = false,
    ) {
        if (!enRango(valor)) return
        info.cands += Cand(
            valor = valor, forma = forma, textoFuente = texto, caja = caja,
            alturaRelativa = alturaRel(n.tok.caja), tieneSigno = n.signo,
            evidencia = evidencia, enteroSinEvidencia = enteroSinEvidencia,
            linea = info.idx, pos = info.offsets[n.idx],
        )
    }

    private fun detectarDecimales() {
        for (info in infos) for (n in info.nums) {
            if (n == null || n.tipo != TipoNum.DECIMAL) continue
            n.usado = true
            agregar(info, n, n.valorDecimal!!, FormaPrecio.DECIMAL, n.tok.raw, n.tok.caja, evidencia = true)
        }
    }

    private fun detectarCentavosMismaLinea() {
        for (info in infos) {
            for (i in 0 until info.toks.lastIndex) {
                val a = info.nums[i] ?: continue
                val b = info.nums[i + 1] ?: continue
                if (a.usado || b.usado || a.tipo != TipoNum.CORTO || b.tipo != TipoNum.CORTO) continue
                if (b.digitos.length != 2 || b.signo) continue
                val ca = a.tok.caja; val cb = b.tok.caja
                val masBajo = ca.alto > 0 && cb.alto < PesosPrecio.RATIO_CENTAVOS * ca.alto
                val soloDos = info.toks.indices.count { !esSignoSuelto(info.toks[it]) } == 2
                if (!masBajo && !soloDos) continue
                a.usado = true; b.usado = true
                val valor = (a.digitos + b.digitos).toDouble() / 100.0
                val caja = CajaPx(minOf(ca.left, cb.left), minOf(ca.top, cb.top), maxOf(ca.right, cb.right), maxOf(ca.bottom, cb.bottom))
                agregar(info, a, valor, FormaPrecio.CENTAVOS_MISMA_LINEA, a.tok.raw + " " + b.tok.raw, caja, evidencia = true)
            }
        }
    }

    private fun detectarCentavosOtraLinea() {
        val bs = ArrayList<Pair<LineaInfo, Num>>()
        for (info in infos) for (n in info.nums) {
            if (n != null && !n.usado && !n.signo && n.tipo == TipoNum.CORTO && n.digitos.length == 2 && info.soloEsteNumero(n)) {
                bs += info to n
            }
        }
        if (bs.isEmpty()) return
        for (infoA in infos) for (a in infoA.nums) {
            if (a == null || a.usado || a.tipo != TipoNum.CORTO || a.digitos.length > 4) continue
            val ca = a.tok.caja
            val b = bs.filter { (infoB, b) ->
                if (b.usado || b === a || infoB.idx == infoA.idx) return@filter false
                val cb = b.tok.caja
                if (cb.left < ca.right - PesosPrecio.TOLERANCIA_PX) return@filter false
                if (ca.ancho > 0 && cb.ancho > 0 && ca.alto > 0 && cb.left - ca.right > 1.5f * ca.alto) return@filter false
                if (cb.top < ca.top - 0.2f * ca.alto || cb.top > ca.top + ca.alto / 2f) return@filter false
                ca.alto <= 0 || cb.alto < ca.alto
            }.minByOrNull { it.second.tok.caja.left - ca.right } ?: continue
            val (_, nb) = b
            a.usado = true; nb.usado = true
            val cb = nb.tok.caja
            val valor = (a.digitos + nb.digitos).toDouble() / 100.0
            val caja = CajaPx(minOf(ca.left, cb.left), minOf(ca.top, cb.top), maxOf(ca.right, cb.right), maxOf(ca.bottom, cb.bottom))
            agregar(infoA, a, valor, FormaPrecio.CENTAVOS_OTRA_LINEA, a.tok.raw, caja, evidencia = true)
        }
    }

    /** Los 2 últimos símbolos (dígitos) miden < 0.8× la altura de los anteriores. */
    private fun evidenciaPegados(n: Num): Boolean = evidenciaSimbolos(n.tok.simbolos, n.digitos.length)

    private fun detectarPegadosYEnteros() {
        for (info in infos) for (n in info.nums) {
            if (n == null || n.usado) continue
            val caja = n.tok.caja
            when (n.tipo) {
                TipoNum.DECIMAL -> Unit
                TipoNum.MILES -> agregar(info, n, n.digitos.toDouble(), FormaPrecio.ENTERO, n.tok.raw, caja, evidencia = false)
                TipoNum.CORTO, TipoNum.LARGO -> {
                    val d = n.digitos
                    val pegable = d.length in 3..5 && d[0] != '0'
                    val evid = pegable && evidenciaPegados(n)
                    when {
                        evid -> agregar(info, n, d.toDouble() / 100.0, FormaPrecio.CENTAVOS_PEGADOS, n.tok.raw, caja, evidencia = true)
                        n.tipo == TipoNum.LARGO -> Unit
                        pegable -> {
                            agregar(info, n, d.toDouble() / 100.0, FormaPrecio.CENTAVOS_PEGADOS, n.tok.raw, caja, evidencia = false)
                            agregar(info, n, d.toDouble(), FormaPrecio.ENTERO, n.tok.raw, caja, evidencia = false, enteroSinEvidencia = true)
                        }
                        else -> agregar(info, n, d.toDouble(), FormaPrecio.ENTERO, n.tok.raw, caja, evidencia = false)
                    }
                }
            }
            n.usado = true
        }
    }

    // ---- Contexto (PLAN3 §3, paso 4) ----

    private fun marcarContexto() {
        val hayOfertaAhora = RegexPrecio.OFERTA_O_AHORA.containsMatchIn(textoNormalizado)
        for (info in infos) {
            val cs = info.cands.sortedBy { it.pos }
            val prefijos = RegexPrecio.UNIT_PREFIJO.findAll(info.texto).toList()
            for (m in prefijos) {
                val primero = cs.firstOrNull { it.pos > m.range.last }
                    ?: infos.getOrNull(info.idx + 1)?.cands?.minByOrNull { it.pos }
                primero?.esUnitario = true
            }
            for (m in RegexPrecio.UNIT_SUFIJO.findAll(info.texto)) {
                if (prefijos.any { it.range.first <= m.range.first && m.range.first <= it.range.last }) continue
                val elegido = cs.lastOrNull { it.pos < m.range.first }
                    ?: cs.firstOrNull { it.pos > m.range.last }
                    ?: if (cs.isEmpty()) infos.getOrNull(info.idx - 1)?.cands?.maxByOrNull { it.pos } else null
                elegido?.esUnitario = true
            }
            for (m in RegexPrecio.ANTERIOR.findAll(info.texto)) {
                if (m.value.startsWith("PRECIO") && !hayOfertaAhora) continue
                val stop = RegexPrecio.DETIENE_ANTERIOR.findAll(info.texto).firstOrNull { it.range.first > m.range.last }
                val limite = stop?.range?.first ?: Int.MAX_VALUE
                val despues = cs.filter { it.pos > m.range.last && it.pos < limite }
                if (despues.isNotEmpty()) despues.forEach { it.esAnterior = true }
                else cs.lastOrNull { it.pos < m.range.first }?.esAnterior = true
            }
            val cerca = cercaClave(info.idx)
            info.cands.forEach { it.cercaClave = cerca }
        }
    }

    /** PRECIO / AHORA / OFERTA / `$` suelto en la línea [i] o la anterior. */
    fun cercaClave(i: Int): Boolean = listOf(i, i - 1).any { k ->
        val info = infos.getOrNull(k) ?: return@any false
        info.tieneSignoSuelto || RegexPrecio.CLAVE_CERCANA.containsMatchIn(info.texto)
    }
}

/** Evidencia de altura en símbolos: los 2 últimos dígitos miden < 0.8× los anteriores. */
internal fun evidenciaSimbolos(simbolos: List<CajaPx>, nDigitos: Int): Boolean {
    if (nDigitos < 3 || simbolos.size < nDigitos) return false
    val ds = simbolos.takeLast(nDigitos)
    val primeros = ds.dropLast(2).map { it.alto }.average()
    val ultimos = ds.takeLast(2).map { it.alto }.average()
    return primeros > 0 && ultimos < PesosPrecio.RATIO_CENTAVOS * primeros
}
