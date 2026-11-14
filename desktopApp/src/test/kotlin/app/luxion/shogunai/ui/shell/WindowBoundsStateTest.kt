package app.luxion.shogunai.ui.shell

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import app.luxion.shogunai.domain.FakeShellPreferenceRepository
import app.luxion.shogunai.domain.model.WindowBounds
import java.awt.Font
import java.awt.Graphics2D
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.HeadlessException
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowBoundsStateTest {

    private val preferences = FakeShellPreferenceRepository()
    private val size = DpSize(1000.dp, 700.dp)

    private class HeadlessEnvironment : GraphicsEnvironment() {
        override fun getScreenDevices(): Array<GraphicsDevice> = throw HeadlessException()
        override fun getDefaultScreenDevice(): GraphicsDevice = throw HeadlessException()
        override fun createGraphics(img: BufferedImage): Graphics2D = img.createGraphics()
        override fun getAllFonts(): Array<Font> = emptyArray()
        override fun getAvailableFontFamilyNames(): Array<String> = emptyArray()
        override fun getAvailableFontFamilyNames(l: Locale): Array<String> = emptyArray()
    }

    @Test
    fun `a floating window saves where it is and its size`() {
        saveWindowBounds(WindowState(position = WindowPosition(40.dp, 60.dp), size = size), preferences)

        assertEquals(WindowBounds(40, 60, 1000, 700), preferences.windowBounds)
    }

    @Test
    fun `a maximized window keeps the floating bounds it had and marks them maximized`() {
        preferences.windowBounds = WindowBounds(40, 60, 1000, 700)

        saveWindowBounds(WindowState(placement = WindowPlacement.Maximized, position = WindowPosition(0.dp, 0.dp), size = size), preferences)

        assertEquals(WindowBounds(40, 60, 1000, 700, maximized = true), preferences.windowBounds)
    }

    @Test
    fun `a window maximized the first time saves where it is as maximized`() {
        saveWindowBounds(WindowState(placement = WindowPlacement.Maximized, position = WindowPosition(0.dp, 0.dp), size = size), preferences)

        assertEquals(WindowBounds(0, 0, 1000, 700, maximized = true), preferences.windowBounds)
    }

    @Test
    fun `a window not placed yet saves nothing`() {
        saveWindowBounds(WindowState(position = WindowPosition(Alignment.Center), size = size), preferences)

        assertNull(preferences.windowBounds)
    }

    @Test
    fun `saved bounds are restored only on a display they overlap`() {
        val screen = listOf(Rectangle(0, 0, 1440, 900))

        assertTrue(isOnAnyDisplay(WindowBounds(40, 60, 1000, 700), screen))
        assertFalse(isOnAnyDisplay(WindowBounds(3000, 60, 1000, 700), screen))
        assertFalse(isOnAnyDisplay(WindowBounds(40, 60, 1000, 700), emptyList()))
    }

    @Test
    fun `a system without displays has no screens`() {
        assertEquals(emptyList(), screenBounds(HeadlessEnvironment()))
    }

    @Test
    fun `the system's displays are the screens`() {
        val screens = screenBounds()
        val first = screens.firstOrNull() ?: Rectangle()

        assertEquals(screens.isNotEmpty(), isOnAnyDisplay(WindowBounds(first.x, first.y, 10, 10)))
    }
}
