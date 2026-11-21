package com.richi_mc.hackathon3b.feature.camera

import androidx.activity.compose.BackHandler
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner as MlKitScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.richi_mc.hackathon3b.core.model.CodigoEscaneado
import com.richi_mc.hackathon3b.feature.analysis.CodigoBarras
import com.richi_mc.hackathon3b.ui.components.CircleIconButton
import com.richi_mc.hackathon3b.ui.components.GlassProgressBar
import com.richi_mc.hackathon3b.ui.components.StatusChip
import com.richi_mc.hackathon3b.ui.theme.Glass
import com.richi_mc.hackathon3b.ui.theme.GlassDimens
import com.richi_mc.hackathon3b.ui.theme.GlassRojo
import com.richi_mc.hackathon3b.ui.theme.GlassRojoTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

/** Lecturas consecutivas iguales necesarias para aceptar un código con checksum válido / inválido. */
internal const val LECTURAS_CHECKSUM_VALIDO = 2
internal const val LECTURAS_CHECKSUM_INVALIDO = 4

/** Tiempo que se muestra el código confirmado antes de entregarlo. */
private const val MS_CONFIRMACION = 600L

/** Código visto en un cuadro del análisis. */
internal data class LecturaCodigo(val valor: String, val formato: String, val checksumOk: Boolean)

/**
 * Debounce de lecturas: acepta un código cuando el mismo valor aparece en cuadros seguidos
 * ([LECTURAS_CHECKSUM_VALIDO] con checksum válido, [LECTURAS_CHECKSUM_INVALIDO] si es inválido).
 * Un cuadro sin código, o con otro valor, reinicia la cuenta. Sin estado compartido: usar desde un solo hilo.
 */
internal class EstabilizadorCodigo {
    private var ultimo: String? = null
    private var consecutivas = 0

    /** Registra el cuadro actual (null = sin código). Devuelve la lectura si ya es estable. */
    fun registrar(lectura: LecturaCodigo?): LecturaCodigo? {
        if (lectura == null) {
            ultimo = null
            consecutivas = 0
            return null
        }
        if (lectura.valor == ultimo) consecutivas++ else {
            ultimo = lectura.valor
            consecutivas = 1
        }
        val necesarias = if (lectura.checksumOk) LECTURAS_CHECKSUM_VALIDO else LECTURAS_CHECKSUM_INVALIDO
        return lectura.takeIf { consecutivas >= necesarias }
    }
}

/**
 * Escáner de código de barras en vivo a pantalla completa (CameraX ImageAnalysis + ML Kit,
 * EAN-13/EAN-8/UPC-A/UPC-E). Llama a [onCodigo] una sola vez con el primer código estable.
 */
@Composable
fun BarcodeScanner(
    onCodigo: (CodigoEscaneado) -> Unit,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier,
    paso: Int = 3,
    totalPasos: Int = 4,
) {
    BackHandler(onBack = onCerrar)
    PermisoCamaraGate(onCerrar = onCerrar, modifier = modifier) {
        EscanerActivo(onCodigo, onCerrar, modifier, paso, totalPasos)
    }
}

