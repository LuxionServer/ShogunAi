package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeBuildTurnStore
import app.luxion.shogunai.domain.FakeDeviceWaitQueue
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeForegroundCommandRunner
import app.luxion.shogunai.domain.FakeProcessLiveness
import app.luxion.shogunai.domain.io.BuildTurnStore
import app.luxion.shogunai.domain.model.BuildSlot
import app.luxion.shogunai.domain.model.BuildTurnError
import app.luxion.shogunai.domain.model.BuildWait
import app.luxion.shogunai.domain.model.BuildWaitProgress
import app.luxion.shogunai.domain.model.ProcessIdentity
import app.luxion.shogunai.domain.model.WaitQueueName
import app.luxion.shogunai.domain.model.WaitTicket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuildTurnUseCasesTest {

    private val common = "/home/dev/projects/main-repo/.git"
    private fun worktree(id: String) = "/home/dev/projects/worktrees/$id"
    private fun admin(id: String) = "$common/worktrees/$id"

    private var now: Instant = Instant.parse("2026-01-05T14:00:00Z")
    private val onSleep = mutableListOf<() -> Unit>()
    private val store = FakeBuildTurnStore()
    private val queue = FakeDeviceWaitQueue()
    private val liveness = FakeProcessLiveness()
    private var limit: Int? = 1
    private var ids = 0
    private val ledger = BuildTurnLedger(
        store,
        FakeEnvironmentStateStore(),
        queue,
        liveness,
        adminDirectoryOf = { path -> admin(path.substringAfterLast('/')) },
        limit = { limit },
        clock = { now },
        sleep = { pause ->
            now += pause
            onSleep.removeFirstOrNull()?.invoke()
        },
        newId = { "slot-${++ids}" },
    )
    private val acquire = AcquireBuildSlotUseCase(ledger)
    private val release = ReleaseBuildSlotUseCase(ledger)
    private val read = ReadBuildTurnUseCase(ledger)
    private val gradleTest = listOf("./gradlew", "test")

    private fun running(id: String, process: ProcessIdentity = ProcessIdentity(2000, 1)) =
        BuildSlot("running-$id", admin(id), worktree(id), "./gradlew build", now.toEpochMilli(), process)
            .also { store.slots = store.slots + it }

    private fun waiter(id: String, process: ProcessIdentity = ProcessIdentity(3000, 1)) =
        WaitTicket("ticket-$id", admin(id), worktree(id), process, now.toEpochMilli())

    @Test
    fun `a free slot is taken at once by the current process`() = runTest {
        val acquisition = acquire(worktree("TASK-1"), gradleTest).getOrThrow()

        assertEquals(
            BuildSlot("slot-1", admin("TASK-1"), worktree("TASK-1"), "./gradlew test", now.toEpochMilli(), liveness.currentProcess),
            acquisition.slot,
        )
        assertEquals(0, acquisition.waitedSeconds)
        assertEquals(listOf(acquisition.slot), store.slots)
    }

    @Test
    fun `each build of a worktree holds its own slot`() = runTest {
        limit = 2

        acquire(worktree("TASK-1"), gradleTest).getOrThrow()
        acquire(worktree("TASK-1"), gradleTest).getOrThrow()

        assertEquals(listOf("slot-1", "slot-2"), store.slots.map { it.id })
    }

    @Test
    fun `with no limit every build gets a slot and is still recorded`() = runTest {
        limit = null
        running("TASK-1")
        queue.write(WaitQueueName.BUILD, listOf(waiter("TASK-2")))

        acquire(worktree("TASK-3"), gradleTest).getOrThrow()

        assertEquals(2, store.slots.size)
    }

    @Test
    fun `a waiter gets the slot once the running build ends`() = runTest {
        val holder = running("TASK-1")
        onSleep += { store.slots = emptyList() }
        val progress = mutableListOf<BuildWaitProgress>()

        val acquisition = acquire(worktree("TASK-2"), gradleTest, BuildWait(onProgress = { progress += it })).getOrThrow()

        assertEquals(admin("TASK-2"), acquisition.slot.adminDirectory)
        assertEquals(10, acquisition.waitedSeconds)
        assertEquals(listOf(BuildWaitProgress(1, 1, 1, listOf(holder))), progress)
        assertTrue(queue.read(WaitQueueName.BUILD).isEmpty())
    }

    @Test
    fun `a slot whose process died counts as free`() = runTest {
        val dead = ProcessIdentity(2000, 1)
        running("TASK-1", dead)
        liveness.dead += dead

        acquire(worktree("TASK-2"), gradleTest).getOrThrow()

        assertEquals(listOf(admin("TASK-2")), store.slots.map { it.adminDirectory })
    }

    @Test
    fun `a build doesn't take a free slot ahead of an earlier waiter`() = runTest {
        queue.write(WaitQueueName.BUILD, listOf(waiter("TASK-2")))
        onSleep += { queue.write(WaitQueueName.BUILD, queue.read(WaitQueueName.BUILD).filterNot { it.id == "ticket-TASK-2" }) }
        val progress = mutableListOf<BuildWaitProgress>()

        acquire(worktree("TASK-4"), gradleTest, BuildWait(onProgress = { progress += it })).getOrThrow()

        assertEquals(listOf(2), progress.map { it.position })
        assertEquals(listOf(admin("TASK-4")), store.slots.map { it.adminDirectory })
    }

    @Test
    fun `the wait times out naming the running builds and leaves the queue`() = runTest {
        val holder = running("TASK-1")
        queue.write(WaitQueueName.BUILD, listOf(waiter("TASK-3")))

        val error = acquire(worktree("TASK-2"), gradleTest, BuildWait(Duration.ofMinutes(5))).exceptionOrNull()

        val timedOut = assertIs<BuildTurnError.WaitTimedOut>(error)
        assertEquals(Duration.ofMinutes(5), timedOut.waited)
        assertEquals(listOf(holder), timedOut.running)
        assertEquals("Waited 5 min for a build slot; The only build slot is taken by main-repo · TASK-1", timedOut.message)
        assertEquals(listOf("ticket-TASK-3"), queue.read(WaitQueueName.BUILD).map { it.id })
        assertEquals(listOf(holder), store.slots)
    }

    @Test
    fun `an unreadable slots file fails without waiting`() = runTest {
        val broken = object : BuildTurnStore {
            override fun read(): List<BuildSlot> = throw IllegalStateException("unreadable")
            override fun write(slots: List<BuildSlot>) = Unit
        }
        val failing = BuildTurnLedger(broken, FakeEnvironmentStateStore(), queue, liveness, { admin("TASK-1") }, { 1 }, { now }, { error("no wait") })

        val error = AcquireBuildSlotUseCase(failing)(worktree("TASK-1"), gradleTest).exceptionOrNull()

        assertEquals("unreadable", error?.message)
        assertTrue(queue.read(WaitQueueName.BUILD).isEmpty())
    }

    @Test
    fun `releasing frees only that slot`() = runTest {
        limit = 2
        val first = acquire(worktree("TASK-1"), gradleTest).getOrThrow().slot
        val second = acquire(worktree("TASK-2"), gradleTest).getOrThrow().slot

        release(first.id).getOrThrow()
        release("unknown").getOrThrow()

        assertEquals(listOf(second), store.slots)
    }

    @Test
    fun `the state lists the limit, the live builds and the live waiters`() = runTest {
        val dead = ProcessIdentity(4000, 1)
        val holder = running("TASK-1")
        running("TASK-5", dead)
        val ticket = waiter("TASK-2")
        queue.write(WaitQueueName.BUILD, listOf(ticket, waiter("TASK-6", dead)))
        liveness.dead += dead

        val state = read().getOrThrow()

        assertEquals(1, state.limit)
        assertEquals(listOf(holder), state.running)
        assertEquals(listOf(ticket), state.waiting)
        assertEquals(listOf(holder), store.slots)
    }

    @Test
    fun `a run takes a slot, runs the command where asked and frees the slot`() = runTest {
        val runner = FakeForegroundCommandRunner(exitCode = 3, whileRunning = { assertEquals(1, store.slots.size) })
        val run = RunInBuildTurnUseCase(acquire, release, runner)

        val result = run(worktree("TASK-1"), "${worktree("TASK-1")}/app", gradleTest).getOrThrow()

        assertEquals(BuildRun(3, 0), result)
        assertEquals(listOf(gradleTest to "${worktree("TASK-1")}/app"), runner.runs)
        assertTrue(store.slots.isEmpty())
    }

    @Test
    fun `a command that can't start frees its slot`() = runTest {
        val run = RunInBuildTurnUseCase(acquire, release, FakeForegroundCommandRunner(exitCode = null))

        val result = run(worktree("TASK-1"), worktree("TASK-1"), listOf("does-not-exist")).getOrThrow()

        assertNull(result.exitCode)
        assertTrue(store.slots.isEmpty())
    }

    @Test
    fun `an interrupted build frees its slot`() = runTest {
        val runner = FakeForegroundCommandRunner(whileRunning = { throw CancellationException("interrupted") })
        val run = RunInBuildTurnUseCase(acquire, release, runner)

        runCatching { run(worktree("TASK-1"), worktree("TASK-1"), gradleTest) }

        assertTrue(store.slots.isEmpty())
    }

    @Test
    fun `a run that never got a slot runs nothing`() = runTest {
        running("TASK-1")
        val runner = FakeForegroundCommandRunner()
        val run = RunInBuildTurnUseCase(acquire, release, runner)

        val error = run(worktree("TASK-2"), worktree("TASK-2"), gradleTest, BuildWait(Duration.ofMinutes(1))).exceptionOrNull()

        assertIs<BuildTurnError.WaitTimedOut>(error)
        assertTrue(runner.runs.isEmpty())
    }
}
