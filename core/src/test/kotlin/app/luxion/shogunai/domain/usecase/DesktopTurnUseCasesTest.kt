package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeDesktopTurnStore
import app.luxion.shogunai.domain.FakeDeviceWaitQueue
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeProcessLiveness
import app.luxion.shogunai.domain.model.DesktopTurn
import app.luxion.shogunai.domain.model.DesktopTurnRelease
import app.luxion.shogunai.domain.model.DesktopWait
import app.luxion.shogunai.domain.model.DesktopWaitProgress
import app.luxion.shogunai.domain.model.ProcessIdentity
import app.luxion.shogunai.domain.model.WaitQueueName
import app.luxion.shogunai.domain.model.WaitTicket
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopTurnUseCasesTest {

    private val common = "/home/dev/projects/main-repo/.git"
    private fun worktree(id: String) = "/home/dev/projects/worktrees/$id"
    private fun admin(id: String) = "$common/worktrees/$id"

    private var now: Instant = Instant.parse("2026-01-05T14:00:00Z")
    private val onSleep = mutableListOf<() -> Unit>()
    private val store = FakeDesktopTurnStore()
    private val queue = FakeDeviceWaitQueue()
    private val liveness = FakeProcessLiveness()
    private var minutes = 10L
    private val ledger = DesktopTurnLedger(
        store,
        FakeEnvironmentStateStore(),
        queue,
        liveness,
        adminDirectoryOf = { path -> admin(path.substringAfterLast('/')) },
        clock = { now },
        sleep = { pause ->
            now += pause
            onSleep.removeFirstOrNull()?.invoke()
        },
    )
    private val acquire = AcquireDesktopTurnUseCase(ledger) { Duration.ofMinutes(minutes) }
    private val release = ReleaseDesktopTurnUseCase(ledger)
    private val read = ReadDesktopTurnUseCase(ledger)

    private fun held(id: String, expiresIn: Duration) =
        DesktopTurn(admin(id), worktree(id), (now - Duration.ofMinutes(1)).toEpochMilli(), (now + expiresIn).toEpochMilli())
            .also { store.turn = it }

    private fun waiter(id: String, process: ProcessIdentity = ProcessIdentity(3000, 1)) =
        WaitTicket("ticket-$id", admin(id), worktree(id), process, now.toEpochMilli())

    @Test
    fun `a free desktop is acquired for the turn duration`() = runTest {
        val acquisition = acquire(worktree("TASK-1")).getOrThrow()

        assertEquals(admin("TASK-1"), acquisition.turn.adminDirectory)
        assertEquals((now + Duration.ofMinutes(10)).toEpochMilli(), acquisition.turn.expiresAt)
        assertFalse(acquisition.renewed)
        assertNull(acquisition.waitedSeconds)
        assertEquals(acquisition.turn, store.turn)
    }

    @Test
    fun `an expired turn is taken over`() = runTest {
        held("TASK-1", Duration.ofMinutes(-1))

        val acquisition = acquire(worktree("TASK-2")).getOrThrow()

        assertEquals(admin("TASK-2"), store.turn?.adminDirectory)
        assertFalse(acquisition.renewed)
    }

    @Test
    fun `the holder renews its turn even while others wait`() = runTest {
        val turn = held("TASK-1", Duration.ofMinutes(5))
        queue.write(WaitQueueName.DESKTOP, listOf(waiter("TASK-2")))

        val acquisition = acquire(worktree("TASK-1")).getOrThrow()

        assertTrue(acquisition.renewed)
        assertEquals(turn.acquiredAt, acquisition.turn.acquiredAt)
        assertEquals((now + Duration.ofMinutes(10)).toEpochMilli(), store.turn?.expiresAt)
        assertEquals(listOf("ticket-TASK-2"), queue.read(WaitQueueName.DESKTOP).map { it.id })
    }

    @Test
    fun `a taken desktop fails naming the holder and the waiters without changing anything`() = runTest {
        val turn = held("TASK-1", Duration.ofMinutes(5))
        queue.write(WaitQueueName.DESKTOP, listOf(waiter("TASK-3"), waiter("TASK-4")))

        val error = assertIs<WorktreeError.DesktopTurnTaken>(acquire(worktree("TASK-2")).exceptionOrNull())

        assertEquals(turn, error.holder)
        assertEquals(2, error.waiting)
        assertTrue("main-repo · TASK-1" in error.message.orEmpty())
        assertTrue("2 worktrees are waiting" in error.message.orEmpty())
        assertTrue("--wait" in error.message.orEmpty())
        assertEquals(turn, store.turn)
    }

    @Test
    fun `an acquisition without waiting doesn't jump the queue of a free desktop`() = runTest {
        queue.write(WaitQueueName.DESKTOP, listOf(waiter("TASK-2")))

        val error = assertIs<WorktreeError.DesktopTurnTaken>(acquire(worktree("TASK-4")).exceptionOrNull())

        assertNull(error.holder)
        assertEquals(1, error.waiting)
        assertNull(store.turn)
    }

    @Test
    fun `a waiter gets the turn once the holder releases it`() = runTest {
        held("TASK-1", Duration.ofMinutes(5))
        onSleep += { store.turn = null }
        val progress = mutableListOf<DesktopWaitProgress>()

        val acquisition = acquire(worktree("TASK-2"), DesktopWait(onProgress = { progress += it })).getOrThrow()

        assertEquals(admin("TASK-2"), acquisition.turn.adminDirectory)
        assertEquals(10, acquisition.waitedSeconds)
        assertEquals(1, progress.single().position)
        assertEquals(admin("TASK-1"), progress.single().holder?.adminDirectory)
        assertTrue(queue.read(WaitQueueName.DESKTOP).isEmpty())
    }

    @Test
    fun `a waiter gets the turn once the holder's expires`() = runTest {
        held("TASK-1", Duration.ofSeconds(3))

        val acquisition = acquire(worktree("TASK-2"), DesktopWait()).getOrThrow()

        assertEquals(admin("TASK-2"), acquisition.turn.adminDirectory)
        assertEquals(4, acquisition.waitedSeconds)
    }

    @Test
    fun `there is room at once with wait`() = runTest {
        assertEquals(0, acquire(worktree("TASK-1"), DesktopWait()).getOrThrow().waitedSeconds)
    }

    @Test
    fun `a wait times out leaving the queue and holding nothing`() = runTest {
        val turn = held("TASK-1", Duration.ofMinutes(60))

        val error = assertIs<WorktreeError.DesktopTurnWaitTimedOut>(
            acquire(worktree("TASK-2"), DesktopWait(timeout = Duration.ofMinutes(5))).exceptionOrNull(),
        )

        assertEquals(Duration.ofMinutes(5), error.waited)
        assertEquals(turn, error.holder)
        assertTrue("5 min" in error.message.orEmpty())
        assertEquals(turn, store.turn)
        assertTrue(queue.read(WaitQueueName.DESKTOP).isEmpty())
    }

    @Test
    fun `waiters are served in order`() = runTest {
        held("TASK-1", Duration.ofMinutes(5))
        val earlier = waiter("TASK-3")
        queue.write(WaitQueueName.DESKTOP, listOf(earlier))
        onSleep += { store.turn = null }
        onSleep += { queue.write(WaitQueueName.DESKTOP, queue.read(WaitQueueName.DESKTOP) - earlier) }
        val progress = mutableListOf<DesktopWaitProgress>()

        acquire(worktree("TASK-2"), DesktopWait(onProgress = { progress += it })).getOrThrow()

        assertEquals(listOf(2, 2), progress.map { it.position })
        assertNull(progress.last().holder)
    }

    @Test
    fun `a dead waiter is dropped when the queue is read`() = runTest {
        val dead = ProcessIdentity(4000, 1)
        liveness.dead += dead
        queue.write(WaitQueueName.DESKTOP, listOf(waiter("TASK-3", dead)))

        acquire(worktree("TASK-2")).getOrThrow()

        assertTrue(queue.read(WaitQueueName.DESKTOP).isEmpty())
    }

    @Test
    fun `an interrupted waiter leaves the queue`() = runTest {
        held("TASK-1", Duration.ofMinutes(5))
        onSleep += { throw CancellationException("interrupted") }

        runCatching { acquire(worktree("TASK-2"), DesktopWait()) }

        assertTrue(queue.read(WaitQueueName.DESKTOP).isEmpty())
    }

    @Test
    fun `the turn expires on its own`() = runTest {
        held("TASK-1", Duration.ofMinutes(5))
        now += Duration.ofMinutes(6)

        val state = read().getOrThrow()

        assertNull(state.holder)
    }

    @Test
    fun `a changed duration applies from the next renewal`() = runTest {
        acquire(worktree("TASK-1")).getOrThrow()
        minutes = 15

        val renewal = acquire(worktree("TASK-1")).getOrThrow()

        assertEquals((now + Duration.ofMinutes(15)).toEpochMilli(), renewal.turn.expiresAt)
    }

    @Test
    fun `the holder releases its turn`() = runTest {
        val turn = held("TASK-1", Duration.ofMinutes(5))

        assertEquals(DesktopTurnRelease.Released(turn), release(worktree("TASK-1")).getOrThrow())
        assertNull(store.turn)
    }

    @Test
    fun `releasing a free desktop succeeds`() = runTest {
        assertEquals(DesktopTurnRelease.NobodyHolds, release(worktree("TASK-1")).getOrThrow())
    }

    @Test
    fun `someone else's turn isn't released`() = runTest {
        val turn = held("TASK-1", Duration.ofMinutes(5))

        val error = assertIs<WorktreeError.DesktopTurnHeldByOther>(release(worktree("TASK-2")).exceptionOrNull())

        assertEquals(turn, error.holder)
        assertEquals(turn, store.turn)
    }

    @Test
    fun `a release if held leaves another worktree's turn`() = runTest {
        val turn = held("TASK-1", Duration.ofMinutes(5))

        assertEquals(DesktopTurnRelease.NobodyHolds, release.ifHeldBy(worktree("TASK-2")).getOrThrow())
        assertEquals(turn, store.turn)
        assertIs<DesktopTurnRelease.Released>(release.ifHeldBy(worktree("TASK-1")).getOrThrow())
        assertNull(store.turn)
    }

    @Test
    fun `the person can release anyone's turn`() = runTest {
        held("TASK-1", Duration.ofMinutes(5))

        assertIs<DesktopTurnRelease.Released>(release.force().getOrThrow())
        assertNull(store.turn)
    }

    @Test
    fun `removing a worktree releases its turn and drops its waits`() = runTest {
        held("TASK-1", Duration.ofMinutes(5))
        queue.write(WaitQueueName.DESKTOP, listOf(waiter("TASK-1"), waiter("TASK-3")))

        release.onWorktreeRemoved(worktree("TASK-1"))
        release.onWorktreeRemoved(worktree("TASK-3"))

        assertNull(store.turn)
        assertTrue(queue.read(WaitQueueName.DESKTOP).isEmpty())
    }

    @Test
    fun `the state lists the holder and the live waiters in order`() = runTest {
        val turn = held("TASK-1", Duration.ofMinutes(5))
        queue.write(WaitQueueName.DESKTOP, listOf(waiter("TASK-2"), waiter("TASK-3")))

        val state = read().getOrThrow()

        assertEquals(turn, state.holder)
        assertEquals(listOf(admin("TASK-2"), admin("TASK-3")), state.waiting.map { it.adminDirectory })
    }

    @Test
    fun `by default the wait sleeps on the real clock until it times out`() = runTest {
        val realTime = DesktopTurnLedger(store, FakeEnvironmentStateStore(), queue, liveness, adminDirectoryOf = { path -> admin(path.substringAfterLast('/')) })
        val start = Instant.now()
        store.turn = DesktopTurn(admin("TASK-1"), worktree("TASK-1"), start.toEpochMilli(), (start + Duration.ofHours(1)).toEpochMilli())

        val error = AcquireDesktopTurnUseCase(realTime) { Duration.ofMinutes(minutes) }(worktree("TASK-2"), DesktopWait(timeout = Duration.ofMillis(50))).exceptionOrNull()

        assertIs<WorktreeError.DesktopTurnWaitTimedOut>(error)
    }
    @Test
    fun `forcing a release of a free desktop says nobody held it`() = runTest {
        assertEquals(DesktopTurnRelease.NobodyHolds, release.force().getOrThrow())
    }

    @Test
    fun `removing a worktree whose admin folder can't be found leaves the turn`() = runTest {
        val turn = held("TASK-1", Duration.ofMinutes(5))
        val blind = DesktopTurnLedger(store, FakeEnvironmentStateStore(), queue, liveness, adminDirectoryOf = { error("not a worktree") }, clock = { now })

        ReleaseDesktopTurnUseCase(blind).onWorktreeRemoved(worktree("TASK-1"))

        assertEquals(turn, store.turn)
    }

    @Test
    fun `a wait stops at an error that isn't a taken desktop`() = runTest {
        val failing = AcquireDesktopTurnUseCase(ledger) { error("unreadable settings") }

        val error = failing(worktree("TASK-1"), DesktopWait(timeout = Duration.ofMinutes(1))).exceptionOrNull()

        assertIs<IllegalStateException>(error)
    }
}
