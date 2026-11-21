package com.richi_mc.hackathon3b.feature.analysis.price

import com.richi_mc.hackathon3b.core.model.CajaPx
import com.richi_mc.hackathon3b.core.model.ElementoOcr
import com.richi_mc.hackathon3b.core.model.LineaOcr
import java.util.Locale

/** Casos sintéticos de PLAN3 §6 con cajas inventadas (px de una imagen de ~1000 de ancho). */
internal class CasoPrecio(val nombre: String, val lineas: List<LineaOcr>)

internal fun el(texto: String, left: Int, top: Int, ancho: Int, alto: Int, simbolos: List<CajaPx> = emptyList()) =
    ElementoOcr(texto, CajaPx(left, top, left + ancho, top + alto), simbolos)

internal fun lin(vararg els: ElementoOcr): LineaOcr {
    val c = CajaPx(
        els.minOf { it.caja.left }, els.minOf { it.caja.top },
        els.maxOf { it.caja.right }, els.maxOf { it.caja.bottom },
    )
    return LineaOcr(els.joinToString(" ") { it.texto }, c, els.toList())
}

/** Símbolos de [n] dígitos; los dos últimos más bajos (centavos pegados) si [chicosAlFinal]. */
internal fun simbolos(n: Int, left: Int, top: Int, anchoSim: Int, alto: Int, chicosAlFinal: Boolean): List<CajaPx> =
    List(n) { i ->
        val chico = chicosAlFinal && i >= n - 2
        val a = if (chico) (alto * 0.55f).toInt() else alto
        val t = if (chico) top + (alto * 0.4f).toInt() else top
        CajaPx(left + i * anchoSim, t, left + (i + 1) * anchoSim, t + a)
    }

internal fun casosPrecio(): List<CasoPrecio> = listOf(
    CasoPrecio("\$24.90", listOf(lin(el("\$24.90", 100, 100, 300, 80)))),
    CasoPrecio("24 90 (90 mas bajo)", listOf(lin(el("24", 100, 100, 140, 80), el("90", 250, 105, 70, 40)))),
    CasoPrecio(
        "2490 (2 ultimos mas bajos)",
        listOf(lin(el("2490", 100, 100, 280, 80, simbolos(4, 100, 100, 70, 80, chicosAlFinal = true)))),
    ),
    CasoPrecio(
        "24 / 90 en otra linea",
        listOf(lin(el("24", 100, 100, 140, 80)), lin(el("90", 245, 102, 70, 40))),
    ),
    CasoPrecio("S24.90", listOf(lin(el("S24.90", 100, 100, 300, 80)))),
    CasoPrecio("\$25", listOf(lin(el("\$25", 100, 100, 160, 80)))),
    CasoPrecio("25 solo", listOf(lin(el("25", 100, 100, 140, 80)))),
    CasoPrecio(
        "600 ML \$15.00 + PRECIO POR LITRO \$25.00",
        listOf(
            lin(el("600", 50, 100, 90, 40), el("ML", 150, 100, 60, 40), el("\$15.00", 230, 100, 200, 40)),
            lin(
                el("PRECIO", 50, 160, 110, 25), el("POR", 170, 160, 60, 25),
                el("LITRO", 240, 160, 90, 25), el("\$25.00", 340, 160, 130, 25),
            ),
        ),
    ),
    CasoPrecio(
        "ANTES \$32.00 + AHORA \$27.50",
        listOf(
            lin(el("ANTES", 50, 60, 100, 30), el("\$32.00", 160, 60, 150, 30)),
            lin(el("AHORA", 50, 120, 140, 50), el("\$27.50", 200, 120, 260, 50)),
        ),
    ),
    CasoPrecio(
        "codigo de barras + fecha",
        listOf(lin(el("7501055300075", 50, 100, 400, 20)), lin(el("12/11/26", 50, 140, 160, 20))),
    ),
)

/** Una línea por caso: ganador, forma, tipo y confianza (PLAN3 §6). */
fun demoCasosPrecio(): List<String> {
    val parser = ScoringPriceParser()
    return casosPrecio().map { caso ->
        val p = parser.parse(caso.lineas)
        val ganador = p.candidatos.firstOrNull()
        val valor = p.valor?.let { String.format(Locale.US, "%.2f", it) } ?: "sin precio"
        val unit = p.precioUnitario?.let { String.format(Locale.US, " unitario=%.2f", it) }.orEmpty()
        "${caso.nombre} -> $valor | ${ganador?.forma ?: "-"} | ${p.tipo} | conf=" +
            String.format(Locale.US, "%.2f", p.confianza ?: 0f) + unit
    }
}

fun main() {
    demoCasosPrecio().forEach(::println)
}
