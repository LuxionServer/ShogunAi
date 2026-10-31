package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.DeviceWait
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.EmulatorSettings
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeEmulator
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import java.time.Duration
import app.luxion.shogunai.domain.FakePortProbe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorktreeEmulatorUseCasesTest {

    private val repository = "/home/dev/projects/main-repo"
    private val common = "$repository/.git"
    private val task = "/home/dev/projects/worktrees/TASK-123"
    private val other = "/home/dev/projects/worktrees/TASK-456"
    private val config = ProjectConfig(baseRepositoryPath = repository, worktreesRoot = "/home/dev/projects/worktrees", secretFiles = emptyList())

    private val git = object : ShellCommandExecutor {
        override suspend fun execute(command: List<String>, workingDirectory: String?) = FakeShellCommandExecutor.success(
            command,
            when (workingDirectory) {
                task -> "$common/worktrees/TASK-123\n$common\n"
                other -> "$common/worktrees/TASK-456\n$common\n"
                else -> "$common\n$common\n"
            },
        )
    }

    private fun provisioning(emulator: EmulatorProvider? = EmulatorProvider()) = FakeRepositoryProvisioningSource(
        byRepository = mapOf(repository to RepositoryProvisioning(providers = RepositoryProviders(emulator = emulator))),
    )

    private val files = FakeFileManager().also {
        it.writeText("$common/worktrees/TASK-123/gitdir", "$task/.git")
        it.writeText("$common/worktrees/TASK-456/gitdir", "$other/.git")
    }
    private val store = FakeEnvironmentStateStore()
    private val pauses = mutableListOf<Long>()

    private fun harness(sdk: FakeAndroidSdk, source: FakeRepositoryProvisioningSource = provisioning()) =
        PoolHarness(git, source, store, sdk, files = files)

    private fun resolve(source: FakeRepositoryProvisioningSource) = harness(FakeAndroidSdk(), source).resolve

    private fun start(sdk: FakeAndroidSdk, source: FakeRepositoryProvisioningSource = provisioning(), timeoutMs: Long = 10_000): StartWorktreeEmulatorUseCase {
        val harness = harness(sdk, source)
        return StartWorktreeEmulatorUseCase(
            source,
            harness.resolve,
            harness.acquire,
            sdk,
            pause = { pauses += it },
            pollIntervalMs = 2_000,
            timeoutMs = timeoutMs,
        )
    }

    @Test
    fun `starts the chosen AVD read-only on the worktree's port and waits for the boot`() = runTest {
        val sdk = FakeAndroidSdk(bootAfterPolls = 2)

        val emulator = start(sdk)(config, task).getOrThrow().device

        assertEquals(WorktreeEmulator("emulator-5554", EmulatorState.RUNNING), emulator)
        assertEquals(listOf(FakeAndroidSdk.Started("Medium_Phone_API_35", 5554, noWindow = false)), sdk.started)
        assertTrue(pauses.isNotEmpty())
    }

    @Test
    fun `by default the boot is polled with real pauses`() = runTest {
        val sdk = FakeAndroidSdk(bootAfterPolls = 2)
        val harness = harness(sdk)

        val emulator = StartWorktreeEmulatorUseCase(provisioning(), harness.resolve, harness.acquire, sdk)(config, task).getOrThrow().device

        assertEquals(EmulatorState.RUNNING, emulator.state)
    }

    @Test
    fun `fails at once with the log tail when the emulator process exits`() = runTest {
        val sdk = FakeAndroidSdk(bootAfterPolls = 1000)
        sdk.process = FakeAndroidSdk.FakeEmulatorProcess(alive = false, log = "line 1\nPANIC: Broken AVD system path")

        val error = start(sdk, timeoutMs = 180_000)(config, task).exceptionOrNull()

        assertIs<WorktreeError.EmulatorExitedEarly>(error)
        assertEquals("emulator-5554", error.serial)
        assertTrue(error.lastLogLines.endsWith("PANIC: Broken AVD system path"))
        assertTrue(pauses.isEmpty(), "no boot timeout is waited")
    }

    @Test
    fun `an emulator already running isn't started again`() = runTest {
        val sdk = FakeAndroidSdk(states = mutableMapOf("emulator-5554" to EmulatorState.RUNNING))

        start(sdk)(config, task).getOrThrow()

        assertTrue(sdk.started.isEmpty())
    }

    @Test
    fun `an emulator already booting is waited for without starting another`() = runTest {
        val sdk = FakeAndroidSdk(states = mutableMapOf("emulator-5554" to EmulatorState.BOOTING), bootAfterPolls = 5)

        val emulator = start(sdk)(config, task).getOrThrow().device

        assertEquals(WorktreeEmulator("emulator-5554", EmulatorState.RUNNING), emulator)
        assertTrue(sdk.started.isEmpty())
    }

    @Test
    fun `machine settings choose the AVD and the window, a start can override them`() = runTest {
        val sdk = FakeAndroidSdk(avds = listOf("Medium_Phone_API_35", "Pixel_8_API_35"))
        val withSettings = config.copy(emulator = EmulatorSettings(avd = "Pixel_8_API_35", noWindow = true))

        start(sdk, provisioning(EmulatorProvider("Medium_Phone_API_35")))(withSettings, task).getOrThrow()
        start(sdk)(withSettings, other, avd = "Medium_Phone_API_35", noWindow = false).getOrThrow()

        assertEquals(
            listOf(
                FakeAndroidSdk.Started("Pixel_8_API_35", 5554, noWindow = true),
                FakeAndroidSdk.Started("Medium_Phone_API_35", 5556, noWindow = false),
            ),
            sdk.started,
        )
    }

    @Test
    fun `fails without an AVD, without the provider or when the boot times out`() = runTest {
        assertIs<WorktreeError.NoAvdAvailable>(start(FakeAndroidSdk(avds = emptyList()))(config, task).exceptionOrNull())
        assertIs<WorktreeError.EmulatorProviderNotDeclared>(start(FakeAndroidSdk(), provisioning(null))(config, task).exceptionOrNull())

        val slow = FakeAndroidSdk(bootAfterPolls = 100)
        val error = start(slow, timeoutMs = 6_000)(config, task).exceptionOrNull()
        assertIs<WorktreeError.EmulatorBootTimeout>(error)
        assertEquals(EmulatorState.BOOTING, slow.states["emulator-5554"], "a timed out emulator keeps booting")
    }

    @Test
    fun `stops a running emulator and does nothing when stopped or never reserved`() = runTest {
        val source = provisioning()
        val sdk = FakeAndroidSdk(states = mutableMapOf("emulator-5554" to EmulatorState.RUNNING))
        val stop = StopWorktreeEmulatorUseCase(source, resolve(source), sdk)

        assertNull(stop(config, task).getOrThrow(), "not acquired yet")

        start(sdk, source)(config, task).getOrThrow()
        assertEquals(WorktreeEmulator("emulator-5554", EmulatorState.STOPPED), stop(config, task).getOrThrow())
        assertEquals(listOf("emulator-5554"), sdk.stopped)

        stop(config, task).getOrThrow()
        assertEquals(listOf("emulator-5554"), sdk.stopped)
    }

    @Test
    fun `reads states without reserving and without Android tools for other repositories`() = runTest {
        val source = provisioning()
        val sdk = FakeAndroidSdk(states = mutableMapOf("emulator-5554" to EmulatorState.RUNNING))
        start(sdk, source)(config, task).getOrThrow()
        val writes = store.writes

        val states = ReadEmulatorStatesUseCase(source, resolve(source), sdk)(
            config,
            listOf(Worktree(task, "feature/TASK-123"), Worktree(other, "feature/TASK-456")),
        ).getOrThrow()

        assertEquals(mapOf(task to WorktreeEmulator("emulator-5554", EmulatorState.RUNNING)), states)
        assertEquals(writes, store.writes)

        val untouched = FakeAndroidSdk()
        val none = ReadEmulatorStatesUseCase(provisioning(null), resolve(provisioning(null)), untouched)(config, listOf(Worktree(task, "x")))
        assertEquals(emptyMap(), none.getOrThrow())
        assertEquals(0, untouched.locates)
        assertEquals(0, untouched.stateQueries)
    }

    @Test
    fun `the running serial is only given for a running emulator`() = runTest {
        val source = provisioning()
        val sdk = FakeAndroidSdk(states = mutableMapOf("emulator-5554" to EmulatorState.RUNNING))
        val read = ReadEmulatorStatesUseCase(source, resolve(source), sdk)

        assertNull(read.runningSerial(config, task), "not acquired yet")

        start(sdk, source)(config, task).getOrThrow()
        assertEquals("emulator-5554", read.runningSerial(config, task))

        sdk.states.remove("emulator-5554")
        assertNull(read.runningSerial(config, task), "stopped")
        assertNull(ReadEmulatorStatesUseCase(provisioning(null), resolve(provisioning(null)), sdk).runningSerial(config, task))
    }

    @Test
    fun `resolving the environment reserves no port and the first start acquires it`() = runTest {
        val source = provisioning()
        val environment = resolve(source)(repository, task).getOrThrow()

        assertTrue(environment.variables.isEmpty())
        assertTrue(environment.ports.isEmpty())
        assertTrue(store.portsByAdminDirectory.isEmpty())

        val started = start(FakeAndroidSdk(), source)(config, task).getOrThrow()

        assertTrue(started.acquisition.acquiredNow)
        assertEquals(mapOf("emulator" to 5554), store.portsByAdminDirectory["$common/worktrees/TASK-123"])
        assertEquals("emulator-5554", resolve(source)(repository, task).getOrThrow().variables.toMap()["ANDROID_SERIAL"])
    }

    @Test
    fun `a start with the pool full and every emulator running fails and reserves nothing`() = runTest {
        val source = provisioning()
        val sdk = FakeAndroidSdk()
        val harness = harness(sdk, source).also { it.limit(emulators = 1) }
        val startFirst = StartWorktreeEmulatorUseCase(source, harness.resolve, harness.acquire, sdk, pause = {})
        startFirst(config, task).getOrThrow()

        val error = startFirst(config, other).exceptionOrNull()

        assertIs<WorktreeError.ResourcePoolFull>(error)
        assertEquals(1, error.limit)
        assertEquals(listOf(task), error.holders.map { it.worktreePath })
        assertEquals(1, sdk.started.size)
        assertNull(store.portsByAdminDirectory["$common/worktrees/TASK-456"])
    }

    @Test
    fun `a full pool releases the least recently used stopped emulator and reports it`() = runTest {
        val source = provisioning()
        val sdk = FakeAndroidSdk()
        val harness = harness(sdk, source).also { it.limit(emulators = 1) }
        val useCase = StartWorktreeEmulatorUseCase(source, harness.resolve, harness.acquire, sdk, pause = {})
        useCase(config, task).getOrThrow()
        sdk.states.clear()
        harness.now = harness.now.plus(Duration.ofMinutes(21))

        val started = useCase(config, other).getOrThrow()

        assertEquals(listOf(task), started.acquisition.releases.map { it.worktreePath })
        assertNull(store.portsByAdminDirectory["$common/worktrees/TASK-123"])
        assertEquals(mapOf("emulator" to 5556), store.portsByAdminDirectory["$common/worktrees/TASK-456"])
        assertTrue(harness.resolve(repository, task).getOrThrow().variables.isEmpty())
    }

    @Test
    fun `a start that waits gets the emulator once the holder's lease expires`() = runTest {
        val source = provisioning()
        val sdk = FakeAndroidSdk()
        val harness = harness(sdk, source).also { it.limit(emulators = 1) }
        val useCase = StartWorktreeEmulatorUseCase(source, harness.resolve, harness.acquire, sdk, pause = {})
        useCase(config, task).getOrThrow()

        val started = useCase(config, other, wait = DeviceWait()).getOrThrow()

        assertEquals(listOf(task), started.acquisition.releases.map { it.worktreePath })
        assertEquals(1200, started.acquisition.waitedSeconds)
        assertEquals("emulator-5556", started.device.serial)
    }

    @Test
    fun `worktrees whose environment can't be read or that failed to read have no state`() = runTest {
        val source = provisioning()
        val sdk = FakeAndroidSdk(missing = true)
        val broken = object : ShellCommandExecutor {
            override suspend fun execute(command: List<String>, workingDirectory: String?) = FakeShellCommandExecutor.failure(command)
        }
        val unresolvable = ReadEmulatorStatesUseCase(source, ResolveWorktreeEnvironmentUseCase(broken, source, store, FakePortProbe()), sdk)
        start(FakeAndroidSdk(), source)(config, task).getOrThrow()

        assertEquals(emptyMap(), unresolvable(config, listOf(Worktree(task, "feature/TASK-123"))).getOrThrow())
        assertNull(ReadEmulatorStatesUseCase(source, resolve(source), sdk).runningSerial(config, task))
        assertEquals(emptyList(), StopWorktreeEmulatorUseCase(source, resolve(source), sdk).stopSerials(config, emptyList()))
    }
}
