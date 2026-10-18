package app.luxion.shogunai.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

fun contrastRatio(foreground: Color, background: Color): Double {
    val lighter = maxOf(foreground.luminance(), background.luminance())
    val darker = minOf(foreground.luminance(), background.luminance())
    return (lighter + 0.05) / (darker + 0.05)
}
