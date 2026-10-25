package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppWindowService
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeHeadlessAppLauncher
import app.luxion.shogunai.domain.FakeHerdrConfigSource
import app.luxion.shogunai.domain.FakeKmpTestWiring
import app.luxion.shogunai.domain.FakeMachineCapacity
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.AppWindow
import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.DesktopAppProvider
import app.luxion.shogunai.domain.model.MacPermission
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.ResourceSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeadlessCommandsTest {

    private val base = "/home/dev/projects/main-repo"
    private val linked = "/home/dev/projects/worktrees/TASK-123"
    private val common = "$base/.git"
    private val admin = "$common/worktrees/TASK-123"
    private val pidFile = "$linked/desktopApp/build/run/main/main.pid"

    private val porcelain = """
        worktree $base
        HEAD 1111111111111111111111111111111111111111
        branch refs/heads/main

        worktree $linked
        HEAD 2222222222222222222222222222222222222222
        branch refs/heads/feature/TASK-123
    """.trimIndent()

    private val out = StringBuilder()
    private val err = StringBuilder()
    private val files = FakeFileManager(existing = listOf(base, linked))
    private val launcher = FakeHeadlessAppLauncher(files, pidFile)
    private var windows = FakeAppWindowService()

    private val executor = object : ShellCommandExecutor {
        override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
            val outside = workingDirectory?.startsWith("/home/dev/projects") != true
            if (outside) return CommandResult(command, 128, "", "not a git repository")
            val stdout = when {
                command.take(3) == listOf("git", "worktree", "list") -> porcelain
                "--show-toplevel" in command -> "$linked\n"
                "--git-dir" in command -> "$admin\n$common\n"
                "--git-common-dir" in command -> "$common\n"
                else -> ""
            }
            return CommandResult(command, 0, stdout, "")
        }
    }

    private fun provisioning(declared: Boolean = true) = FakeRepositoryProvisioningSource(
        byRepository = mapOf(
            linked to RepositoryProvisioning(
                providers = RepositoryProviders(desktopApp = DesktopAppProvider(":desktopApp").takeIf { declared }),
            ),
        ),
    )

    private fun cli(declared: Boolean = true, workingDirectory: String = linked, input: String = "") = ShogunCli(
        executor = executor,
        fileManager = files,
        projectRepository = FakeProjectRepository(emptyList()),
        provisioningSource = provisioning(declared),
        daemonSource = FakeGradleDaemonSource(),
        gradleUserHome = "/home/dev/.gradle",
        environmentStateStore = FakeEnvironmentStateStore(),
        portProbe = FakePortProbe(),
        androidSdk = FakeAndroidSdk(),
        appleSimulators = FakeAppleSimulators(),
        kmpTestWiring = FakeKmpTestWiring(),
        resourceLimits = FakeResourceLimitsRepository(ResourceSettings(CapacityProfile.PER_WORKTREE)),
        machineCapacity = FakeMachineCapacity(),
        derivedDataSource = FakeDerivedDataSource(),
        herdrConfig = FakeHerdrConfigSource(),
        workingDirectory = workingDirectory,
        out = out,
        err = err,
        readInput = { input },
        canonicalPath = { it.trimEnd('/') },
        environmentVariable = { null },
        headlessLauncher = launcher,
        appWindows = windows,
        pauseWhileStartingApp = {},
    )

    private fun appWindow() {
        windows.windows[launcher.nextPid] = AppWindow(77, isOnScreen = true)
    }

    @Test
    fun `start launches the app hidden and prints its process id`() {
        appWindow()

        assertEquals(0, cli().run(listOf("headless", "start")), err.toString())

        assertEquals("4242 running hidden\n", out.toString())
        assertEquals("$admin/shogunai/headless/home", launcher.launches.single().homePath)
    }

    @Test
    fun `capture to a file prints its path`() {
        appWindow()
        cli().run(listOf("headless", "start"))
        out.clear()

        assertEquals(0, cli().run(listOf("headless", "capture", "--output", "/tmp/example/window.png")), err.toString())

        assertEquals("/tmp/example/window.png\n", out.toString())
        assertEquals("/tmp/example/window.png", windows.captures.single().second)
    }

    @Test
    fun `capture without a running app says to start it first`() {
        assertEquals(1, cli().run(listOf("headless", "capture")))

        assertTrue("shogun headless start" in err.toString(), err.toString())
        assertTrue(windows.captures.isEmpty())
    }

    @Test
    fun `status when stopped prints stopped`() {
        assertEquals(0, cli().run(listOf("headless", "status")))

        assertEquals("stopped", out.lines().first())
    }

    @Test
    fun `status as JSON reports a running hidden app`() {
        appWindow()
        cli().run(listOf("headless", "start"))
        out.clear()

        assertEquals(0, cli().run(listOf("headless", "status", "--json")))

        val json = Json.parseToJsonElement(out.toString().trim()).jsonObject
        assertEquals(1, out.toString().trim().lines().size)
        assertEquals("running", json.getValue("state").jsonPrimitive.content)
        assertEquals("true", json.getValue("hidden").jsonPrimitive.content)
        assertEquals(4242L, json.getValue("pid").jsonPrimitive.long)
    }

    @Test
    fun `stop ends the app`() {
        appWindow()
        cli().run(listOf("headless", "start"))
        out.clear()

        assertEquals(0, cli().run(listOf("headless", "stop")))

        assertEquals("stopped 4242\n", out.toString())
        assertEquals(listOf(4242L), launcher.stopped)
    }

    @Test
    fun `a denied hide warns which permission would hide the window`() {
        windows.granted = setOf(MacPermission.SCREEN_RECORDING)
        appWindow()

        assertEquals(0, cli().run(listOf("headless", "start")))

        assertEquals("4242 running visible\n", out.toString())
        assertTrue("Accessibility" in err.toString(), err.toString())
    }

    @Test
    fun `a repository without the provider is told how to declare it`() {
        assertEquals(1, cli(declared = false).run(listOf("headless", "start")))

        assertTrue("\"desktop-app\": { \"module\": \":desktopApp\" }" in err.toString(), err.toString())
        assertTrue(launcher.launches.isEmpty())
    }

    @Test
    fun `outside a repository it needs a worktree`() {
        assertEquals(1, cli(workingDirectory = "/home/dev/elsewhere").run(listOf("headless", "start")))

        assertTrue("No Git repository" in err.toString(), err.toString())
    }

    @Test
    fun `other platforms are not supported`() {
        windows = FakeAppWindowService(isSupported = false)

        assertEquals(1, cli().run(listOf("headless", "start")))

        assertTrue("only supported on macOS" in err.toString(), err.toString())
        assertTrue(launcher.launches.isEmpty())
    }

    private fun sessionStart(declared: Boolean = true): String {
        assertEquals(0, cli(declared, input = """{"cwd":"$linked","hook_event_name":"SessionStart"}""").run(listOf("hook", "session-start")), err.toString())
        return out.toString()
    }

    @Test
    fun `the session start context tells the agent how to check the desktop app`() {
        val context = sessionStart()

        listOf("shogun headless start", "shogun headless capture", "take_screenshot", "restart", "menu bar").forEach {
            assertTrue(it in context, context)
        }
    }

    @Test
    fun `the session start context has no desktop app line without the provider`() {
        assertFalse("shogun headless" in sessionStart(declared = false))
    }

    @Test
    fun `the session start context has no desktop app line on another platform`() {
        windows = FakeAppWindowService(isSupported = false)

        assertFalse("shogun headless" in sessionStart())
    }

    @Test
    fun `options that don't apply to the action are usage errors`() {
        assertEquals(2, cli().run(listOf("headless", "stop", "--fresh")))
        assertEquals(2, cli().run(listOf("headless", "start", "--output", "x.png")))
        assertEquals(2, cli().run(listOf("headless", "launch")))
        assertFalse(launcher.launches.isNotEmpty())
    }
}
