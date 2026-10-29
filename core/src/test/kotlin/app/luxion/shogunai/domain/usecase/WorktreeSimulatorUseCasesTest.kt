package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.DeviceWait
import app.luxion.shogunai.domain.model.IosSimulatorProvider
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.SimulatorDeviceType
import app.luxion.shogunai.domain.model.SimulatorRuntime
import app.luxion.shogunai.domain.model.SimulatorSettings
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.WorktreeSimulator
import kotlinx.coroutines.test.runTest
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorktreeSimulatorUseCasesTest {

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

    private val iphone16Pro = SimulatorDeviceType("com.apple.CoreSimulator.SimDeviceType.iPhone-16-Pro", "iPhone 16 Pro", "iPhone")
    private val ios26 = SimulatorRuntime(
        "com.apple.CoreSimulator.SimRuntime.iOS-26-3",
        "iOS 26.3",
        "26.3",
        "iOS",
        listOf(FakeAppleSimulators.IPHONE_16, iphone16Pro),
    )

    private val simulators = FakeAppleSimulators(installedRuntimes = listOf(FakeAppleSimulators.IOS_18_5, ios26))
    private val files = FakeFileManager().also {
        it.writeText("$common/worktrees/TASK-123/gitdir", "$task/.git")
        it.writeText("$common/worktrees/TASK-456/gitdir", "$other/.git")
    }
    private val store = FakeEnvironmentStateStore()
    private var machineSettings = SimulatorSettings()
    private val pauses = mutableListOf<Long>()

    private fun provisioning(provider: IosSimulatorProvider? = IosSimulatorProvider()) = FakeRepositoryProvisioningSource(
        byRepository = mapOf(repository to RepositoryProvisioning(providers = RepositoryProviders(iosSimulator = provider))),
    )

    private fun harness(source: FakeRepositoryProvisioningSource = provisioning(), fake: FakeAppleSimulators = simulators) =
        PoolHarness(git, source, store, simulators = fake, files = files, simulatorSettings = machineSettings)

    private fun resolve(source: FakeRepositoryProvisioningSource = provisioning()) = harness(source).resolve

    private fun start(source: FakeRepositoryProvisioningSource = provisioning(), timeoutMs: Long = 10_000): StartWorktreeSimulatorUseCase {
        val harness = harness(source)
        return StartWorktreeSimulatorUseCase(
            source,
            simulators,
            harness.acquire,
            harness.resolve,
            pause = { pauses += it },
            timeoutMs = timeoutMs,
        )
    }

    private suspend fun acquire(path: String) = harness().acquire.simulator(repository, path).getOrThrow()

    @Test
    fun `each worktree gets its own new simulator, named after it and exported`() = runTest {
        acquire(task)
        val first = resolve()(repository, task).getOrThrow()
        val again = resolve()(repository, task).getOrThrow()
        acquire(other)
        val second = resolve()(repository, other).getOrThrow()

        assertEquals(listOf("IOS_SIMULATOR_UDID" to "UDID-1"), first.variables)
        assertEquals(first, again)
        assertNotEquals(first.variables, second.variables)
        assertEquals(
            listOf(
                FakeAppleSimulators.Created("ShogunAi · main-repo · TASK-123", FakeAppleSimulators.IPHONE_16.identifier, ios26.identifier),
                FakeAppleSimulators.Created("ShogunAi · main-repo · TASK-456", FakeAppleSimulators.IPHONE_16.identifier, ios26.identifier),
            ),
            simulators.created,
        )
    }

    @Test
    fun `the machine setting and the repository's preference choose the simulator`() = runTest {
        machineSettings = SimulatorSettings(runtime = "iOS 18.5")

        val source = provisioning(IosSimulatorProvider(device = "iPhone 16"))
        harness(source).acquire.simulator(repository, task).getOrThrow()

        assertEquals(
            FakeAppleSimulators.Created("ShogunAi · main-repo · TASK-123", FakeAppleSimulators.IPHONE_16.identifier, FakeAppleSimulators.IOS_18_5.identifier),
            simulators.created.single(),
        )
    }

    @Test
    fun `a simulator deleted outside ShogunAi is created again`() = runTest {
        acquire(task)
        simulators.devicesByUdid.clear()

        assertTrue(resolve()(repository, task).getOrThrow().variables.isEmpty())
        assertEquals(1, simulators.created.size)

        acquire(task)

        assertEquals(listOf("IOS_SIMULATOR_UDID" to "UDID-2"), resolve()(repository, task).getOrThrow().variables)
    }

    @Test
    fun `a new worktree creates no simulator and exports no UDID`() = runTest {
        val environment = resolve()(repository, task).getOrThrow()

        assertTrue(environment.variables.isEmpty())
        assertTrue(simulators.created.isEmpty())
        assertTrue(store.resourcesByAdminDirectory.isEmpty())
    }

    @Test
    fun `nothing iOS happens without the provider or off macOS`() = runTest {
        assertTrue(resolve(provisioning(null))(repository, task).getOrThrow().isEmpty)
        simulators.supported = false
        assertTrue(resolve()(repository, task).getOrThrow().isEmpty)
        assertEquals(0, simulators.calls)

        assertIs<WorktreeError.IosSimulatorsNotSupported>(start()(config, task).exceptionOrNull())
        assertIs<WorktreeError.IosSimulatorProviderNotDeclared>(start(provisioning(null))(config, task).exceptionOrNull())
    }

    @Test
    fun `fails without an iOS runtime`() = runTest {
        simulators.installedRuntimes = emptyList()

        assertIs<WorktreeError.NoIosRuntime>(harness().acquire.simulator(repository, task).exceptionOrNull())
    }

    @Test
    fun `by default the boot is polled with real pauses`() = runTest {
        val harness = harness()

        val simulator = StartWorktreeSimulatorUseCase(provisioning(), simulators, harness.acquire, harness.resolve)(config, task).getOrThrow().device

        assertEquals(SimulatorState.BOOTED, simulator.state)
    }

    @Test
    fun `start creates if needed, boots and waits`() = runTest {
        val simulator = start()(config, task, SimulatorSettings(device = "iPhone 16", runtime = "iOS 18.5")).getOrThrow().device

        assertEquals(WorktreeSimulator("UDID-1", "ShogunAi · main-repo · TASK-123", SimulatorState.BOOTED), simulator)
        assertEquals(FakeAppleSimulators.IOS_18_5.identifier, simulators.created.single().runtime)
        assertEquals(listOf("UDID-1"), simulators.booted)
        assertTrue(pauses.isNotEmpty())
        assertEquals(listOf("UDID-1"), simulators.shown)
    }

    @Test
    fun `starting a booted simulator shows it again`() = runTest {
        start()(config, task).getOrThrow()
        start()(config, task).getOrThrow()

        assertEquals(listOf("UDID-1"), simulators.booted)
        assertEquals(listOf("UDID-1", "UDID-1"), simulators.shown)
    }

    @Test
    fun `a window that can't be opened doesn't fail the start`() = runTest {
        simulators.showFailure = IllegalStateException("Simulator.app not found")

        val simulator = start()(config, task).getOrThrow().device

        assertEquals(SimulatorState.BOOTED, simulator.state)
        assertTrue(simulators.shown.isEmpty())
    }

    @Test
    fun `starting a booted simulator boots nothing and a slow boot times out`() = runTest {
        start()(config, task).getOrThrow()
        start()(config, task).getOrThrow()
        assertEquals(listOf("UDID-1"), simulators.booted)

        val slow = FakeAppleSimulators(bootAfterPolls = 100)
        val slowHarness = PoolHarness(git, provisioning(), FakeEnvironmentStateStore(), simulators = slow)
        val slowStart = StartWorktreeSimulatorUseCase(
            provisioning(),
            slow,
            slowHarness.acquire,
            slowHarness.resolve,
            pause = {},
            timeoutMs = 3_000,
        )
        assertIs<WorktreeError.SimulatorBootTimeout>(slowStart(config, task).exceptionOrNull())
    }

    @Test
    fun `stop shuts down and does nothing without a simulator`() = runTest {
        val stop = StopWorktreeSimulatorUseCase(provisioning(), simulators, resolve())

        assertNull(stop(config, task).getOrThrow())
        start()(config, task).getOrThrow()
        assertEquals(SimulatorState.SHUTDOWN, stop(config, task).getOrThrow()?.state)
        assertEquals(listOf("UDID-1"), simulators.shutDown)
    }

    @Test
    fun `shutting down by UDID skips a simulator that is already shut down`() = runTest {
        val stop = StopWorktreeSimulatorUseCase(provisioning(), simulators, resolve())
        start()(config, task).getOrThrow()

        stop.shutDown("UDID-1")
        stop.shutDown("UDID-1")

        assertEquals(listOf("UDID-1"), simulators.shutDown)
    }

    @Test
    fun `states are read without creating simulators`() = runTest {
        acquire(task)
        val createdBefore = simulators.created.size

        val states = ReadSimulatorStatesUseCase(provisioning(), simulators, resolve())(
            config,
            listOf(Worktree(task, "feature/TASK-123"), Worktree(other, "feature/TASK-456")),
        ).getOrThrow()

        assertEquals(mapOf(task to WorktreeSimulator("UDID-1", "ShogunAi · main-repo · TASK-123", SimulatorState.SHUTDOWN)), states)
        assertEquals(createdBefore, simulators.created.size)
    }

    @Test
    fun `the running UDID is the worktree's simulator unless it is shut down`() = runTest {
        val read = ReadSimulatorStatesUseCase(provisioning(), simulators, resolve())

        assertNull(read.runningUdid(config, task), "not acquired yet")
        start()(config, task).getOrThrow()
        assertEquals("UDID-1", read.runningUdid(config, task))
        simulators.devicesByUdid["UDID-1"] = simulators.devicesByUdid.getValue("UDID-1").copy(state = SimulatorState.BOOTING)
        assertEquals("UDID-1", read.runningUdid(config, task), "booting")
        StopWorktreeSimulatorUseCase(provisioning(), simulators, resolve())(config, task).getOrThrow()
        assertNull(read.runningUdid(config, task), "shut down")
        assertNull(ReadSimulatorStatesUseCase(provisioning(null), simulators, resolve(provisioning(null))).runningUdid(config, task))
    }

    @Test
    fun `orphans are ShogunAi simulators no worktree has recorded, and they can be deleted`() = runTest {
        acquire(task)
        simulators.addExisting("GONE", "ShogunAi · main-repo · TASK-9", SimulatorState.BOOTED)
        simulators.addExisting("MINE", "iPhone 16")
        val delete = DeleteSimulatorsUseCase(provisioning(), simulators, resolve())

        val orphans = delete.orphans(config).getOrThrow()
        assertEquals(listOf("GONE"), orphans.map { it.udid })

        assertEquals(listOf("GONE"), delete(listOf("GONE", "MISSING")).getOrThrow())
        assertEquals(listOf("GONE"), simulators.deleted)
        assertTrue("MINE" in simulators.devicesByUdid)
    }

    @Test
    fun `a full simulator pool deletes the least recently used shut-down simulator`() = runTest {
        val source = provisioning()
        val pool = harness(source).also { it.limit(simulators = 1) }
        val useCase = StartWorktreeSimulatorUseCase(source, simulators, pool.acquire, pool.resolve, pause = {})
        useCase(config, task).getOrThrow()
        simulators.shutdown("UDID-1")
        pool.now = pool.now.plus(Duration.ofMinutes(21))

        val started = useCase(config, other).getOrThrow()

        assertEquals(listOf(task), started.acquisition.releases.map { it.worktreePath })
        assertEquals(listOf("UDID-1"), simulators.deleted)
        assertNull(store.resourcesByAdminDirectory["$common/worktrees/TASK-123"])
        assertTrue(pool.resolve(repository, task).getOrThrow().variables.isEmpty())
        assertEquals("UDID-2", started.device.udid)
    }

    @Test
    fun `a full simulator pool with every simulator booted fails and creates nothing`() = runTest {
        val source = provisioning()
        val pool = harness(source).also { it.limit(simulators = 1) }
        val useCase = StartWorktreeSimulatorUseCase(source, simulators, pool.acquire, pool.resolve, pause = {})
        useCase(config, task).getOrThrow()

        val error = useCase(config, other).exceptionOrNull()

        assertIs<WorktreeError.ResourcePoolFull>(error)
        assertEquals(1, simulators.created.size)
        assertTrue(simulators.deleted.isEmpty())
    }

    @Test
    fun `a start that waits gets a simulator once the holder's lease expires`() = runTest {
        val source = provisioning()
        val pool = harness(source).also { it.limit(simulators = 1) }
        val useCase = StartWorktreeSimulatorUseCase(source, simulators, pool.acquire, pool.resolve, pause = {})
        useCase(config, task).getOrThrow()

        val started = useCase(config, other, wait = DeviceWait()).getOrThrow()

        assertEquals(listOf(task), started.acquisition.releases.map { it.worktreePath })
        assertEquals(1200, started.acquisition.waitedSeconds)
        assertEquals(listOf("UDID-1"), simulators.deleted)
    }
}
