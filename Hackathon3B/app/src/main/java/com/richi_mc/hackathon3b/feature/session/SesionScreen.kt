package com.richi_mc.hackathon3b.feature.session

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsNotFixed
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.richi_mc.hackathon3b.app.AppContainer
import com.richi_mc.hackathon3b.core.model.Cadena
import com.richi_mc.hackathon3b.core.model.Problema
import com.richi_mc.hackathon3b.core.model.TipoFoto
import com.richi_mc.hackathon3b.core.model.Ubicacion
import com.richi_mc.hackathon3b.core.model.Umbrales
import com.richi_mc.hackathon3b.feature.camera.CameraCapture
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
import com.richi_mc.hackathon3b.ui.theme.Glass
import com.richi_mc.hackathon3b.ui.theme.GlassDimens
import com.richi_mc.hackathon3b.ui.theme.GlassRojo
import com.richi_mc.hackathon3b.ui.theme.GlassRojoTheme
import java.io.File

/** Sugerencias fijas por cadena (demo). */
private val SUGERENCIAS: Map<Cadena, List<String>> = mapOf(
    Cadena.TRES_B to listOf("3B Lindavista", "3B Tlalnepantla", "3B Ecatepec"),
    Cadena.SORIANA to listOf("Soriana Río Consulado", "Soriana Tlalpan", "Soriana Hípico"),
    Cadena.BODEGA_AURRERA to listOf("Bodega Aurrerá Vallejo", "Bodega Aurrerá Iztapalapa"),
    Cadena.OTRA to emptyList(),
)

@Composable
fun SesionScreen(
    container: AppContainer,
    onSesionLista: () -> Unit,      // sesión guardada en SessionStore → producto
    onAtras: () -> Unit,
) {
    val vm: SesionViewModel = viewModel(
        factory = viewModelFactory { initializer { SesionViewModel(container) } }
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.refrescarUbicacion() }

    val pedirUbicacion: () -> Unit = {
        val concedido = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (concedido) vm.refrescarUbicacion()
        else launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    LaunchedEffect(Unit) { pedirUbicacion() }

    if (state.camaraAbierta) {
        BackHandler { vm.cerrarCamara() }
        CameraCapture(
            tipo = TipoFoto.FACHADA,
            onFoto = { path -> vm.onFoto(path, onSesionLista) },
            onCerrar = vm::cerrarCamara,
            paso = 1,
            totalPasos = 1,
        )
        return
    }

    SesionContent(
        state = state,
        onAtras = onAtras,
        onCadena = vm::elegirCadena,
        onTienda = vm::cambiarTienda,
        onRefrescarGps = pedirUbicacion,
        onTomarFoto = vm::abrirCamara,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SesionContent(
    state: SesionUiState,
    onAtras: () -> Unit,
    onCadena: (Cadena) -> Unit,
    onTienda: (String) -> Unit,
    onRefrescarGps: () -> Unit,
    onTomarFoto: () -> Unit,
) {
    GlassBackground {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GlassDimens.screenSide)
                .padding(top = GlassDimens.s12, bottom = GlassDimens.s24),
            verticalArrangement = GlassDimens.itemSpacing,
        ) {
            CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", onAtras)
            Text("Nueva sucursal", style = Glass.type.display)

            GpsBloque(state.gps, onRefrescarGps)

            Text("Cadena", style = Glass.type.secondary, color = GlassRojo.inkSecondary)
            SegmentedControl(
                options = Cadena.entries,
                selected = state.cadena,
                label = { it.corta },
                onSelect = onCadena,
            )

            GlassTextField(
                value = state.tienda,
                onValueChange = onTienda,
                label = "Tienda",
                placeholder = "Nombre de la sucursal",
            )
            val sugerencias = state.cadena?.let { SUGERENCIAS[it] }.orEmpty()
            if (sugerencias.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sugerencias.forEach { s -> SugerenciaChip(s) { onTienda(s) } }
                }
            }

            when {
                state.analizando -> GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = GlassRojo.accentInk,
                            strokeWidth = 2.dp,
                        )
                        Text(
                            "Analizando foto de fachada…",
                            style = Glass.type.body,
                            modifier = Modifier.padding(start = GlassDimens.s12),
                        )
                    }
                }
                state.rechazo != null -> RechazoCard(state.rechazo.path, state.rechazo.problemas, onTomarFoto)
            }

            PrimaryButton(
                text = "Tomar foto de fachada",
                onClick = onTomarFoto,
                enabled = state.puedeTomarFoto,
                icon = Icons.Rounded.CameraAlt,
            )
        }
    }
}

