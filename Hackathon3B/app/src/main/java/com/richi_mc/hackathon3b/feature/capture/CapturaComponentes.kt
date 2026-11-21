package com.richi_mc.hackathon3b.feature.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.richi_mc.hackathon3b.core.model.AnalisisImagen
import com.richi_mc.hackathon3b.core.model.BorradorCaptura
import com.richi_mc.hackathon3b.core.model.Problema
import com.richi_mc.hackathon3b.core.model.TipoPrecio
import com.richi_mc.hackathon3b.ui.components.StatusChip
import com.richi_mc.hackathon3b.ui.theme.Glass
import com.richi_mc.hackathon3b.ui.theme.GlassDimens
import com.richi_mc.hackathon3b.ui.theme.GlassRojo
import java.io.File
import java.util.Locale

/** Piezas compartidas por Producto, Revisión y Confirmación (internas al paquete). */

internal fun colorProblema(p: Problema): Color = if (p.bloqueante) GlassRojo.danger else GlassRojo.warning

internal fun iconoProblema(p: Problema): ImageVector =
    if (p.bloqueante) Icons.Rounded.ErrorOutline else Icons.Rounded.Warning

/** "$24.90"; null → null. */
internal fun formatoPrecio(valor: Double?): String? =
    valor?.let { String.format(Locale.US, "$%.2f", it) }

/** Compara códigos ignorando ceros a la izquierda (UPC-A vs EAN-13). */
internal fun mismoCodigo(a: String, b: String) = a.trim().trimStart('0') == b.trim().trimStart('0')

internal fun tipoPrecioTexto(t: TipoPrecio) = when (t) {
    TipoPrecio.REGULAR -> "Unitario"
    TipoPrecio.OFERTA -> "Oferta"
    TipoPrecio.VOLUMEN -> "Promocional"
    TipoPrecio.DESCONOCIDO -> "No identificado"
}

/** "EAN_13" -> "EAN 13"; null -> null. */
internal fun formatoLegible(formato: String?): String? = formato?.replace('_', ' ')

/** Código final del borrador, con la misma regla que `Captura.codigoBarrasFinal`. */
internal fun codigoFinal(b: BorradorCaptura): String? =
    b.codigoEscaneado?.takeIf { it.checksumOk }?.valor
        ?: b.producto?.codigoBarras ?: b.etiqueta?.codigoBarras ?: b.codigoEscaneado?.valor

/** Miniatura de una foto (radio 14); sin archivo muestra un marcador. */
@Composable
internal fun Miniatura(
    path: String?,
    descripcion: String,
    modifier: Modifier = Modifier,
    tamano: Dp = 72.dp,
) {
    val shape = RoundedCornerShape(GlassDimens.radiusIconBox)
    Box(
        modifier
            .size(tamano)
            .clip(shape)
            .background(GlassRojo.glassFillStrong, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (path.isNullOrEmpty()) {
            Icon(Icons.Rounded.Image, contentDescription = null, tint = GlassRojo.inkMuted, modifier = Modifier.size(24.dp))
        } else {
            AsyncImage(
                model = File(path),
                contentDescription = descripcion,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(tamano),
            )
        }
    }
}

/** Chip "Válida" (si no hay bloqueantes) + un chip por problema (danger bloqueante / warning no bloqueante). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EstadoAnalisisChips(analisis: AnalisisImagen, modifier: Modifier = Modifier) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!analisis.tieneBloqueantes) {
            StatusChip("Válida", GlassRojo.success, icon = Icons.Rounded.CheckCircle)
        }
        analisis.problemas.forEach { p ->
            StatusChip(p.corto, colorProblema(p), icon = iconoProblema(p))
        }
    }
}

/** Lista de mensajes de problemas en lenguaje claro, cada uno con ícono (no solo color). */
@Composable
internal fun ProblemasMensajes(problemas: List<Problema>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        problemas.forEach { p ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    iconoProblema(p),
                    contentDescription = null,
                    tint = colorProblema(p),
                    modifier = Modifier.padding(top = 2.dp).size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(p.mensaje, style = Glass.type.secondary, color = GlassRojo.inkSecondary)
            }
        }
    }
}
