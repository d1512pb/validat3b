package com.richi_mc.hackathon3b.feature.analysis.enhance

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import com.richi_mc.hackathon3b.core.model.CajaPx
import com.richi_mc.hackathon3b.core.model.LineaOcr
import com.richi_mc.hackathon3b.core.model.OcrEnhancer
import com.richi_mc.hackathon3b.ui.components.GlassCard
import com.richi_mc.hackathon3b.ui.theme.Glass
import com.richi_mc.hackathon3b.ui.theme.GlassDimens
import com.richi_mc.hackathon3b.ui.theme.GlassRojoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pantalla demo: recorte mejorado de [zona] en la foto [path] y el texto que lee [enhancer] en él. */
@Composable
fun DemoReOcr(path: String, zona: CajaPx, enhancer: OcrEnhancer, modifier: Modifier = Modifier) {
    var recorte by remember { mutableStateOf<ImageBitmap?>(null) }
    var lineas by remember { mutableStateOf<List<LineaOcr>?>(null) }
    LaunchedEffect(path, zona) {
        recorte = withContext(Dispatchers.Default) {
            recorteMejorado(path, zona)?.let { b: Bitmap -> b.asImageBitmap() }
        }
        lineas = enhancer.reOcr(path, zona)
    }
    ContenidoDemoReOcr(recorte, lineas, modifier)
}

/** Parte sin lógica de [DemoReOcr]; [lineas] null significa "leyendo". */
@Composable
fun ContenidoDemoReOcr(recorte: ImageBitmap?, lineas: List<LineaOcr>?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(GlassDimens.screenSide),
        verticalArrangement = Arrangement.spacedBy(GlassDimens.s12),
    ) {
        Text("RECORTE MEJORADO", style = Glass.type.label)
        GlassCard(Modifier.fillMaxWidth()) {
            if (recorte != null) {
                Image(recorte, "Recorte mejorado", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
            } else {
                Text("Sin recorte", style = Glass.type.body)
            }
        }
        Text("TEXTO LEÍDO", style = Glass.type.label)
        GlassCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(GlassDimens.s8)) {
                when {
                    lineas == null -> Text("Leyendo…", style = Glass.type.body)
                    lineas.isEmpty() -> Text("No se leyó texto", style = Glass.type.body)
                    else -> lineas.forEach {
                        Text("${it.texto}  (${it.caja.ancho}x${it.caja.alto})", style = Glass.type.bodyStrong)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun DemoReOcrPreview() {
    val ejemplo = remember {
        val b = Bitmap.createBitmap(360, 120, Bitmap.Config.ARGB_8888)
        val px = IntArray(360 * 120) { i -> val v = (i % 360) * 255 / 359; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        b.setPixels(px, 0, 360, 0, 0, 360, 120)
        b.asImageBitmap()
    }
    GlassRojoTheme {
        ContenidoDemoReOcr(
            recorte = ejemplo,
            lineas = listOf(
                LineaOcr("$24", CajaPx(100, 40, 220, 160)),
                LineaOcr("90", CajaPx(230, 20, 280, 70)),
            ),
        )
    }
}
