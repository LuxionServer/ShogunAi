package app.luxion.shogunai.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Paleta pensada para modo oscuro. El modo claro (spec futura "theme-mode-switching")
// añadirá un ShogunLightColorScheme análogo y un parámetro darkTheme aquí; hasta entonces
// ShogunAiTheme aplica siempre este esquema.
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

@Composable
fun ShogunAiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ShogunDarkColorScheme,
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
