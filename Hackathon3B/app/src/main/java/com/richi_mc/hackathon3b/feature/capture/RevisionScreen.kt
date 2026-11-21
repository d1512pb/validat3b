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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
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
import com.richi_mc.hackathon3b.core.model.FuenteDescripcion
import com.richi_mc.hackathon3b.core.model.PrecioDetectado
import com.richi_mc.hackathon3b.core.model.Problema
import com.richi_mc.hackathon3b.core.model.TipoPrecio
import com.richi_mc.hackathon3b.ui.components.CircleIconButton
import com.richi_mc.hackathon3b.ui.components.GlassBackground
import com.richi_mc.hackathon3b.ui.components.GlassButton
import com.richi_mc.hackathon3b.ui.components.GlassCard
import com.richi_mc.hackathon3b.ui.components.GlassTextField
import com.richi_mc.hackathon3b.ui.components.LocalHazeState
import com.richi_mc.hackathon3b.ui.components.PrimaryButton
import com.richi_mc.hackathon3b.ui.components.SegmentedControl
import com.richi_mc.hackathon3b.ui.components.StatusChip
import com.richi_mc.hackathon3b.ui.components.glassSurface
import com.richi_mc.hackathon3b.feature.labeling.EtiquetaChips
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
        state = state.copy(campos = vm.campos),
        onConfirmar = { vm.confirmar(onConfirmada) },
        onRetomar = onRetomar,
        onDescripcion = vm::cambiarDescripcion,
        onPrecio = vm::cambiarPrecio,
        onRestaurarDescripcion = vm::restaurarDescripcion,
        onRestaurarPrecio = vm::restaurarPrecio,
        onCodigoCatalogo = vm::cambiarCodigoCatalogo,
        onContratipo = vm::cambiarContratipo,
    )
}

@Composable
fun RevisionContent(
    state: RevisionUiState,
    onConfirmar: () -> Unit,
    onRetomar: () -> Unit,
    onDescripcion: (String) -> Unit = {},
    onPrecio: (String) -> Unit = {},
    onRestaurarDescripcion: () -> Unit = {},
    onRestaurarPrecio: () -> Unit = {},
    onCodigoCatalogo: (String) -> Unit = {},
    onContratipo: (Boolean) -> Unit = {},
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
                // Con un escaneo válido, los problemas de código de la foto no cuentan (problemasProducto).
                FotoCard(
                    "Producto", b.producto?.path,
                    b.producto?.copy(problemas = b.problemasProducto), existe = b.producto != null,
                )
                FotoCard("Etiqueta de precio", b.etiqueta?.path, b.etiqueta, existe = b.etiqueta != null)
                EsperadoCard(
                    campos = state.campos,
                    avisos = state.avisos,
                    revisarPrecio = b.precio?.requiereRevision == true,
                    onDescripcion = onDescripcion,
                    onPrecio = onPrecio,
                    onRestaurarDescripcion = onRestaurarDescripcion,
                    onRestaurarPrecio = onRestaurarPrecio,
                    onCodigoCatalogo = onCodigoCatalogo,
                    onContratipo = onContratipo,
                )
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
                    else -> {
                        EstadoAnalisisChips(analisis)
                        EtiquetaChips(analisis.etiquetas)
                    }
                }
            }
        }
        if (analisis != null && analisis.problemas.isNotEmpty()) {
            Spacer(Modifier.height(GlassDimens.s12))
            ProblemasMensajes(analisis.problemas)
        }
    }
}

// ------------------------------------------------------------- producto esperado

/** Chip de la fuente de lo detectado (descripción); null = no se detectó nada. */
@Composable
private fun ChipFuente(fuente: FuenteDescripcion?) {
    when (fuente) {
        FuenteDescripcion.CATALOGO -> StatusChip("Del catálogo", GlassRojo.success, icon = Icons.Rounded.CheckCircle)
        FuenteDescripcion.ETIQUETA_RACK -> StatusChip("Leído de la etiqueta", GlassRojo.info, icon = Icons.Rounded.Info)
        FuenteDescripcion.FOTO_PRODUCTO -> StatusChip("Leído del producto", GlassRojo.info, icon = Icons.Rounded.Info)
        FuenteDescripcion.CATEGORIA -> StatusChip("Sugerencia por categoría", GlassRojo.warning, icon = Icons.Rounded.Warning)
        FuenteDescripcion.MANUAL, null -> StatusChip("No detectado", GlassRojo.warning, icon = Icons.Rounded.Warning)
    }
}

