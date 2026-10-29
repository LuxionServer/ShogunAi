package app.luxion.shogunai.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val ElevationBorderWidth: Dp = 1.dp
private val Level2Shadow: Dp = 4.dp

data class ElevationLevel(val container: Color, val border: Color?, val shadow: Dp)

data class ShogunElevation(val level0: ElevationLevel, val level1: ElevationLevel, val level2: ElevationLevel)

private fun elevationOf(scheme: ColorScheme) = ShogunElevation(
    level0 = ElevationLevel(container = scheme.surface, border = null, shadow = 0.dp),
    level1 = ElevationLevel(container = scheme.surfaceContainerLow, border = scheme.outlineVariant, shadow = 0.dp),
    level2 = ElevationLevel(container = scheme.surfaceContainerHigh, border = scheme.outlineVariant, shadow = Level2Shadow),
)

val LightElevation = elevationOf(ShogunLightColorScheme)
val DarkElevation = elevationOf(ShogunDarkColorScheme)

fun Modifier.elevation(level: ElevationLevel, shape: Shape): Modifier = this
    .then(if (level.shadow > 0.dp) Modifier.shadow(level.shadow, shape, clip = false) else Modifier)
    .background(level.container, shape)
    .then(level.border?.let { Modifier.border(ElevationBorderWidth, it, shape) } ?: Modifier)
