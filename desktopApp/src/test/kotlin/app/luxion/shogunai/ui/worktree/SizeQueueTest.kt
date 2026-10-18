package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.Worktree
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class SizeQueueTest {

    private val shown = Any()
    private val hidden = Any()
    private val measured = mutableListOf<String>()
    private val delivered = mutableMapOf<String, Long>()

    private fun SizeQueue.submit(owner: Any, vararg paths: String) = submit(
        owner = owner,
        worktrees = paths.map { Worktree(path = it, branch = null) },
        measure = { worktree ->
            measured += worktree.path
            yield()
            worktree.path.length.toLong()
        },
        deliver = { path, size -> delivered[path] = size },
    )

    @Test
    fun `shown projects are measured first, one worktree at a time`() = runTest(StandardTestDispatcher()) {
        val queue = SizeQueue(this)
        queue.prioritize(setOf(shown))

        queue.submit(hidden, "/hidden/a", "/hidden/b")
        queue.submit(shown, "/shown/a", "/shown/b")
        advanceUntilIdle()

        assertEquals(listOf("/shown/a", "/shown/b", "/hidden/a", "/hidden/b"), measured)
        assertEquals(4, delivered.size)
    }

    @Test
    fun `a new submission replaces the owner's pending worktrees`() = runTest(StandardTestDispatcher()) {
        val queue = SizeQueue(this)

        queue.submit(hidden, "/old/a", "/old/b")
        queue.submit(hidden, "/new/a")
        advanceUntilIdle()

        assertEquals(listOf("/new/a"), measured)
    }

    @Test
    fun `cancelling drops the owner's pending worktrees`() = runTest(StandardTestDispatcher()) {
        val queue = SizeQueue(this)

        queue.submit(hidden, "/hidden/a")
        queue.submit(shown, "/shown/a")
        queue.cancel(hidden)
        advanceUntilIdle()

        assertEquals(listOf("/shown/a"), measured)
    }

}
