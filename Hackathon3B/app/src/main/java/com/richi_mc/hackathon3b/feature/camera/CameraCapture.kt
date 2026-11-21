package com.richi_mc.hackathon3b.feature.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.richi_mc.hackathon3b.core.model.TipoFoto
import com.richi_mc.hackathon3b.ui.components.AccentFab
import com.richi_mc.hackathon3b.ui.components.CircleIconButton
import com.richi_mc.hackathon3b.ui.components.GlassBackground
import com.richi_mc.hackathon3b.ui.components.GlassButton
import com.richi_mc.hackathon3b.ui.components.GlassCard
import com.richi_mc.hackathon3b.ui.components.GlassProgressBar
import com.richi_mc.hackathon3b.ui.components.IconBox
import com.richi_mc.hackathon3b.ui.components.PrimaryButton
import com.richi_mc.hackathon3b.ui.components.StatusChip
import com.richi_mc.hackathon3b.ui.theme.Glass
import com.richi_mc.hackathon3b.ui.theme.GlassDimens
import com.richi_mc.hackathon3b.ui.theme.GlassRojo
import com.richi_mc.hackathon3b.ui.theme.GlassRojoTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/**
 * Cámara a pantalla completa reutilizable. Guarda la foto en filesDir/capturas/<uuid>.jpg
 * (lado mayor 1600 px, JPEG q85, rotación EXIF aplicada) y llama a [onFoto] con la ruta absoluta.
 * [paso]/[totalPasos] alimentan la ProgressBar de la franja superior.
 * Pide el permiso de cámara si falta; si se niega muestra una pantalla de explicación.
 */
@Composable
fun CameraCapture(
    tipo: TipoFoto,
    onFoto: (path: String) -> Unit,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier,
    paso: Int = 1,
    totalPasos: Int = 3,
) {
    PermisoCamaraGate(onCerrar = onCerrar, modifier = modifier) {
        CamaraActiva(tipo, onFoto, onCerrar, modifier, paso, totalPasos)
    }
}

/**
 * Pide el permiso de cámara si falta y muestra [content] cuando está concedido; si se niega,
 * muestra la pantalla de explicación. Compartido por [CameraCapture] y [BarcodeScanner].
 */
@Composable
internal fun PermisoCamaraGate(
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var permiso by remember { mutableStateOf(tienePermisoCamara(context)) }
    var respondio by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permiso = it
        respondio = true
    }
    LaunchedEffect(Unit) { if (!permiso) launcher.launch(Manifest.permission.CAMERA) }
    // Si el usuario concede el permiso desde Ajustes, al volver se detecta aquí.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permiso = tienePermisoCamara(context) }

    when {
        permiso -> content()
        respondio -> PermisoDenegado(
            onReintentar = { launcher.launch(Manifest.permission.CAMERA) },
            onAjustes = { abrirAjustes(context) },
            onCerrar = onCerrar,
            modifier = modifier,
        )
        else -> Box(modifier.fillMaxSize().background(GlassRojo.bg)) // esperando respuesta del diálogo
    }
}

private fun tienePermisoCamara(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun abrirAjustes(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

@Composable
private fun PermisoDenegado(
    onReintentar: () -> Unit,
    onAjustes: () -> Unit,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassBackground(modifier) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = GlassDimens.screenSide, vertical = GlassDimens.s24),
            verticalArrangement = Arrangement.Center,
        ) {
            GlassCard(Modifier.fillMaxWidth(), principal = true) {
                IconBox(Icons.Rounded.NoPhotography, GlassRojo.warning)
                Spacer(Modifier.height(GlassDimens.s16))
                Text("Se necesita la cámara", style = Glass.type.title)
                Spacer(Modifier.height(GlassDimens.s8))
                Text(
                    "Validat3B usa la cámara para fotografiar la fachada, el producto y la etiqueta de precio. " +
                        "Permite el acceso para continuar. Si ya lo negaste, actívalo desde Ajustes.",
                    style = Glass.type.secondary,
                )
            }
            Spacer(Modifier.height(GlassDimens.s24))
            PrimaryButton("Reintentar", onReintentar)
            Spacer(Modifier.height(GlassDimens.s12))
            GlassButton("Abrir ajustes", onAjustes)
            Spacer(Modifier.height(GlassDimens.s12))
            GlassButton("Cerrar", onCerrar)
        }
    }
}

