package com.richi_mc.hackathon3b.feature.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.richi_mc.hackathon3b.app.AppContainer
import com.richi_mc.hackathon3b.core.model.Cadena
import com.richi_mc.hackathon3b.core.model.Sesion
import com.richi_mc.hackathon3b.ui.components.CircleIconButton
import com.richi_mc.hackathon3b.ui.components.GlassBackground
import com.richi_mc.hackathon3b.ui.components.GlassButton
import com.richi_mc.hackathon3b.ui.components.GlassCard
import com.richi_mc.hackathon3b.ui.components.GlassTextField
import com.richi_mc.hackathon3b.ui.components.IconBox
import com.richi_mc.hackathon3b.ui.components.PrimaryButton
import com.richi_mc.hackathon3b.ui.components.SegmentedControl
import com.richi_mc.hackathon3b.ui.components.StatusChip
import com.richi_mc.hackathon3b.ui.theme.Glass
import com.richi_mc.hackathon3b.ui.theme.GlassDimens
import com.richi_mc.hackathon3b.ui.theme.GlassRojo
import com.richi_mc.hackathon3b.ui.theme.GlassRojoTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/** Resultado del último intento de "Enviar pendientes". */
sealed interface ResultadoEnvio {
    data class Enviadas(val cuantas: Int) : ResultadoEnvio
    data object NadaQueEnviar : ResultadoEnvio
    data object Fallo : ResultadoEnvio
}

@Composable
fun InicioScreen(
    container: AppContainer,
    onIniciarCaptura: () -> Unit,   // → sesion (nueva sucursal)
    onContinuar: () -> Unit,        // → producto (sesión activa)
) {
    val scope = rememberCoroutineScope()
    val sesion by container.sessionStore.sesion.collectAsStateWithLifecycle()
    val pendientes by container.capturaRepository.observarPendientes()
        .collectAsStateWithLifecycle(initialValue = 0)
    val url by container.ajustes.url.collectAsStateWithLifecycle()
    val simulado by container.ajustes.modoSimulado.collectAsStateWithLifecycle()

    var enviando by remember { mutableStateOf(false) }
    var resultado by remember { mutableStateOf<ResultadoEnvio?>(null) }

    var probando by remember { mutableStateOf(false) }
    var resultadoPrueba by remember { mutableStateOf<Result<String>?>(null) }

    InicioContent(
        sesion = sesion,
        pendientes = pendientes,
        enviando = enviando,
        resultadoEnvio = resultado,
        url = url,
        modoSimulado = simulado,
        onIniciarCaptura = onIniciarCaptura,
        onContinuar = onContinuar,
        onCambiarSucursal = {
            scope.launch {
                container.sessionStore.cerrar()
                onIniciarCaptura()
            }
        },
        onEnviarPendientes = {
            if (!enviando) {
                enviando = true
                resultado = null
                scope.launch {
                    resultado = try {
                        val n = container.capturaRepository.enviarPendientes()
                        if (n > 0) ResultadoEnvio.Enviadas(n) else ResultadoEnvio.NadaQueEnviar
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ResultadoEnvio.Fallo
                    }
                    enviando = false
                }
            }
        },
        onGuardarUrl = container.ajustes::setUrl,
        onModoSimulado = container.ajustes::setModoSimulado,
        probando = probando,
        resultadoPrueba = resultadoPrueba,
        onProbarConexion = {
            if (!probando) {
                probando = true
                resultadoPrueba = null
                scope.launch {
                    resultadoPrueba = try {
                        container.capturaRepository.probarServidor()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                    probando = false
                }
            }
        },
    )
}

@Composable
fun InicioContent(
    sesion: Sesion?,
    pendientes: Int,
    enviando: Boolean,
    resultadoEnvio: ResultadoEnvio?,
    url: String,
    modoSimulado: Boolean,
    onIniciarCaptura: () -> Unit,
    onContinuar: () -> Unit,
    onCambiarSucursal: () -> Unit,
    onEnviarPendientes: () -> Unit,
    onGuardarUrl: (String) -> Unit,
    onModoSimulado: (Boolean) -> Unit,
    probando: Boolean = false,
    resultadoPrueba: Result<String>? = null,
    onProbarConexion: () -> Unit = {},
) {
    var ajustesAbiertos by remember { mutableStateOf(false) }

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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Validat3B", style = Glass.type.display, modifier = Modifier.weight(1f))
                CircleIconButton(
                    icon = Icons.Rounded.Settings,
                    contentDescription = if (ajustesAbiertos) "Ocultar ajustes del servidor" else "Mostrar ajustes del servidor",
                    onClick = { ajustesAbiertos = !ajustesAbiertos },
                )
            }

            AnimatedVisibility(ajustesAbiertos) {
                AjustesServidorCard(url, modoSimulado, onGuardarUrl, onModoSimulado, probando, resultadoPrueba, onProbarConexion)
            }

            SesionActivaCard(sesion)

            if (sesion != null) {
                PrimaryButton("Continuar en ${sesion.tienda}", onContinuar)
                GlassButton("Cambiar de sucursal", onCambiarSucursal)
            } else {
                PrimaryButton("Iniciar captura", onIniciarCaptura)
            }

            Spacer(Modifier.height(GlassDimens.s4))

            if (pendientes > 0) {
                StatusChip(
                    text = if (pendientes == 1) "1 captura pendiente de envío" else "$pendientes capturas pendientes de envío",
                    color = GlassRojo.warning,
                    icon = Icons.Rounded.Schedule,
                )
            } else {
                StatusChip("Sin capturas pendientes", GlassRojo.success, icon = Icons.Rounded.CheckCircle)
            }
            GlassButton(
                text = if (enviando) "Enviando…" else "Enviar pendientes",
                onClick = onEnviarPendientes,
                enabled = pendientes > 0 && !enviando,
                icon = Icons.Rounded.CloudUpload,
            )
            when (resultadoEnvio) {
                is ResultadoEnvio.Enviadas -> StatusChip(
                    if (resultadoEnvio.cuantas == 1) "Se envió 1 captura" else "Se enviaron ${resultadoEnvio.cuantas} capturas",
                    GlassRojo.success, icon = Icons.Rounded.CheckCircle,
                )
                ResultadoEnvio.NadaQueEnviar -> StatusChip(
                    "No se envió ninguna: revisa la conexión o el servidor",
                    GlassRojo.info, icon = Icons.Rounded.Info,
                )
                ResultadoEnvio.Fallo -> StatusChip(
                    "No se pudo enviar: revisa la conexión",
                    GlassRojo.danger, icon = Icons.Rounded.ErrorOutline,
                )
                null -> Unit
            }
        }
    }
}

