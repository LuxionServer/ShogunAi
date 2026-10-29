package app.luxion.shogunai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

val Geist = FontFamily(
    Font(resource = "fonts/Geist-Regular.ttf", weight = FontWeight.Normal),
    Font(resource = "fonts/Geist-Medium.ttf", weight = FontWeight.Medium),
    Font(resource = "fonts/Geist-SemiBold.ttf", weight = FontWeight.SemiBold),
)

val GeistMono = FontFamily(
    Font(resource = "fonts/GeistMono-Regular.ttf", weight = FontWeight.Normal),
    Font(resource = "fonts/GeistMono-Medium.ttf", weight = FontWeight.Medium),
    Font(resource = "fonts/GeistMono-SemiBold.ttf", weight = FontWeight.SemiBold),
)

data class ShogunTextStyles(
    val data: TextStyle,
    val dataSmall: TextStyle,
    val viewTitle: TextStyle,
    val sectionTitle: TextStyle,
    val eyebrow: TextStyle,
)

private const val TABULAR_FIGURES = "tnum"
private const val EYEBROW_LETTER_SPACING_EM = 0.06f

private fun geist(weight: FontWeight, size: TextUnit, lineHeight: TextUnit) = TextStyle(
    fontFamily = Geist,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
)

private fun geistMono(size: TextUnit, lineHeight: TextUnit, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = GeistMono,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    fontFeatureSettings = TABULAR_FIGURES,
)

val ShogunTypography = Typography(
    displayLarge = geist(FontWeight.SemiBold, 40.sp, 48.sp),
    displayMedium = geist(FontWeight.SemiBold, 34.sp, 42.sp),
    displaySmall = geist(FontWeight.SemiBold, 28.sp, 36.sp),
    headlineLarge = geist(FontWeight.SemiBold, 26.sp, 32.sp),
    headlineMedium = geist(FontWeight.SemiBold, 24.sp, 30.sp),
    headlineSmall = geist(FontWeight.SemiBold, 22.sp, 28.sp),
    titleLarge = geist(FontWeight.SemiBold, 18.sp, 24.sp),
    titleMedium = geist(FontWeight.Medium, 15.sp, 20.sp),
    titleSmall = geist(FontWeight.Medium, 13.sp, 18.sp),
    bodyLarge = geist(FontWeight.Normal, 14.sp, 20.sp),
    bodyMedium = geist(FontWeight.Normal, 13.sp, 18.sp),
    bodySmall = geist(FontWeight.Normal, 12.sp, 16.sp),
    labelLarge = geist(FontWeight.Medium, 13.sp, 18.sp),
    labelMedium = geist(FontWeight.Medium, 12.sp, 16.sp),
    labelSmall = geist(FontWeight.Medium, 11.sp, 14.sp),
)

val ShogunDataTextStyles = ShogunTextStyles(
    data = geistMono(13.sp, 18.sp),
    dataSmall = geistMono(12.sp, 16.sp),
    viewTitle = geist(FontWeight.SemiBold, 16.sp, 22.sp),
    sectionTitle = geist(FontWeight.SemiBold, 13.sp, 18.sp),
    eyebrow = geistMono(10.5.sp, 14.sp, FontWeight.Medium).copy(letterSpacing = EYEBROW_LETTER_SPACING_EM.em),
)
