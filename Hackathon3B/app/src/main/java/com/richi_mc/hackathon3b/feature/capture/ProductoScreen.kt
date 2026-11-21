package com.richi_mc.hackathon3b.feature.capture

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.richi_mc.hackathon3b.core.model.Sesion
import com.richi_mc.hackathon3b.core.model.TipoFoto
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

    val camara = state.camara
    if (camara != null) {
        BackHandler { vm.cerrarCamara() }
        CameraCapture(
            tipo = camara,
            onFoto = { path -> vm.onFoto(path, camara) },
            onCerrar = vm::cerrarCamara,
            paso = if (camara == TipoFoto.PRODUCTO) 2 else 3,
            totalPasos = 3,
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
    onRevisar: () -> Unit,
    onAtras: () -> Unit,
) {
    GlassBackground {
        val sesion = state.sesion
        if (sesion == null) {
            SinSesion(onAtras)
            return@GlassBackground
        }
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
                    esContratipo = state.borrador.productoEsperado.esContratipo,
                    onDescripcion = onDescripcion,
                    onCodigoBarras = onCodigoBarras,
                    onContratipo = onContratipo,
                )

                PasoCard(
                    titulo = "1. Foto del producto",
                    guia = "El código de barras debe verse completo",
                    icono = Icons.Rounded.Inventory2,
                    analisis = state.borrador.producto,
                    analizando = state.analizando == TipoFoto.PRODUCTO,
                    ocupado = state.analizando != null,
                    onTomar = { onTomar(TipoFoto.PRODUCTO) },
                )
                PasoCard(
                    titulo = "2. Etiqueta de precio",
                    guia = "Que se lean el precio y la descripción",
                    icono = Icons.Rounded.Sell,
                    analisis = state.borrador.etiqueta,
                    analizando = state.analizando == TipoFoto.ETIQUETA,
                    ocupado = state.analizando != null,
                    onTomar = { onTomar(TipoFoto.ETIQUETA) },
                )

                if (state.error != null) {
                    Row(verticalAlignment = Alignment.Top) {
                        androidx.compose.material3.Icon(
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
                if (!state.puedeRevisar) {
                    Text(
                        "Toma la foto del producto y la de la etiqueta para continuar.",
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
        GlassProgressBar(pasosHechos / 3f)
        Text("$pasosHechos de 3 fotos", style = Glass.type.secondary, color = GlassRojo.inkMuted)
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

@Composable
private fun PasoCard(
    titulo: String,
    guia: String,
    icono: ImageVector,
    analisis: AnalisisImagen?,
    analizando: Boolean,
    ocupado: Boolean,
    onTomar: () -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                analizando -> IconBox(icono, GlassRojo.info)
                analisis == null -> IconBox(icono, GlassRojo.inkSecondary)
                analisis.tieneBloqueantes -> IconBox(Icons.Rounded.ErrorOutline, GlassRojo.danger)
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
                EstadoAnalisisChips(analisis)
                if (analisis.problemas.isNotEmpty()) {
                    Spacer(Modifier.height(GlassDimens.s8))
                    ProblemasMensajes(analisis.problemas)
                }
            }
        }
        Spacer(Modifier.height(GlassDimens.s12))
        GlassButton(
            text = if (analisis == null) "Tomar foto" else "Retomar foto",
            onClick = onTomar,
            enabled = !ocupado,
            icon = Icons.Rounded.CameraAlt,
        )
    }
}

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

@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 1000)
@Composable
private fun ProductoVacioPreview() {
    GlassRojoTheme {
        ProductoContent(
            state = ProductoUiState(sesion = EjemplosPreview.sesion, capturadas = 3),
            descripcion = "", codigoBarras = "",
            onDescripcion = {}, onCodigoBarras = {}, onContratipo = {}, onTomar = {}, onRevisar = {}, onAtras = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 1000)
@Composable
private fun ProductoConProblemasPreview() {
    GlassRojoTheme {
        ProductoContent(
            state = ProductoUiState(
                sesion = EjemplosPreview.sesion,
                borrador = BorradorCaptura(
                    productoEsperado = EjemplosPreview.esperado,
                    producto = EjemplosPreview.producto,
                    etiqueta = EjemplosPreview.etiquetaConProblemas,
                ),
                analizando = null,
            ),
            descripcion = "Leche entera 1 L", codigoBarras = "7501055300075",
            onDescripcion = {}, onCodigoBarras = {}, onContratipo = {}, onTomar = {}, onRevisar = {}, onAtras = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 800)
@Composable
private fun ProductoSinSesionPreview() {
    GlassRojoTheme {
        ProductoContent(
            state = ProductoUiState(),
            descripcion = "", codigoBarras = "",
            onDescripcion = {}, onCodigoBarras = {}, onContratipo = {}, onTomar = {}, onRevisar = {}, onAtras = {},
        )
    }
}
