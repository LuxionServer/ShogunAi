package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeClaudeSessionRegistry
import app.luxion.shogunai.domain.model.ClaudeSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClaudeSessionNamingTest {

    private val folder = "/projects/worktrees/task-123"

    @Test
    fun `a name is only added to a claude command`() {
        val naming = ClaudeSessionNaming()

        assertEquals("claude -n task", naming.named("claude", "task"))
        assertEquals("codex", naming.named("codex", "task"))
        assertEquals("claude", naming.named("claude", null))
    }

    @Test
    fun `the focused session is the expected one or else the newest named one`() {
        val registry = FakeClaudeSessionRegistry(
            listOf(
                ClaudeSession(1, folder, null, startedAt = 30),
                ClaudeSession(2, folder, "older", startedAt = null),
                ClaudeSession(3, folder, "newer", startedAt = 20),
                ClaudeSession(5, folder, "middle", startedAt = 10),
                ClaudeSession(4, "/elsewhere", "elsewhere", startedAt = 40),
                ClaudeSession(6, "/two", "first", startedAt = 1),
                ClaudeSession(7, "/two", "second", startedAt = null),
            ),
        )
        val naming = ClaudeSessionNaming(registry, isClaudeProcessAlive = { true })

        assertEquals("older", naming.focusedSessionName(folder, "older"))
        assertEquals("newer", naming.focusedSessionName(folder, "missing"))
        assertNull(naming.focusedSessionName("/empty", null))
        assertEquals("elsewhere", naming.focusedSessionName("/elsewhere", null))
        assertEquals("first", naming.focusedSessionName("/two", null))
    }

    @Test
    fun `a session whose process can't be checked is kept`() {
        val registry = FakeClaudeSessionRegistry(listOf(ClaudeSession(1, folder, "task")))
        val naming = ClaudeSessionNaming(registry, isClaudeProcessAlive = { error("no access") })

        assertEquals(listOf(1L), naming.liveSessionsIn(folder).map { it.pid })
    }
}
