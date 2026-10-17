package app.luxion.shogunai.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals

class TypographyTest {

    @Test
    fun `every typography slot uses Geist`() {
        val slots = with(ShogunTypography) {
            mapOf(
                "displayLarge" to displayLarge, "displayMedium" to displayMedium, "displaySmall" to displaySmall,
                "headlineLarge" to headlineLarge, "headlineMedium" to headlineMedium, "headlineSmall" to headlineSmall,
                "titleLarge" to titleLarge, "titleMedium" to titleMedium, "titleSmall" to titleSmall,
                "bodyLarge" to bodyLarge, "bodyMedium" to bodyMedium, "bodySmall" to bodySmall,
                "labelLarge" to labelLarge, "labelMedium" to labelMedium, "labelSmall" to labelSmall,
            )
        }
        slots.forEach { (name, style) -> assertEquals(Geist, style.fontFamily, "$name doesn't use Geist") }
    }

    @Test
    fun `data styles use Geist Mono with tabular figures`() {
        listOf(ShogunDataTextStyles.data, ShogunDataTextStyles.dataSmall).forEach { style ->
            assertEquals(GeistMono, style.fontFamily)
            assertEquals("tnum", style.fontFeatureSettings)
        }
    }

    @Test
    fun `every bundled font is on the classpath`() {
        listOf("Geist", "GeistMono").forEach { family ->
            listOf("Regular", "Medium", "SemiBold").forEach { weight ->
                val path = "fonts/$family-$weight.ttf"
                checkNotNull(javaClass.classLoader.getResource(path)) { "$path is missing" }
            }
        }
        checkNotNull(javaClass.classLoader.getResource("fonts/OFL.txt")) { "The font license is missing" }
    }
}
