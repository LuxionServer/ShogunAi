package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeDeviceWaitQueue
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeProcessLiveness
import app.luxion.shogunai.domain.model.WaitQueueName
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WaitInLineTest {

    private val queue = FakeDeviceWaitQueue()
    private val inLine = WaitInLine(FakeEnvironmentStateStore(), queue, FakeProcessLiveness(), { Instant.EPOCH }, {})

    @Test
    fun `a failure other than being blocked while waiting ends the wait with that failure`() = runTest {
        val attempts = ArrayDeque(listOf(Result.failure<Unit>(IllegalStateException("blocked")), Result.failure(IllegalArgumentException("broken"))))

        val error = assertFailsWith<IllegalArgumentException> {
            inLine.await(
                queue = WaitQueueName.EMULATOR,
                adminDirectory = "/home/dev/projects/main-repo/.git/worktrees/TASK-123",
                worktreePath = "/home/dev/projects/worktrees/TASK-123",
                timeout = Duration.ofMinutes(5),
                attempt = { attempts.removeFirst() },
                blockedBy = { it as? IllegalStateException },
                onWaiting = { null },
                timedOut = { _, blocked -> blocked },
            )
        }

        assertEquals("broken", error.message)
        assertTrue(queue.read(WaitQueueName.EMULATOR).isEmpty())
    }
}
