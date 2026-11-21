package com.richi_mc.hackathon3b.feature.labeling

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.richi_mc.hackathon3b.core.model.EtiquetaImagen
import com.richi_mc.hackathon3b.ui.components.StatusChip
import com.richi_mc.hackathon3b.ui.theme.GlassRojo
import com.richi_mc.hackathon3b.ui.theme.GlassRojoTheme
import kotlin.math.roundToInt

/** Hasta 3 chips `info` con la etiqueta traducida y su porcentaje ("Botella 91 %"). Vacía → nada. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EtiquetaChips(etiquetas: List<EtiquetaImagen>, modifier: Modifier = Modifier) {
    if (etiquetas.isEmpty()) return
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        etiquetas.take(3).forEach { e ->
            StatusChip(
                text = "${TraduccionEtiquetas.es(e.texto)} ${(e.confianza * 100).roundToInt()} %",
                color = GlassRojo.info,
                icon = Icons.AutoMirrored.Rounded.Label,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF020617)
@Composable
private fun EtiquetaChipsPreview() {
    GlassRojoTheme {
        EtiquetaChips(
            listOf(
                EtiquetaImagen("Bottle", 0.91f, 0),
                EtiquetaImagen("Drink", 0.84f, 1),
                EtiquetaImagen("Cola", 0.66f, 2),
                EtiquetaImagen("Food", 0.55f, 3),
            ),
        )
    }
}
