package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
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
    private val store = FakeEnvironmentStateStore()
    private var machineSettings = SimulatorSettings()
    private val pauses = mutableListOf<Long>()

    private fun provisioning(provider: IosSimulatorProvider? = IosSimulatorProvider()) = FakeRepositoryProvisioningSource(
        byRepository = mapOf(repository to RepositoryProvisioning(providers = RepositoryProviders(iosSimulator = provider))),
    )

    private fun contributions() = MachineProviderContributions(simulators, { machineSettings })

    private fun resolve(source: FakeRepositoryProvisioningSource = provisioning(), factory: ProviderContributions = contributions()) =
        ResolveWorktreeEnvironmentUseCase(git, source, store, FakePortProbe(), factory)

    private fun start(source: FakeRepositoryProvisioningSource = provisioning(), timeoutMs: Long = 10_000) =
        StartWorktreeSimulatorUseCase(
            source,
            simulators,
            { repositoryPath, worktreePath, requested ->
                resolve(source, contributions().requesting(requested))(repositoryPath, worktreePath)
            },
            pause = { pauses += it },
            timeoutMs = timeoutMs,
        )

    @Test
    fun `each worktree gets its own new simulator, named after it and exported`() = runTest {
        val first = resolve()(repository, task).getOrThrow()
        val again = resolve()(repository, task).getOrThrow()
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

        resolve(provisioning(IosSimulatorProvider(device = "iPhone 16")))(repository, task).getOrThrow()

        assertEquals(
            FakeAppleSimulators.Created("ShogunAi · main-repo · TASK-123", FakeAppleSimulators.IPHONE_16.identifier, FakeAppleSimulators.IOS_18_5.identifier),
            simulators.created.single(),
        )
    }

    @Test
    fun `a simulator deleted outside ShogunAi is created again`() = runTest {
        resolve()(repository, task).getOrThrow()
        simulators.devicesByUdid.clear()

        val environment = resolve()(repository, task).getOrThrow()

        assertEquals(listOf("IOS_SIMULATOR_UDID" to "UDID-2"), environment.variables)
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

        assertIs<WorktreeError.NoIosRuntime>(resolve()(repository, task).exceptionOrNull())
    }

    @Test
    fun `start creates if needed, boots and waits`() = runTest {
        val simulator = start()(config, task, SimulatorSettings(device = "iPhone 16", runtime = "iOS 18.5")).getOrThrow()

        assertEquals(WorktreeSimulator("UDID-1", "ShogunAi · main-repo · TASK-123", SimulatorState.BOOTED), simulator)
        assertEquals(FakeAppleSimulators.IOS_18_5.identifier, simulators.created.single().runtime)
        assertEquals(listOf("UDID-1"), simulators.booted)
        assertTrue(pauses.isNotEmpty())
    }

    @Test
    fun `starting a booted simulator boots nothing and a slow boot times out`() = runTest {
        start()(config, task).getOrThrow()
        start()(config, task).getOrThrow()
        assertEquals(listOf("UDID-1"), simulators.booted)

        val slow = FakeAppleSimulators(bootAfterPolls = 100)
        val slowStart = StartWorktreeSimulatorUseCase(
            provisioning(),
            slow,
            { repositoryPath, worktreePath, _ ->
                ResolveWorktreeEnvironmentUseCase(git, provisioning(), FakeEnvironmentStateStore(), FakePortProbe(), MachineProviderContributions(slow, { SimulatorSettings() }))(repositoryPath, worktreePath)
            },
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
    fun `states are read without creating simulators`() = runTest {
        resolve()(repository, task).getOrThrow()
        val createdBefore = simulators.created.size

        val states = ReadSimulatorStatesUseCase(provisioning(), simulators, resolve())(
            config,
            listOf(Worktree(task, "feature/TASK-123"), Worktree(other, "feature/TASK-456")),
        ).getOrThrow()

        assertEquals(mapOf(task to WorktreeSimulator("UDID-1", "ShogunAi · main-repo · TASK-123", SimulatorState.SHUTDOWN)), states)
        assertEquals(createdBefore, simulators.created.size)
    }

    @Test
    fun `orphans are ShogunAi simulators no worktree has recorded, and they can be deleted`() = runTest {
        resolve()(repository, task).getOrThrow()
        simulators.addExisting("GONE", "ShogunAi · main-repo · TASK-9", SimulatorState.BOOTED)
        simulators.addExisting("MINE", "iPhone 16")
        val delete = DeleteSimulatorsUseCase(provisioning(), simulators, resolve())

        val orphans = delete.orphans(config).getOrThrow()
        assertEquals(listOf("GONE"), orphans.map { it.udid })

        assertEquals(listOf("GONE"), delete(listOf("GONE", "MISSING")).getOrThrow())
        assertEquals(listOf("GONE"), simulators.deleted)
        assertTrue("MINE" in simulators.devicesByUdid)
    }
}
