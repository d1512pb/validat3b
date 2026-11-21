package com.richi_mc.hackathon3b.feature.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.richi_mc.hackathon3b.app.AppContainer
import com.richi_mc.hackathon3b.core.model.AnalisisImagen
import com.richi_mc.hackathon3b.core.model.BorradorCaptura
import com.richi_mc.hackathon3b.core.model.PrecioDetectado
import com.richi_mc.hackathon3b.core.model.Problema
import com.richi_mc.hackathon3b.core.model.TipoPrecio
import com.richi_mc.hackathon3b.ui.components.CircleIconButton
import com.richi_mc.hackathon3b.ui.components.GlassBackground
import com.richi_mc.hackathon3b.ui.components.GlassButton
import com.richi_mc.hackathon3b.ui.components.GlassCard
import com.richi_mc.hackathon3b.ui.components.LocalHazeState
import com.richi_mc.hackathon3b.ui.components.PrimaryButton
import com.richi_mc.hackathon3b.ui.components.StatusChip
import com.richi_mc.hackathon3b.ui.theme.Glass
import com.richi_mc.hackathon3b.ui.theme.GlassDimens
import com.richi_mc.hackathon3b.ui.theme.GlassRojo
import com.richi_mc.hackathon3b.ui.theme.GlassRojoTheme

@Composable
fun RevisionScreen(
    container: AppContainer,
    onConfirmada: (captureId: String) -> Unit,  // captura guardada (y envío lanzado) → confirmacion
    onRetomar: () -> Unit,                       // → producto
) {
    val vm: RevisionViewModel = viewModel(
        factory = viewModelFactory { initializer { RevisionViewModel(container) } }
    )
    val state by vm.state.collectAsStateWithLifecycle()
    RevisionContent(
        state = state,
        onConfirmar = { vm.confirmar(onConfirmada) },
        onRetomar = onRetomar,
    )
}

@Composable
fun RevisionContent(
    state: RevisionUiState,
    onConfirmar: () -> Unit,
    onRetomar: () -> Unit,
) {
    var verTexto by rememberSaveable { mutableStateOf(false) }
    val b = state.borrador

    GlassBackground {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(GlassRojo.navFill)
                        .statusBarsPadding()
                        .padding(horizontal = GlassDimens.screenSide, vertical = GlassDimens.s12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", onRetomar)
                    Spacer(Modifier.width(GlassDimens.s12))
                    Column(Modifier.weight(1f)) {
                        Text("REVISIÓN", style = Glass.type.label)
                        Text(
                            state.sesion?.tienda ?: "Revisa tu captura",
                            style = Glass.type.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(GlassRojo.glassBorder))
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = GlassDimens.screenSide)
                    .padding(vertical = GlassDimens.s16),
                verticalArrangement = GlassDimens.itemSpacing,
            ) {
                FotoCard("Fachada", state.sesion?.fachadaPath, state.sesion?.fachadaAnalisis, existe = state.sesion != null)
                FotoCard("Producto", b.producto?.path, b.producto, existe = b.producto != null)
                FotoCard("Etiqueta de precio", b.etiqueta?.path, b.etiqueta, existe = b.etiqueta != null)
                DatosCard(b, onVerTexto = { verTexto = true })
            }

            Column(
                Modifier
                    .padding(horizontal = GlassDimens.screenSide)
                    .padding(top = GlassDimens.s8, bottom = GlassDimens.s12)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val motivos = state.motivosBloqueo
                if (motivos.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Rounded.ErrorOutline, contentDescription = null,
                            tint = GlassRojo.danger, modifier = Modifier.padding(top = 2.dp).size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "No se puede confirmar: " + motivos.joinToString(" ") + " Toca \"Retomar\" para corregirlo.",
                            style = Glass.type.secondary, color = GlassRojo.danger,
                        )
                    }
                }
                if (state.error != null) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Rounded.Warning, contentDescription = null,
                            tint = GlassRojo.warning, modifier = Modifier.padding(top = 2.dp).size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(state.error, style = Glass.type.secondary, color = GlassRojo.warning)
                    }
                }
                GlassButton("Retomar", onRetomar, icon = Icons.Rounded.CameraAlt, enabled = !state.confirmando)
                PrimaryButton(
                    "Confirmar captura", onConfirmar,
                    enabled = state.puedeConfirmar, icon = Icons.Rounded.CheckCircle,
                )
            }
        }
    }

    if (verTexto) {
        TextoLeidoDialog(b, onCerrar = { verTexto = false })
    }
}

