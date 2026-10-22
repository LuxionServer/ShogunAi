package app.luxion.shogunai.infrastructure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopAppLauncherTest {

    @Test
    fun `opens the macOS bundle through open`() {
        assertEquals(
            listOf("open", "/Applications/ShogunAi.app"),
            appLaunchCommand("/Applications/ShogunAi.app/Contents/MacOS/ShogunAi", "Mac OS X"),
        )
    }

    @Test
    fun `runs the Windows launcher`() {
        assertEquals(
            listOf("C:\\Program Files\\ShogunAi\\ShogunAi.exe"),
            appLaunchCommand("C:\\Program Files\\ShogunAi\\ShogunAi.exe", "Windows 11"),
        )
    }

    @Test
    fun `runs the Linux launcher`() {
        assertEquals(listOf("/opt/shogunai/bin/ShogunAi"), appLaunchCommand("/opt/shogunai/bin/ShogunAi", "Linux"))
    }

    @Test
    fun `is not available from a Java run`() {
        assertNull(appLaunchCommand("/usr/lib/jvm/java-21/bin/java", "Linux"))
        assertNull(appLaunchCommand("/usr/lib/jvm/java-21/bin/java", "Mac OS X"))
        assertNull(appLaunchCommand("C:\\Java\\bin\\java.exe", "Windows 11"))
        assertNull(appLaunchCommand(null, "Linux"))
    }

    @Test
    fun `reports availability from the running executable`() {
        assertTrue(DesktopAppLauncher({ "/opt/shogunai/bin/ShogunAi" }, "Linux").isAvailable())
        assertFalse(DesktopAppLauncher({ "/usr/bin/java" }, "Linux").isAvailable())
    }
}
