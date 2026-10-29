package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClaudeSessionTest {

    private fun session(status: ClaudeSessionStatus?, startedAt: Long? = 1_000L, statusUpdatedAt: Long? = 1_083L) =
        ClaudeSession(42, "/home/dev/projects/main-repo", "main-repo", startedAt, status, statusUpdatedAt)

    @Test
    fun `busy is working and waiting is blocked`() {
        assertEquals(AgentState.WORKING, session(ClaudeSessionStatus.BUSY).state)
        assertEquals(AgentState.BLOCKED, session(ClaudeSessionStatus.WAITING).state)
    }

    @Test
    fun `idle right after starting is idle`() {
        assertEquals(AgentState.IDLE, session(ClaudeSessionStatus.IDLE).state)
    }

    @Test
    fun `idle after a turn is done`() {
        assertEquals(AgentState.DONE, session(ClaudeSessionStatus.IDLE, statusUpdatedAt = 39_000L).state)
    }

    @Test
    fun `idle without both times is idle`() {
        assertEquals(AgentState.IDLE, session(ClaudeSessionStatus.IDLE, startedAt = null, statusUpdatedAt = 39_000L).state)
        assertEquals(AgentState.IDLE, session(ClaudeSessionStatus.IDLE, statusUpdatedAt = null).state)
    }

    @Test
    fun `no status gives no state`() {
        assertNull(session(null).state)
    }
}
