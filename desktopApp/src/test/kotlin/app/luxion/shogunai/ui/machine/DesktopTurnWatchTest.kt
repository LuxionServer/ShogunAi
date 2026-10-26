package app.luxion.shogunai.ui.machine

import app.luxion.shogunai.domain.model.DesktopTurn
import app.luxion.shogunai.domain.model.DesktopTurnRelease
import app.luxion.shogunai.domain.model.DesktopTurnState
import app.luxion.shogunai.domain.model.ProcessIdentity
import app.luxion.shogunai.domain.model.WaitTicket
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.ui.worktree.WorktreeDesktopTurn
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopTurnWatchTest {

    private val holderPath = "/home/dev/projects/worktrees/TASK-1"
    private val waiterPath = "/home/dev/projects/worktrees/TASK-2"
    private val holder = DesktopTurn("/home/dev/projects/main-repo/.git/worktrees/TASK-1", holderPath, 0, 600_000)
    private val waiter = WaitTicket("t", "/home/dev/projects/main-repo/.git/worktrees/TASK-2", waiterPath, ProcessIdentity(5, 1), 0)
    private var state = DesktopTurnState(holder, listOf(waiter))
    private var release: Result<DesktopTurnRelease> = Result.success(DesktopTurnRelease.Released(holder))
    private val watch = DesktopTurnWatch(read = { Result.success(state) }, forceRelease = { release })

    @Test
    fun `the state follows a release made outside the app`() = runTest {
        watch.refresh()
        assertEquals(WorktreeDesktopTurn.Holding(600_000), watch.rowTurn(holderPath))
        assertEquals(WorktreeDesktopTurn.Waiting(1), watch.rowTurn("$waiterPath/"))

        state = DesktopTurnState(null, emptyList())
        watch.refresh()

        assertNull(watch.rowTurn(holderPath))
        assertFalse(watch.isActive)
    }

    @Test
    fun `releasing asks first and then refreshes`() = runTest {
        watch.refresh()
        watch.requestRelease()
        assertEquals(holder, watch.pendingRelease)

        state = DesktopTurnState(null, listOf(waiter))
        watch.confirmRelease()

        assertNull(watch.pendingRelease)
        assertNull(watch.state.holder)
        assertTrue(watch.isActive)
    }

    @Test
    fun `a failed release is kept as the error`() = runTest {
        watch.refresh()
        release = Result.failure(WorktreeError.DesktopTurnHeldByOther(holder))

        watch.confirmRelease()

        assertIs<WorktreeError.DesktopTurnHeldByOther>(watch.error)
        watch.dismissError()
        assertNull(watch.error)
    }
}
