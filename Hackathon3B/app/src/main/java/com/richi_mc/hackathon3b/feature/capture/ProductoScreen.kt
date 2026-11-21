package com.richi_mc.hackathon3b.feature.capture

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.richi_mc.hackathon3b.app.AppContainer
import com.richi_mc.hackathon3b.core.model.AnalisisImagen
import com.richi_mc.hackathon3b.core.model.BorradorCaptura
import com.richi_mc.hackathon3b.core.model.CodigoEscaneado
import com.richi_mc.hackathon3b.core.model.PrecioDetectado
import com.richi_mc.hackathon3b.core.model.Problema
import com.richi_mc.hackathon3b.core.model.Sesion
import com.richi_mc.hackathon3b.core.model.TipoFoto
import com.richi_mc.hackathon3b.feature.camera.BarcodeScanner
import com.richi_mc.hackathon3b.feature.camera.CameraCapture
import com.richi_mc.hackathon3b.ui.components.CircleIconButton
import com.richi_mc.hackathon3b.ui.components.GlassBackground
import com.richi_mc.hackathon3b.ui.components.GlassButton
import com.richi_mc.hackathon3b.ui.components.GlassCard
import com.richi_mc.hackathon3b.ui.components.GlassProgressBar
import com.richi_mc.hackathon3b.ui.components.GlassTextField
import com.richi_mc.hackathon3b.ui.components.IconBox
import com.richi_mc.hackathon3b.ui.components.PrimaryButton
import com.richi_mc.hackathon3b.ui.components.SegmentedControl
import com.richi_mc.hackathon3b.ui.components.StatusChip
import com.richi_mc.hackathon3b.ui.theme.Glass
import com.richi_mc.hackathon3b.ui.theme.GlassDimens
import com.richi_mc.hackathon3b.ui.theme.GlassRojo
import com.richi_mc.hackathon3b.ui.theme.GlassRojoTheme

private const val PASO_PRODUCTO = 2
private const val PASO_CODIGO = 3
private const val PASO_ETIQUETA = 4

@Composable
fun ProductoScreen(
    container: AppContainer,
    onRevisar: () -> Unit,          // producto y etiqueta en container.borrador → revision
    onAtras: () -> Unit,            // → inicio
) {
    val vm: ProductoViewModel = viewModel(
        factory = viewModelFactory { initializer { ProductoViewModel(container) } }
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val selectorProducto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.onImagenElegida(context, uri, TipoFoto.PRODUCTO)
    }
    val selectorEtiqueta = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.onImagenElegida(context, uri, TipoFoto.ETIQUETA)
    }

    val camara = state.camara
    if (camara != null) {
        BackHandler { vm.cerrarCamara() }
        CameraCapture(
            tipo = camara,
            onFoto = { path -> vm.onFoto(path, camara) },
            onCerrar = vm::cerrarCamara,
            paso = if (camara == TipoFoto.PRODUCTO) PASO_PRODUCTO else PASO_ETIQUETA,
            totalPasos = ProductoUiState.TOTAL_PASOS,
        )
        return
    }
    if (state.escaner) {
        BackHandler { vm.cerrarEscaner() }
        BarcodeScanner(
            onCodigo = vm::onCodigoEscaneado,
            onCerrar = vm::cerrarEscaner,
            paso = PASO_CODIGO,
            totalPasos = ProductoUiState.TOTAL_PASOS,
        )
        return
    }

    ProductoContent(
        state = state,
        descripcion = vm.descripcion,
        codigoBarras = vm.codigoBarras,
        onDescripcion = vm::cambiarDescripcion,
        onCodigoBarras = vm::cambiarCodigoBarras,
        onContratipo = vm::cambiarContratipo,
        onTomar = vm::abrirCamara,
        onSubir = { tipo ->
            val pedido = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            if (tipo == TipoFoto.PRODUCTO) selectorProducto.launch(pedido) else selectorEtiqueta.launch(pedido)
        },
        onEscanear = vm::abrirEscaner,
        onRevisar = onRevisar,
        onAtras = onAtras,
    )
}

