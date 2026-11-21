package com.richi_mc.hackathon3b.feature.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.richi_mc.hackathon3b.app.AppContainer
import com.richi_mc.hackathon3b.core.model.Captura
import com.richi_mc.hackathon3b.core.model.EstadoEnvio
import com.richi_mc.hackathon3b.core.model.Decision
import com.richi_mc.hackathon3b.core.model.ResultadoServidor
import com.richi_mc.hackathon3b.core.model.TipoFoto
import com.richi_mc.hackathon3b.ui.components.GlassBackground
import com.richi_mc.hackathon3b.ui.components.GlassButton
import com.richi_mc.hackathon3b.ui.components.GlassCard
import com.richi_mc.hackathon3b.ui.components.IconBox
import com.richi_mc.hackathon3b.ui.components.PrimaryButton
import com.richi_mc.hackathon3b.ui.components.StatusChip
import com.richi_mc.hackathon3b.ui.theme.Glass
import com.richi_mc.hackathon3b.ui.theme.GlassDimens
import com.richi_mc.hackathon3b.ui.theme.GlassRojo
import com.richi_mc.hackathon3b.ui.theme.GlassRojoTheme
import java.io.File
import kotlin.math.roundToInt

@Composable
fun ConfirmacionScreen(
    container: AppContainer,
    captureId: String,
    onSiguienteProducto: () -> Unit,  // → producto (misma sesión)
    onCambiarSucursal: () -> Unit,    // cierra sesión → sesion
    onInicio: () -> Unit,             // → inicio
) {
    val vm: ConfirmacionViewModel = viewModel(
        key = "confirmacion-$captureId",
        factory = viewModelFactory { initializer { ConfirmacionViewModel(container, captureId) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ConfirmacionContent(
        captureId = captureId,
        state = state,
        onSiguienteProducto = onSiguienteProducto,
        onCambiarSucursal = onCambiarSucursal,
        onInicio = onInicio,
        onExportar = { vm.exportar { ruta -> compartirJson(context, ruta) } },
        onReintentar = vm::reintentarEnvio,
    )
}

/** Comparte el JSON exportado con ACTION_SEND vía FileProvider (authority = `<package>.fileprovider`). */
private fun compartirJson(context: Context, path: String) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", File(path))
    val envio = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val selector = Intent.createChooser(envio, "Compartir capturas").apply {
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(selector)
}

@Composable
fun ConfirmacionContent(
    captureId: String,
    state: ConfirmacionUiState,
    onSiguienteProducto: () -> Unit,
    onCambiarSucursal: () -> Unit,
    onInicio: () -> Unit,
    onExportar: () -> Unit,
    onReintentar: () -> Unit = {},
) {
    GlassBackground {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GlassDimens.screenSide)
                .padding(top = GlassDimens.screenTop, bottom = GlassDimens.s24),
            verticalArrangement = GlassDimens.itemSpacing,
        ) {
            IconBox(Icons.Rounded.CheckCircle, GlassRojo.success)
            Text("Captura guardada", style = Glass.type.display)
            Text(captureId, style = Glass.type.secondary, color = GlassRojo.inkMuted)

            state.captura?.let { EstadoEnvioChip(it) }
            if (state.captura?.estado.let { it == EstadoEnvio.ERROR || it == EstadoEnvio.FALLIDA }) {
                GlassButton(
                    if (state.reintentando) "Reintentando…" else "Reintentar envío",
                    onReintentar,
                    enabled = !state.reintentando,
                    icon = Icons.Rounded.Refresh,
                )
            }
            state.captura?.takeIf { it.estado == EstadoEnvio.FALLIDA }?.error
                ?.takeIf { it.isNotBlank() }?.let { error ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Rounded.ErrorOutline, contentDescription = null,
                            tint = GlassRojo.danger, modifier = Modifier.padding(top = 2.dp).size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(error, style = Glass.type.secondary, color = GlassRojo.inkSecondary)
                    }
                }
            state.captura?.respuesta?.let { RespuestaCard(it) }

            ResumenCard(state)

            PrimaryButton("Siguiente producto", onSiguienteProducto)
            GlassButton("Cambiar de sucursal", onCambiarSucursal, icon = Icons.Rounded.Storefront)
            GlassButton(
                if (state.exportando) "Exportando…" else "Exportar JSON",
                onExportar,
                enabled = state.captura != null && !state.exportando,
                icon = Icons.Rounded.Share,
            )
            if (state.errorExportar != null) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Rounded.Warning, contentDescription = null,
                        tint = GlassRojo.warning, modifier = Modifier.padding(top = 2.dp).size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(state.errorExportar, style = Glass.type.secondary, color = GlassRojo.warning)
                }
            }
            Text(
                "Ir al inicio",
                style = Glass.type.secondary,
                color = GlassRojo.inkSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onInicio)
                    .padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun EstadoEnvioChip(c: Captura) {
    when (c.estado) {
        EstadoEnvio.PENDIENTE -> StatusChip("Enviando…", GlassRojo.info, icon = Icons.Rounded.Sync)
        EstadoEnvio.ENVIADA -> StatusChip("Enviada", GlassRojo.success, icon = Icons.Rounded.CloudDone)
        EstadoEnvio.ERROR -> {
            val texto = c.error?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { if (it.length > 90) it.take(90).trimEnd() + "…" else it }
                ?: "Sin conexión, se enviará después"
            StatusChip(texto, GlassRojo.warning, icon = Icons.Rounded.CloudOff)
        }
        EstadoEnvio.FALLIDA ->
            StatusChip("No se pudo enviar: revisión manual", GlassRojo.danger, icon = Icons.Rounded.ErrorOutline)
    }
}

private data class DecisionUi(val texto: String, val color: Color, val icono: ImageVector)

private fun decisionUi(decision: String) = when (decision) {
    "aprobada" -> DecisionUi("Aprobada", GlassRojo.success, Icons.Rounded.CheckCircle)
    "rechazada" -> DecisionUi("Rechazada", GlassRojo.danger, Icons.Rounded.Cancel)
    else -> DecisionUi("Revisión manual", GlassRojo.warning, Icons.AutoMirrored.Rounded.HelpOutline)
}

private fun nombreFoto(t: TipoFoto) = when (t) {
    TipoFoto.FACHADA -> "fachada"
    TipoFoto.PRODUCTO -> "producto"
    TipoFoto.ETIQUETA -> "etiqueta"
}

private fun tipoPrecioLegible(t: String) = when (t.lowercase()) {
    "regular" -> "Precio unitario"
    "oferta" -> "Precio de oferta"
    "volumen" -> "Precio promocional"
    else -> null
}

private fun motivoLegible(code: String): String = when (code) {
    "CONTRATIPO_INVALIDO" -> "Contratipo no válido"
    "PRODUCTO_NO_COINCIDE" -> "El producto no coincide con lo esperado"
    "DESCRIPCION_INCOMPLETA" -> "Descripción incompleta"
    "PRODUCTO_NO_VISIBLE" -> "Producto no visible"
    "FACHADA_INCORRECTA" -> "Fachada incorrecta"
    "CAPTURA_LEJANA" -> "Captura demasiado lejana"
    "IMAGEN_BORROSA" -> "Imagen borrosa"
    "FOTO_DE_PANTALLA" -> "Foto tomada de una pantalla"
    "SIN_ETIQUETA_PRECIO" -> "Sin etiqueta de precio"
    "SIN_PRECIO_UNITARIO" -> "Sin precio unitario"
    else -> code.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
private fun RespuestaCard(r: ResultadoServidor) {
    val d = decisionUi(r.decision)
    GlassCard(Modifier.fillMaxWidth(), principal = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBox(d.icono, d.color)
            Spacer(Modifier.width(GlassDimens.s12))
            Column(Modifier.weight(1f)) {
                Text("RESPUESTA DEL SERVIDOR", style = Glass.type.label)
                Text(d.texto, style = Glass.type.title, color = d.color)
            }
            r.confidenceScore?.let {
                StatusChip("${(it * 100).roundToInt()} %", d.color, icon = d.icono)
            }
        }
        r.reasonCode?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(GlassDimens.s12))
            Text("MOTIVO", style = Glass.type.label)
            Text(motivoLegible(it), style = Glass.type.bodyStrong)
        }
        r.explicacion?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(GlassDimens.s12))
            Text(it, style = Glass.type.secondary, color = GlassRojo.inkSecondary)
        }
        val mensajes = r.mensajesCapturista
        if (mensajes.isNotEmpty() && mensajes != listOfNotNull(r.explicacion?.takeIf { it.isNotBlank() })) {
            Spacer(Modifier.height(GlassDimens.s12))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                mensajes.forEach { aviso ->
                    LineaConIcono(Icons.Rounded.Warning, aviso, GlassRojo.warning)
                }
            }
        }
        if (r.fotosARepetir.isNotEmpty() && r.decision != Decision.APROBADA) {
            Spacer(Modifier.height(GlassDimens.s12))
            LineaConIcono(
                Icons.Rounded.PhotoCamera,
                "Repite la foto de: " + r.fotosARepetir.joinToString(", ") { nombreFoto(it) },
                GlassRojo.warning,
            )
        }
        val tipo = r.tipoPrecio?.let { tipoPrecioLegible(it) }
        val promo = r.promocion?.takeIf { it.isNotBlank() }
        if (tipo != null || promo != null) {
            Spacer(Modifier.height(GlassDimens.s12))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tipo?.let { LineaConIcono(Icons.Rounded.LocalOffer, it, GlassRojo.inkSecondary) }
                promo?.let { LineaConIcono(Icons.Rounded.LocalOffer, "Promoción: $it", GlassRojo.inkSecondary) }
            }
        }
    }
}