@Composable
private fun CamaraActiva(
    tipo: TipoFoto,
    onFoto: (String) -> Unit,
    onCerrar: () -> Unit,
    modifier: Modifier,
    paso: Int,
    totalPasos: Int,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val onFotoActual by rememberUpdatedState(onFoto)

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            .build()
    }
    val preview = remember { Preview.Builder().build() }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var flashOn by remember { mutableStateOf(false) }
    var procesando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var foco by remember { mutableStateOf<FocoToque?>(null) }
    var focoId by remember { mutableIntStateOf(0) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(Unit) {
        try {
            val p = awaitCameraProvider(context)
            provider = p
            preview.setSurfaceProvider(previewView.surfaceProvider)
            p.unbindAll()
            val cam = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            camera = cam
            // Etiquetas: enfoque inicial al centro del marco (el autoenfoque a veces toma el fondo).
            if (tipo == TipoFoto.ETIQUETA) {
                runCatching {
                    val punto = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)
                    cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(punto).build())
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "No se pudo iniciar la cámara: cierra e intenta de nuevo."
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { provider?.unbind(preview, imageCapture) }
            camera = null
        }
    }
    // El indicador de enfoque desaparece solo.
    LaunchedEffect(foco) {
        if (foco != null) {
            delay(800)
            foco = null
        }
    }

    fun disparar() {
        if (procesando) return
        procesando = true
        error = null
        val temporal = File.createTempFile("captura_", ".jpg", context.cacheDir)
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(temporal).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    scope.launch {
                        try {
                            val destino = File(context.filesDir, "capturas")
                            val resultado = withContext(Dispatchers.IO) {
                                try {
                                    ImageUtils.guardarReescalada(temporal, destino)
                                } finally {
                                    temporal.delete()
                                }
                            }
                            procesando = false
                            onFotoActual(resultado.absolutePath)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            procesando = false
                            error = "No se pudo guardar la foto: intenta de nuevo."
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    temporal.delete()
                    procesando = false
                    error = "No se pudo tomar la foto: intenta de nuevo."
                }
            },
        )
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        CameraOverlay(
            tipo = tipo,
            paso = paso,
            totalPasos = totalPasos,
            flashOn = flashOn,
            procesando = procesando,
            error = error,
            foco = foco,
            onCerrar = onCerrar,
            onFlash = {
                flashOn = !flashOn
                imageCapture.flashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
            },
            onDisparar = ::disparar,
            onTapFoco = { punto ->
                val cam = camera
                if (cam != null) {
                    val mp = previewView.meteringPointFactory.createPoint(punto.x, punto.y)
                    runCatching { cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(mp).build()) }
                }
                focoId++
                foco = FocoToque(punto, focoId)
            },
        )
    }
}

internal suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                cont.resume(future.get())
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

/** Punto donde se tocó para enfocar; [id] distingue toques repetidos en el mismo lugar. */
data class FocoToque(val punto: Offset, val id: Int)

private fun TipoFoto.instruccion() = when (this) {
    TipoFoto.FACHADA -> "Encuadra la fachada con el nombre de la tienda"
    TipoFoto.PRODUCTO -> "El código de barras debe verse completo"
    TipoFoto.ETIQUETA -> "Que la etiqueta llene el marco"
}

/**
 * UI superpuesta a la cámara (sin estado, sin blur: franja solida sobre el PreviewView).
 * Franja superior con cerrar + instrucción + progreso, marco guía, y controles inferiores.
 */
