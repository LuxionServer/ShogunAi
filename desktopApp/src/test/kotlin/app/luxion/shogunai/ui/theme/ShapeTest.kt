package app.luxion.shogunai.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals

class ShapeTest {

    private val density = Density(1f)
    private val size = Size(200f, 100f)

    private fun CornerBasedShape.radius(): Float = topStart.toPx(size, density)

    @Test
    fun `radius scale is 4, 6, 8 and 12`() {
        assertEquals(listOf(4f, 6f, 8f, 12f), listOf(ControlShape, RowShape, PanelShape, DialogShape).map { it.radius() })
    }

    @Test
    fun `material shapes map onto the scale without pills`() {
        with(ShogunShapes) {
            assertEquals(listOf(4f, 6f, 8f, 12f, 12f), listOf(extraSmall, small, medium, large, extraLarge).map { it.radius() })
        }
    }
}
