package com.richi_mc.hackathon3b.feature.suggestion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.richi_mc.hackathon3b.core.model.EtiquetaImagen
import com.richi_mc.hackathon3b.core.model.FuenteDescripcion
import com.richi_mc.hackathon3b.core.model.LineaOcr
import com.richi_mc.hackathon3b.core.model.PrecioDetectado
import com.richi_mc.hackathon3b.core.model.Sugerencia
import com.richi_mc.hackathon3b.core.model.TipoPrecio
import com.richi_mc.hackathon3b.ui.components.GlassCard
import com.richi_mc.hackathon3b.ui.components.StatusChip
import com.richi_mc.hackathon3b.ui.theme.Glass
import com.richi_mc.hackathon3b.ui.theme.GlassDimens
import com.richi_mc.hackathon3b.ui.theme.GlassRojo
import com.richi_mc.hackathon3b.ui.theme.GlassRojoTheme

/** Tres casos fijos de PLAN2 §5: código en catálogo, solo etiqueta legible y solo foto del producto. */
object DemoSugerencias {
    class Caso(val titulo: String, val esperada: FuenteDescripcion, val sugerencia: Sugerencia)

    private val precio = PrecioDetectado(24.90, TipoPrecio.REGULAR, true, "$24.90", 24.90)
    private val sinPrecio = PrecioDetectado(null, TipoPrecio.DESCONOCIDO, false, "")
    private val servicio = ReglasSuggestionService(
        traducir = { if (it == "Bottle") "Botella" else it },
        catalogo = { if (it == "7501055300075") "Leche Lala entera 1 L" else null },
    )

    fun casos(): List<Caso> = listOf(
        Caso(
            "Código en catálogo", FuenteDescripcion.CATALOGO,
            servicio.sugerir(emptyList(), emptyList(), emptyList(), precio, "7501055300075"),
        ),
        Caso(
            "Solo etiqueta legible", FuenteDescripcion.ETIQUETA_RACK,
            servicio.sugerir(
                emptyList(),
                listOf(
                    LineaOcr("LECHE ENTERA LALA 1L", 40, 10),
                    LineaOcr("$24.90", 120, 60),
                    LineaOcr("PRECIO POR LITRO $24.90", 30, 200),
                ),
                emptyList(), precio, null,
            ),
        ),
        Caso(
            "Solo foto del producto", FuenteDescripcion.FOTO_PRODUCTO,
            servicio.sugerir(
                listOf(
                    LineaOcr("Coca-Cola", 150, 100),
                    LineaOcr("Sabor original", 60, 260),
                    LineaOcr("600 ml", 40, 400),
                    LineaOcr("7501055300075", 20, 460),
                ),
                emptyList(), listOf(EtiquetaImagen("Bottle", 0.9f, 0)), sinPrecio, null,
            ),
        ),
    )
}

@Composable
private fun DemoSugerenciasContenido() {
    Column(
        Modifier.background(GlassRojo.bg).padding(GlassDimens.screenSide),
        verticalArrangement = GlassDimens.itemSpacing,
    ) {
        Text("Sugerencias de descripción", style = Glass.type.title)
        DemoSugerencias.casos().forEach { caso ->
            val s = caso.sugerencia
            val ok = s.fuente == caso.esperada
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(GlassDimens.s8)) {
                    Text(caso.titulo.uppercase(), style = Glass.type.label)
                    Text(s.descripcion ?: "(sin sugerencia)", style = Glass.type.bodyStrong)
                    Text(
                        "Fuente ${s.fuente} · confianza ${(s.confianza * 100).toInt()} %",
                        style = Glass.type.secondary,
                    )
                    StatusChip(
                        text = if (ok) "Fuente esperada" else "Fuente inesperada",
                        color = if (ok) GlassRojo.success else GlassRojo.warning,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 700)
@Composable
private fun DemoSugerenciasPreview() = GlassRojoTheme { DemoSugerenciasContenido() }
