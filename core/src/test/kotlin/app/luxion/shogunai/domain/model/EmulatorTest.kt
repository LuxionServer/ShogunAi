package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EmulatorTest {

    private val available = listOf("Medium_Phone_API_35", "Pixel_8_API_35")

    @Test
    fun `the requested AVD wins`() {
        assertEquals("Tablet", chooseAvd("Tablet", EmulatorSettings(avd = "Pixel_8_API_35"), "Pixel_8_API_35", available))
    }

    @Test
    fun `the machine setting wins over the repository's preference`() {
        assertEquals(
            "Medium_Phone_API_35",
            chooseAvd(null, EmulatorSettings(avd = "Medium_Phone_API_35"), "Pixel_8_API_35", available),
        )
    }

    @Test
    fun `the repository's preference is used when it exists here`() {
        assertEquals("Pixel_8_API_35", chooseAvd(null, EmulatorSettings(), "Pixel_8_API_35", available))
    }

    @Test
    fun `a preference missing here falls back to the first AVD`() {
        assertEquals("Medium_Phone_API_35", chooseAvd(null, EmulatorSettings(), "Foldable_API_34", available))
    }

    @Test
    fun `no AVD at all`() {
        assertNull(chooseAvd(null, EmulatorSettings(), null, emptyList()))
    }

    @Test
    fun `serial of a console port`() {
        assertEquals("emulator-5556", emulatorSerial(5556))
    }
}