@Composable
fun CameraOverlay(
    tipo: TipoFoto,
    paso: Int,
    totalPasos: Int,
    flashOn: Boolean,
    procesando: Boolean,
    error: String?,
    foco: FocoToque?,
    onCerrar: () -> Unit,
    onFlash: () -> Unit,
    onDisparar: () -> Unit,
    onTapFoco: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        // Captura de toques para enfocar (los botones consumen los suyos).
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onTapFoco(it) } })

        Column(Modifier.fillMaxSize()) {
            // Franja superior sólida (nav-fill), se extiende bajo la barra de estado.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(GlassRojo.navFill)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = GlassDimens.screenSide, vertical = GlassDimens.s12),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleIconButton(Icons.Rounded.Close, "Cerrar cámara", onCerrar, solid = true)
                    Spacer(Modifier.size(GlassDimens.s12))
                    Column(Modifier.weight(1f)) {
                        Text("PASO $paso DE $totalPasos", style = Glass.type.label)
                        Text(tipo.instruccion(), style = Glass.type.bodyStrong)
                    }
                }
                Spacer(Modifier.height(GlassDimens.s12))
                GlassProgressBar(if (totalPasos > 0) paso.toFloat() / totalPasos else 0f)
            }

            // Marco guía.
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val ancho = when (tipo) {
                    TipoFoto.PRODUCTO -> 0.74f
                    TipoFoto.ETIQUETA -> 0.85f
                    else -> 0.9f
                }
                val proporcion = when (tipo) {
                    TipoFoto.PRODUCTO -> 1f
                    TipoFoto.ETIQUETA -> 1.5f
                    else -> 1.6f
                }
                Box(
                    Modifier
                        .fillMaxWidth(ancho)
                        .aspectRatio(proporcion)
                        .border(2.dp, GlassRojo.accentBright, RoundedCornerShape(20.dp))
                )
            }

            // Controles inferiores.
            Column(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(bottom = GlassDimens.s24),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (error != null) {
                    StatusChip(
                        text = error,
                        color = GlassRojo.danger,
                        icon = Icons.Rounded.ErrorOutline,
                        modifier = Modifier
                            .padding(horizontal = GlassDimens.screenSide)
                            .clip(CircleShape)
                            .background(GlassRojo.bgElevated, CircleShape),
                    )
                    Spacer(Modifier.height(GlassDimens.s16))
                }
                Box(Modifier.fillMaxWidth().height(68.dp), contentAlignment = Alignment.Center) {
                    AccentFab(Icons.Rounded.PhotoCamera, "Tomar foto", onDisparar, enabled = !procesando)
                    if (procesando) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(68.dp)
                                .semantics { contentDescription = "Procesando foto" },
                            color = GlassRojo.accentBright,
                            strokeWidth = 3.dp,
                        )
                    }
                    CircleIconButton(
                        icon = if (flashOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                        contentDescription = if (flashOn) "Apagar flash" else "Encender flash",
                        onClick = onFlash,
                        solid = true,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 40.dp),
                    )
                }
            }
        }

        if (foco != null) FocusRing(foco)
    }
}

/** Círculo breve accent-bright donde se tocó para enfocar. */
@Composable
private fun FocusRing(foco: FocoToque) {
    val escala = remember(foco.id) { Animatable(1.4f) }
    LaunchedEffect(foco.id) { escala.animateTo(1f, tween(250)) }
    val mitad = with(LocalDensity.current) { 32.dp.roundToPx() }
    Box(
        Modifier
            .offset { IntOffset(foco.punto.x.roundToInt() - mitad, foco.punto.y.roundToInt() - mitad) }
            .size(64.dp)
            .scale(escala.value)
            .border(2.dp, GlassRojo.accentBright, CircleShape)
    )
}

@ComposePreview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun CameraOverlayEtiquetaPreview() {
    GlassRojoTheme {
        CameraOverlay(
            tipo = TipoFoto.ETIQUETA, paso = 3, totalPasos = 3, flashOn = false, procesando = false,
            error = null, foco = FocoToque(Offset(500f, 900f), 1),
            onCerrar = {}, onFlash = {}, onDisparar = {}, onTapFoco = {},
            modifier = Modifier.background(Color(0xFF334155)),
        )
    }
}

@ComposePreview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun CameraOverlayProductoPreview() {
    GlassRojoTheme {
        CameraOverlay(
            tipo = TipoFoto.PRODUCTO, paso = 2, totalPasos = 3, flashOn = true, procesando = true,
            error = "No se pudo tomar la foto: intenta de nuevo.", foco = null,
            onCerrar = {}, onFlash = {}, onDisparar = {}, onTapFoco = {},
            modifier = Modifier.background(Color(0xFF334155)),
        )
    }
}
