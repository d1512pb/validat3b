package com.richi_mc.hackathon3b.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.richi_mc.hackathon3b.R

/** Tokens de color del sistema Glass Rojo (PLAN.md §4). No inventar otros. */
object GlassRojo {
    val bg = Color(0xFF020617)
    val bgElevated = Color(0xFF0F172A)
    val navFill = Color(0x8C0F172A)            // rgba(15,23,42,0.55)
    val glassFill = Color(0x0FFFFFFF)          // 0.06
    val glassFillStrong = Color(0x1FFFFFFF)    // 0.12
    val glassFillActive = Color(0x29FFFFFF)    // 0.16
    val glassFallback = Color(0x1AFFFFFF)      // 0.10 (sin blur, API < 31)
    val glassBorder = Color(0x1FFFFFFF)        // 0.12
    val glassHighlight = Color(0x14FFFFFF)     // inset 0 1px 0 rgba(255,255,255,0.08)
    val ink = Color(0xFFF8FAFC)
    val inkStrong = Color(0xFFE2E8F0)
    val inkSecondary = Color(0xFFCBD5E1)
    val inkMuted = Color(0xFF94A3B8)
    val accent = Color(0xFFDC2626)
    val accentBright = Color(0xFFEF4444)
    val accentInk = Color(0xFFF87171)
    val success = Color(0xFF34D399)
    val warning = Color(0xFFFBBF24)
    val info = Color(0xFF38BDF8)
    val danger = Color(0xFFFCA5A5)
    val blob1 = Color(0xFFEF4444)
    val blob2 = Color(0xFFF43F5E)
    val blob3 = Color(0xFFF97316)
    val glow = Color(0x99EF4444)               // rgba(239,68,68,0.6)
}

/** Escala de espaciado y radios (§4). */
object GlassDimens {
    val s4 = 4.dp; val s8 = 8.dp; val s12 = 12.dp; val s16 = 16.dp
    val s20 = 20.dp; val s24 = 24.dp; val s32 = 32.dp
    val cardPadding = 16.dp
    val screenSide = 20.dp
    val screenTop = 24.dp
    val radiusInput = 16.dp
    val radiusCard = 20.dp
    val radiusCardMain = 24.dp
    val radiusButton = 18.dp
    val radiusIconBox = 14.dp
    val buttonHeight = 54.dp
    val iconBox = 44.dp
    val circleButton = 44.dp
    val fab = 54.dp
    val icon = 20.dp
    val itemSpacing = Arrangement.spacedBy(12.dp)
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    R.font.inter,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700))

@Immutable
data class GlassTypography(
    val display: TextStyle,
    val title: TextStyle,
    val bodyStrong: TextStyle,
    val body: TextStyle,
    val secondary: TextStyle,
    /** Usar con `.uppercase()` en el texto. */
    val label: TextStyle,
)

val DefaultGlassTypography = GlassTypography(
    display = TextStyle(fontFamily = Inter, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = GlassRojo.ink),
    title = TextStyle(fontFamily = Inter, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = GlassRojo.ink),
    bodyStrong = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = GlassRojo.ink),
    body = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, color = GlassRojo.ink),
    secondary = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, color = GlassRojo.inkSecondary),
    label = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.08.em, color = GlassRojo.inkMuted),
)

val LocalGlassColors = staticCompositionLocalOf { GlassRojo }
val LocalGlassTypography = staticCompositionLocalOf { DefaultGlassTypography }

/** Acceso corto: `Glass.colors.accent`, `Glass.type.title`. */
object Glass {
    val colors: GlassRojo @Composable get() = LocalGlassColors.current
    val type: GlassTypography @Composable get() = LocalGlassTypography.current
}

@Composable
fun GlassRojoTheme(content: @Composable () -> Unit) {
    // MaterialTheme solo como base neutra (los componentes Glass no usan sus colores).
    val scheme = darkColorScheme(
        primary = GlassRojo.accent,
        onPrimary = GlassRojo.ink,
        background = GlassRojo.bg,
        onBackground = GlassRojo.ink,
        surface = GlassRojo.bgElevated,
        onSurface = GlassRojo.ink,
        surfaceTint = Color.Transparent,
        error = GlassRojo.danger,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalGlassColors provides GlassRojo,
            LocalGlassTypography provides DefaultGlassTypography,
            content = content
        )
    }
}
