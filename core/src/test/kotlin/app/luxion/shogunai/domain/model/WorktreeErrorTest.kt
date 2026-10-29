package app.luxion.shogunai.domain.model

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorktreeErrorTest {

    @Test
    fun `fixed errors explain what to do`() {
        assertTrue(WorktreeError.RepositoryHasNoCommits.message.orEmpty().contains("make a first commit"))
        assertTrue(WorktreeError.AppNotAvailable.message.orEmpty().contains("install the app"))
        assertTrue(WorktreeError.HeadlessAppHasNoWindow.message.orEmpty().contains("no window"))
    }

    @Test
    fun `errors about a path name it`() {
        assertEquals("claude, codex is running in /projects/worktrees/task-123", WorktreeError.AgentRunningInWorktree("/projects/worktrees/task-123", listOf("claude", "codex")).message)
        assertEquals("No multirepo found at /projects/product", WorktreeError.MultirepoNotFound("/projects/product").message)
    }

    @Test
    fun `desktop taken summary leaves out the wait hint`() {
        val error = WorktreeError.DesktopTurnTaken(holder = null, waiting = 2)

        assertEquals("2 worktrees are waiting for the desktop first", error.summary)
        assertEquals(error.summary + ". Wait for it with --wait", error.message)
    }

    @Test
    fun `a full pool counts the worktrees waiting for one`() {
        val holder = PoolHolder(ResourceKind.EMULATOR, "/projects/worktrees/task-123", null, PoolHolderState.RUNNING, idle = false, lastUsed = Instant.EPOCH)

        assertTrue(WorktreeError.ResourcePoolFull(ResourceKind.EMULATOR, 1, listOf(holder), waiting = 3).message.orEmpty().contains("; 3 worktrees are waiting for one"))
    }

    @Test
    fun `wait text shows minutes and seconds`() {
        assertEquals("2 min", Duration.ofMinutes(2).toWaitText())
        assertEquals("2 min 5 s", Duration.ofSeconds(125).toWaitText())
        assertEquals("45 s", Duration.ofSeconds(45).toWaitText())
    }
}
