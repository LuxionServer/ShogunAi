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
    fun `hides the app through the helper`() = runTest {
        val executor = FakeShellCommandExecutor { success(it, stdout = """{"code":0}""") }

        assertEquals(HideOutcome.HIDDEN, MacAppWindowService(executor, scripts.toString(), osName = "Mac OS X").hide(4242))
        assertEquals("hide", executor.executedCommands.single()[4])
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

    @Test
    fun `missing or non boolean fields read as off screen, zero sized, denied or not granted`() = runTest {
        assertEquals(AppWindow(0, isOnScreen = false), MacAppWindowService.parseMainWindow("""[{"onScreen":"yes"}]"""))
        assertEquals(AppWindow(7, isOnScreen = false), MacAppWindowService.parseMainWindow("""[{"id":7,"width":"x"}]"""))
        assertNull(MacAppWindowService.parseMainWindow("{}"))
        assertEquals(HideOutcome.DENIED, MacAppWindowService.parseHide("{}"))
        assertEquals(HideOutcome.DENIED, MacAppWindowService.parseHide("""{"code":"x"}"""))
        val executor = FakeShellCommandExecutor { success(it, stdout = """{"accessibility":"yes"}""") }
        val service = MacAppWindowService(executor, scripts.toString(), osName = "Mac OS X")
        assertFalse(service.isGranted(MacPermission.SCREEN_RECORDING))
        assertFalse(service.isGranted(MacPermission.ACCESSIBILITY))
    }

    @Test
    fun `the helper script is written once and rewritten when it changes, and its failure is reported`() = runTest {
        val service = MacAppWindowService(FakeShellCommandExecutor { success(it, stdout = "[]") }, scripts.toString(), osName = "Mac OS X")

        service.mainWindow(1)
        service.mainWindow(1)
        scripts.resolve(MacAppWindowService.SCRIPT_NAME).toFile().writeText("old")
        service.mainWindow(1)

        assertEquals(MacAppWindowService.SCRIPT, scripts.resolve(MacAppWindowService.SCRIPT_NAME).readText())
        val failing = MacAppWindowService(FakeShellCommandExecutor { failure(it, stderr = "denied") }, scripts.toString(), osName = "Mac OS X")
        assertEquals("denied", assertFailsWith<WorktreeError.MacWindowToolFailed> { failing.hide(1) }.detail)
    }

    @Test
    fun `a capture that fails silently reports its output`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it, stderr = " ").copy(stdout = "no window") }

        val error = assertFailsWith<WorktreeError.MacWindowToolFailed> {
            MacAppWindowService(executor, scripts.toString(), osName = "Mac OS X").capture(AppWindow(1, false), scripts.resolve("w.png").toString())
        }
        assertEquals("no window", error.detail)
    }

    @Test
    fun `a window with a size that isn't a number counts as empty`() {
        assertEquals(AppWindow(2, isOnScreen = false), MacAppWindowService.parseMainWindow("""[{"id":1,"width":"x","height":5},{"id":2,"width":2,"height":2}]"""))
    }
}
