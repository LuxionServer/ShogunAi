package app.luxion.shogunai.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun `counts inside sentences use Geist with tabular figures`() {
        assertStyle(ShogunDataTextStyles.figures, Geist, FontWeight.Normal, 12f, 16f)
        assertEquals("tnum", ShogunDataTextStyles.figures.fontFeatureSettings)
    }

    @Test
    fun `title and label roles follow the v2 scale`() {
        with(ShogunDataTextStyles) {
            assertStyle(viewTitle, Geist, FontWeight.SemiBold, 16f, 22f)
            assertStyle(sectionTitle, Geist, FontWeight.SemiBold, 13f, 18f)
            assertStyle(eyebrow, GeistMono, FontWeight.Medium, 10.5f, 14f)
            assertEquals(0.06f, eyebrow.letterSpacing.value, 0.001f)
            assertTrue(eyebrow.letterSpacing.isEm)
        }
    }

    @Test
    fun `body and secondary text keep their sizes`() {
        assertStyle(ShogunTypography.bodyMedium, Geist, FontWeight.Normal, 13f, 18f)
        assertStyle(ShogunTypography.bodySmall, Geist, FontWeight.Normal, 12f, 16f)
    }

    private fun assertStyle(style: TextStyle, family: FontFamily, weight: FontWeight, size: Float, lineHeight: Float) {
        assertEquals(family, style.fontFamily)
        assertEquals(weight, style.fontWeight)
        assertEquals(size, style.fontSize.value, 0.001f)
        assertEquals(lineHeight, style.lineHeight.value, 0.001f)
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
