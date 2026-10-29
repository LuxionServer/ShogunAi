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

    private val tokens = listOf(
        Triple("light", ShogunLightColorScheme, LightShogunColors),
        Triple("dark", ShogunDarkColorScheme, DarkShogunColors),
    )

    private val elevations = listOf(
        Triple("light", ShogunLightColorScheme, LightElevation),
        Triple("dark", ShogunDarkColorScheme, DarkElevation),
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
    fun `ink primary carries its label`() {
        tokens.forEach { (name, scheme, _) ->
            assertContrast("$name onPrimary on ink primary", scheme.onPrimary, scheme.primary, TEXT_CONTRAST)
            assertContrast("$name ink primary on background", scheme.primary, scheme.background, TEXT_CONTRAST)
        }
    }

    @Test
    fun `accent reaches link contrast on every surface`() {
        tokens.forEach { (name, scheme, colors) ->
            scheme.surfaces().forEach { (surfaceName, surface) ->
                assertContrast("$name accent on $surfaceName", colors.accent, surface, TEXT_CONTRAST)
            }
            assertContrast("$name onAccent on accent", colors.onAccent, colors.accent, TEXT_CONTRAST)
            assertContrast("$name onAccentContainer on accentContainer", colors.onAccentContainer, colors.accentContainer, TEXT_CONTRAST)
        }
    }

    @Test
    fun `selected row text reaches text contrast on the accent container`() {
        schemes.zip(tokens).forEach { (scheme, token) ->
            val (name, colorScheme, status) = scheme
            val selection = token.third.accentContainer
            assertContrast("$name onSurface on selection", colorScheme.onSurface, selection, TEXT_CONTRAST)
            assertContrast("$name onSurfaceVariant on selection", colorScheme.onSurfaceVariant, selection, TEXT_CONTRAST)
            assertContrast("$name accent on selection", token.third.accent, selection, TEXT_CONTRAST)
            status.all().forEach { (statusName, colors) ->
                assertContrast("$name $statusName on selection", colors.color, selection, TEXT_CONTRAST)
            }
        }
    }

    @Test
    fun `agent icon colors reach indicator contrast where the indicator is shown`() {
        schemes.zip(tokens).forEach { (scheme, token) ->
            val (name, colorScheme, status) = scheme
            mapOf(
                "surfaceContainerLow" to colorScheme.surfaceContainerLow,
                "surfaceContainer" to colorScheme.surfaceContainer,
                "accentContainer" to token.third.accentContainer,
                "secondaryContainer" to colorScheme.secondaryContainer,
                "surface" to colorScheme.surface,
            ).forEach { (surfaceName, surface) ->
                status.all().forEach { (statusName, colors) ->
                    assertContrast("$name agent icon $statusName on $surfaceName", colors.color, surface, INDICATOR_CONTRAST)
                }
            }
        }
    }

    @Test
    fun `disabled content stays legible on every surface and on the disabled container`() {
        tokens.forEach { (name, scheme, colors) ->
            (scheme.surfaces() + ("disabledContainer" to colors.disabledContainer)).forEach { (surfaceName, surface) ->
                assertContrast("$name disabled on $surfaceName", colors.disabledContent, surface, DISABLED_CONTRAST)
            }
            assertTrue(colors.disabledContainer != scheme.primary, "$name disabled container equals the primary fill")
        }
    }

    @Test
    fun `text reaches its targets on every elevation level`() {
        elevations.forEach { (name, scheme, elevation) ->
            elevation.levels().forEach { (levelName, level) ->
                assertContrast("$name onSurface on $levelName", scheme.onSurface, level.container, TEXT_CONTRAST)
                assertContrast("$name onSurfaceVariant on $levelName", scheme.onSurfaceVariant, level.container, TEXT_CONTRAST)
            }
        }
    }

    @Test
    fun `raised levels have a border that separates them`() {
        elevations.forEach { (name, scheme, elevation) ->
            listOf("level1" to elevation.level1, "level2" to elevation.level2).forEach { (levelName, level) ->
                val border = checkNotNull(level.border) { "$name $levelName has no border" }
                assertContrast("$name $levelName border on its container", border, level.container, BORDER_CONTRAST)
                assertContrast("$name $levelName border on background", border, scheme.background, BORDER_CONTRAST)
            }
            assertTrue(elevation.level2.shadow > elevation.level1.shadow, "$name level2 has no shadow above level1")
        }
    }

    private fun ShogunElevation.levels(): Map<String, ElevationLevel> =
        mapOf("level0" to level0, "level1" to level1, "level2" to level2)

    @Test
    fun `contrast ratio follows the WCAG definition`() {
        assertEquals(21.0, contrastRatio(Color.Black, Color.White), 0.01)
        assertEquals(1.0, contrastRatio(Color.White, Color.White), 0.01)
        assertEquals(contrastRatio(Color.Red, Color.White), contrastRatio(Color.White, Color.Red), 0.0001)
    }

    private companion object {
        const val TEXT_CONTRAST = 4.5
        const val INDICATOR_CONTRAST = 3.0
        const val DISABLED_CONTRAST = 3.0
        const val BORDER_CONTRAST = 1.3
    }
}