/**
 * Fila bajo un campo: chip de la fuente o, si el usuario lo editó, chip "Editado" + botón para
 * restaurar el valor detectado (con ese valor escrito debajo para saber a qué se vuelve).
 */
@Composable
private fun FuenteCampo(
    editado: Boolean,
    detectado: String?,
    nombreCampo: String,
    onRestaurar: () -> Unit,
    chipFuente: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = GlassDimens.s8), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (editado) {
                StatusChip("Editado", GlassRojo.accentInk, icon = Icons.Rounded.Edit)
                Spacer(Modifier.weight(1f))
                CircleIconButton(Icons.Rounded.Restore, "Restaurar $nombreCampo detectado", onRestaurar)
            } else {
                chipFuente()
            }
        }
        if (editado) {
            Text(
                if (detectado.isNullOrBlank()) "No se detectó nada" else "Detectado: $detectado",
                style = Glass.type.secondary, color = GlassRojo.inkMuted,
            )
        }
    }
}

@Composable
private fun EsperadoCard(
    campos: CamposEsperado,
    avisos: List<String>,
    /** El precio detectado tiene confianza baja (PLAN3 §4): chip "Revisa el precio" mientras no se edite. */
    revisarPrecio: Boolean,
    onDescripcion: (String) -> Unit,
    onPrecio: (String) -> Unit,
    onRestaurarDescripcion: () -> Unit,
    onRestaurarPrecio: () -> Unit,
    onCodigoCatalogo: (String) -> Unit,
    onContratipo: (Boolean) -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text("PRODUCTO ESPERADO", style = Glass.type.label)
        Text(
            "Revisa lo que leyó la app y corrígelo si no es el producto.",
            style = Glass.type.secondary, color = GlassRojo.inkMuted,
        )
        Spacer(Modifier.height(GlassDimens.s12))

        GlassTextField(
            value = campos.descripcion,
            onValueChange = onDescripcion,
            label = "Descripción",
            placeholder = "Escribe la descripción",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        )
        FuenteCampo(
            editado = campos.descripcionEditada,
            detectado = campos.descripcionDetectada,
            nombreCampo = "la descripción",
            onRestaurar = onRestaurarDescripcion,
        ) { ChipFuente(campos.fuente.takeIf { campos.descripcionDetectada != null }) }

        Spacer(Modifier.height(GlassDimens.s12))
        CampoPrecio(valor = campos.precioTexto, onValor = onPrecio)
        FuenteCampo(
            editado = campos.precioEditado,
            detectado = formatoPrecio(campos.precioDetectado),
            nombreCampo = "el precio",
            onRestaurar = onRestaurarPrecio,
        ) {
            if (campos.precioDetectado != null && revisarPrecio && !campos.precioEditado) {
                StatusChip("Revisa el precio", GlassRojo.warning, icon = Icons.Rounded.Warning)
            } else if (campos.precioDetectado != null) {
                StatusChip("Leído de la etiqueta", GlassRojo.info, icon = Icons.Rounded.Info)
            } else {
                StatusChip("No detectado", GlassRojo.warning, icon = Icons.Rounded.Warning)
            }
        }
        avisos.forEach { aviso ->
            Spacer(Modifier.height(GlassDimens.s8))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Rounded.Warning, contentDescription = null,
                    tint = GlassRojo.warning, modifier = Modifier.padding(top = 2.dp).size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(aviso, style = Glass.type.secondary, color = GlassRojo.warning)
            }
        }

        Spacer(Modifier.height(GlassDimens.s16))
        GlassTextField(
            value = campos.codigoCatalogo,
            onValueChange = onCodigoCatalogo,
            label = "Código de barras del catálogo",
            placeholder = "Solo números (opcional)",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(Modifier.height(GlassDimens.s12))
        Text("Es contratipo", style = Glass.type.secondary, color = GlassRojo.inkSecondary)
        Spacer(Modifier.height(6.dp))
        SegmentedControl(
            options = listOf(true, false),
            selected = campos.esContratipo,
            label = { if (it) "Sí" else "No" },
            onSelect = onContratipo,
        )
    }
}

/**
 * Campo de precio: igual que GlassTextField (52 dp, radio 16, foco accent-ink) pero con prefijo "$" y el
 * valor en estilo stat (24 sp bold) para que el precio se vea de un vistazo.
 */
