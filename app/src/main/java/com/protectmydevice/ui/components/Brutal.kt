package com.protectmydevice.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.protectmydevice.R
import com.protectmydevice.ui.theme.Ink
import com.protectmydevice.ui.theme.Lime
import com.protectmydevice.ui.theme.LocalBrutal

val Outline = 2.dp

/** Solid, un-blurred drop shadow: the same shape again, shifted down-right. */
fun Modifier.hardShadow(color: Color, offset: Dp, radius: Dp) = drawBehind {
    val o = offset.toPx()
    drawRoundRect(color, topLeft = Offset(o, o), size = size, cornerRadius = CornerRadius(radius.toPx()))
}

/** Outlined block with a hard shadow. Reserves room for the shadow so neighbours never overlap it. */
@Composable
fun BrutalBox(
    modifier: Modifier = Modifier,
    color: Color = LocalBrutal.current.card,
    radius: Dp = 20.dp,
    shadow: Dp = 5.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val b = LocalBrutal.current
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .padding(end = shadow, bottom = shadow)
            .hardShadow(b.shadow, shadow, radius)
            .clip(shape)
            .background(color)
            .border(Outline, b.line, shape),
        content = content,
    )
}

/** Anything tappable: the face sits [lift] above its shadow and physically drops onto it when pressed. */
@Composable
fun BrutalPressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Lime,
    radius: Dp = 14.dp,
    lift: Dp = 4.dp,
    enabled: Boolean = true,
    clickable: Boolean = true,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val b = LocalBrutal.current
    val shape = RoundedCornerShape(radius)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val drop by animateDpAsState(if (pressed) lift else 0.dp, tween(70), label = "press")
    Box(
        modifier
            .padding(end = lift, bottom = lift)
            .then(if (enabled) Modifier.hardShadow(b.shadow, lift, radius) else Modifier)
            .offset { IntOffset(drop.roundToPx(), drop.roundToPx()) }
            .clip(shape)
            .background(if (enabled) color else b.raised)
            .border(Outline, if (enabled) b.line else b.muted, shape)
            .clickable(interaction, indication = null, enabled = enabled && clickable, role = Role.Button, onClick = onClick),
        contentAlignment = contentAlignment,
        content = content,
    )
}

@Composable
fun BrutalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Lime,
    contentColor: Color = Ink,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false,
) {
    val tint = if (enabled) contentColor else LocalBrutal.current.muted
    BrutalPressable(
        onClick = onClick,
        modifier = modifier,
        color = color,
        radius = if (compact) 12.dp else 16.dp,
        lift = if (compact) 3.dp else 4.dp,
        enabled = enabled,
        clickable = !loading,
    ) {
        Row(
            Modifier
                .defaultMinSize(minHeight = if (compact) 40.dp else 52.dp)
                .padding(horizontal = if (compact) 14.dp else 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = tint, strokeWidth = 2.5.dp)
            else if (icon != null) Icon(painterResource(icon), null, Modifier.size(20.dp), tint = tint)
            Text(
                text,
                color = tint,
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Small round icon-only button (sign out, refresh). */
@Composable
fun BrutalIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    val b = LocalBrutal.current
    BrutalPressable(onClick, modifier, color = b.card, radius = 14.dp, lift = 3.dp, clickable = !loading) {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = b.onCard, strokeWidth = 2.5.dp)
            else Icon(painterResource(icon), contentDescription, Modifier.size(20.dp), tint = b.onCard)
        }
    }
}

/** Mono, uppercase label on a flat colour chip. Tilt it a little and it reads like a sticker. */
@Composable
fun Sticker(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Lime,
    contentColor: Color = Ink,
    rotation: Float = 0f,
    @DrawableRes icon: Int? = null,
    outline: Color = LocalBrutal.current.line,
) {
    val shape = RoundedCornerShape(7.dp)
    Row(
        modifier
            .rotate(rotation)
            .clip(shape)
            .background(color)
            .border(1.5.dp, outline, shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(painterResource(icon), null, Modifier.size(13.dp), tint = contentColor)
        Text(text.uppercase(), color = contentColor, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** The app mark: the launcher icon's shield on a lime tile. */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Lime)
            .border(Outline, LocalBrutal.current.line, shape),
        contentAlignment = Alignment.Center,
    ) {
        // The adaptive-icon foreground keeps its art in the middle 2/3, so draw it oversized.
        Icon(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(size * 1.55f), tint = Ink)
    }
}
