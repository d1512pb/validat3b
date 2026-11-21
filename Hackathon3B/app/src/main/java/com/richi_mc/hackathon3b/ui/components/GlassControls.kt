package com.richi_mc.hackathon3b.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.richi_mc.hackathon3b.ui.theme.Glass
import com.richi_mc.hackathon3b.ui.theme.GlassDimens
import com.richi_mc.hackathon3b.ui.theme.GlassRojo

private fun Modifier.redGlow(shape: androidx.compose.ui.graphics.Shape, enabled: Boolean): Modifier =
    if (!enabled) this else this.dropShadow(
        shape,
        Shadow(radius = 24.dp, spread = (-6).dp, color = GlassRojo.glow, offset = DpOffset(0.dp, 8.dp))
    )

/** Botón primario rojo. Uno por pantalla. Deshabilitado: opacidad 0.4, sin glow. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(GlassDimens.radiusButton)
    Row(
        modifier
            .fillMaxWidth()
            .height(GlassDimens.buttonHeight)
            .alpha(if (enabled) 1f else 0.4f)
            .redGlow(shape, enabled)
            .clip(shape)
            .background(GlassRojo.accent, shape)
            .border(1.dp, Color.White.copy(alpha = 0.25f), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = GlassRojo.ink, modifier = Modifier.size(GlassDimens.icon))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Glass.type.bodyStrong, color = GlassRojo.ink)
    }
}

/** Botón glass secundario ("Retomar", "Cambiar de sucursal"). */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(GlassDimens.radiusButton)
    Row(
        modifier
            .fillMaxWidth()
            .height(GlassDimens.buttonHeight)
            .alpha(if (enabled) 1f else 0.4f)
            .glassSurface(shape, LocalHazeState.current)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = GlassRojo.inkStrong, modifier = Modifier.size(GlassDimens.icon))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Glass.type.bodyStrong, color = GlassRojo.inkStrong)
    }
}

/**
 * Botón circular de 44 dp (flash, regresar, cerrar). [solid] = usa nav-fill sin blur
 * (obligatorio sobre la cámara).
 */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    solid: Boolean = false,
) {
    val base = modifier.size(GlassDimens.circleButton)
    val surf = if (solid) base.clip(CircleShape).background(GlassRojo.navFill, CircleShape)
        .border(1.dp, GlassRojo.glassBorder, CircleShape)
    else base.glassSurface(CircleShape, LocalHazeState.current)
    Box(
        surf
            .semantics { this.contentDescription = contentDescription }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = GlassRojo.inkStrong, modifier = Modifier.size(GlassDimens.icon))
    }
}

/** FAB rojo de 54 dp con glow (disparador de la cámara). */
@Composable
fun AccentFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = GlassDimens.fab,
) {
    Box(
        modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.4f)
            .redGlow(CircleShape, enabled)
            .clip(CircleShape)
            .background(GlassRojo.accent, CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .semantics { this.contentDescription = contentDescription }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = GlassRojo.ink, modifier = Modifier.size(22.dp))
    }
}

/** Chip de estado: texto del color, fondo al 14 %, borde al 30 %. Siempre con ícono y texto. */
@Composable
fun StatusChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f), CircleShape)
            .border(1.dp, color.copy(alpha = 0.30f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = Glass.type.label.copy(letterSpacing = Glass.type.body.letterSpacing), color = color)
    }
}

/** Barra de progreso 8 dp: pista glass-fill-strong, relleno accent-bright. */
@Composable
fun GlassProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(GlassRojo.glassFillStrong)
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(CircleShape)
                .background(GlassRojo.accentBright)
        )
    }
}

/** Selector segmentado (cadena): contenedor glass redondo, padding 4; activa glass-fill-active. */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .glassSurface(CircleShape, LocalHazeState.current)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { opt ->
            val active = opt == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(if (active) GlassRojo.glassFillActive else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(opt) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(opt),
                    style = if (active) Glass.type.secondary.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    else Glass.type.secondary,
                    color = if (active) GlassRojo.ink else GlassRojo.inkSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Campo de texto glass: 52 dp, radio 16; foco = contorno 2 dp accent-ink al 70 %. */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(GlassDimens.radiusInput)
    Column(modifier.fillMaxWidth()) {
        Text(label, style = Glass.type.secondary, color = GlassRojo.inkSecondary)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .glassSurface(shape, LocalHazeState.current)
                .then(if (focused) Modifier.border(2.dp, GlassRojo.accentInk.copy(alpha = 0.7f), shape) else Modifier)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, style = Glass.type.body, color = GlassRojo.inkMuted)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = Glass.type.body,
                cursorBrush = SolidColor(GlassRojo.accentInk),
                keyboardOptions = keyboardOptions,
                interactionSource = remember { MutableInteractionSource() },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
            )
        }
    }
}

/** Contenedor de ícono 44 dp, radio 14, para encabezar tarjetas. */
@Composable
fun IconBox(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(GlassDimens.radiusIconBox)
    Box(
        modifier
            .size(GlassDimens.iconBox)
            .clip(shape)
            .background(tint.copy(alpha = 0.14f), shape)
            .border(1.dp, tint.copy(alpha = 0.30f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}
