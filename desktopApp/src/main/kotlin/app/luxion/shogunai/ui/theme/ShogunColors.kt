package app.luxion.shogunai.ui.theme

import androidx.compose.ui.graphics.Color

data class ShogunColors(
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val brand: Color,
    val disabledContent: Color,
    val disabledContainer: Color,
    val scrim: Color,
)

val LightShogunColors = ShogunColors(
    accent = SlateBlueLight,
    onAccent = Neutral100,
    accentContainer = SlateBlueContainerLight,
    onAccentContainer = OnSlateBlueContainerLight,
    brand = BrandRedLightScheme,
    disabledContent = Neutral53,
    disabledContainer = Neutral89,
    scrim = Neutral2.copy(alpha = ScrimAlpha),
)

val DarkShogunColors = ShogunColors(
    accent = SlateBlueDark,
    onAccent = OnSlateBlueDark,
    accentContainer = SlateBlueContainerDark,
    onAccentContainer = OnSlateBlueContainerDark,
    brand = BrandRedDarkScheme,
    disabledContent = Neutral50,
    disabledContainer = Neutral18,
    scrim = Neutral2.copy(alpha = ScrimAlpha),
)
