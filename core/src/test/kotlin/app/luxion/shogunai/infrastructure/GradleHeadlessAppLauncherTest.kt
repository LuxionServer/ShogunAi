package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.HeadlessLaunch
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GradleHeadlessAppLauncherTest {

    private val root = Files.createTempDirectory("headless-launcher")
    private val worktree = root.resolve("TASK-123").also { Files.createDirectories(it) }
    private val scripts = root.resolve("shogunai")
    private val state = root.resolve("admin/shogunai/headless")

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    private fun launch(home: String? = state.resolve("home").toString()) =
        HeadlessLaunch(worktree.toString(), ":desktopApp", home, state.resolve("launch.log").toString())

    @Test
    fun `runs the module's asynchronous Hot Reload task from the worktree with the init script`() = runTest {
        val executor = FakeShellCommandExecutor { success(it, stdout = "Started 'MainKt' in background (4242)") }

        val launched = GradleHeadlessAppLauncher(executor, scripts.toString()).launch(launch())

        val script = scripts.resolve(GradleHeadlessAppLauncher.INIT_SCRIPT_NAME)
        assertEquals(0, launched.exitCode)
        assertEquals(4242L, launched.pid)
        assertEquals(
            listOf(
                worktree.resolve("gradlew").toString(), "--console=plain", "-I", script.toString(),
                "-Pshogunai.headless.module=:desktopApp", "-Pshogunai.headless.home=${state.resolve("home")}",
                "-Pcompose.reload.devToolsHeadless=true", ":desktopApp:hotRunAsync",
            ),
            executor.executedCommands.single(),
        )
        assertEquals(worktree.toString(), executor.workingDirectories.single())
        assertEquals(GradleHeadlessAppLauncher.INIT_SCRIPT, script.readText())
        assertTrue("Started 'MainKt' in background (4242)" in state.resolve("launch.log").readText())
        assertTrue(state.resolve("home").exists())
    }

    @Test
    fun `the pid comes from the line Hot Reload prints once the app started`() {
        assertEquals(32923L, GradleHeadlessAppLauncher.startedPid("> Task :desktopApp:hotRunAsync\nStarted 'app.example.MainKt' in background (32923)\nBUILD SUCCESSFUL"))
        assertEquals(null, GradleHeadlessAppLauncher.startedPid("BUILD FAILED"))
    }

    @Test
    fun `without an isolated home no home property is passed`() = runTest {
        val executor = FakeShellCommandExecutor()

        GradleHeadlessAppLauncher(executor, scripts.toString()).launch(launch(home = null))

        assertFalse(executor.executedCommands.single().any { it.startsWith("-Pshogunai.headless.home") })
    }

    @Test
    fun `the init script sets the background properties only on the declared module's run task`() {
        val script = GradleHeadlessAppLauncher.INIT_SCRIPT
        assertTrue("path == headlessModule" in script)
        assertTrue("it.name == \"hotRun\"" in script)
        assertTrue("systemProperty(\"apple.awt.UIElement\", \"true\")" in script)
        assertTrue("systemProperty(\"shogunai.headless\", \"true\")" in script)
        assertTrue("systemProperty(\"user.home\", headlessHome)" in script)
    }

    @Test
    fun `the launch log is replaced on each start`() = runTest {
        val launcher = GradleHeadlessAppLauncher(FakeShellCommandExecutor { success(it, stdout = "first") }, scripts.toString())
        launcher.launch(launch())
        GradleHeadlessAppLauncher(FakeShellCommandExecutor { success(it, stdout = "second") }, scripts.toString()).launch(launch())

        assertEquals("second\n", state.resolve("launch.log").readText())
    }

    @Test
    fun `stopping ends the app's process and a gone process is left alone`() = runTest {
        val launcher = GradleHeadlessAppLauncher(FakeShellCommandExecutor { success(it) }, scripts.toString())
        val process = startSleepingProcess()

        assertTrue(launcher.isAlive(process.pid()))
        launcher.stop(process.pid())
        launcher.stop(Long.MAX_VALUE)

        assertFalse(process.isAlive)
        assertFalse(launcher.isAlive(process.pid()))
    }

    @Test
    fun `a process that ignores the stop request is killed after the grace period`() = runTest {
        val launcher = GradleHeadlessAppLauncher(FakeShellCommandExecutor { success(it) }, scripts.toString(), stopGraceSeconds = 0)
        val process = ProcessBuilder("sh", "-c", "trap '' TERM; while true; do sleep 1; done").start()

        launcher.stop(process.pid())

        assertTrue(process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS))
    }

    @Test
    fun `an outdated init script is rewritten`() = runTest {
        val script = scripts.resolve(GradleHeadlessAppLauncher.INIT_SCRIPT_NAME)
        Files.createDirectories(scripts)
        Files.writeString(script, "old")

        GradleHeadlessAppLauncher(FakeShellCommandExecutor { success(it) }, scripts.toString()).launch(launch())
        GradleHeadlessAppLauncher(FakeShellCommandExecutor { success(it) }, scripts.toString()).launch(launch())

        assertEquals(GradleHeadlessAppLauncher.INIT_SCRIPT, script.readText())
    }
}