@Composable
fun ProductoContent(
    state: ProductoUiState,
    descripcion: String,
    codigoBarras: String,
    onDescripcion: (String) -> Unit,
    onCodigoBarras: (String) -> Unit,
    onContratipo: (Boolean) -> Unit,
    onTomar: (TipoFoto) -> Unit,
    onSubir: (TipoFoto) -> Unit,
    onEscanear: () -> Unit,
    onRevisar: () -> Unit,
    onAtras: () -> Unit,
) {
    GlassBackground {
        val sesion = state.sesion
        if (sesion == null) {
            SinSesion(onAtras)
            return@GlassBackground
        }
        val borrador = state.borrador
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            Encabezado(sesion, state.numero, onAtras)

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = GlassDimens.screenSide)
                    .padding(top = GlassDimens.s16, bottom = GlassDimens.s16),
                verticalArrangement = GlassDimens.itemSpacing,
            ) {
                Progreso(state.pasosHechos)

                EsperadoCard(
                    descripcion = descripcion,
                    codigoBarras = codigoBarras,
                    esContratipo = borrador.productoEsperado.esContratipo,
                    onDescripcion = onDescripcion,
                    onCodigoBarras = onCodigoBarras,
                    onContratipo = onContratipo,
                )

                PasoFotoCard(
                    titulo = "1. Foto del producto",
                    guia = "Que se vea el producto; el código de barras ayuda si está visible",
                    icono = Icons.Rounded.Inventory2,
                    analisis = borrador.producto,
                    problemas = borrador.problemasProducto,
                    analizando = state.analizando == TipoFoto.PRODUCTO,
                    ocupado = state.analizando != null,
                    onTomar = { onTomar(TipoFoto.PRODUCTO) },
                    onSubir = { onSubir(TipoFoto.PRODUCTO) },
                    precio = null,
                )
                CodigoCard(
                    borrador = borrador,
                    ocupado = state.analizando != null,
                    onEscanear = onEscanear,
                )
                PasoFotoCard(
                    titulo = "3. Etiqueta de precio",
                    guia = "Que se lean el precio y la descripción",
                    icono = Icons.Rounded.Sell,
                    analisis = borrador.etiqueta,
                    problemas = borrador.etiqueta?.problemas.orEmpty(),
                    analizando = state.analizando == TipoFoto.ETIQUETA,
                    ocupado = state.analizando != null,
                    onTomar = { onTomar(TipoFoto.ETIQUETA) },
                    onSubir = { onSubir(TipoFoto.ETIQUETA) },
                    precio = borrador.precio,
                    esEtiqueta = true,
                )

                if (state.error != null) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Rounded.ErrorOutline, contentDescription = null,
                            tint = GlassRojo.danger, modifier = Modifier.padding(top = 2.dp).size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(state.error, style = Glass.type.secondary, color = GlassRojo.danger)
                    }
                }
            }

            Column(
                Modifier
                    .padding(horizontal = GlassDimens.screenSide)
                    .padding(top = GlassDimens.s8, bottom = GlassDimens.s12)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!state.puedeRevisar && state.motivosBloqueo.isNotEmpty()) {
                    Text(
                        state.motivosBloqueo.joinToString(" "),
                        style = Glass.type.secondary, color = GlassRojo.inkMuted,
                    )
                }
                PrimaryButton("Revisar captura", onRevisar, enabled = state.puedeRevisar)
            }
        }
    }
}

@Composable
private fun Encabezado(sesion: Sesion, numero: Int, onAtras: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .background(GlassRojo.navFill)
                .statusBarsPadding()
                .padding(horizontal = GlassDimens.screenSide, vertical = GlassDimens.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", onAtras)
            Spacer(Modifier.width(GlassDimens.s12))
            Column(Modifier.weight(1f)) {
                Text("PRODUCTO $numero", style = Glass.type.label)
                Text(sesion.tienda, style = Glass.type.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sesion.cadena.etiqueta, style = Glass.type.secondary, color = GlassRojo.inkMuted, maxLines = 1)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(GlassRojo.glassBorder))
    }
}

