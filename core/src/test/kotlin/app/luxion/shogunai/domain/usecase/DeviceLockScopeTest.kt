package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.ScriptedShellCommandExecutor
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.FIRST_EMULATOR_PORT
import app.luxion.shogunai.domain.model.IosSimulatorProvider
import app.luxion.shogunai.domain.model.LAST_EMULATOR_PORT
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DeviceLockScopeTest {

    private val repository = "/home/dev/projects/main-repo"
    private val common = "$repository/.git"
    private val task1 = "/home/dev/projects/worktrees/TASK-1"
    private val task2 = "/home/dev/projects/worktrees/TASK-2"
    private val task3 = "/home/dev/projects/worktrees/TASK-3"
    private val admin1 = "$common/worktrees/TASK-1"
    private val admin2 = "$common/worktrees/TASK-2"
    private val admin3 = "$common/worktrees/TASK-3"

    private val git = object : ScriptedShellCommandExecutor() {
        override suspend fun respond(command: List<String>, workingDirectory: String?) = FakeShellCommandExecutor.success(
            command,
            when (workingDirectory) {
                task1 -> "$admin1\n$common\n"
                task2 -> "$admin2\n$common\n"
                task3 -> "$admin3\n$common\n"
                else -> "$common\n$common\n"
            },
        )
    }

    private val source = FakeRepositoryProvisioningSource(
        byRepository = mapOf(
            repository to RepositoryProvisioning(
                providers = RepositoryProviders(emulator = EmulatorProvider(), iosSimulator = IosSimulatorProvider()),
            ),
        ),
    )

    private val files = FakeFileManager().also {
        it.writeText("$admin1/gitdir", "$task1/.git")
        it.writeText("$admin2/gitdir", "$task2/.git")
        it.writeText("$admin3/gitdir", "$task3/.git")
    }
    private val store = FakeEnvironmentStateStore()
    private val sdk = FakeAndroidSdk().also { it.lockProbe = { store.held } }
    private val harness = harness(FakePortProbe())
    private val simulators get() = harness.simulators

    private fun harness(portProbe: FakePortProbe) =
        PoolHarness(git, source, store, sdk, files = files, portProbe = portProbe).also { pool ->
            pool.simulators.lockProbe = { store.held }
        }

    private fun expiredLease() = harness.now.minus(Duration.ofMinutes(21)).toEpochMilli()

    private fun validLease() = harness.now.toEpochMilli()

    private fun emulatorOfTask1(state: EmulatorState, lease: Long, port: Int = 5554) {
        store.writePorts(admin1, mapOf("emulator" to port))
        sdk.states["emulator-$port"] = state
        store.writeLeases(admin1, mapOf("emulator" to lease))
    }

    private fun simulatorOfTask1(state: SimulatorState, lease: Long) {
        store.writeResources(admin1, mapOf("ios-simulator" to "UDID-X"))
        simulators.addExisting("UDID-X", "ShogunAi · main-repo · TASK-1", state)
        store.writeLeases(admin1, mapOf("ios-simulator" to lease))
    }

    @Test
    fun `a running emulator released to make room is stopped without holding the lock`() = runTest {
        harness.limit(emulators = 1)
        emulatorOfTask1(EmulatorState.RUNNING, expiredLease())

        val acquisition = harness.acquire.emulator(repository, task2).getOrThrow()

        assertEquals(listOf(task1), acquisition.releases.map { it.worktreePath })
        assertEquals(listOf("emulator-5554"), sdk.stopped)
        assertEquals(5556, store.readPorts(admin2)["emulator"])
        assertTrue(sdk.stateQueries > 0)
        assertEquals(emptyList(), sdk.callsUnderLock)
    }

    @Test
    fun `a simulator is listed, created and its victim deleted without holding the lock`() = runTest {
        harness.limit(simulators = 1)
        simulatorOfTask1(SimulatorState.BOOTED, expiredLease())

        harness.acquire.simulator(repository, task2).getOrThrow()

        assertEquals(listOf("UDID-X"), simulators.shutDown)
        assertEquals(listOf("UDID-X"), simulators.deleted)
        assertEquals(mapOf("ios-simulator" to "UDID-1"), store.readResources(admin2))
        assertEquals(emptyList(), simulators.callsUnderLock)
    }

    @Test
    fun `two worktrees racing for the last emulator never exceed the limit`() = runTest {
        harness.limit(emulators = 2)
        emulatorOfTask1(EmulatorState.RUNNING, validLease())
        var raced: Result<ResourceAcquisition>? = null
        sdk.afterListing = { raced = harness.acquire.emulator(repository, task3) }

        val result = harness.acquire.emulator(repository, task2)

        assertTrue(raced!!.isSuccess)
        assertIs<WorktreeError.ResourcePoolFull>(result.exceptionOrNull())
        assertEquals(setOf(admin1, admin3), store.portsByAdminDirectory.keys)
        assertEquals(2, store.reservedPortsOnMachine(common).size)
    }

    @Test
    fun `an acquisition while a released emulator is still being stopped finds the pool full`() = runTest {
        harness.limit(emulators = 1)
        emulatorOfTask1(EmulatorState.RUNNING, expiredLease())
        var raced: Result<ResourceAcquisition>? = null
        sdk.onStop = { raced = harness.acquire.emulator(repository, task3) }

        harness.acquire.emulator(repository, task2).getOrThrow()

        assertIs<WorktreeError.ResourcePoolFull>(raced!!.exceptionOrNull())
        assertEquals(setOf(admin2), store.portsByAdminDirectory.keys)
    }

    @Test
    fun `a simulator created while another worktree takes the last place is deleted`() = runTest {
        harness.limit(simulators = 1)
        var raced: Result<ResourceAcquisition>? = null
        simulators.onCreate = { raced = harness.acquire.simulator(repository, task3) }

        val result = harness.acquire.simulator(repository, task2)

        assertTrue(raced!!.isSuccess)
        assertIs<WorktreeError.ResourcePoolFull>(result.exceptionOrNull())
        assertEquals(listOf("UDID-1"), simulators.deleted)
        assertEquals(mapOf("ios-simulator" to "UDID-2"), store.readResources(admin3))
        assertEquals(emptyMap(), store.readResources(admin2))
    }

    @Test
    fun `a simulator the same worktree recorded while this one was created is kept`() = runTest {
        simulators.onCreate = { harness.acquire.simulator(repository, task2).getOrThrow() }

        val acquisition = harness.acquire.simulator(repository, task2).getOrThrow()

        assertFalse(acquisition.acquiredNow)
        assertEquals(listOf("UDID-1"), simulators.deleted)
        assertEquals(mapOf("ios-simulator" to "UDID-2"), store.readResources(admin2))
    }

    @Test
    fun `a simulator recorded by another process after the listing is not taken for a deleted one`() = runTest {
        simulators.afterListing = { harness.acquire.simulator(repository, task2).getOrThrow() }

        val acquisition = harness.acquire.simulator(repository, task2).getOrThrow()

        assertFalse(acquisition.acquiredNow)
        assertEquals(1, simulators.created.size)
        assertEquals(emptyList(), simulators.deleted)
        assertEquals(mapOf("ios-simulator" to "UDID-1"), store.readResources(admin2))
    }

    @Test
    fun `an emulator acquisition fails when adb can't be read`() = runTest {
        sdk.missing = true

        val result = harness.acquire.emulator(repository, task2)

        assertIs<WorktreeError.AndroidSdkNotFound>(result.exceptionOrNull())
        assertEquals(emptyMap(), store.readPorts(admin2))
    }

    @Test
    fun `with every port busy only running emulators are not stopped for a port`() = runTest {
        val busy = (FIRST_EMULATOR_PORT..LAST_EMULATOR_PORT + 1).toSet()
        val pool = harness(FakePortProbe(busy))
        emulatorOfTask1(EmulatorState.RUNNING, expiredLease())

        val result = pool.acquire.emulator(repository, task2)

        assertIs<WorktreeError.NoEmulatorPort>(result.exceptionOrNull())
        assertEquals(emptyList(), sdk.stopped)
        assertEquals(mapOf("emulator" to 5554), store.readPorts(admin1))
        assertEquals(emptyMap(), store.readReleases(admin1))
    }

    @Test
    fun `a released emulator that can't be stopped leaves the requester without a port`() = runTest {
        harness.limit(emulators = 1)
        emulatorOfTask1(EmulatorState.RUNNING, expiredLease())
        sdk.stopFailure = IllegalStateException("adb failed")

        val result = harness.acquire.emulator(repository, task2)

        assertEquals("adb failed", result.exceptionOrNull()?.message)
        assertEquals(emptyMap(), store.readPorts(admin2))
        assertEquals(emptyMap(), store.readLeases(admin2))
        assertEquals(emptyMap(), store.readPorts(admin1))
    }

    @Test
    fun `a rollback leaves a reservation written meanwhile by another process`() = runTest {
        harness.limit(emulators = 1)
        emulatorOfTask1(EmulatorState.RUNNING, expiredLease())
        sdk.onStop = { store.writePorts(admin2, mapOf("emulator" to 5570)) }
        sdk.stopFailure = IllegalStateException("adb failed")

        assertTrue(harness.acquire.emulator(repository, task2).isFailure)

        assertEquals(mapOf("emulator" to 5570), store.readPorts(admin2))
    }

    @Test
    fun `an acquisition cancelled while shutting down a released simulator leaves nothing reserved`() = runTest {
        harness.limit(simulators = 1)
        simulatorOfTask1(SimulatorState.BOOTED, expiredLease())
        simulators.shutdownFailure = CancellationException("cancelled")

        assertFailsWith<CancellationException> { harness.acquire.simulator(repository, task2) }

        assertEquals(emptyMap(), store.readResources(admin2))
        assertEquals(listOf("UDID-1"), simulators.deleted)
        assertTrue("UDID-X" in simulators.devicesByUdid)
    }

    @Test
    fun `a simulator that can't be created records nothing and releases nobody`() = runTest {
        harness.limit(simulators = 1)
        simulatorOfTask1(SimulatorState.SHUTDOWN, expiredLease())
        simulators.createFailure = IllegalStateException("simctl failed")

        val result = harness.acquire.simulator(repository, task2)

        assertEquals("simctl failed", result.exceptionOrNull()?.message)
        assertEquals(emptyMap(), store.readResources(admin2))
        assertEquals(mapOf("ios-simulator" to "UDID-X"), store.readResources(admin1))
        assertEquals(emptyList(), simulators.deleted)
    }

    @Test
    fun `a rollback after another process released the reservation changes nothing`() = runTest {
        harness.limit(emulators = 1)
        emulatorOfTask1(EmulatorState.RUNNING, expiredLease())
        sdk.onStop = { store.release(admin2, "emulator") }
        sdk.stopFailure = IllegalStateException("adb failed")

        assertTrue(harness.acquire.emulator(repository, task2).isFailure)

        assertEquals(emptyMap(), store.readPorts(admin2))
    }

    @Test
    fun `a released simulator deleted outside ShogunAi is only removed from the reservations`() = runTest {
        harness.limit(simulators = 1)
        simulatorOfTask1(SimulatorState.SHUTDOWN, expiredLease())
        simulators.devicesByUdid.remove("UDID-X")

        val acquisition = harness.acquire.simulator(repository, task2).getOrThrow()

        assertEquals(listOf(task1), acquisition.releases.map { it.worktreePath })
        assertEquals(emptyList(), simulators.deleted)
        assertEquals(emptyMap(), store.readResources(admin1))
    }

    @Test
    fun `the pool of a kind reads the same holders as its own reading`() = runTest {
        emulatorOfTask1(EmulatorState.RUNNING, validLease())

        assertEquals(harness.readPool.emulators(repository), harness.readPool.of(ResourceKind.EMULATOR, repository))
    }
}
