package app.luxion.shogunai.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorSchemeTest {

    private val roleGetters = ColorScheme::class.java.methods
        .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == Long::class.javaPrimitiveType }

    private fun ColorScheme.roles(): Map<String, Color> =
        roleGetters.associate { it.name.removePrefix("get").substringBefore('-') to Color((it.invoke(this) as Long).toULong()) }

    @Test
    fun `every color role is covered`() {
        assertEquals(EXPECTED_ROLE_COUNT, roleGetters.size)
    }

    @Test
    fun `no light role keeps Material's baseline value`() {
        assertNoBaselineRoles(ShogunLightColorScheme, lightColorScheme())
    }

    @Test
    fun `no dark role keeps Material's baseline value`() {
        assertNoBaselineRoles(ShogunDarkColorScheme, darkColorScheme())
    }

    private fun assertNoBaselineRoles(scheme: ColorScheme, baseline: ColorScheme) {
        val baselineRoles = baseline.roles()
        val kept = scheme.roles().filter { (role, color) -> color == baselineRoles.getValue(role) && color != Color.White }
        assertTrue(kept.isEmpty(), "Roles left on Material's baseline: ${kept.keys}")
    }

    private companion object {
        const val EXPECTED_ROLE_COUNT = 48
    }
}