@Composable
private fun Progreso(pasosHechos: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GlassProgressBar(pasosHechos / ProductoUiState.TOTAL_PASOS.toFloat())
        Text("$pasosHechos de ${ProductoUiState.TOTAL_PASOS} pasos", style = Glass.type.secondary, color = GlassRojo.inkMuted)
    }
}

@Composable
private fun EsperadoCard(
    descripcion: String,
    codigoBarras: String,
    esContratipo: Boolean,
    onDescripcion: (String) -> Unit,
    onCodigoBarras: (String) -> Unit,
    onContratipo: (Boolean) -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text("Producto esperado", style = Glass.type.title)
        Text("Opcional: lo que dices estar fotografiando.", style = Glass.type.secondary, color = GlassRojo.inkMuted)
        Spacer(Modifier.height(GlassDimens.s12))
        GlassTextField(
            value = descripcion,
            onValueChange = onDescripcion,
            label = "Descripción",
            placeholder = "Ej. Leche entera 1 L",
        )
        Spacer(Modifier.height(GlassDimens.s12))
        GlassTextField(
            value = codigoBarras,
            onValueChange = onCodigoBarras,
            label = "Código de barras del catálogo",
            placeholder = "Solo números",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(Modifier.height(GlassDimens.s12))
        Text("Es contratipo", style = Glass.type.secondary, color = GlassRojo.inkSecondary)
        Spacer(Modifier.height(6.dp))
        SegmentedControl(
            options = listOf(true, false),
            selected = esContratipo,
            label = { if (it) "Sí" else "No" },
            onSelect = onContratipo,
        )
    }
}

/** "Tomar foto" / "Subir imagen" lado a lado (ambos GlassButton). */
@Composable
private fun BotonesFoto(
    hayFoto: Boolean,
    ocupado: Boolean,
    onTomar: () -> Unit,
    onSubir: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(GlassDimens.s8)) {
        GlassButton(
            text = if (hayFoto) "Retomar" else "Tomar foto",
            onClick = onTomar,
            modifier = Modifier.weight(1f),
            enabled = !ocupado,
            icon = Icons.Rounded.PhotoCamera,
        )
        GlassButton(
            text = "Subir imagen",
            onClick = onSubir,
            modifier = Modifier.weight(1f),
            enabled = !ocupado,
            icon = Icons.Rounded.Image,
        )
    }
}

/**
 * Bloque "Texto encontrado": etiqueta en mayúsculas + texto (máx. 6 líneas, "Ver todo"/"Ver menos").
 * [extra] va debajo del texto (p. ej. el precio detectado).
 */
@Composable
private fun TextoEncontrado(
    texto: String?,
    modifier: Modifier = Modifier,
    extra: (@Composable () -> Unit)? = null,
) {
    var expandido by rememberSaveable { mutableStateOf(false) }
    var desborda by remember { mutableStateOf(false) }
    val limpio = texto?.trim().orEmpty()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("TEXTO ENCONTRADO", style = Glass.type.label)
        if (limpio.isEmpty()) {
            Text("Sin texto leído", style = Glass.type.secondary, color = GlassRojo.inkMuted)
        } else {
            Text(
                limpio,
                style = Glass.type.secondary,
                maxLines = if (expandido) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expandido) desborda = it.hasVisualOverflow },
            )
            if (desborda || expandido) {
                Text(
                    if (expandido) "Ver menos" else "Ver todo",
                    style = Glass.type.bodyStrong,
                    color = GlassRojo.accentInk,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable(role = Role.Button) { expandido = !expandido }
                        .padding(vertical = 12.dp),
                )
            }
        }
        extra?.invoke()
    }
}

/** "Precio detectado: $24.90 · Regular · Con precio unitario" o "No se encontró precio". */
@Composable
private fun PrecioDetectadoLinea(precio: PrecioDetectado?) {
    val valor = formatoPrecio(precio?.valor)
    Spacer(Modifier.height(4.dp))
    if (precio == null || valor == null) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Rounded.ErrorOutline, contentDescription = null,
                tint = GlassRojo.danger, modifier = Modifier.padding(top = 2.dp).size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("No se encontró precio", style = Glass.type.secondary, color = GlassRojo.danger)
        }
    } else {
        val unitario = if (precio.tienePrecioUnitario) "Con precio unitario" else "Sin precio unitario"
        Text(
            "Precio detectado: $valor · ${tipoPrecioTexto(precio.tipo)} · $unitario",
            style = Glass.type.bodyStrong, color = GlassRojo.ink,
        )
    }
}