@Composable
private fun EscanerActivo(
    onCodigo: (CodigoEscaneado) -> Unit,
    onCerrar: () -> Unit,
    modifier: Modifier,
    paso: Int,
    totalPasos: Int,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val onCodigoActual by rememberUpdatedState(onCodigo)

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }
    val preview = remember { Preview.Builder().build() }
    val analysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(android.util.Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                    )
                    .build()
            )
            .build()
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                )
                .build()
        )
    }
    val estabilizador = remember { EstabilizadorCodigo() }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var torchDisponible by remember { mutableStateOf(false) }
    var leyendo by remember { mutableStateOf<String?>(null) }
    var confirmado by remember { mutableStateOf<CodigoEscaneado?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // Analizador: los resultados de ML Kit llegan en el hilo principal.
    DisposableEffect(Unit) {
        analysis.setAnalyzer(executor) { proxy ->
            analizarCuadro(proxy, scanner) { lectura ->
                if (confirmado != null) return@analizarCuadro
                leyendo = lectura?.valor
                val estable = estabilizador.registrar(lectura)
                if (estable != null) {
                    confirmado = CodigoEscaneado(
                        valor = estable.valor,
                        formato = estable.formato,
                        checksumOk = estable.checksumOk,
                        timestampMillis = System.currentTimeMillis(),
                    )
                }
            }
        }
        onDispose {
            analysis.clearAnalyzer()
            runCatching { provider?.unbind(preview, analysis) }
            camera = null
            executor.shutdown()
            scanner.close()
        }
    }

    LaunchedEffect(Unit) {
        try {
            val p = awaitCameraProvider(context)
            provider = p
            preview.setSurfaceProvider(previewView.surfaceProvider)
            p.unbindAll()
            val cam = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            camera = cam
            torchDisponible = cam.cameraInfo.hasFlashUnit()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "No se pudo iniciar la cámara: cierra e intenta de nuevo."
        }
    }

    // Código estable: vibración, mostrarlo ~600 ms y entregarlo una sola vez.
    LaunchedEffect(confirmado) {
        val codigo = confirmado ?: return@LaunchedEffect
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(MS_CONFIRMACION)
        onCodigoActual(codigo)
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        BarcodeScannerOverlay(
            paso = paso,
            totalPasos = totalPasos,
            torchOn = torchOn,
            torchDisponible = torchDisponible,
            leyendo = leyendo,
            confirmado = confirmado?.valor,
            error = error,
            onCerrar = onCerrar,
            onTorch = {
                torchOn = !torchOn
                camera?.cameraControl?.enableTorch(torchOn)
            },
        )
    }
}

/** Procesa un cuadro con ML Kit y entrega la mejor lectura (o null) en el hilo principal. Cierra siempre el proxy. */
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun analizarCuadro(proxy: ImageProxy, scanner: MlKitScanner, onResultado: (LecturaCodigo?) -> Unit) {
    val media = proxy.image
    if (media == null) {
        proxy.close()
        return
    }
    try {
        val imagen = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        scanner.process(imagen)
            .addOnSuccessListener { codigos ->
                val lecturas = codigos.mapNotNull { it.aLectura() }
                onResultado(lecturas.firstOrNull { it.checksumOk } ?: lecturas.firstOrNull())
            }
            .addOnFailureListener { onResultado(null) }
            .addOnCompleteListener { proxy.close() }
    } catch (e: Exception) {
        proxy.close()
        onResultado(null)
    }
}

private fun Barcode.aLectura(): LecturaCodigo? {
    val valor = rawValue?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val formato = when (format) {
        Barcode.FORMAT_EAN_13 -> "EAN_13"
        Barcode.FORMAT_EAN_8 -> "EAN_8"
        Barcode.FORMAT_UPC_A -> "UPC_A"
        Barcode.FORMAT_UPC_E -> "UPC_E"
        else -> return null
    }
    return LecturaCodigo(valor, formato, CodigoBarras.checksumValido(valor, formato))
}

/**
 * UI superpuesta al escáner (sin estado, sin blur): franja superior sólida con cerrar + instrucción +
 * progreso, marco guía con línea de escaneo, chip del último código visto y linterna.
 * [confirmado] != null muestra el código en grande (lectura aceptada).
 */
