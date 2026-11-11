package com.protectmydevice.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The bits Material doesn't have a slot for: the outline/shadow colour and the plain card surface. */
@Immutable
data class Brutal(
    val line: Color,
    val shadow: Color,
    val card: Color,
    val onCard: Color,
    val raised: Color,
    val muted: Color,
)

private val LightBrutal = Brutal(
    line = Ink, shadow = Ink, card = Color.White, onCard = Ink, raised = PaperDim, muted = Color(0xFF615C50),
)
private val DarkBrutal = Brutal(
    line = Paper, shadow = Color(0xFF4B4A45), card = NightCard, onCard = Paper, raised = NightRaised, muted = Color(0xFFA9A497),
)

val LocalBrutal = staticCompositionLocalOf { LightBrutal }

private val LightColors = lightColorScheme(
    primary = Ink, onPrimary = Paper,
    primaryContainer = Lime, onPrimaryContainer = Ink,
    secondary = Color(0xFF4B36B8), onSecondary = Color.White,
    secondaryContainer = Lilac, onSecondaryContainer = Ink,
    tertiary = Color(0xFF6E5200), onTertiary = Color.White,
    tertiaryContainer = Hazard, onTertiaryContainer = Ink,
    error = Color(0xFFC4290F), onError = Color.White,
    errorContainer = Coral, onErrorContainer = Ink,
    background = Paper, onBackground = Ink,
    surface = Paper, onSurface = Ink,
    surfaceVariant = PaperDim, onSurfaceVariant = Color(0xFF615C50),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEDE8DD), surfaceContainerHigh = PaperDim,
    surfaceContainerHighest = Color(0xFFDFD9CB),
    outline = Ink, outlineVariant = Color(0xFFCBC5B6),
    inverseSurface = Ink, inverseOnSurface = Paper, inversePrimary = Lime,
)

private val DarkColors = darkColorScheme(
    primary = Lime, onPrimary = Ink,
    primaryContainer = Lime, onPrimaryContainer = Ink,
    secondary = Lilac, onSecondary = Ink,
    secondaryContainer = Lilac, onSecondaryContainer = Ink,
    tertiary = Hazard, onTertiary = Ink,
    tertiaryContainer = Hazard, onTertiaryContainer = Ink,
    error = Color(0xFFFF8A75), onError = Ink,
    errorContainer = Coral, onErrorContainer = Ink,
    background = Night, onBackground = Paper,
    surface = Night, onSurface = Paper,
    surfaceVariant = NightRaised, onSurfaceVariant = Color(0xFFA9A497),
    surfaceContainerLowest = Color(0xFF0A0A0A), surfaceContainerLow = NightCard,
    surfaceContainer = Color(0xFF212120), surfaceContainerHigh = NightRaised,
    surfaceContainerHighest = Color(0xFF323230),
    outline = Paper, outlineVariant = Color(0xFF3D3D3A),
    inverseSurface = Paper, inverseOnSurface = Ink, inversePrimary = Ink,
)

private val ProtectShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun ProtectTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBrutal provides if (darkTheme) DarkBrutal else LightBrutal) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = ProtectTypography,
            shapes = ProtectShapes,
            content = content,
        )
    }
}
