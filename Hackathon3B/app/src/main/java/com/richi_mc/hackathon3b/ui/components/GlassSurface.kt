package com.richi_mc.hackathon3b.ui.components

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.richi_mc.hackathon3b.ui.theme.GlassDimens
import com.richi_mc.hackathon3b.ui.theme.GlassRojo
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Estado de Haze del [GlassBackground] actual; null = sin blur (p. ej. sobre la cámara). */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

private val blurDisponible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Receta glass única: relleno glass-fill + borde 1 dp glass-border + brillo interno
 * + desenfoque 20 dp. Sin blur (API < 31 o sin HazeState): relleno blanco al 10 %.
 */
fun Modifier.glassSurface(
    shape: Shape,
    hazeState: HazeState?,
    fill: Color = GlassRojo.glassFill,
    blurRadius: Dp = 20.dp,
): Modifier {
    val base = this.clip(shape)
    val conFondo = if (hazeState != null && blurDisponible) {
        base.hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = GlassRojo.bg,
                tint = HazeTint(fill),
                blurRadius = blurRadius,
                noiseFactor = 0f,
            )
        )
    } else {
        base.background(if (fill == GlassRojo.glassFill) GlassRojo.glassFallback else fill, shape)
    }
    return conFondo
        .drawWithContent {
            drawContent()
            // brillo interno: línea de 1 px arriba
            drawLine(GlassRojo.glassHighlight, Offset(0f, 0.5f), Offset(size.width, 0.5f), 1f)
        }
        .border(1.dp, GlassRojo.glassBorder, shape)
}

/** Fondo de toda pantalla (excepto cámara): bg + manchas rojas desenfocadas. */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val hazeState = rememberHazeState()
    Box(modifier.fillMaxSize().background(GlassRojo.bg)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
        ) {
            drawRect(GlassRojo.bg)
            fun blob(color: Color, alpha: Float, center: Offset, radiusPx: Float) {
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to color.copy(alpha = alpha),
                        0.55f to color.copy(alpha = alpha * 0.45f),
                        1f to Color.Transparent,
                        center = center,
                        radius = radiusPx,
                    ),
                    radius = radiusPx,
                    center = center,
                )
            }
            val d = density
            blob(GlassRojo.blob1, 0.55f, Offset(40f * d, 60f * d), 300f * d)
            blob(GlassRojo.blob2, 0.35f, Offset(size.width - 30f * d, size.height - 80f * d), 260f * d)
            blob(GlassRojo.blob3, 0.25f, Offset(size.width, size.height * 0.4f), 160f * d)
        }
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            content()
        }
    }
}

/** Tarjeta glass: padding 16, radio 20 (principal = 24). */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    principal: Boolean = false,
    contentPadding: Dp = GlassDimens.cardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(if (principal) GlassDimens.radiusCardMain else GlassDimens.radiusCard)
    Column(
        modifier
            .glassSurface(shape, LocalHazeState.current)
            .padding(contentPadding),
        content = content,
    )
}
