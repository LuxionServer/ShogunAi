package app.luxion.shogunai.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.luxion.shogunai.domain.model.ThemeMode

private val ShogunDarkColorScheme = darkColorScheme(
    primary = ShogunRed,
    onPrimary = ShogunOnAccent,
    secondary = ShogunSlate,
    onSecondary = ShogunOnAccent,
    tertiary = ShogunRedLight,
    onTertiary = ShogunOnAccent,
    background = ShogunBackground,
    onBackground = ShogunOnBackground,
    surface = ShogunSurface,
    onSurface = ShogunOnSurface,
    surfaceVariant = ShogunSurfaceVariant,
    onSurfaceVariant = ShogunOnSurfaceVariant,
    error = ShogunError,
    outline = ShogunOutline,
)

private val ShogunLightColorScheme = lightColorScheme(
    primary = ShogunRed,
    onPrimary = ShogunOnAccent,
    secondary = ShogunSlate,
    onSecondary = ShogunOnAccent,
    tertiary = ShogunRedLight,
    onTertiary = ShogunOnAccent,
    background = ShogunLightBackground,
    onBackground = ShogunLightOnBackground,
    surface = ShogunLightSurface,
    onSurface = ShogunLightOnSurface,
    surfaceVariant = ShogunLightSurfaceVariant,
    onSurfaceVariant = ShogunLightOnSurfaceVariant,
    error = ShogunLightError,
    outline = ShogunLightOutline,
)

@Composable
fun ShogunAiTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val systemDark by rememberSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }
    MaterialTheme(
        colorScheme = if (useDark) ShogunDarkColorScheme else ShogunLightColorScheme,
        typography = ShogunTypography,
    ) {
        // Surface pinta el fondo real de la ventana con colorScheme.background;
        // sin esto, Compose Desktop deja el lienzo por defecto (blanco) detrás del contenido.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content,
        )
    }
}