@Composable
private fun CampoPrecio(valor: String, onValor: (String) -> Unit) {
    var enfocado by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(GlassDimens.radiusInput)
    val stat = Glass.type.display.copy(fontSize = 24.sp, lineHeight = 30.sp)
    Column(Modifier.fillMaxWidth()) {
        Text("Precio", style = Glass.type.secondary, color = GlassRojo.inkSecondary)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .glassSurface(shape, LocalHazeState.current)
                .then(if (enfocado) Modifier.border(2.dp, GlassRojo.accentInk.copy(alpha = 0.7f), shape) else Modifier)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$", style = stat, color = GlassRojo.inkMuted)
            Spacer(Modifier.width(4.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (valor.isEmpty()) Text("0.00", style = stat, color = GlassRojo.inkMuted)
                BasicTextField(
                    value = valor,
                    onValueChange = onValor,
                    singleLine = true,
                    textStyle = stat,
                    cursorBrush = SolidColor(GlassRojo.accentInk),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    interactionSource = remember { MutableInteractionSource() },
                    modifier = Modifier.fillMaxWidth().onFocusChanged { enfocado = it.isFocused },
                )
            }
        }
    }
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
                "Código en la foto del producto",
                if (codProducto != null) {
                    codProducto + (b.producto?.formatoCodigo?.let { " · " + it.replace('_', ' ') } ?: "")
                } else "No detectado",
            ) {
                if (codProducto == null) {
                    if (b.codigoEscaneado?.checksumOk == true) {
                        StatusChip("Sin código en la foto", GlassRojo.inkMuted, icon = Icons.Rounded.Info)
                    } else {
                        StatusChip("Sin código", GlassRojo.danger, icon = Icons.Rounded.ErrorOutline)
                    }
                }
            }

            val escaneado = b.codigoEscaneado
            Dato(
                "Código escaneado",
                if (escaneado != null) {
                    escaneado.valor + (formatoLegible(escaneado.formato)?.let { " · $it" } ?: "")
                } else "No se escaneó",
            ) {
                when {
                    escaneado == null && b.requiereEscaneo ->
                        StatusChip("Falta escanear", GlassRojo.danger, icon = Icons.Rounded.ErrorOutline)
                    escaneado == null -> StatusChip("No necesario", GlassRojo.inkMuted, icon = Icons.Rounded.Info)
                    escaneado.checksumOk ->
                        StatusChip("Checksum válido", GlassRojo.success, icon = Icons.Rounded.CheckCircle)
                    else ->
                        StatusChip("Código inválido: vuelve a escanear", GlassRojo.danger, icon = Icons.Rounded.ErrorOutline)
                }
                if (escaneado != null && codProducto != null) {
                    Spacer(Modifier.height(4.dp))
                    if (mismoCodigo(escaneado.valor, codProducto)) {
                        StatusChip("Coincide con la foto", GlassRojo.success, icon = Icons.Rounded.CheckCircle)
                    } else {
                        StatusChip("No coincide con la foto", GlassRojo.warning, icon = Icons.Rounded.Warning)
                    }
                }
            }

            Dato("Código final", codigoFinal(b) ?: "No detectado") {
                if (codigoFinal(b) == null) StatusChip("Sin código", GlassRojo.danger, icon = Icons.Rounded.ErrorOutline)
            }

            Dato("Código en la etiqueta", codEtiqueta ?: "No detectado") {
                val referencia = b.codigoEscaneado?.takeIf { it.checksumOk }?.valor ?: codProducto
                when {
                    referencia == null || codEtiqueta == null ->
                        StatusChip("No se pudo comparar", GlassRojo.warning, icon = Icons.Rounded.Warning)
                    mismoCodigo(referencia, codEtiqueta) ->
                        StatusChip("Coincide con el producto", GlassRojo.success, icon = Icons.Rounded.CheckCircle)
                    else ->
                        StatusChip("No coincide con el producto", GlassRojo.warning, icon = Icons.Rounded.Warning)
                }
            }

            if (codCatalogo != null) {
                Dato("Código del catálogo", codCatalogo) {
                    val final = codigoFinal(b)
                    when {
                        final == null ->
                            StatusChip("No se pudo comparar", GlassRojo.warning, icon = Icons.Rounded.Warning)
                        mismoCodigo(final, codCatalogo) ->
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

private fun borradorEjemplo(esperado: com.richi_mc.hackathon3b.core.model.ProductoEsperado) = BorradorCaptura(
    esperado, EjemplosPreview.producto, EjemplosPreview.etiqueta, EjemplosPreview.precio,
)

private fun estadoEjemplo(
    esperado: com.richi_mc.hackathon3b.core.model.ProductoEsperado,
    borrador: BorradorCaptura = borradorEjemplo(esperado),
) = RevisionUiState(
    sesion = EjemplosPreview.sesion, borrador = borrador, campos = CamposEsperado.desde(esperado),
)

/** Prellenada: descripción y precio leídos de la etiqueta, sin editar. */
@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 1900)
@Composable
private fun RevisionOkPreview() {
    GlassRojoTheme {
        RevisionContent(state = estadoEjemplo(EjemplosPreview.esperado), onConfirmar = {}, onRetomar = {})
    }
}

/** Editada: chip "Editado" y botón de restaurar en ambos campos. */
@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 1900)
@Composable
private fun RevisionEditadaPreview() {
    GlassRojoTheme {
        RevisionContent(state = estadoEjemplo(EjemplosPreview.esperadoEditado), onConfirmar = {}, onRetomar = {})
    }
}

/** Precio con confianza baja: chip warning "Revisa el precio" en lugar de "Leído de la etiqueta". */
@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 1900)
@Composable
private fun RevisionRevisaPrecioPreview() {
    GlassRojoTheme {
        RevisionContent(
            state = estadoEjemplo(
                EjemplosPreview.esperado,
                BorradorCaptura(
                    EjemplosPreview.esperado, EjemplosPreview.producto, EjemplosPreview.etiqueta,
                    EjemplosPreview.precio.copy(confianza = 0.45f),
                ),
            ),
            onConfirmar = {}, onRetomar = {},
        )
    }
}