@Composable
private fun SesionActivaCard(sesion: Sesion?) {
    GlassCard(Modifier.fillMaxWidth(), principal = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBox(Icons.Rounded.Storefront, if (sesion != null) GlassRojo.accentInk else GlassRojo.inkMuted)
            Column(Modifier.padding(start = GlassDimens.s12)) {
                if (sesion != null) {
                    Text(sesion.cadena.etiqueta.uppercase(), style = Glass.type.label)
                    Text(sesion.tienda, style = Glass.type.title)
                } else {
                    Text("SIN SESIÓN", style = Glass.type.label)
                    Text("Ninguna sucursal activa", style = Glass.type.title)
                }
            }
        }
        Spacer(Modifier.height(GlassDimens.s12))
        if (sesion != null) {
            val hora = remember(sesion.inicioMillis) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(sesion.inicioMillis))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon14(Icons.Rounded.Schedule)
                Text("Inicio $hora", style = Glass.type.secondary, color = GlassRojo.inkMuted, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.padding(horizontal = GlassDimens.s8))
                Icon14(Icons.Rounded.GpsFixed)
                Text(
                    sesion.precisionM?.let { "GPS ±${it.toInt()} m" } ?: "GPS sin precisión",
                    style = Glass.type.secondary, color = GlassRojo.inkMuted,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        } else {
            Text(
                "Elige la cadena y la tienda, y toma la foto de fachada para empezar.",
                style = Glass.type.secondary, color = GlassRojo.inkMuted,
            )
        }
    }
}

@Composable
private fun Icon14(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    androidx.compose.material3.Icon(
        icon, contentDescription = null, tint = GlassRojo.inkMuted,
        modifier = Modifier.size(14.dp),
    )
}

