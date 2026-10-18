package app.luxion.shogunai.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.lightColorScheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import app.luxion.shogunai.domain.model.ThemeMode

private val LocalShogunStatusColors = staticCompositionLocalOf { DarkStatusColors }
private val LocalShogunTextStyles = staticCompositionLocalOf { ShogunDataTextStyles }

object ShogunTheme {
    val status: ShogunStatusColors
        @Composable get() = LocalShogunStatusColors.current

    val typography: ShogunTextStyles
        @Composable get() = LocalShogunTextStyles.current
}

val ShogunLightColorScheme: ColorScheme = lightColorScheme(
    primary = BrandRedLightScheme,
    onPrimary = Neutral100,
    primaryContainer = BrandRedContainerLight,
    onPrimaryContainer = OnBrandRedContainerLight,
    inversePrimary = BrandRedInverseLight,
    secondary = Neutral38,
    onSecondary = Neutral100,
    secondaryContainer = Neutral89,
    onSecondaryContainer = Neutral14,
    tertiary = SlateBlueLight,
    onTertiary = Neutral100,
    tertiaryContainer = SlateBlueContainerLight,
    onTertiaryContainer = OnSlateBlueContainerLight,
    background = Neutral98,
    onBackground = Neutral9,
    surface = Neutral98,
    onSurface = Neutral9,
    surfaceVariant = Neutral89,
    onSurfaceVariant = Neutral34,
    surfaceTint = BrandRedLightScheme,
    inverseSurface = Neutral18,
    inverseOnSurface = Neutral94,
    error = DangerLight,
    onError = Neutral100,
    errorContainer = DangerContainerLight,
    onErrorContainer = OnDangerContainerLight,
    outline = Neutral50,
    outlineVariant = Neutral80,
    scrim = Neutral2,
    surfaceBright = Neutral100,
    surfaceContainer = Neutral94,
    surfaceContainerHigh = Neutral91,
    surfaceContainerHighest = Neutral89,
    surfaceContainerLow = Neutral96,
    surfaceContainerLowest = Neutral100,
    surfaceDim = Neutral91,
    primaryFixed = BrandRedContainerLight,
    primaryFixedDim = BrandRedFixedDim,
    onPrimaryFixed = OnBrandRedFixed,
    onPrimaryFixedVariant = OnBrandRedFixedVariant,
    secondaryFixed = Neutral89,
    secondaryFixedDim = Neutral80,
    onSecondaryFixed = Neutral9,
    onSecondaryFixedVariant = Neutral26,
    tertiaryFixed = SlateBlueContainerLight,
    tertiaryFixedDim = SlateBlueFixedDim,
    onTertiaryFixed = OnSlateBlueFixed,
    onTertiaryFixedVariant = OnSlateBlueFixedVariant,
)

val ShogunDarkColorScheme: ColorScheme = darkColorScheme(
    primary = BrandRedDarkScheme,
    onPrimary = Neutral100,
    primaryContainer = BrandRedContainerDark,
    onPrimaryContainer = OnBrandRedContainerDark,
    inversePrimary = BrandRedLightScheme,
    secondary = Neutral74,
    onSecondary = Neutral11,
    secondaryContainer = Neutral22,
    onSecondaryContainer = Neutral91,
    tertiary = SlateBlueDark,
    onTertiary = OnSlateBlueDark,
    tertiaryContainer = SlateBlueContainerDark,
    onTertiaryContainer = OnSlateBlueContainerDark,
    background = Neutral6,
    onBackground = Neutral91,
    surface = Neutral6,
    onSurface = Neutral91,
    surfaceVariant = Neutral18,
    onSurfaceVariant = Neutral74,
    surfaceTint = BrandRedDarkScheme,
    inverseSurface = Neutral91,
    inverseOnSurface = Neutral11,
    error = DangerDark,
    onError = OnDangerDark,
    errorContainer = DangerContainerDark,
    onErrorContainer = OnDangerContainerDark,
    outline = Neutral53,
    outlineVariant = Neutral26,
    scrim = Neutral2,
    surfaceBright = Neutral22,
    surfaceContainer = Neutral11,
    surfaceContainerHigh = Neutral14,
    surfaceContainerHighest = Neutral18,
    surfaceContainerLow = Neutral9,
    surfaceContainerLowest = Neutral4,
    surfaceDim = Neutral6,
    primaryFixed = BrandRedContainerLight,
    primaryFixedDim = BrandRedFixedDim,
    onPrimaryFixed = OnBrandRedFixed,
    onPrimaryFixedVariant = OnBrandRedFixedVariant,
    secondaryFixed = Neutral89,
    secondaryFixedDim = Neutral80,
    onSecondaryFixed = Neutral9,
    onSecondaryFixedVariant = Neutral26,
    tertiaryFixed = SlateBlueContainerLight,
    tertiaryFixedDim = SlateBlueFixedDim,
    onTertiaryFixed = OnSlateBlueFixed,
    onTertiaryFixedVariant = OnSlateBlueFixedVariant,
)

private val ShogunRippleConfiguration = RippleConfiguration(
    rippleAlpha = RippleAlpha(draggedAlpha = 0.16f, focusedAlpha = 0.2f, hoveredAlpha = 0.08f, pressedAlpha = 0.12f),
)

@Composable
fun ShogunAiTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val systemDark by rememberSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }
    CompositionLocalProvider(
        LocalShogunStatusColors provides if (useDark) DarkStatusColors else LightStatusColors,
        LocalShogunTextStyles provides ShogunDataTextStyles,
    ) {
        MaterialTheme(
            colorScheme = if (useDark) ShogunDarkColorScheme else ShogunLightColorScheme,
            typography = ShogunTypography,
            shapes = ShogunShapes,
        ) {
            CompositionLocalProvider(LocalRippleConfiguration provides ShogunRippleConfiguration) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    content = content,
                )
            }
        }
    }
}
