package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeGradleDaemonSource.Companion.gradleDaemon
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.IOS_SIMULATOR_KEY
import app.luxion.shogunai.domain.model.IosSimulatorProvider
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.SimulatorSettings
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.Worktree
import kotlinx.coroutines.test.runTest
import java.time.Instant
import app.luxion.shogunai.domain.io.AppleSimulators
import app.luxion.shogunai.domain.io.GradleDaemonSource
import app.luxion.shogunai.domain.model.DaemonProcess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseWorktreeResourcesUseCaseTest {

    private val repository = "/home/dev/projects/main-repo"
    private val common = "$repository/.git"
    private val task = "/home/dev/projects/worktrees/TASK-123"
    private val config = ProjectConfig(baseRepositoryPath = repository, worktreesRoot = "/home/dev/projects/worktrees", secretFiles = emptyList())
    private val worktree = Worktree(task, "feature/TASK-123")

    private val git = object : ShellCommandExecutor {
        override suspend fun execute(command: List<String>, workingDirectory: String?) = FakeShellCommandExecutor.success(
            command,
            if (workingDirectory == task) "$common/worktrees/TASK-123\n$common\n" else "$common\n$common\n",
        )
    }

    private fun releaseWith(emulator: EmulatorProvider?, sdk: FakeAndroidSdk): ReleaseWorktreeResourcesUseCase =
        releaseWith(RepositoryProviders(emulator = emulator), sdk)

    private fun releaseWith(
        providers: RepositoryProviders,
        sdk: FakeAndroidSdk = FakeAndroidSdk(),
        simulators: AppleSimulators = FakeAppleSimulators(),
        daemons: GradleDaemonSource = FakeGradleDaemonSource(),
        unreadable: Set<String> = emptySet(),
    ): ReleaseWorktreeResourcesUseCase {
        val source = FakeRepositoryProvisioningSource(
            byRepository = mapOf(repository to RepositoryProvisioning(providers = providers)),
            unreadable = unreadable,
        )
        val acquired = FakeEnvironmentStateStore(mapOf("$common/worktrees/TASK-123" to mapOf("emulator" to 5554)))
        acquired.writeResources("$common/worktrees/TASK-123", mapOf(IOS_SIMULATOR_KEY to "UDID-1"))
        val contributions = MachineProviderContributions(simulators, { SimulatorSettings() }, userHome = "/home/dev")
        val resolve = ResolveWorktreeEnvironmentUseCase(git, source, acquired, FakePortProbe(), contributions, trust = FakeRepositoryTrust(approvesEverything = true))
        return ReleaseWorktreeResourcesUseCase(
            provisioningSource = source,
            readEmulatorStates = ReadEmulatorStatesUseCase(source, resolve, sdk),
            stopEmulator = StopWorktreeEmulatorUseCase(source, resolve, sdk),
            readSimulatorStates = ReadSimulatorStatesUseCase(source, simulators, resolve),
            stopSimulator = StopWorktreeSimulatorUseCase(source, simulators, resolve),
            listDaemons = ListGradleDaemonsUseCase(daemons, FakeFileManager()),
            stopDaemons = StopGradleDaemonsUseCase(daemons, git),
        )
    }

    @Test
    fun `stops the running emulator of the worktree after planning`() = runTest {
        val sdk = FakeAndroidSdk(states = mutableMapOf("emulator-5554" to EmulatorState.RUNNING))
        val release = releaseWith(EmulatorProvider(), sdk)

        val plan = release.plan(config, worktree, listOf(worktree))
        val released = release.release(config, plan)

        assertEquals("emulator-5554", plan.emulatorSerial)
        assertEquals(listOf("emulator-5554"), released.stoppedEmulators)
        assertEquals(listOf("emulator-5554"), sdk.stopped)
        assertTrue(released.failures.isEmpty())
    }

    @Test
    fun `plans nothing for a stopped emulator or a repository without providers`() = runTest {
        val stopped = FakeAndroidSdk(states = mutableMapOf("emulator-5554" to EmulatorState.STOPPED))
        assertNull(releaseWith(EmulatorProvider(), stopped).plan(config, worktree, listOf(worktree)).emulatorSerial)

        val untouched = FakeAndroidSdk()
        val release = releaseWith(null, untouched)
        val released = release.release(config, release.plan(config, worktree, listOf(worktree)))

        assertEquals(ReleasedResources(), released)
        assertEquals(0, untouched.locates)
    }

    @Test
    fun `stops the booted simulator and the idle Gradle daemons of the worktree`() = runTest {
        val simulators = FakeAppleSimulators().also {
            it.devicesByUdid["UDID-1"] = SimulatorDevice("UDID-1", "ShogunAi · main-repo · TASK-123", SimulatorState.BOOTED)
        }
        val daemons = FakeGradleDaemonSource(listOf(gradleDaemon(41, DaemonState.Idle(Instant.EPOCH), lastBuildDirectory = task)))
        val release = releaseWith(RepositoryProviders(iosSimulator = IosSimulatorProvider(), gradle = GradleProvider()), simulators = simulators, daemons = daemons)

        val plan = release.plan(config, worktree, listOf(worktree))
        val released = release.release(config, plan)

        assertEquals("UDID-1", plan.simulatorUdid)
        assertEquals(listOf(41L), plan.idleDaemonPids)
        assertEquals(listOf("UDID-1"), released.stoppedSimulators)
        assertEquals(listOf(41L), released.stoppedDaemons)
        assertTrue(released.failures.isEmpty())
    }

    private val everything = RepositoryProviders(emulator = EmulatorProvider(), iosSimulator = IosSimulatorProvider(), gradle = GradleProvider())

    private val failingSimulators = object : AppleSimulators by FakeAppleSimulators() {
        override suspend fun devices(): List<SimulatorDevice> = throw IllegalStateException("simctl failed")
        override suspend fun shutdown(udid: String) = throw IllegalStateException("simctl failed")
    }

    private val failingDaemons = object : GradleDaemonSource by FakeGradleDaemonSource() {
        override suspend fun list(): List<DaemonProcess> = throw IllegalStateException("jps failed")
    }

    @Test
    fun `a prunable worktree plans nothing`() = runTest {
        assertEquals(WorktreeResourcePlan(), releaseWith(everything).plan(config, worktree.copy(isPrunable = true), listOf(worktree)))
    }

    @Test
    fun `providers that can't be read are reported as the plan's only failure`() = runTest {
        val plan = releaseWith(everything, unreadable = setOf(repository)).plan(config, worktree, listOf(worktree))

        assertTrue(plan.failures.single().startsWith("couldn't read the repository's providers: "))
    }

    @Test
    fun `devices and daemons that can't be read are reported and planned as nothing`() = runTest {
        val plan = releaseWith(everything, FakeAndroidSdk(missing = true), failingSimulators, failingDaemons).plan(config, worktree, listOf(worktree))

        assertEquals(WorktreeResourcePlan(failures = plan.failures), plan)
        assertEquals(
            listOf("couldn't read the emulator: ", "couldn't read the simulator: ", "couldn't list Gradle daemons: "),
            plan.failures.map { it.substringBefore(": ") + ": " },
        )
    }

    @Test
    fun `devices without a state or a simulator shut down are planned as nothing`() = runTest {
        val simulators = FakeAppleSimulators().also {
            it.devicesByUdid["UDID-1"] = SimulatorDevice("UDID-1", "ShogunAi · main-repo · TASK-123", SimulatorState.SHUTDOWN)
        }

        val plan = releaseWith(everything, simulators = simulators).plan(config, Worktree("/home/dev/projects/worktrees/OTHER", "feature/OTHER"), listOf(worktree))

        assertNull(plan.emulatorSerial)
        assertNull(plan.simulatorUdid)
    }

    @Test
    fun `stops that fail are reported and nothing counts as stopped`() = runTest {
        val plan = WorktreeResourcePlan(emulatorSerial = "emulator-5554", simulatorUdid = "UDID-1", idleDaemonPids = listOf(41L))

        val released = releaseWith(everything, FakeAndroidSdk(missing = true), failingSimulators, failingDaemons).release(config, plan)

        assertEquals(ReleasedResources(failures = released.failures), released)
        assertEquals(3, released.failures.size)
    }

    @Test
    fun `a simulator already shut down is planned as nothing`() = runTest {
        val simulators = FakeAppleSimulators().also {
            it.devicesByUdid["UDID-1"] = SimulatorDevice("UDID-1", "ShogunAi · main-repo · TASK-123", SimulatorState.SHUTDOWN)
        }

        assertNull(releaseWith(RepositoryProviders(iosSimulator = IosSimulatorProvider()), simulators = simulators).plan(config, worktree, listOf(worktree)).simulatorUdid)
    }
}