@Composable
private fun PasoFotoCard(
    titulo: String,
    guia: String,
    icono: ImageVector,
    analisis: AnalisisImagen?,
    problemas: List<Problema>,
    analizando: Boolean,
    ocupado: Boolean,
    onTomar: () -> Unit,
    onSubir: () -> Unit,
    precio: PrecioDetectado?,
    esEtiqueta: Boolean = false,
) {
    val bloqueada = problemas.any { it.bloqueante }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                analizando -> IconBox(icono, GlassRojo.info)
                analisis == null -> IconBox(icono, GlassRojo.inkSecondary)
                bloqueada -> IconBox(Icons.Rounded.ErrorOutline, GlassRojo.danger)
                else -> IconBox(Icons.Rounded.CheckCircle, GlassRojo.success)
            }
            Spacer(Modifier.width(GlassDimens.s12))
            Column(Modifier.weight(1f)) {
                Text(titulo, style = Glass.type.title)
                Text(guia, style = Glass.type.secondary, color = GlassRojo.inkSecondary)
            }
            if (analisis != null) {
                Spacer(Modifier.width(GlassDimens.s12))
                Miniatura(analisis.path, "Miniatura: $titulo")
            }
        }
        Spacer(Modifier.height(GlassDimens.s12))
        when {
            analizando -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp), color = GlassRojo.info, strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(GlassDimens.s8))
                Text("Analizando foto…", style = Glass.type.secondary, color = GlassRojo.info)
            }
            analisis == null -> StatusChip("Pendiente", GlassRojo.inkMuted, icon = Icons.Rounded.Schedule)
            else -> {
                EstadoAnalisisChips(analisis.copy(problemas = problemas))
                if (problemas.isNotEmpty()) {
                    Spacer(Modifier.height(GlassDimens.s8))
                    ProblemasMensajes(problemas)
                }
            }
        }
        Spacer(Modifier.height(GlassDimens.s12))
        BotonesFoto(hayFoto = analisis != null, ocupado = ocupado, onTomar = onTomar, onSubir = onSubir)
        if (analisis != null && !analizando) {
            Spacer(Modifier.height(GlassDimens.s12))
            TextoEncontrado(
                texto = analisis.textoOcr,
                extra = if (esEtiqueta) ({ PrecioDetectadoLinea(precio) }) else null,
            )
        }
    }
}

