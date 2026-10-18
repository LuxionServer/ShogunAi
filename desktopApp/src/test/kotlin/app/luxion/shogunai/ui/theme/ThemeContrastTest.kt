package app.luxion.shogunai.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeContrastTest {

    private val schemes = listOf(
        Triple("light", ShogunLightColorScheme, LightStatusColors),
        Triple("dark", ShogunDarkColorScheme, DarkStatusColors),
    )

    private fun ColorScheme.surfaces(): Map<String, Color> = mapOf(
        "background" to background,
        "surface" to surface,
        "surfaceContainerLowest" to surfaceContainerLowest,
        "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest,
    )

    private fun ShogunStatusColors.all(): Map<String, StatusColor> =
        Status.entries.associate { it.name to of(it) }

    private fun assertContrast(label: String, foreground: Color, background: Color, minimum: Double) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(ratio >= minimum, "$label has a contrast of %.2f:1, below %.1f:1".format(ratio, minimum))
    }

    @Test
    fun `on-colors reach text contrast on their roles`() {
        schemes.forEach { (name, scheme, _) ->
            listOf(
                Triple("onPrimary", scheme.onPrimary, scheme.primary),
                Triple("onPrimaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer),
                Triple("onSecondary", scheme.onSecondary, scheme.secondary),
                Triple("onSecondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer),
                Triple("onTertiary", scheme.onTertiary, scheme.tertiary),
                Triple("onTertiaryContainer", scheme.onTertiaryContainer, scheme.tertiaryContainer),
                Triple("onError", scheme.onError, scheme.error),
                Triple("onErrorContainer", scheme.onErrorContainer, scheme.errorContainer),
                Triple("onBackground", scheme.onBackground, scheme.background),
                Triple("onSurfaceVariant on surfaceVariant", scheme.onSurfaceVariant, scheme.surfaceVariant),
                Triple("inverseOnSurface", scheme.inverseOnSurface, scheme.inverseSurface),
            ).forEach { (role, foreground, background) ->
                assertContrast("$name $role", foreground, background, TEXT_CONTRAST)
            }
        }
    }

    @Test
    fun `surface text and outlines reach their targets on every surface`() {
        schemes.forEach { (name, scheme, _) ->
            scheme.surfaces().forEach { (surfaceName, surface) ->
                assertContrast("$name onSurface on $surfaceName", scheme.onSurface, surface, TEXT_CONTRAST)
                assertContrast("$name onSurfaceVariant on $surfaceName", scheme.onSurfaceVariant, surface, TEXT_CONTRAST)
                assertContrast("$name outline on $surfaceName", scheme.outline, surface, INDICATOR_CONTRAST)
            }
        }
    }

    @Test
    fun `status colors reach text contrast on every surface`() {
        schemes.forEach { (name, scheme, status) ->
            scheme.surfaces().forEach { (surfaceName, surface) ->
                status.all().forEach { (statusName, colors) ->
                    assertContrast("$name $statusName on $surfaceName", colors.color, surface, TEXT_CONTRAST)
                }
            }
        }
    }

    @Test
    fun `status on-colors reach text contrast on their fills`() {
        schemes.forEach { (name, _, status) ->
            status.all().forEach { (statusName, colors) ->
                assertContrast("$name on $statusName", colors.onColor, colors.color, TEXT_CONTRAST)
                assertContrast("$name on $statusName container", colors.onContainer, colors.container, TEXT_CONTRAST)
            }
        }
    }

    @Test
    fun `contrast ratio follows the WCAG definition`() {
        assertEquals(21.0, contrastRatio(Color.Black, Color.White), 0.01)
        assertEquals(1.0, contrastRatio(Color.White, Color.White), 0.01)
        assertEquals(contrastRatio(Color.Red, Color.White), contrastRatio(Color.White, Color.Red), 0.0001)
    }

    private companion object {
        const val TEXT_CONTRAST = 4.5
        const val INDICATOR_CONTRAST = 3.0
    }
}