@Composable
private fun FotoCard(titulo: String, path: String?, analisis: AnalisisImagen?, existe: Boolean) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row {
            Miniatura(path, "Miniatura: $titulo", tamano = 96.dp)
            Spacer(Modifier.width(GlassDimens.s12))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(titulo, style = Glass.type.title)
                when {
                    !existe -> StatusChip("Falta la foto", GlassRojo.danger, icon = Icons.Rounded.ErrorOutline)
                    analisis == null -> StatusChip("Sin análisis", GlassRojo.inkMuted, icon = Icons.Rounded.Info)
                    else -> EstadoAnalisisChips(analisis)
                }
            }
        }
        if (analisis != null && analisis.problemas.isNotEmpty()) {
            Spacer(Modifier.height(GlassDimens.s12))
            ProblemasMensajes(analisis.problemas)
        }
    }
}

private fun mismoCodigo(a: String, b: String) = a.trim().trimStart('0') == b.trim().trimStart('0')

private fun tipoPrecioTexto(t: TipoPrecio) = when (t) {
    TipoPrecio.REGULAR -> "Regular"
    TipoPrecio.OFERTA -> "Oferta"
    TipoPrecio.VOLUMEN -> "Volumen"
    TipoPrecio.DESCONOCIDO -> "No identificado"
}

