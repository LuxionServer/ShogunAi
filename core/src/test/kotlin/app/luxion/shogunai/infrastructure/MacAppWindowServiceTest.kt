package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.AppWindow
import app.luxion.shogunai.domain.model.HideOutcome
import app.luxion.shogunai.domain.model.MacPermission
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MacAppWindowServiceTest {

    private val scripts = Files.createTempDirectory("headless-windows")

    @AfterTest
    fun cleanUp() {
        scripts.toFile().deleteRecursively()
    }

    private val script get() = scripts.resolve(MacAppWindowService.SCRIPT_NAME).toString()

    @Test
    fun `is supported only on macOS`() {
        assertTrue(MacAppWindowService(FakeShellCommandExecutor(), scripts.toString(), osName = "Mac OS X").isSupported)
        assertFalse(MacAppWindowService(FakeShellCommandExecutor(), scripts.toString(), osName = "Linux").isSupported)
    }

    @Test
    fun `finds the largest window of the process with the helper given the pid as an argument`() = runTest {
        val executor = FakeShellCommandExecutor {
            success(it, stdout = """[{"id":1298,"onScreen":true,"width":80,"height":28},{"id":1297,"onScreen":false,"width":1200,"height":800}]""")
        }

        val window = MacAppWindowService(executor, scripts.toString(), osName = "Mac OS X").mainWindow(4242)

        assertEquals(AppWindow(1297, isOnScreen = false), window)
        assertEquals(listOf("osascript", "-l", "JavaScript", script, "windows", "4242"), executor.executedCommands.single())
        assertEquals(MacAppWindowService.SCRIPT, scripts.resolve(MacAppWindowService.SCRIPT_NAME).readText())
    }

    @Test
    fun `no window when the process has none`() {
        assertNull(MacAppWindowService.parseMainWindow("[]"))
    }

    @Test
    fun `hiding succeeds only when the accessibility call returns success`() {
        assertEquals(HideOutcome.HIDDEN, MacAppWindowService.parseHide("""{"code":0}"""))
        assertEquals(HideOutcome.DENIED, MacAppWindowService.parseHide("""{"code":-25211}"""))
    }

    @Test
    fun `reads each permission from the helper`() = runTest {
        val executor = FakeShellCommandExecutor { success(it, stdout = """{"screenRecording":false,"accessibility":true}""") }
        val service = MacAppWindowService(executor, scripts.toString(), osName = "Mac OS X")

        assertFalse(service.isGranted(MacPermission.SCREEN_RECORDING))
        assertTrue(service.isGranted(MacPermission.ACCESSIBILITY))
    }

    @Test
    fun `captures only the window, without shadow or sound`() = runTest {
        val executor = FakeShellCommandExecutor()
        val output = scripts.resolve("captures/window.png").toString()

        MacAppWindowService(executor, scripts.toString(), osName = "Mac OS X").capture(AppWindow(1297, false), output)

        assertEquals(listOf("screencapture", "-x", "-o", "-l", "1297", output), executor.executedCommands.single())
        assertTrue(Files.isDirectory(scripts.resolve("captures")))
    }

    @Test
    fun `a failed capture is reported`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it, stderr = "could not create image from window") }

        val error = assertFailsWith<WorktreeError.MacWindowToolFailed> {
            MacAppWindowService(executor, scripts.toString(), osName = "Mac OS X").capture(AppWindow(1297, false), scripts.resolve("w.png").toString())
        }
        assertTrue("could not create image" in error.detail)
    }
}