/** Sección "2. Código de barras": escaneo en vivo, código de la foto y comparaciones. */
@Composable
private fun CodigoCard(
    borrador: BorradorCaptura,
    ocupado: Boolean,
    onEscanear: () -> Unit,
) {
    val escaneado = borrador.codigoEscaneado
    val codFoto = borrador.producto?.codigoBarras
    val sinFoto = borrador.producto == null
    val atenuada = sinFoto && escaneado == null
    val codCatalogo = borrador.productoEsperado.codigoBarras?.takeIf { it.isNotBlank() }
    val final = codigoFinal(borrador)

    GlassCard(Modifier.fillMaxWidth().alpha(if (atenuada) 0.6f else 1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                escaneado != null && escaneado.checksumOk -> IconBox(Icons.Rounded.CheckCircle, GlassRojo.success)
                escaneado != null -> IconBox(Icons.Rounded.ErrorOutline, GlassRojo.danger)
                codFoto != null && !borrador.requiereEscaneo -> IconBox(Icons.Rounded.CheckCircle, GlassRojo.success)
                borrador.requiereEscaneo -> IconBox(Icons.Rounded.Warning, GlassRojo.warning)
                else -> IconBox(Icons.Rounded.QrCodeScanner, GlassRojo.inkSecondary)
            }
            Spacer(Modifier.width(GlassDimens.s12))
            Column(Modifier.weight(1f)) {
                Text("2. Código de barras", style = Glass.type.title)
                Text(
                    "Escanéalo del producto o del empaque",
                    style = Glass.type.secondary, color = GlassRojo.inkSecondary,
                )
            }
        }
        Spacer(Modifier.height(GlassDimens.s12))

        when {
            escaneado != null -> {
                Text(
                    escaneado.valor,
                    style = Glass.type.display.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                )
                formatoLegible(escaneado.formato)?.let {
                    Text(it, style = Glass.type.secondary, color = GlassRojo.inkMuted)
                }
                Spacer(Modifier.height(GlassDimens.s8))
                CodigoChips {
                    if (escaneado.checksumOk) {
                        StatusChip("Checksum válido", GlassRojo.success, icon = Icons.Rounded.CheckCircle)
                    } else {
                        StatusChip("Código inválido: vuelve a escanear", GlassRojo.danger, icon = Icons.Rounded.ErrorOutline)
                    }
                    if (codFoto != null && !mismoCodigo(escaneado.valor, codFoto)) {
                        StatusChip(
                            "El código escaneado no coincide con el de la foto",
                            GlassRojo.warning, icon = Icons.Rounded.Warning,
                        )
                    }
                }
            }
            codFoto != null && !borrador.requiereEscaneo -> {
                Text("Detectado en la foto: $codFoto", style = Glass.type.bodyStrong)
                Spacer(Modifier.height(GlassDimens.s8))
                CodigoChips {
                    StatusChip("Código leído en la foto", GlassRojo.success, icon = Icons.Rounded.CheckCircle)
                }
            }
            borrador.requiereEscaneo -> Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Rounded.Warning, contentDescription = null,
                    tint = GlassRojo.warning, modifier = Modifier.padding(top = 2.dp).size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "La foto no muestra el código. Escanéalo del producto o del empaque.",
                    style = Glass.type.secondary, color = GlassRojo.warning,
                )
            }
            sinFoto -> Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Rounded.Schedule, contentDescription = null,
                    tint = GlassRojo.inkMuted, modifier = Modifier.padding(top = 2.dp).size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Primero toma la foto del producto", style = Glass.type.secondary, color = GlassRojo.inkMuted)
            }
        }

        if (codCatalogo != null && final != null) {
            Spacer(Modifier.height(GlassDimens.s8))
            CodigoChips {
                if (mismoCodigo(final, codCatalogo)) {
                    StatusChip("Coincide con el catálogo", GlassRojo.success, icon = Icons.Rounded.CheckCircle)
                } else {
                    StatusChip("No coincide con el catálogo", GlassRojo.warning, icon = Icons.Rounded.Warning)
                }
            }
        }

        Spacer(Modifier.height(GlassDimens.s12))
        GlassButton(
            text = when {
                escaneado != null -> "Volver a escanear"
                codFoto != null && !borrador.requiereEscaneo -> "Escanear de todos modos"
                else -> "Escanear código"
            },
            onClick = onEscanear,
            enabled = !ocupado,
            icon = Icons.Rounded.QrCodeScanner,
        )

        Spacer(Modifier.height(GlassDimens.s12))
        TextoEncontrado(texto = codigosLeidos(borrador))
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CodigoChips(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** Códigos leídos por cada vía (foto, escaneo, etiqueta), uno por línea; vacío si no hay ninguno. */
internal fun codigosLeidos(b: BorradorCaptura): String = buildList {
    b.producto?.codigoBarras?.let { add("Foto del producto: $it" + (formatoLegible(b.producto.formatoCodigo)?.let { f -> " ($f)" } ?: "")) }
    b.codigoEscaneado?.let {
        add("Escaneado: ${it.valor}" + (formatoLegible(it.formato)?.let { f -> " ($f)" } ?: ""))
    }
    b.etiqueta?.codigoBarras?.let { add("Etiqueta: $it") }
}.joinToString("\n")

@Composable
private fun SinSesion(onAtras: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(GlassDimens.screenSide),
        verticalArrangement = Arrangement.Center,
    ) {
        GlassCard(Modifier.fillMaxWidth()) {
            IconBox(Icons.Rounded.ErrorOutline, GlassRojo.warning)
            Spacer(Modifier.height(GlassDimens.s12))
            Text("No hay una sucursal activa", style = Glass.type.title)
            Text(
                "Inicia una sesión desde el inicio para capturar productos.",
                style = Glass.type.secondary, color = GlassRojo.inkSecondary,
            )
        }
        Spacer(Modifier.height(GlassDimens.s16))
        PrimaryButton("Volver al inicio", onAtras)
    }
}

