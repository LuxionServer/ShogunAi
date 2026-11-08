package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeAppWindowService
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeHeadlessAppLauncher
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.model.AppWindow
import app.luxion.shogunai.domain.model.DesktopAppProvider
import app.luxion.shogunai.domain.model.HeadlessAppState
import app.luxion.shogunai.domain.model.MacPermission
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import app.luxion.shogunai.domain.io.HeadlessAppLauncher
import app.luxion.shogunai.domain.model.HeadlessLaunch
import app.luxion.shogunai.domain.model.HeadlessLaunchResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorktreeHeadlessAppUseCasesTest {

    private val worktree = "/projects/main-repo-worktrees/TASK-123"
    private val admin = "/projects/main-repo/.git/worktrees/TASK-123"
    private val state = "$admin/shogunai/headless"
    private val pidFile = "$worktree/desktopApp/build/run/main/main.pid"
    private val appLog = "$worktree/desktopApp/build/run/main/hotRun.stderr.txt"

    private var provider: DesktopAppProvider? = DesktopAppProvider(":desktopApp")
    private val files = FakeFileManager()
    private val launcher = FakeHeadlessAppLauncher(files, pidFile)
    private var windows = FakeAppWindowService()
    private var pauses = 0

    private fun provisioning() = FakeRepositoryProvisioningSource(
        mapOf(worktree to RepositoryProvisioning(providers = RepositoryProviders(desktopApp = provider))),
    )

    private fun worktrees() = HeadlessAppWorktrees(provisioning(), { admin }, launcher, windows, files)

    private fun start(timeoutMs: Long = 5_000, onPause: () -> Unit = {}) = StartHeadlessAppUseCase(
        worktrees(), launcher, windows, files,
        pause = { pauses++; onPause() },
        pollIntervalMs = 500,
        timeoutMs = timeoutMs,
    )

    private fun appWithWindow(pid: Long = launcher.nextPid) {
        windows.windows[pid] = AppWindow(id = 77, isOnScreen = true)
    }

    private fun running(pid: Long = 4242) {
        files.writtenProperties[pidFile] = mapOf("pid" to pid.toString())
        launcher.alive += pid
        appWithWindow(pid)
    }

    @Test
    fun `starts the module's app with an isolated home and hides it once its window exists`() = runTest {
        appWithWindow()
        windows.windowAfterPolls = 2

        val started = start()(worktree).getOrThrow()

        val launch = launcher.launches.single()
        assertEquals(worktree, launch.worktreePath)
        assertEquals(":desktopApp", launch.module)
        assertEquals("$state/home", launch.homePath)
        assertEquals("$state/launch.log", launch.logPath)
        assertEquals(4242, started.pid)
        assertFalse(started.alreadyRunning)
        assertTrue(started.hidden)
        assertEquals(listOf(4242L), windows.hidden)
        assertEquals(2, pauses)
    }

    @Test
    fun `by default the window is awaited with real pauses`() = runTest {
        appWithWindow()
        windows.windowAfterPolls = 2

        val started = StartHeadlessAppUseCase(worktrees(), launcher, windows, files)(worktree).getOrThrow()

        assertTrue(started.hidden)
    }

    @Test
    fun `the pid printed by the launch is used before the pid file is written`() = runTest {
        launcher.writesPidFile = false
        appWithWindow()

        assertEquals(4242, start()(worktree).getOrThrow().pid)
    }

    @Test
    fun `an app already running is not launched again and is hidden again`() = runTest {
        running()

        val started = start()(worktree).getOrThrow()

        assertTrue(launcher.launches.isEmpty())
        assertTrue(started.alreadyRunning)
        assertTrue(started.hidden)
    }

    @Test
    fun `visible leaves the window shown`() = runTest {
        appWithWindow()

        val started = start()(worktree, visible = true).getOrThrow()

        assertTrue(windows.hidden.isEmpty())
        assertFalse(started.hidden)
    }

    @Test
    fun `fresh empties the isolated home before launching`() = runTest {
        appWithWindow()

        start()(worktree, fresh = true).getOrThrow()

        assertEquals(listOf("$state/home"), files.deletedRecursively)
    }

    @Test
    fun `without an isolated home the app uses the real home and fresh deletes nothing`() = runTest {
        provider = DesktopAppProvider(":apps:desktop", isolatedHome = false)
        val launcher = FakeHeadlessAppLauncher(files, "$worktree/apps/desktop/build/run/main/main.pid")
        windows.windows[launcher.nextPid] = AppWindow(1, true)
        val use = StartHeadlessAppUseCase(
            HeadlessAppWorktrees(provisioning(), { admin }, launcher, windows, files), launcher, windows, files, pause = {},
        )

        use(worktree, fresh = true).getOrThrow()

        assertNull(launcher.launches.single().homePath)
        assertTrue(files.deletedRecursively.isEmpty())
    }

    @Test
    fun `a failed launch reports the end of its log`() = runTest {
        launcher.exitCode = 1
        files.writeText("$state/launch.log", "Task :desktopApp:compileKotlin FAILED\ne: Unresolved reference\n")

        val error = start()(worktree).exceptionOrNull()

        assertIs<WorktreeError.HeadlessAppLaunchFailed>(error)
        assertEquals("$state/launch.log", error.logPath)
        assertTrue("Unresolved reference" in error.lastLogLines)
    }

    @Test
    fun `a launch that leaves no process behind reports the end of its log`() = runTest {
        launcher.startsApp = false
        files.writeText("$state/launch.log", "BUILD SUCCESSFUL\n")

        val error = start()(worktree).exceptionOrNull()

        assertIs<WorktreeError.HeadlessAppLaunchFailed>(error)
        assertEquals("BUILD SUCCESSFUL", error.lastLogLines)
    }

    @Test
    fun `an app that exits before its window fails at once with its log`() = runTest {
        files.writeText(appLog, "Exception in thread main\n")

        val error = start(onPause = { launcher.alive.clear() })(worktree).exceptionOrNull()

        assertIs<WorktreeError.HeadlessAppExitedEarly>(error)
        assertTrue("Exception in thread main" in error.lastLogLines)
        assertEquals(1, pauses)
    }

    @Test
    fun `no window within the timeout fails and leaves the app running`() = runTest {
        val error = start(timeoutMs = 2_000)(worktree).exceptionOrNull()

        assertIs<WorktreeError.HeadlessAppWindowTimeout>(error)
        assertTrue(4242L in launcher.alive)
        assertTrue(launcher.stopped.isEmpty())
    }

    @Test
    fun `a denied hide keeps the app running visible and names Accessibility`() = runTest {
        windows.granted = setOf(MacPermission.SCREEN_RECORDING)
        appWithWindow()

        val started = start()(worktree).getOrThrow()

        assertFalse(started.hidden)
        assertEquals(MacPermission.ACCESSIBILITY, started.missingPermission)
    }

    @Test
    fun `other platforms are not supported and nothing is launched`() = runTest {
        windows = FakeAppWindowService(isSupported = false)

        assertIs<WorktreeError.HeadlessAppsNotSupported>(start()(worktree).exceptionOrNull())
        assertTrue(launcher.launches.isEmpty())
    }

    @Test
    fun `a repository without the provider is told how to declare it`() = runTest {
        provider = null

        assertIs<WorktreeError.DesktopAppProviderNotDeclared>(start()(worktree).exceptionOrNull())
    }

    @Test
    fun `stop ends a running app and succeeds when it is stopped`() = runTest {
        running()
        val stop = StopHeadlessAppUseCase(worktrees(), launcher)

        assertTrue(stop(worktree).getOrThrow().wasRunning)
        assertEquals(listOf(4242L), launcher.stopped)
        assertFalse(stop(worktree).getOrThrow().wasRunning)
        assertEquals(listOf(4242L), launcher.stopped)
    }

    @Test
    fun `status of a running hidden app`() = runTest {
        running()
        windows.windows[4242] = AppWindow(77, isOnScreen = false)

        val status = ReadHeadlessAppUseCase(worktrees(), windows)(worktree).getOrThrow()

        assertEquals(HeadlessAppState.RUNNING, status.state)
        assertEquals(4242L, status.pid)
        assertTrue(status.hidden)
        assertEquals("$state/launch.log", status.paths.logPath)
        assertEquals(MacPermission.entries.toSet(), status.grantedPermissions)
    }

    @Test
    fun `the process state for the views never asks the window service`() = runTest {
        running()
        windows.windows[4242] = AppWindow(77, isOnScreen = false)

        val process = ReadHeadlessAppUseCase(worktrees(), windows).process(worktree).getOrThrow()

        assertEquals(HeadlessAppState.RUNNING, process?.state)
        assertEquals(4242L, process?.pid)
        assertEquals("$state/launch.log", process?.logPath)
        assertEquals(0, windows.queries)
    }

    @Test
    fun `the process state of a crashed app is stopped`() = runTest {
        files.writtenProperties[pidFile] = mapOf("pid" to "4242")

        val process = ReadHeadlessAppUseCase(worktrees(), windows).process(worktree).getOrThrow()

        assertEquals(HeadlessAppState.STOPPED, process?.state)
        assertNull(process?.pid)
    }

    @Test
    fun `a process killed outside ShogunAi is reported as stopped`() = runTest {
        files.writtenProperties[pidFile] = mapOf("pid" to "4242")

        val status = ReadHeadlessAppUseCase(worktrees(), windows)(worktree).getOrThrow()

        assertEquals(HeadlessAppState.STOPPED, status.state)
        assertNull(status.pid)
    }

    @Test
    fun `capture writes the window to a new file in the captures folder`() = runTest {
        running()
        val clock = Clock.fixed(Instant.parse("2026-10-25T10:15:30.123Z"), ZoneOffset.UTC)

        val path = CaptureHeadlessAppUseCase(worktrees(), windows, clock)(worktree).getOrThrow()

        assertEquals("$state/captures/capture-20261025-101530-123.png", path)
        assertEquals(AppWindow(77, true) to path, windows.captures.single())
    }

    @Test
    fun `capture to a given path`() = runTest {
        running()

        val path = CaptureHeadlessAppUseCase(worktrees(), windows)(worktree, "/tmp/example/window.png").getOrThrow()

        assertEquals("/tmp/example/window.png", path)
    }

    @Test
    fun `capture without a running app writes nothing`() = runTest {
        assertIs<WorktreeError.HeadlessAppNotRunning>(CaptureHeadlessAppUseCase(worktrees(), windows)(worktree).exceptionOrNull())
        assertTrue(windows.captures.isEmpty())
    }

    @Test
    fun `capture without Screen Recording names the permission`() = runTest {
        running()
        windows.granted = setOf(MacPermission.ACCESSIBILITY)

        val error = CaptureHeadlessAppUseCase(worktrees(), windows)(worktree).exceptionOrNull()

        assertIs<WorktreeError.MacPermissionMissing>(error)
        assertEquals(MacPermission.SCREEN_RECORDING, error.permission)
        assertTrue("System Settings › Privacy & Security › Screen Recording" in error.message.orEmpty())
        assertTrue(windows.captures.isEmpty())
    }

    @Test
    fun `before removing a worktree its running app is stopped`() = runTest {
        running()

        assertNull(StopHeadlessAppUseCase(worktrees(), launcher).beforeRemoving(worktree))
        assertEquals(listOf(4242L), launcher.stopped)
    }

    @Test
    fun `before removing a worktree without the provider nothing runs`() = runTest {
        provider = null

        assertNull(StopHeadlessAppUseCase(worktrees(), launcher).beforeRemoving(worktree))
        assertTrue(launcher.stopped.isEmpty())
    }

    @Test
    fun `no process state for a repository without the provider`() = runTest {
        provider = null

        assertNull(ReadHeadlessAppUseCase(worktrees(), windows).process(worktree).getOrThrow())
    }

    @Test
    fun `a failed stop before removing is returned as a warning`() = runTest {
        running()
        launcher.failStop = IllegalStateException("operation not permitted")

        val failure = StopHeadlessAppUseCase(worktrees(), launcher).beforeRemoving(worktree)

        assertIs<WorktreeError.HeadlessAppStopFailed>(failure)
    }

    @Test
    fun `a launch without a printed pid uses the pid file`() = runTest {
        val silentLaunch = object : HeadlessAppLauncher by launcher {
            override suspend fun launch(launch: HeadlessLaunch): HeadlessLaunchResult = launcher.launch(launch).copy(pid = null)
        }
        appWithWindow()

        val started = StartHeadlessAppUseCase(worktrees(), silentLaunch, windows, files, pause = { }, pollIntervalMs = 500, timeoutMs = 5_000)(worktree).getOrThrow()

        assertEquals(4242, started.pid)
    }

    @Test
    fun `a running app without a window or a visible one with its window hidden reports it`() = runTest {
        running()
        windows.windows.remove(4242)

        assertFalse(start()(worktree).getOrThrow().hidden)

        windows.windows[4242] = AppWindow(77, isOnScreen = false)
        assertTrue(start()(worktree, visible = true).getOrThrow().hidden)
    }

    @Test
    fun `status of a running app without a window or a shown one`() = runTest {
        running()
        windows.windows.remove(4242)

        val withoutWindow = ReadHeadlessAppUseCase(worktrees(), windows)(worktree).getOrThrow()
        appWithWindow(4242)
        val shown = ReadHeadlessAppUseCase(worktrees(), windows)(worktree).getOrThrow()

        assertFalse(withoutWindow.hasWindow)
        assertFalse(withoutWindow.hidden)
        assertFalse(shown.hidden)
    }

    @Test
    fun `capture of an app without a window fails`() = runTest {
        running()
        windows.windows.remove(4242)

        assertIs<WorktreeError.HeadlessAppHasNoWindow>(CaptureHeadlessAppUseCase(worktrees(), windows)(worktree).exceptionOrNull())
    }

    @Test
    fun `an unreadable pid, a dead process or a missing log mean nothing runs`() = runTest {
        files.writtenProperties[pidFile] = mapOf("pid" to "not a number")
        assertEquals(HeadlessAppState.STOPPED, ReadHeadlessAppUseCase(worktrees(), windows).process(worktree).getOrThrow()?.state)

        files.writtenProperties[pidFile] = mapOf("pid" to "4242")
        assertEquals(HeadlessAppState.STOPPED, ReadHeadlessAppUseCase(worktrees(), windows).process(worktree).getOrThrow()?.state)
        assertEquals("", worktrees().tailOf("/missing.log"))
    }

    @Test
    fun `an app isn't declared where windows aren't supported or the configuration can't be read`() = runTest {
        val unsupported = HeadlessAppWorktrees(provisioning(), { admin }, launcher, FakeAppWindowService(isSupported = false), files)
        val unreadable = HeadlessAppWorktrees(FakeRepositoryProvisioningSource(unreadable = setOf(worktree)), { admin }, launcher, windows, files)

        assertFalse(unsupported.declaresApp(worktree))
        assertFalse(unreadable.declaresApp(worktree))
    }

    @Test
    fun `a stop that fails with a typed error before removing is returned as it is`() = runTest {
        running()
        launcher.failStop = WorktreeError.MacWindowToolFailed("denied")

        assertEquals(launcher.failStop, StopHeadlessAppUseCase(worktrees(), launcher).beforeRemoving(worktree))
    }
}