@Composable
fun BarcodeScannerOverlay(
    paso: Int,
    totalPasos: Int,
    torchOn: Boolean,
    torchDisponible: Boolean,
    leyendo: String?,
    confirmado: String?,
    error: String?,
    onCerrar: () -> Unit,
    onTorch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(GlassRojo.navFill)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = GlassDimens.screenSide, vertical = GlassDimens.s12),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(Icons.Rounded.Close, "Cerrar escáner", onCerrar, solid = true)
                Spacer(Modifier.size(GlassDimens.s12))
                Column(Modifier.weight(1f)) {
                    Text("PASO $paso DE $totalPasos", style = Glass.type.label)
                    Text("Apunta al código de barras del producto o del empaque", style = Glass.type.bodyStrong)
                }
            }
            Spacer(Modifier.height(GlassDimens.s12))
            GlassProgressBar(if (totalPasos > 0) paso.toFloat() / totalPasos else 0f)
        }

        // Marco guía horizontal con línea de escaneo.
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(2.2f)
                    .border(2.dp, GlassRojo.accentBright, RoundedCornerShape(GlassDimens.radiusCard))
                    .clip(RoundedCornerShape(GlassDimens.radiusCard)),
                contentAlignment = Alignment.Center,
            ) {
                if (confirmado == null) LineaDeEscaneo() else CodigoConfirmado(confirmado)
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(bottom = GlassDimens.s24),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (error != null) {
                ChipSolido { StatusChip(error, GlassRojo.danger, icon = Icons.Rounded.ErrorOutline) }
                Spacer(Modifier.height(GlassDimens.s16))
            } else if (leyendo != null && confirmado == null) {
                ChipSolido { StatusChip("Leyendo ${leyendo.take(6)}…", GlassRojo.info, icon = Icons.Rounded.QrCodeScanner) }
                Spacer(Modifier.height(GlassDimens.s16))
            }
            Box(Modifier.fillMaxWidth().height(GlassDimens.circleButton), contentAlignment = Alignment.Center) {
                if (torchDisponible) {
                    CircleIconButton(
                        icon = if (torchOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                        contentDescription = if (torchOn) "Apagar linterna" else "Encender linterna",
                        onClick = onTorch,
                        solid = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChipSolido(content: @Composable () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = GlassDimens.screenSide)
            .clip(CircleShape)
            .background(GlassRojo.bgElevated, CircleShape)
    ) { content() }
}

/** Línea horizontal accent-bright que sube y baja dentro del marco. */
@Composable
private fun LineaDeEscaneo() {
    val transicion = rememberInfiniteTransition(label = "escaneo")
    val fraccion by transicion.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "fraccion",
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val y = with(LocalDensity.current) { (constraints.maxHeight * fraccion).toDp() }
        Box(
            Modifier
                .offset(y = y)
                .padding(horizontal = GlassDimens.s16)
                .fillMaxWidth()
                .height(2.dp)
                .background(GlassRojo.accentBright, CircleShape)
        )
    }
}

/** Código aceptado, en grande dentro del marco. */
@Composable
private fun CodigoConfirmado(valor: String) {
    Column(
        Modifier
            .fillMaxSize()
            .background(GlassRojo.bgElevated.copy(alpha = 0.88f))
            .padding(GlassDimens.s16),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Icon(Icons.Rounded.CheckCircle, null, tint = GlassRojo.success, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(GlassDimens.s8))
        Text(valor, style = Glass.type.display, textAlign = TextAlign.Center)
    }
}

@ComposePreview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun BarcodeScannerOverlayLeyendoPreview() {
    GlassRojoTheme {
        BarcodeScannerOverlay(
            paso = 3, totalPasos = 4, torchOn = false, torchDisponible = true,
            leyendo = "7501055300075", confirmado = null, error = null,
            onCerrar = {}, onTorch = {},
            modifier = Modifier.background(Color(0xFF334155)),
        )
    }
}

@ComposePreview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun BarcodeScannerOverlayConfirmadoPreview() {
    GlassRojoTheme {
        BarcodeScannerOverlay(
            paso = 3, totalPasos = 4, torchOn = true, torchDisponible = true,
            leyendo = null, confirmado = "7501055300075", error = null,
            onCerrar = {}, onTorch = {},
            modifier = Modifier.background(Color(0xFF334155)),
        )
    }
}