// --------------------------------------------------------------------------- previews

private val escaneoOk = CodigoEscaneado("7501055363056", "EAN_13", true, 0L)

@Composable
private fun PreviewProducto(state: ProductoUiState, descripcion: String = "", codigo: String = "") {
    GlassRojoTheme {
        ProductoContent(
            state = state,
            descripcion = descripcion, codigoBarras = codigo,
            onDescripcion = {}, onCodigoBarras = {}, onContratipo = {}, onTomar = {}, onSubir = {},
            onEscanear = {}, onRevisar = {}, onAtras = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 1200)
@Composable
private fun ProductoVacioPreview() =
    PreviewProducto(ProductoUiState(sesion = EjemplosPreview.sesion, capturadas = 3))

@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 1400)
@Composable
private fun ProductoCodigoEnFotoPreview() = PreviewProducto(
    ProductoUiState(
        sesion = EjemplosPreview.sesion,
        borrador = BorradorCaptura(
            productoEsperado = EjemplosPreview.esperado,
            producto = EjemplosPreview.producto,
            etiqueta = EjemplosPreview.etiqueta,
            precio = EjemplosPreview.precio,
        ),
    ),
    descripcion = "Leche entera 1 L", codigo = "7501055300075",
)

@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 1400)
@Composable
private fun ProductoRequiereEscaneoPreview() = PreviewProducto(
    ProductoUiState(
        sesion = EjemplosPreview.sesion,
        borrador = BorradorCaptura(
            producto = EjemplosPreview.analisis(
                TipoFoto.PRODUCTO, "LECHE ENTERA 1 L", problemas = listOf(Problema.SIN_CODIGO_BARRAS),
            ),
        ),
    ),
)

@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 1500)
@Composable
private fun ProductoCodigoEscaneadoPreview() = PreviewProducto(
    ProductoUiState(
        sesion = EjemplosPreview.sesion,
        borrador = BorradorCaptura(
            productoEsperado = EjemplosPreview.esperado,
            producto = EjemplosPreview.analisis(
                TipoFoto.PRODUCTO, "LECHE ENTERA 1 L", problemas = listOf(Problema.SIN_CODIGO_BARRAS),
                codigo = "7501055300075",
            ),
            codigoEscaneado = escaneoOk,
            etiqueta = EjemplosPreview.etiqueta,
            precio = EjemplosPreview.precio,
        ),
    ),
    descripcion = "Leche entera 1 L", codigo = "7501055300075",
)

@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 1400)
@Composable
private fun ProductoCodigoInvalidoPreview() = PreviewProducto(
    ProductoUiState(
        sesion = EjemplosPreview.sesion,
        borrador = BorradorCaptura(
            producto = EjemplosPreview.analisis(
                TipoFoto.PRODUCTO, "LECHE", problemas = listOf(Problema.SIN_CODIGO_BARRAS),
            ),
            codigoEscaneado = escaneoOk.copy(valor = "7501055363057", checksumOk = false),
        ),
    ),
)

@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 1400)
@Composable
private fun ProductoConProblemasPreview() = PreviewProducto(
    ProductoUiState(
        sesion = EjemplosPreview.sesion,
        borrador = BorradorCaptura(
            productoEsperado = EjemplosPreview.esperado,
            producto = EjemplosPreview.producto,
            etiqueta = EjemplosPreview.etiquetaConProblemas,
            precio = PrecioDetectado(null, com.richi_mc.hackathon3b.core.model.TipoPrecio.DESCONOCIDO, false, ""),
        ),
    ),
    descripcion = "Leche entera 1 L", codigo = "7501055300075",
)

@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 800)
@Composable
private fun ProductoSinSesionPreview() = PreviewProducto(ProductoUiState())
