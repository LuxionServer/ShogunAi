package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeDeviceWaitQueue
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeHerdrConfigSource
import app.luxion.shogunai.domain.FakeKmpTestWiring
import app.luxion.shogunai.domain.FakeMachineCapacity
import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeProcessLiveness
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.HangingShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.engine.EngineOptions
import app.luxion.shogunai.engine.ShogunEngine
import app.luxion.shogunai.infrastructure.LocalAndroidSdk
import java.nio.file.Files
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class HungEmulatorCommandsTest {

    private val base = "/home/dev/projects/main-repo"
    private val common = "$base/.git"
    private val task1 = "/home/dev/projects/worktrees/TASK-1"
    private val task2 = "/home/dev/projects/worktrees/TASK-2"
    private val logs = Files.createTempDirectory("hung-emulator-commands")

    @AfterTest
    fun cleanUp() {
        logs.toFile().deleteRecursively()
    }

    private val porcelain = """
        worktree $base
        HEAD 1111111111111111111111111111111111111111
        branch refs/heads/main

        worktree $task1
        HEAD 2222222222222222222222222222222222222222
        branch refs/heads/feature/TASK-1

        worktree $task2
        HEAD 3333333333333333333333333333333333333333
        branch refs/heads/feature/TASK-2
    """.trimIndent()

    private val out = StringBuilder()
    private val err = StringBuilder()
    private val online = mutableSetOf<String>()
    private val hung = mutableSetOf<String>()

    private val executor = HangingShellCommandExecutor(
        hangs = { command -> command.contains("getprop") && hung.any { it in command } },
        waitsForTheLimit = false,
        responder = { command, workingDirectory -> respond(command, workingDirectory) },
    )

    private fun respond(command: List<String>, workingDirectory: String?): CommandResult = when {
        command.first() == "/sdk/platform-tools/adb" && command.contains("devices") ->
            FakeShellCommandExecutor.success(command, online.joinToString("\n", prefix = "List of devices attached\n") { "$it\tdevice" })
        command.first() == "/sdk/platform-tools/adb" -> FakeShellCommandExecutor.success(command, "1\n")
        command.first() == "/sdk/emulator/emulator" -> FakeShellCommandExecutor.success(command, "Medium_Phone_API_35\n")
        workingDirectory?.startsWith("/home/dev/projects") != true -> CommandResult(command, 128, "", "not a git repository")
        else -> FakeShellCommandExecutor.success(command, gitOutput(command, workingDirectory))
    }

    private fun gitOutput(command: List<String>, workingDirectory: String): String {
        val worktree = listOf(task1, task2).firstOrNull { workingDirectory.startsWith(it) }
        val admin = worktree?.let { "$common/worktrees/${it.substringAfterLast('/')}" } ?: common
        return when {
            command.take(3) == listOf("git", "worktree", "list") -> porcelain
            "--show-toplevel" in command -> "${worktree ?: base}\n"
            "--git-dir" in command -> "$admin\n$common\n"
            "--git-common-dir" in command -> "$common\n"
            else -> ""
        }
    }

    private val provisioning = FakeRepositoryProvisioningSource(
        byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(emulator = EmulatorProvider()))),
    )
    private val environmentStore = FakeEnvironmentStateStore()

    private fun androidSdkOfANewProcess() = LocalAndroidSdk(
        executor = executor,
        fileManager = FakeFileManager(existing = listOf("/sdk/emulator/emulator")),
        environmentVariable = { if (it == "ANDROID_HOME") "/sdk" else null },
        userHome = "/home/dev",
        logDirectory = logs,
        launch = { command, _ ->
            online += "emulator-" + command[command.indexOf("-port") + 1]
            FakeAndroidSdk.FakeEmulatorProcess()
        },
    )

    private fun run(vararg args: String, workingDirectory: String = task1): Int = ShogunCli(
        engine = ShogunEngine(
            cliEnginePorts(
                executor = executor,
                fileManager = FakeFileManager(existing = listOf(base, task1, task2)),
                projectRepository = FakeProjectRepository(emptyList()),
                provisioningSource = provisioning,
                gradleDaemons = FakeGradleDaemonSource(),
                gradleUserHome = "/home/dev/.gradle",
                environmentStateStore = environmentStore,
                portProbe = FakePortProbe(),
                androidSdk = androidSdkOfANewProcess(),
                appleSimulators = FakeAppleSimulators(),
                kmpTestWiring = FakeKmpTestWiring(),
                resourceLimits = FakeResourceLimitsRepository(ResourceSettings(CapacityProfile.PER_WORKTREE)),
                machineCapacity = FakeMachineCapacity(),
                derivedData = FakeDerivedDataSource(),
                herdrConfig = FakeHerdrConfigSource(),
                orca = FakeOrcaClient(installed = false),
                workingDirectory = workingDirectory,
                environment = hostEnvironment(),
                deviceWaitQueue = FakeDeviceWaitQueue(),
                processLiveness = FakeProcessLiveness(),
                physicalDevices = emptyList(),
            ),
            EngineOptions(clock = { Instant.parse("2026-01-05T14:00:00Z") }),
        ),
        workingDirectory = workingDirectory,
        out = out,
        err = err,
        canonicalPath = { it.trimEnd('/') },
        environment = hostEnvironment(),
    ).run(args.toList())

    private fun startBothThenHang(serial: String) {
        assertEquals(0, run("emulator", "start", workingDirectory = task2), err.toString())
        assertEquals(0, run("emulator", "start", workingDirectory = task1), err.toString())
        hung += serial
        out.clear()
        err.clear()
        executor.executedCommands.clear()
    }

    @Test
    fun `emulator start succeeds while another worktree's emulator doesn't answer`() {
        assertEquals(0, run("emulator", "start", workingDirectory = task2), err.toString())
        hung += "emulator-5554"
        out.clear()

        assertEquals(0, run("emulator", "start", workingDirectory = task1), err.toString())

        assertEquals("emulator-5556 running\nexport ANDROID_SERIAL=emulator-5556\n", out.toString())
        assertFalse("emulator-5554" in err, err.toString())
    }

    @Test
    fun `emulator status doesn't ask another worktree's emulator`() {
        startBothThenHang("emulator-5554")

        assertEquals(0, run("emulator", "status", workingDirectory = task1), err.toString())

        assertEquals("emulator-5556 running\n", out.toString())
        assertEquals("", err.toString())
        assertFalse(executor.executedCommands.any { "emulator-5554" in it })
    }

    @Test
    fun `emulator status of the worktree's own hung emulator says so and what to do`() {
        startBothThenHang("emulator-5556")

        assertEquals(0, run("emulator", "status", workingDirectory = task1))

        assertEquals("emulator-5556 unresponsive\n", out.toString())
        assertTrue("isn't answering adb" in err && "shogun emulator stop" in err, err.toString())
        assertFalse("getprop" in err, err.toString())
    }

    @Test
    fun `emulator status json reports an unresponsive state`() {
        startBothThenHang("emulator-5556")

        assertEquals(0, run("emulator", "status", "--json", workingDirectory = task1))

        assertEquals("unresponsive", Json.parseToJsonElement(out.toString()).jsonObject.getValue("state").jsonPrimitive.content)
    }

    @Test
    fun `pool status lists a hung emulator as unresponsive next to a running one`() {
        startBothThenHang("emulator-5554")

        assertEquals(0, run("pool", "status", workingDirectory = base), err.toString())

        val lines = out.lines()
        assertTrue(lines.any { it.trim() == "main-repo · TASK-2 unresponsive" }, out.toString())
        assertTrue(lines.any { it.trim() == "main-repo · TASK-1 running" }, out.toString())
    }
}
