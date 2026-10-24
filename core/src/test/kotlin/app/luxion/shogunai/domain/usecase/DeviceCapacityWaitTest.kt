package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.DeviceWait
import app.luxion.shogunai.domain.model.DeviceWaitProgress
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.IosSimulatorProvider
import app.luxion.shogunai.domain.model.ProcessIdentity
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.WaitTicket
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceCapacityWaitTest {

    private val repository = "/home/dev/projects/main-repo"
    private val common = "$repository/.git"
    private val task1 = "/home/dev/projects/worktrees/TASK-1"
    private val task2 = "/home/dev/projects/worktrees/TASK-2"
    private val task3 = "/home/dev/projects/worktrees/TASK-3"
    private val admin1 = "$common/worktrees/TASK-1"
    private val admin2 = "$common/worktrees/TASK-2"
    private val admin3 = "$common/worktrees/TASK-3"

    private val git = object : ShellCommandExecutor {
        override suspend fun execute(command: List<String>, workingDirectory: String?) = FakeShellCommandExecutor.success(
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
    private val sdk = FakeAndroidSdk()
    private val harness = PoolHarness(git, source, store, sdk, files = files)
    private val otherProcess = ProcessIdentity(2000, 5)

    private fun runningEmulatorOfTask1(leaseSeconds: Long) {
        harness.limit(emulators = 1)
        store.writePorts(admin1, mapOf("emulator" to 5554))
        sdk.states["emulator-5554"] = EmulatorState.RUNNING
        val leaseStart = harness.now.minus(Duration.ofMinutes(20)).plusSeconds(leaseSeconds)
        store.writeLeases(admin1, mapOf("emulator" to leaseStart.toEpochMilli()))
    }

    private fun waiterTask3(): WaitTicket =
        WaitTicket("ticket-3", admin3, task3, otherProcess, harness.now.toEpochMilli())
            .also { harness.waitQueue.write(ResourceKind.EMULATOR, listOf(it)) }

    @Test
    fun `waits until a lease expires, then releases it and acquires`() = runTest {
        runningEmulatorOfTask1(leaseSeconds = 25)

        val acquisition = harness.acquire.emulator(repository, task2, DeviceWait()).getOrThrow()

        assertTrue(acquisition.acquiredNow)
        assertEquals(listOf(task1), acquisition.releases.map { it.worktreePath })
        assertEquals(listOf("emulator-5554"), sdk.stopped)
        assertEquals(26, acquisition.waitedSeconds)
        assertEquals(listOf(10L, 10L, 6L), harness.pauses.map { it.seconds })
        assertTrue(harness.waitQueue.read(ResourceKind.EMULATOR).isEmpty())
    }

    @Test
    fun `with room the wait acquires at once`() = runTest {
        val acquisition = harness.acquire.emulator(repository, task2, DeviceWait()).getOrThrow()

        assertEquals(0, acquisition.waitedSeconds)
        assertTrue(harness.pauses.isEmpty())
    }

    @Test
    fun `a worktree that holds its device doesn't wait even with a full pool`() = runTest {
        runningEmulatorOfTask1(leaseSeconds = 600)

        val acquisition = harness.acquire.emulator(repository, task1, DeviceWait()).getOrThrow()

        assertEquals(false, acquisition.acquiredNow)
        assertTrue(harness.pauses.isEmpty())
    }

    @Test
    fun `without a wait the result has no waiting time`() = runTest {
        assertNull(harness.acquire.emulator(repository, task2).getOrThrow().waitedSeconds)
    }

    @Test
    fun `gives up after the timeout, leaving the queue and reserving nothing`() = runTest {
        runningEmulatorOfTask1(leaseSeconds = 0)
        harness.agentStates = mapOf(task1 to AgentState.WORKING)

        val error = harness.acquire.emulator(repository, task2, DeviceWait(Duration.ofMinutes(5))).exceptionOrNull()

        assertIs<WorktreeError.DeviceWaitTimedOut>(error)
        assertEquals(Duration.ofMinutes(5), error.waited)
        assertEquals(listOf(task1), error.holders.map { it.worktreePath })
        assertTrue(error.message.orEmpty().startsWith("Waited 5 min for an emulator"))
        assertTrue(store.readPorts(admin2).isEmpty())
        assertTrue(harness.waitQueue.read(ResourceKind.EMULATOR).isEmpty())
    }

    @Test
    fun `waiters are served in order and each wait reports its place`() = runTest {
        runningEmulatorOfTask1(leaseSeconds = 5)
        waiterTask3()
        val progress = mutableListOf<DeviceWaitProgress>()
        harness.onPause = { if (harness.pauses.size == 3) harness.liveness.dead += otherProcess }

        val acquisition = harness.acquire.emulator(repository, task2, DeviceWait(onProgress = { progress += it })).getOrThrow()

        assertTrue(acquisition.acquiredNow)
        assertEquals(listOf(2, 2, 2), progress.map { it.position })
        assertEquals(listOf(2, 2, 2), progress.map { it.waiting })
        assertTrue(progress.first().nextLeaseExpiry != null)
        assertNull(progress.last().nextLeaseExpiry)
        assertTrue(harness.waitQueue.read(ResourceKind.EMULATOR).isEmpty())
    }

    @Test
    fun `a start without waiting can't take capacity another worktree waits for`() = runTest {
        runningEmulatorOfTask1(leaseSeconds = 0)
        waiterTask3()

        val error = harness.acquire.emulator(repository, task2).exceptionOrNull()

        assertIs<WorktreeError.ResourcePoolFull>(error)
        assertEquals(1, error.waiting)
        assertTrue(error.message.orEmpty().contains("1 worktree is waiting for one"))
        assertTrue(sdk.stopped.isEmpty())
        assertEquals(mapOf("emulator" to 5554), store.readPorts(admin1))
    }

    @Test
    fun `the holder of a device restarts it while others wait`() = runTest {
        runningEmulatorOfTask1(leaseSeconds = 600)
        waiterTask3()

        assertEquals(false, harness.acquire.emulator(repository, task1).getOrThrow().acquiredNow)
    }

    @Test
    fun `a dead waiter is dropped and doesn't block`() = runTest {
        harness.limit(emulators = 1)
        waiterTask3()
        harness.liveness.dead += otherProcess

        assertTrue(harness.acquire.emulator(repository, task2).getOrThrow().acquiredNow)
        assertTrue(harness.waitQueue.read(ResourceKind.EMULATOR).isEmpty())
    }

    @Test
    fun `an interrupted wait leaves the queue`() = runTest {
        runningEmulatorOfTask1(leaseSeconds = 600)
        harness.onPause = { throw CancellationException("interrupted") }

        val error = harness.acquire.emulator(repository, task2, DeviceWait()).exceptionOrNull()

        assertIs<CancellationException>(error)

        assertTrue(harness.waitQueue.read(ResourceKind.EMULATOR).isEmpty())
    }

    @Test
    fun `the pool-full error points to waiting`() = runTest {
        runningEmulatorOfTask1(leaseSeconds = 600)

        val error = harness.acquire.emulator(repository, task2).exceptionOrNull()

        assertIs<WorktreeError.ResourcePoolFull>(error)
        assertEquals(0, error.waiting)
        assertTrue(error.message.orEmpty().endsWith("Wait for one with --wait, stop one, or raise the limit in Preferences"))
    }
}