@Composable
private fun GpsBloque(gps: GpsUi, onRefrescar: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                when (gps) {
                    GpsUi.Buscando -> StatusChip("Buscando GPS…", GlassRojo.info, icon = Icons.Rounded.GpsNotFixed)
                    is GpsUi.Listo -> {
                        val p = gps.ubicacion.precisionM
                        val bueno = p != null && p <= Umbrales.GPS_BUENO_M
                        StatusChip(
                            text = if (p != null) "GPS ±${p.toInt()} m" else "GPS sin precisión",
                            color = if (bueno) GlassRojo.success else GlassRojo.warning,
                            icon = if (bueno) Icons.Rounded.GpsFixed else Icons.Rounded.Warning,
                        )
                    }
                    GpsUi.SinUbicacion -> StatusChip("Sin ubicación", GlassRojo.danger, icon = Icons.Rounded.LocationOff)
                }
            }
            CircleIconButton(Icons.Rounded.Refresh, "Actualizar ubicación", onRefrescar)
        }
        when (gps) {
            GpsUi.SinUbicacion -> Text(
                "No se pudo obtener la ubicación: permite el acceso a la ubicación, activa el GPS y toca actualizar.",
                style = Glass.type.secondary, color = GlassRojo.danger,
            )
            is GpsUi.Listo -> {
                val p = gps.ubicacion.precisionM
                if (p == null || p > Umbrales.GPS_BUENO_M) {
                    Text(
                        "Precisión baja: sal a un lugar abierto y toca actualizar.",
                        style = Glass.type.secondary, color = GlassRojo.warning,
                    )
                }
            }
            GpsUi.Buscando -> Unit
        }
    }
}

@Composable
private fun SugerenciaChip(texto: String, onClick: () -> Unit) {
    Box(
        Modifier
            .glassSurface(CircleShape, LocalHazeState.current)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, style = Glass.type.secondary, color = GlassRojo.inkStrong)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RechazoCard(path: String, problemas: List<Problema>, onRetomar: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        AsyncImage(
            model = File(path),
            contentDescription = "Foto de fachada rechazada",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(GlassDimens.radiusInput)),
        )
        Spacer(Modifier.height(GlassDimens.s12))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            problemas.forEach { StatusChip(it.corto, GlassRojo.danger, icon = Icons.Rounded.ErrorOutline) }
        }
        problemas.forEach {
            Spacer(Modifier.height(GlassDimens.s8))
            Text(it.mensaje, style = Glass.type.secondary, color = GlassRojo.inkSecondary)
        }
        Spacer(Modifier.height(GlassDimens.s12))
        GlassButton("Retomar", onRetomar, icon = Icons.Rounded.CameraAlt)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 800)
@Composable
private fun SesionPreview() {
    GlassRojoTheme {
        SesionContent(
            state = SesionUiState(
                cadena = Cadena.SORIANA, tienda = "Soriana Río Consulado",
                gps = GpsUi.Listo(Ubicacion(19.5, -99.13, 12f)),
            ),
            onAtras = {}, onCadena = {}, onTienda = {}, onRefrescarGps = {}, onTomarFoto = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF020617, heightDp = 800)
@Composable
private fun SesionSinUbicacionPreview() {
    GlassRojoTheme {
        SesionContent(
            state = SesionUiState(
                cadena = Cadena.TRES_B, tienda = "",
                gps = GpsUi.SinUbicacion,
                rechazo = RechazoFachada("", listOf(Problema.BORROSA)),
            ),
            onAtras = {}, onCadena = {}, onTienda = {}, onRefrescarGps = {}, onTomarFoto = {},
        )
    }
}