@Composable
private fun DatosCard(b: BorradorCaptura, onVerTexto: () -> Unit) {
    val codProducto = b.producto?.codigoBarras
    val codEtiqueta = b.etiqueta?.codigoBarras
    val codCatalogo = b.productoEsperado.codigoBarras?.takeIf { it.isNotBlank() }
    val precio: PrecioDetectado? = b.precio

    GlassCard(Modifier.fillMaxWidth()) {
        Text("Datos detectados", style = Glass.type.title)
        Spacer(Modifier.height(GlassDimens.s12))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Dato(
                "Código de barras",
                if (codProducto != null) {
                    codProducto + (b.producto?.formatoCodigo?.let { " · " + it.replace('_', ' ') } ?: "")
                } else "No detectado",
            ) {
                if (codProducto == null) StatusChip("Sin código", GlassRojo.danger, icon = Icons.Rounded.ErrorOutline)
            }

            Dato("Código en la etiqueta", codEtiqueta ?: "No detectado") {
                when {
                    codProducto == null || codEtiqueta == null ->
                        StatusChip("No se pudo comparar", GlassRojo.warning, icon = Icons.Rounded.Warning)
                    mismoCodigo(codProducto, codEtiqueta) ->
                        StatusChip("Coincide con el producto", GlassRojo.success, icon = Icons.Rounded.CheckCircle)
                    else ->
                        StatusChip("No coincide con el producto", GlassRojo.warning, icon = Icons.Rounded.Warning)
                }
            }

            if (codCatalogo != null) {
                Dato("Código del catálogo", codCatalogo) {
                    when {
                        codProducto == null ->
                            StatusChip("No se pudo comparar", GlassRojo.warning, icon = Icons.Rounded.Warning)
                        mismoCodigo(codProducto, codCatalogo) ->
                            StatusChip("Coincide con el catálogo", GlassRojo.success, icon = Icons.Rounded.CheckCircle)
                        else ->
                            StatusChip("No coincide con el catálogo", GlassRojo.warning, icon = Icons.Rounded.Warning)
                    }
                }
            }

            Dato("Precio", formatoPrecio(precio?.valor) ?: "No detectado") {
                if (precio?.valor == null) StatusChip("Sin precio", GlassRojo.danger, icon = Icons.Rounded.ErrorOutline)
            }
            Dato("Tipo de precio", precio?.let { tipoPrecioTexto(it.tipo) } ?: "No identificado")
            Dato("Precio unitario", precio?.precioUnitario?.let { formatoPrecio(it) } ?: if (precio?.tienePrecioUnitario == true) "Visible" else "No visible") {
                if (precio?.tienePrecioUnitario == true) {
                    StatusChip("Sí", GlassRojo.success, icon = Icons.Rounded.CheckCircle)
                } else {
                    StatusChip("No", GlassRojo.warning, icon = Icons.Rounded.Warning)
                }
            }

            val ocr = b.etiqueta?.textoOcr?.trim().orEmpty()
            Column {
                Text("TEXTO LEÍDO DE LA ETIQUETA", style = Glass.type.label)
                Spacer(Modifier.height(4.dp))
                Text(
                    ocr.ifBlank { "No se leyó texto." },
                    style = Glass.type.secondary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            GlassButton("Ver texto leído", onVerTexto, icon = Icons.AutoMirrored.Rounded.Notes)
        }
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String, chip: (@Composable () -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(etiqueta.uppercase(), style = Glass.type.label)
        Text(valor, style = Glass.type.bodyStrong)
        chip?.invoke()
    }
}

@Composable
private fun TextoLeidoDialog(b: BorradorCaptura, onCerrar: () -> Unit) {
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // El diálogo vive en otra ventana: sin Haze (los componentes glass usan su relleno de respaldo).
        CompositionLocalProvider(LocalHazeState provides null) {
            val shape = RoundedCornerShape(GlassDimens.radiusCardMain)
            Column(
                Modifier
                    .padding(horizontal = GlassDimens.screenSide)
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .clip(shape)
                    .background(GlassRojo.bgElevated, shape)
                    .border(1.dp, GlassRojo.glassBorder, shape)
                    .padding(GlassDimens.s20),
            ) {
                Text("Texto leído", style = Glass.type.title)
                Spacer(Modifier.height(GlassDimens.s12))
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    TextoOcr("Producto", b.producto?.textoOcr)
                    TextoOcr("Etiqueta de precio", b.etiqueta?.textoOcr)
                }
                Spacer(Modifier.height(GlassDimens.s16))
                GlassButton("Cerrar", onCerrar)
            }
        }
    }
}

@Composable
private fun TextoOcr(titulo: String, texto: String?) {
    Column {
        Text(titulo.uppercase(), style = Glass.type.label)
        Spacer(Modifier.height(4.dp))
        Text(
            texto?.trim().orEmpty().ifBlank { "No se leyó texto." },
            style = Glass.type.secondary,
        )
    }
}

// --------------------------------------------------------------------------- previews

@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 1500)
@Composable
private fun RevisionOkPreview() {
    GlassRojoTheme {
        RevisionContent(
            state = RevisionUiState(
                sesion = EjemplosPreview.sesion,
                borrador = BorradorCaptura(
                    EjemplosPreview.esperado, EjemplosPreview.producto, EjemplosPreview.etiqueta, EjemplosPreview.precio,
                ),
            ),
            onConfirmar = {}, onRetomar = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 1500)
@Composable
private fun RevisionBloqueadaPreview() {
    GlassRojoTheme {
        RevisionContent(
            state = RevisionUiState(
                sesion = EjemplosPreview.sesion,
                borrador = BorradorCaptura(
                    EjemplosPreview.esperado,
                    EjemplosPreview.analisis(
                        com.richi_mc.hackathon3b.core.model.TipoFoto.PRODUCTO, "LECHE",
                        problemas = listOf(Problema.SIN_CODIGO_BARRAS),
                    ),
                    EjemplosPreview.etiquetaConProblemas,
                    PrecioDetectado(null, TipoPrecio.DESCONOCIDO, false, ""),
                ),
            ),
            onConfirmar = {}, onRetomar = {},
        )
    }
}
