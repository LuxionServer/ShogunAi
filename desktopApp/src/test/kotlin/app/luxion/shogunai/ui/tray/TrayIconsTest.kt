package app.luxion.shogunai.ui.tray

import app.luxion.shogunai.ui.isMacOs
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrayIconsTest {

    private fun BufferedImage.opaqueColors(): Set<Int> = buildSet {
        for (x in 0 until width) for (y in 0 until height) {
            val argb = getRGB(x, y)
            if (argb ushr 24 == 0xFF) add(argb and 0xFFFFFF)
        }
    }

    private fun BufferedImage.alphaAt(x: Double, y: Double): Int = getRGB((x * width).toInt(), (y * height).toInt()) ushr 24

    @Test
    fun `a dark macOS menu bar gets a white template helmet with the crest cut out`() {
        val image = trayIconImage(darkMenuBar = true, mac = true)

        assertEquals(44, image.width)
        assertEquals(setOf(Color.WHITE.rgb and 0xFFFFFF), image.opaqueColors())
        assertEquals(0xFF, image.alphaAt(0.4, 0.45))
        assertTrue(image.alphaAt(0.49, 0.45) < 0xFF)
    }

    @Test
    fun `a light macOS menu bar gets a black template helmet`() {
        val image = trayIconImage(darkMenuBar = false, mac = true)

        assertEquals(setOf(0), image.opaqueColors())
    }

    @Test
    fun `the icon follows the system the app runs on`() {
        assertEquals(if (isMacOs) 44 else 64, trayIconImage(darkMenuBar = false).width)
    }

    @Test
    fun `other systems get a larger colored helmet with a red crest`() {
        val image = trayIconImage(darkMenuBar = true, mac = false)

        assertEquals(64, image.width)
        val colors = image.opaqueColors()
        assertTrue(0x3F4E5F in colors)
        assertTrue(0xC0392B in colors)
        assertEquals(0xC0392B, image.getRGB(32, 16) and 0xFFFFFF)
    }
}