@Composable
private fun AjustesServidorCard(
    url: String,
    modoSimulado: Boolean,
    onGuardarUrl: (String) -> Unit,
    onModoSimulado: (Boolean) -> Unit,
    probando: Boolean,
    resultadoPrueba: Result<String>?,
    onProbarConexion: () -> Unit,
) {
    var texto by remember(url) { mutableStateOf(url) }
    GlassCard(Modifier.fillMaxWidth()) {
        Text("Servidor", style = Glass.type.title)
        Spacer(Modifier.height(GlassDimens.s12))
        GlassTextField(
            value = texto,
            onValueChange = { texto = it },
            label = "URL del servidor",
            placeholder = "http://10.0.2.2:8000/",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Spacer(Modifier.height(GlassDimens.s12))
        GlassButton("Guardar URL", { onGuardarUrl(texto.trim()) }, enabled = texto.isNotBlank() && texto.trim() != url)
        Spacer(Modifier.height(GlassDimens.s12))
        Text("Modo simulado", style = Glass.type.secondary, color = GlassRojo.inkSecondary)
        Spacer(Modifier.height(6.dp))
        SegmentedControl(
            options = listOf(true, false),
            selected = modoSimulado,
            label = { if (it) "Sí" else "No" },
            onSelect = onModoSimulado,
        )
        Spacer(Modifier.height(GlassDimens.s12))
        GlassButton(
            if (probando) "Probando…" else "Probar conexión", onProbarConexion,
            enabled = !probando && url.isNotBlank(), icon = Icons.Rounded.Wifi,
        )
        if (probando) {
            Spacer(Modifier.height(GlassDimens.s8))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = GlassRojo.inkSecondary,
                )
                Text(
                    "Conectando con el servidor…", style = Glass.type.secondary,
                    color = GlassRojo.inkSecondary, modifier = Modifier.padding(start = GlassDimens.s8),
                )
            }
        }
        resultadoPrueba?.let { r ->
            Spacer(Modifier.height(GlassDimens.s8))
            r.fold(
                onSuccess = { StatusChip(it, GlassRojo.success, icon = Icons.Rounded.CheckCircle) },
                onFailure = {
                    StatusChip(
                        it.message ?: "No se pudo conectar con el servidor",
                        GlassRojo.danger, icon = Icons.Rounded.ErrorOutline,
                    )
                },
            )
        }
    }
}

private fun sesionDemo() = Sesion(
    id = "demo", cadena = Cadena.TRES_B, tienda = "3B Lindavista",
    lat = 19.5, lon = -99.13, precisionM = 12f, fachadaPath = "",
    inicioMillis = System.currentTimeMillis(),
)

@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 800)
@Composable
private fun InicioConSesionPreview() {
    GlassRojoTheme {
        InicioContent(
            sesion = sesionDemo(), pendientes = 3, enviando = false,
            resultadoEnvio = ResultadoEnvio.Enviadas(2),
            url = "http://10.0.2.2:8000/", modoSimulado = true,
            onIniciarCaptura = {}, onContinuar = {}, onCambiarSucursal = {},
            onEnviarPendientes = {}, onGuardarUrl = {}, onModoSimulado = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 800)
@Composable
private fun InicioSinSesionPreview() {
    GlassRojoTheme {
        InicioContent(
            sesion = null, pendientes = 0, enviando = false, resultadoEnvio = null,
            url = "http://10.0.2.2:8000/", modoSimulado = false,
            onIniciarCaptura = {}, onContinuar = {}, onCambiarSucursal = {},
            onEnviarPendientes = {}, onGuardarUrl = {}, onModoSimulado = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 700)
@Composable
private fun AjustesServidorPreview() {
    GlassRojoTheme {
        Column(Modifier.padding(GlassDimens.screenSide), verticalArrangement = GlassDimens.itemSpacing) {
            AjustesServidorCard(
                "http://10.0.2.2:8000/", true, {}, {}, probando = false,
                resultadoPrueba = Result.success("Servidor en línea, modo simulado"), onProbarConexion = {},
            )
            AjustesServidorCard(
                "http://10.0.2.2:8000/", false, {}, {}, probando = true,
                resultadoPrueba = Result.failure(Exception("No se pudo conectar con el servidor")), onProbarConexion = {},
            )
        }
    }
}