/** No detectada: campos vacíos, chip "No detectado", no se puede confirmar sin descripción. */
@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 1900)
@Composable
private fun RevisionNoDetectadaPreview() {
    GlassRojoTheme {
        RevisionContent(
            state = estadoEjemplo(
                EjemplosPreview.esperadoVacio,
                BorradorCaptura(
                    EjemplosPreview.esperadoVacio, EjemplosPreview.producto, EjemplosPreview.etiquetaConProblemas,
                    PrecioDetectado(null, TipoPrecio.DESCONOCIDO, false, ""),
                ),
            ),
            onConfirmar = {}, onRetomar = {},
        )
    }
}

/** Solo una categoría de ML Kit como pista (chip warning) y precio sin detectar. */
@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 1900)
@Composable
private fun RevisionCategoriaPreview() {
    GlassRojoTheme {
        RevisionContent(
            state = estadoEjemplo(
                EjemplosPreview.esperadoPorCategoria,
                BorradorCaptura(
                    EjemplosPreview.esperadoPorCategoria, EjemplosPreview.producto, EjemplosPreview.etiqueta,
                    PrecioDetectado(null, TipoPrecio.DESCONOCIDO, false, ""),
                ),
            ),
            onConfirmar = {}, onRetomar = {},
        )
    }
}

/** Foto de la pared: PRODUCTO_NO_VISIBLE es advertencia (no bloquea, se puede retomar) y se ven las etiquetas. */
@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 1900)
@Composable
private fun RevisionProductoNoVisiblePreview() {
    GlassRojoTheme {
        RevisionContent(
            state = estadoEjemplo(
                EjemplosPreview.esperado,
                BorradorCaptura(
                    EjemplosPreview.esperado, EjemplosPreview.productoNoVisible,
                    EjemplosPreview.etiqueta, EjemplosPreview.precio,
                ),
            ),
            onConfirmar = {}, onRetomar = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 1900)
@Composable
private fun RevisionBloqueadaPreview() {
    GlassRojoTheme {
        RevisionContent(
            state = estadoEjemplo(
                EjemplosPreview.esperado,
                BorradorCaptura(
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

@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 2000)
@Composable
private fun RevisionConEscaneoPreview() {
    GlassRojoTheme {
        RevisionContent(
            state = estadoEjemplo(
                EjemplosPreview.esperado,
                BorradorCaptura(
                    productoEsperado = EjemplosPreview.esperado,
                    producto = EjemplosPreview.analisis(
                        com.richi_mc.hackathon3b.core.model.TipoFoto.PRODUCTO, "LECHE",
                        problemas = listOf(Problema.SIN_CODIGO_BARRAS),
                    ),
                    etiqueta = EjemplosPreview.etiqueta,
                    precio = EjemplosPreview.precio,
                    codigoEscaneado = com.richi_mc.hackathon3b.core.model.CodigoEscaneado(
                        "7501055300075", "EAN_13", true, 0L,
                    ),
                ),
            ),
            onConfirmar = {}, onRetomar = {},
        )
    }
}
