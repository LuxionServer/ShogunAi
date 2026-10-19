package app.luxion.shogunai.ui.tray

import app.luxion.shogunai.ui.worktree.WorktreeRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrayLaunchWatchTest {

    private val ref = WorktreeRef("api", "~/projects/TASK-123")
    private val otherRef = WorktreeRef("api", "~/projects/TASK-456")
    private val watch = TrayLaunchWatch()

    @Test
    fun `a launch that fails is reported once`() {
        watch.started(ref, currentError = null)

        val failure = IllegalStateException("launch failed")

        assertEquals(listOf(ref), watch.failures(mapOf(ref to failure)))
        assertTrue(watch.failures(mapOf(ref to failure)).isEmpty())
    }

    @Test
    fun `a launch that hasn't failed isn't reported`() {
        watch.started(ref, currentError = null)

        assertTrue(watch.failures(emptyMap()).isEmpty())
    }

    @Test
    fun `an error that was already there before the launch isn't reported`() {
        val previous = IllegalStateException("earlier failure")
        watch.started(ref, currentError = previous)

        assertTrue(watch.failures(mapOf(ref to previous)).isEmpty())
    }

    @Test
    fun `a new error replacing an earlier one is reported`() {
        val previous = IllegalStateException("earlier failure")
        watch.started(ref, currentError = previous)

        assertEquals(listOf(ref), watch.failures(mapOf(ref to IllegalStateException("new failure"))))
    }

    @Test
    fun `an error on a worktree that wasn't launched from the tray isn't reported`() {
        watch.started(ref, currentError = null)

        assertTrue(watch.failures(mapOf(otherRef to IllegalStateException("failure"))).isEmpty())
    }
}
