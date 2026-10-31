package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.IosSimulatorProvider
import app.luxion.shogunai.domain.model.ReleaseRecord
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.Instant
import app.luxion.shogunai.domain.model.ProcessIdentity
import app.luxion.shogunai.domain.model.WaitTicket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResourceLeasesTest {

    private val repository = "/home/dev/projects/main-repo"
    private val common = "$repository/.git"
    private val tasks = (1..5).associate { "/home/dev/projects/worktrees/TASK-$it" to "$common/worktrees/TASK-$it" }
    private val task1 = "/home/dev/projects/worktrees/TASK-1"
    private val task2 = "/home/dev/projects/worktrees/TASK-2"
    private val task3 = "/home/dev/projects/worktrees/TASK-3"
    private val task4 = "/home/dev/projects/worktrees/TASK-4"
    private val admin1 = tasks.getValue(task1)
    private val admin2 = tasks.getValue(task2)
    private val admin3 = tasks.getValue(task3)
    private val admin4 = tasks.getValue(task4)
    private val task5 = "/home/dev/projects/worktrees/TASK-5"

    private val git = object : ShellCommandExecutor {
        override suspend fun execute(command: List<String>, workingDirectory: String?) = FakeShellCommandExecutor.success(
            command,
            "${tasks[workingDirectory] ?: common}\n$common\n",
        )
    }

    private val source = FakeRepositoryProvisioningSource(
        byRepository = mapOf(
            repository to RepositoryProvisioning(
                providers = RepositoryProviders(emulator = EmulatorProvider(), iosSimulator = IosSimulatorProvider()),
            ),
        ),
    )

    private val files = FakeFileManager().also { files ->
        tasks.forEach { (path, admin) -> files.writeText("$admin/gitdir", "$path/.git") }
    }
    private val store = FakeEnvironmentStateStore()
    private val sdk = FakeAndroidSdk(bootAfterPolls = Int.MAX_VALUE)
    private val pool = PoolHarness(git, source, store, sdk, files = files)
    private val expired = pool.now.minus(Duration.ofMinutes(30))

    private fun hold(admin: String, port: Int, state: EmulatorState, leaseRenewedAt: Instant? = expired, lastUsedAt: Instant = expired) {
        store.writePorts(admin, store.readPorts(admin) + ("emulator" to port))
        store.writeLastUsed(admin, mapOf("emulator" to lastUsedAt.toEpochMilli()))
        leaseRenewedAt?.let { store.writeLeases(admin, mapOf("emulator" to it.toEpochMilli())) }
        sdk.states["emulator-$port"] = state
    }

    private fun holderOf(task: String) = pool.readPool.let { runBlockingPool(it) }.holders.single { it.worktreePath == task }

    private fun runBlockingPool(readPool: ReadResourcePoolUseCase) = kotlinx.coroutines.runBlocking { readPool.emulators(repository) }

    @Test
    fun `the lease duration defaults to 20 minutes and rejects non positive values`() {
        assertEquals(Duration.ofMinutes(20), app.luxion.shogunai.domain.model.ResourceSettings().leaseDuration)
        assertEquals(Duration.ofMinutes(20), app.luxion.shogunai.domain.model.ResourceSettings(leaseMinutes = 0).leaseDuration)
        assertEquals(Duration.ofMinutes(35), app.luxion.shogunai.domain.model.ResourceSettings(leaseMinutes = 35).leaseDuration)
        assertEquals(Duration.ofMinutes(20), pool.limits.leaseDuration())
    }

    @Test
    fun `the desktop turn duration defaults to 10 minutes and rejects non positive values`() {
        assertEquals(Duration.ofMinutes(10), app.luxion.shogunai.domain.model.ResourceSettings().desktopTurnDuration)
        assertEquals(Duration.ofMinutes(10), app.luxion.shogunai.domain.model.ResourceSettings(desktopTurnMinutes = 0).desktopTurnDuration)
        assertEquals(Duration.ofMinutes(10), app.luxion.shogunai.domain.model.ResourceSettings(desktopTurnMinutes = -3).desktopTurnDuration)
        pool.settings.settings = pool.settings.settings.copy(desktopTurnMinutes = 15)
        assertEquals(Duration.ofMinutes(15), pool.limits.desktopTurnDuration())
    }

    @Test
    fun `a stored lease duration is the one resolved`() {
        pool.settings.settings = pool.settings.settings.copy(leaseMinutes = 45)

        assertEquals(Duration.ofMinutes(45), pool.limits.leaseDuration())
    }

    @Test
    fun `each state of a device with an expired lease has its release rank`() {
        hold(admin1, 5554, EmulatorState.STOPPED)
        hold(admin2, 5556, EmulatorState.STOPPED)
        hold(admin3, 5558, EmulatorState.RUNNING)
        hold(admin4, 5560, EmulatorState.STOPPED)
        pool.agentStates = mapOf(task2 to AgentState.DONE, task4 to AgentState.BLOCKED)

        assertEquals(1, holderOf(task1).releaseRank)
        assertEquals(2, holderOf(task2).releaseRank)
        assertEquals(3, holderOf(task3).releaseRank)
        assertEquals(4, holderOf(task4).releaseRank)
    }

    @Test
    fun `a working agent a valid lease or a booting device are never releasable`() {
        hold(admin1, 5554, EmulatorState.STOPPED)
        hold(admin2, 5556, EmulatorState.STOPPED, leaseRenewedAt = pool.now.minus(Duration.ofMinutes(5)))
        hold(admin3, 5558, EmulatorState.BOOTING)
        pool.agentStates = mapOf(task1 to AgentState.WORKING)

        assertNull(holderOf(task1).releaseRank)
        assertNull(holderOf(task2).releaseRank)
        assertNull(holderOf(task3).releaseRank)
    }

    @Test
    fun `a reservation without a recorded lease counts as expired`() {
        hold(admin1, 5554, EmulatorState.STOPPED, leaseRenewedAt = null)

        assertEquals(1, holderOf(task1).releaseRank)
    }

    @Test
    fun `the release order follows the agent state before the last use`() = runTest {
        pool.limit(emulators = 3)
        hold(admin1, 5554, EmulatorState.STOPPED, lastUsedAt = expired.minusSeconds(1000))
        hold(admin2, 5556, EmulatorState.STOPPED)
        hold(admin3, 5558, EmulatorState.RUNNING, lastUsedAt = expired.minusSeconds(2000))
        pool.agentStates = mapOf(task1 to AgentState.DONE)

        val first = pool.acquire.emulator(repository, task4).getOrThrow()
        assertEquals(listOf(task2), first.releases.map { it.worktreePath })

        pool.agentStates = pool.agentStates + (task4 to AgentState.WORKING)
        pool.now = pool.now.plus(Duration.ofMinutes(30))
        val second = pool.acquire.emulator(repository, task5).getOrThrow()
        assertEquals(listOf(task1), second.releases.map { it.worktreePath })
    }

    @Test
    fun `a running emulator with an expired lease is stopped and then released`() = runTest {
        pool.limit(emulators = 1)
        hold(admin1, 5554, EmulatorState.RUNNING)

        val acquisition = pool.acquire.emulator(repository, task2).getOrThrow()

        assertEquals(listOf("emulator-5554"), sdk.stopped)
        assertEquals(listOf(task1), acquisition.releases.map { it.worktreePath })
        assertTrue(store.readPorts(admin1).isEmpty())
    }

    @Test
    fun `a blocked agent is released after a running emulator`() = runTest {
        pool.limit(emulators = 2)
        hold(admin1, 5554, EmulatorState.STOPPED)
        hold(admin2, 5556, EmulatorState.RUNNING)
        pool.agentStates = mapOf(task1 to AgentState.BLOCKED)

        val acquisition = pool.acquire.emulator(repository, task3).getOrThrow()

        assertEquals(listOf(task2), acquisition.releases.map { it.worktreePath })
    }

    @Test
    fun `a stopped emulator with a valid lease is not released and the error says when it expires`() = runTest {
        pool.limit(emulators = 1)
        hold(admin1, 5554, EmulatorState.STOPPED, leaseRenewedAt = pool.now.minus(Duration.ofMinutes(2)))

        val error = pool.acquire.emulator(repository, task2).exceptionOrNull()

        assertIs<WorktreeError.ResourcePoolFull>(error)
        assertTrue(error.message.orEmpty().contains("main-repo · TASK-1 (stopped, lease until"), error.message)
        assertTrue(store.readPorts(admin2).isEmpty())
    }

    @Test
    fun `a working agent is named in the error`() = runTest {
        pool.limit(emulators = 1)
        hold(admin1, 5554, EmulatorState.RUNNING)
        pool.agentStates = mapOf(task1 to AgentState.WORKING)

        val error = pool.acquire.emulator(repository, task2).exceptionOrNull()

        assertIs<WorktreeError.ResourcePoolFull>(error)
        assertTrue(error.message.orEmpty().contains("(running, agent working)"), error.message)
        assertTrue(sdk.stopped.isEmpty())
    }

    @Test
    fun `releasing records why on the victim and re-acquiring clears it`() = runTest {
        pool.limit(emulators = 1)
        hold(admin1, 5554, EmulatorState.STOPPED)

        pool.acquire.emulator(repository, task2).getOrThrow()

        val record = store.readReleases(admin1).getValue("emulator")
        assertEquals(ReleaseRecord(pool.now.toEpochMilli(), "main-repo · TASK-2", "emulator-5554"), record)
        assertEquals(record, ReadReleaseRecordUseCase(pool.resolve, store)(task1, ResourceKind.EMULATOR))

        pool.now = pool.now.plus(Duration.ofMinutes(30))
        sdk.states.clear()
        pool.acquire.emulator(repository, task1).getOrThrow()

        assertTrue(store.readReleases(admin1).isEmpty())
        assertNull(ReadReleaseRecordUseCase(pool.resolve, store)(task1, ResourceKind.EMULATOR))
    }

    @Test
    fun `a simulator release is read from its own key and Gradle daemons have none`() = runTest {
        val record = ReleaseRecord(pool.now.toEpochMilli(), "main-repo · TASK-2", "UDID-1")
        store.writeReleases(admin1, mapOf("ios-simulator" to record))

        assertEquals(record, ReadReleaseRecordUseCase(pool.resolve, store)(task1, ResourceKind.IOS_SIMULATOR))
        assertNull(ReadReleaseRecordUseCase(pool.resolve, store)(task1, ResourceKind.GRADLE_DAEMON))
    }

    @Test
    fun `acquiring writes the lease at the current time`() = runTest {
        pool.acquire.emulator(repository, task1).getOrThrow()

        assertEquals(pool.now.toEpochMilli(), store.readLeases(admin1)["emulator"])
    }

    @Test
    fun `a released port is not handed out while another is free`() = runTest {
        pool.limit(emulators = 1)
        hold(admin1, 5554, EmulatorState.STOPPED)

        pool.acquire.emulator(repository, task2).getOrThrow()

        assertEquals(mapOf("emulator" to 5556), store.readPorts(admin2))
    }

    @Test
    fun `a recently released port is used when nothing else is free`() = runTest {
        val busy = PoolHarness(git, source, store, sdk, files = files, portProbe = FakePortProbe(busy = (5556..5585).toSet()))
        busy.limit(emulators = 1)
        hold(admin1, 5554, EmulatorState.STOPPED)

        busy.acquire.emulator(repository, task2).getOrThrow()

        assertEquals(mapOf("emulator" to 5554), store.readPorts(admin2))
    }

    @Test
    fun `a worktree gets its previous port back when it is free`() = runTest {
        pool.limit(emulators = 1)
        hold(admin1, 5554, EmulatorState.STOPPED)
        pool.acquire.emulator(repository, task2).getOrThrow()
        pool.now = pool.now.plus(Duration.ofMinutes(30))
        sdk.states.clear()

        pool.acquire.emulator(repository, task1).getOrThrow()

        assertEquals(mapOf("emulator" to 5554), store.readPorts(admin1))
    }

    @Test
    fun `a port released longer ago than the lease is no longer avoided`() = runTest {
        pool.limit(emulators = 1)
        hold(admin1, 5554, EmulatorState.STOPPED)
        pool.acquire.emulator(repository, task2).getOrThrow()
        pool.now = pool.now.plus(Duration.ofMinutes(30))
        sdk.states.clear()

        pool.acquire.emulator(repository, task3).getOrThrow()

        assertEquals(mapOf("emulator" to 5554), store.readPorts(admin3))
    }

    @Test
    fun `simulators follow the same leases and a booted one is shut down before it is deleted`() = runTest {
        pool.limit(simulators = 1)
        pool.simulators.addExisting("UDID-A", "ShogunAi · main-repo · TASK-1", SimulatorState.BOOTED)
        store.writeResources(admin1, mapOf("ios-simulator" to "UDID-A"))
        store.writeLastUsed(admin1, mapOf("ios-simulator" to expired.toEpochMilli()))
        store.writeLeases(admin1, mapOf("ios-simulator" to pool.now.minus(Duration.ofMinutes(1)).toEpochMilli()))

        assertIs<WorktreeError.ResourcePoolFull>(pool.acquire.simulator(repository, task2).exceptionOrNull())

        pool.now = pool.now.plus(Duration.ofMinutes(25))
        val acquisition = pool.acquire.simulator(repository, task2).getOrThrow()

        assertEquals(listOf(task1), acquisition.releases.map { it.worktreePath })
        assertEquals(listOf("UDID-A"), pool.simulators.deleted)
        assertEquals("main-repo · TASK-2", store.readReleases(admin1).getValue("ios-simulator").forWorktree)
    }

    @Test
    fun `renewing touches only the leases of the devices a worktree holds`() = runTest {
        hold(admin1, 5554, EmulatorState.STOPPED)
        val renew = RenewWorktreeLeasesUseCase(pool.resolve, store, clock = { pool.now })

        renew(task1).getOrThrow()
        renew(task2).getOrThrow()

        assertEquals(mapOf("emulator" to pool.now.toEpochMilli()), store.readLeases(admin1))
        assertEquals(expired.toEpochMilli(), store.readLastUsed(admin1)["emulator"])
        assertTrue(store.readLeases(admin2).isEmpty())
        assertTrue(store.readPorts(admin2).isEmpty())
    }

    @Test
    fun `an unlimited pool still waits behind another worktree in line`() = runTest {
        pool.waitQueue.write(ResourceKind.IOS_SIMULATOR, listOf(WaitTicket("ticket-3", admin3, task3, ProcessIdentity(2000, 5), pool.now.toEpochMilli())))

        val error = assertIs<WorktreeError.ResourcePoolFull>(pool.acquire.simulator(repository, task2).exceptionOrNull())

        assertEquals(0, error.limit)
    }

    @Test
    fun `releases without a previous device don't bring a port back or avoid one`() = runTest {
        store.writeReleases(admin2, mapOf("emulator" to ReleaseRecord(pool.now.toEpochMilli(), "main-repo · TASK-1", null)))
        store.writeReleases(admin3, mapOf("emulator" to ReleaseRecord(pool.now.toEpochMilli(), "main-repo · TASK-1", null)))
        store.writePorts(admin3, emptyMap())

        pool.acquire.emulator(repository, task2).getOrThrow()

        assertEquals(mapOf("emulator" to 5554), store.readPorts(admin2))
    }

    @Test
    fun `without a free port no emulator can be reserved`() = runTest {
        val busy = PoolHarness(git, source, store, sdk, files = files, portProbe = FakePortProbe(busy = (5554..5585).toSet()))

        assertIs<WorktreeError.NoEmulatorPort>(busy.acquire.emulator(repository, task2).exceptionOrNull())
    }

    @Test
    fun `a holder never used is released before one used long ago`() = runTest {
        pool.limit(emulators = 2)
        hold(admin1, 5554, EmulatorState.STOPPED)
        store.writePorts(admin2, mapOf("emulator" to 5556))
        store.writeLeases(admin2, mapOf("emulator" to expired.toEpochMilli()))

        val acquisition = pool.acquire.emulator(repository, task3).getOrThrow()

        assertEquals(listOf(task2), acquisition.releases.map { it.worktreePath })
    }

    @Test
    fun `a simulator that no longer exists is released without being deleted`() = runTest {
        pool.limit(simulators = 1)
        store.writeResources(admin1, mapOf("ios-simulator" to "UDID-GONE"))
        store.writeLeases(admin1, mapOf("ios-simulator" to expired.toEpochMilli()))
        pool.simulators.addExisting("UDID-B", "ShogunAi · main-repo · TASK-3", SimulatorState.SHUTDOWN)
        store.writeResources(admin3, mapOf("ios-simulator" to "UDID-B"))
        store.writeLeases(admin3, mapOf("ios-simulator" to pool.now.toEpochMilli()))

        val result = pool.acquire.simulator(repository, task2)

        assertTrue(pool.simulators.deleted.none { it == "UDID-GONE" }, result.toString())
    }

    @Test
    fun `a worktree whose folders can't be resolved has no release record`() = runTest {
        val broken = object : ShellCommandExecutor {
            override suspend fun execute(command: List<String>, workingDirectory: String?) = FakeShellCommandExecutor.failure(command)
        }
        val resolve = ResolveWorktreeEnvironmentUseCase(broken, source, store, FakePortProbe())

        assertNull(ReadReleaseRecordUseCase(resolve, store)(task1, ResourceKind.EMULATOR))
    }

    @Test
    fun `renewing touches the lease of a held simulator`() = runTest {
        store.writeResources(admin1, mapOf("ios-simulator" to "UDID-A"))

        RenewWorktreeLeasesUseCase(pool.resolve, store, clock = { pool.now })(task1).getOrThrow()

        assertEquals(mapOf("ios-simulator" to pool.now.toEpochMilli()), store.readLeases(admin1))
    }

    @Test
    fun `when every free port was released moments ago the first one is still taken`() = runTest {
        val narrow = PoolHarness(git, source, store, sdk, files = files, portProbe = FakePortProbe(busy = (5556..5585).toSet()))
        store.writeReleases(admin3, mapOf("emulator" to ReleaseRecord(narrow.now.toEpochMilli(), "main-repo · TASK-1", "emulator-5554")))

        narrow.acquire.emulator(repository, task2).getOrThrow()

        assertEquals(mapOf("emulator" to 5554), store.readPorts(admin2))
    }

    @Test
    fun `a previous port that is busy now isn't taken back`() = runTest {
        val narrow = PoolHarness(git, source, store, sdk, files = files, portProbe = FakePortProbe(busy = setOf(5554)))
        store.writeReleases(admin2, mapOf("emulator" to ReleaseRecord(narrow.now.toEpochMilli(), "main-repo · TASK-1", "emulator-5554")))

        narrow.acquire.emulator(repository, task2).getOrThrow()

        assertEquals(mapOf("emulator" to 5556), store.readPorts(admin2))
    }

    @Test
    fun `waiting stops at a failure other than a full pool`() = runTest {
        val busy = PoolHarness(git, source, store, sdk, files = files, portProbe = FakePortProbe(busy = (5554..5585).toSet()))

        assertIs<WorktreeError.NoEmulatorPort>(busy.acquire.emulator(repository, task2, wait = app.luxion.shogunai.domain.model.DeviceWait()).exceptionOrNull())
    }
}
