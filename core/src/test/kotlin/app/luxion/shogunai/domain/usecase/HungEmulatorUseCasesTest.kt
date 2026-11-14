package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.HangingShellCommandExecutor
import app.luxion.shogunai.domain.ScriptedShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.PoolHolderState
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeEmulator
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.infrastructure.LocalAndroidSdk
import java.nio.file.Files
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

class HungEmulatorUseCasesTest {

    private val repository = "/home/dev/projects/main-repo"
    private val common = "$repository/.git"
    private val task = "/home/dev/projects/worktrees/TASK-123"
    private val other = "/home/dev/projects/worktrees/TASK-456"
    private val config = ProjectConfig(baseRepositoryPath = repository, worktreesRoot = "/home/dev/projects/worktrees", secretFiles = emptyList())
    private val logs = Files.createTempDirectory("hung-emulators")

    @AfterTest
    fun cleanUp() {
        logs.toFile().deleteRecursively()
    }

    private val git = object : ScriptedShellCommandExecutor() {
        override suspend fun respond(command: List<String>, workingDirectory: String?) = FakeShellCommandExecutor.success(
            command,
            when (workingDirectory) {
                task -> "$common/worktrees/TASK-123\n$common\n"
                other -> "$common/worktrees/TASK-456\n$common\n"
                else -> "$common\n$common\n"
            },
        )
    }

    private val source = FakeRepositoryProvisioningSource(
        byRepository = mapOf(repository to RepositoryProvisioning(providers = RepositoryProviders(emulator = EmulatorProvider()))),
    )
    private val files = FakeFileManager().also {
        it.writeText("$common/worktrees/TASK-123/gitdir", "$task/.git")
        it.writeText("$common/worktrees/TASK-456/gitdir", "$other/.git")
    }
    private val store = FakeEnvironmentStateStore()

    private val online = mutableSetOf<String>()
    private val hung = mutableSetOf<String>()
    private val adb = HangingShellCommandExecutor(
        hangs = { command -> command.contains("getprop") && hung.any { it in command } },
        responder = { command ->
            when {
                command.contains("devices") ->
                    FakeShellCommandExecutor.success(command, online.joinToString("\n", prefix = "List of devices attached\n") { "$it\tdevice" })
                command.contains("-list-avds") -> FakeShellCommandExecutor.success(command, "Medium_Phone_API_35\n")
                command.contains("getprop") -> FakeShellCommandExecutor.success(command, "1\n")
                else -> FakeShellCommandExecutor.success(command)
            }
        },
    )
    private val sdk = LocalAndroidSdk(
        executor = adb,
        fileManager = FakeFileManager(existing = listOf("/sdk/emulator/emulator")),
        environmentVariable = { if (it == "ANDROID_HOME") "/sdk" else null },
        userHome = "/home/dev",
        osName = "Mac OS X",
        logDirectory = logs,
        launch = { command, _ ->
            online += "emulator-" + command[command.indexOf("-port") + 1]
            FakeAndroidSdk.FakeEmulatorProcess()
        },
    )
    private val harness = PoolHarness(git, source, store, sdk, files = files)

    private fun start(timeoutMs: Long = 10_000) =
        StartWorktreeEmulatorUseCase(source, harness.resolve, harness.acquire, sdk, pause = {}, timeoutMs = timeoutMs)

    private val states get() = ReadEmulatorStatesUseCase(source, harness.resolve, sdk)

    private suspend fun hangAsInANewProcess(serial: String) {
        sdk.stopEmulator(sdk.locate(repository), serial)
        hung += serial
        adb.executedCommands.clear()
    }

    private fun probesOf(serial: String) = adb.executedCommands.count { serial in it && it.contains("getprop") }

    @Test
    fun `a start succeeds while another worktree's emulator doesn't answer`() = runTest {
        start()(config, other).getOrThrow()
        hangAsInANewProcess("emulator-5554")

        val started = start()(config, task).getOrThrow()

        assertEquals(WorktreeEmulator("emulator-5556", EmulatorState.RUNNING), started.device)
        assertEquals(CommandTimeouts.DEVICE_PROBE.inWholeMilliseconds, currentTime)
    }

    @Test
    fun `a worktree's status doesn't ask another worktree's emulator`() = runTest {
        start()(config, other).getOrThrow()
        start()(config, task).getOrThrow()
        hangAsInANewProcess("emulator-5554")

        val status = states(config, listOf(Worktree(task, "feature/TASK-123"))).getOrThrow()

        assertEquals(mapOf(task to WorktreeEmulator("emulator-5556", EmulatorState.RUNNING)), status)
        assertEquals(0, probesOf("emulator-5554"))
        assertEquals(0L, currentTime)
    }

    @Test
    fun `the worktree's own emulator that doesn't answer is reported as unresponsive`() = runTest {
        start()(config, task).getOrThrow()
        hangAsInANewProcess("emulator-5554")

        val status = states(config, listOf(Worktree(task, "feature/TASK-123"))).getOrThrow()

        assertEquals(mapOf(task to WorktreeEmulator("emulator-5554", EmulatorState.UNRESPONSIVE)), status)
    }

    @Test
    fun `a start whose own emulator never answers says so without the adb command`() = runTest {
        hung += "emulator-5554"

        val error = start(timeoutMs = 4_000)(config, task).exceptionOrNull()

        assertIs<WorktreeError.EmulatorUnresponsive>(error)
        assertEquals("emulator-5554", error.serial)
        assertTrue("shogun emulator stop" in error.message)
        assertFalse("getprop" in error.message)
    }

    @Test
    fun `a start waits for its own unresponsive emulator without launching another`() = runTest {
        online += "emulator-5554"
        hung += "emulator-5554"
        var polls = 0
        val useCase = StartWorktreeEmulatorUseCase(source, harness.resolve, harness.acquire, sdk, pause = { if (++polls == 2) hung.clear() })

        val started = useCase(config, task).getOrThrow()

        assertEquals(EmulatorState.RUNNING, started.device.state)
        assertFalse(adb.executedCommands.any { it.contains("-avd") })
    }

    @Test
    fun `the pool reading lists a hung holder as unresponsive next to a running one`() = runTest {
        start()(config, other).getOrThrow()
        start()(config, task).getOrThrow()
        hangAsInANewProcess("emulator-5554")

        val usage = harness.readPool.emulators(repository)

        assertEquals(
            mapOf<String?, PoolHolderState>(other to PoolHolderState.UNRESPONSIVE, task to PoolHolderState.RUNNING),
            usage.holders.associate { it.worktreePath to it.state },
        )
        assertEquals(CommandTimeouts.DEVICE_PROBE.inWholeMilliseconds, currentTime)
    }

    @Test
    fun `an unresponsive holder isn't released to make room`() = runTest {
        harness.limit(emulators = 1)
        start()(config, other).getOrThrow()
        hangAsInANewProcess("emulator-5554")
        harness.now = harness.now.plus(Duration.ofMinutes(21))
        adb.executedCommands.clear()

        val error = start()(config, task).exceptionOrNull()

        assertIs<WorktreeError.ResourcePoolFull>(error)
        assertEquals(listOf(PoolHolderState.UNRESPONSIVE), error.holders.map { it.state })
        assertFalse(adb.executedCommands.any { it.contains("emu") })
    }
}