@Composable
private fun LineaConIcono(icono: ImageVector, texto: String, color: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icono, contentDescription = null,
            tint = color, modifier = Modifier.padding(top = 2.dp).size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(texto, style = Glass.type.secondary, color = color, overflow = TextOverflow.Clip)
    }
}

@Composable
private fun ResumenCard(state: ConfirmacionUiState) {
    val r = state.resumen
    GlassCard(Modifier.fillMaxWidth()) {
        Text("Resumen de la sesión", style = Glass.type.title)
        Spacer(Modifier.height(GlassDimens.s12))
        Row(Modifier.fillMaxWidth()) {
            Cifra(r.capturadas, if (r.capturadas == 1) "capturada" else "capturadas", GlassRojo.ink, Modifier.weight(1f))
            Cifra(r.enviadas, if (r.enviadas == 1) "enviada" else "enviadas", GlassRojo.success, Modifier.weight(1f))
            Cifra(r.pendientes, if (r.pendientes == 1) "pendiente" else "pendientes",
                if (r.pendientes > 0) GlassRojo.warning else GlassRojo.inkMuted, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Cifra(n: Int, etiqueta: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text("$n", style = Glass.type.display, color = color)
        Text(etiqueta, style = Glass.type.secondary, color = GlassRojo.inkMuted)
    }
}

// --------------------------------------------------------------------------- previews

@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 1100)
@Composable
private fun ConfirmacionRespuestaPreview() {
    GlassRojoTheme {
        ConfirmacionContent(
            captureId = EjemplosPreview.captura.captureId,
            state = ConfirmacionUiState(EjemplosPreview.captura, ResumenSesion(4, 3, 1)),
            onSiguienteProducto = {}, onCambiarSucursal = {}, onInicio = {}, onExportar = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 900)
@Composable
private fun ConfirmacionSinConexionPreview() {
    GlassRojoTheme {
        ConfirmacionContent(
            captureId = EjemplosPreview.captura.captureId,
            state = ConfirmacionUiState(
                EjemplosPreview.captura.copy(
                    estado = EstadoEnvio.ERROR, respuesta = null, error = "Sin conexión, se enviará después",
                ),
                ResumenSesion(1, 0, 1),
            ),
            onSiguienteProducto = {}, onCambiarSucursal = {}, onInicio = {}, onExportar = {}, onReintentar = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 1200)
@Composable
private fun ConfirmacionRechazadaPreview() {
    GlassRojoTheme {
        ConfirmacionContent(
            captureId = EjemplosPreview.captura.captureId,
            state = ConfirmacionUiState(
                EjemplosPreview.captura.copy(
                    respuesta = ResultadoServidor(
                        decision = "rechazada", confidenceScore = 0.88, reasonCode = "PRODUCTO_NO_COINCIDE",
                        explicacion = "El producto de la foto no coincide con el esperado.",
                        avisos = listOf("Revisa que sea el producto o contratipo esperado."),
                        validaciones = mapOf("producto" to "falla", "evidencia_precio" to "falla"),
                        fotosARepetir = listOf(TipoFoto.PRODUCTO, TipoFoto.ETIQUETA),
                        tipoPrecio = "oferta", promocion = "2x\$50",
                    ),
                ),
                ResumenSesion(4, 4, 0),
            ),
            onSiguienteProducto = {}, onCambiarSucursal = {}, onInicio = {}, onExportar = {}, onReintentar = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 900)
@Composable
private fun ConfirmacionFallidaPreview() {
    GlassRojoTheme {
        ConfirmacionContent(
            captureId = EjemplosPreview.captura.captureId,
            state = ConfirmacionUiState(
                EjemplosPreview.captura.copy(
                    estado = EstadoEnvio.FALLIDA, respuesta = null,
                    error = "Revisa la clave de acceso del servidor. No autorizado (NO_AUTORIZADO)",
                ),
                ResumenSesion(1, 0, 1),
            ),
            onSiguienteProducto = {}, onCambiarSucursal = {}, onInicio = {}, onExportar = {}, onReintentar = {},
        )
    }
}
