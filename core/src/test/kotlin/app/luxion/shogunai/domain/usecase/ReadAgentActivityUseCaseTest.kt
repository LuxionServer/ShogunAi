package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeAgentSessionStore
import app.luxion.shogunai.domain.model.AgentSessionRecord
import app.luxion.shogunai.domain.model.AgentState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReadAgentActivityUseCaseTest {

    private val worktree = "/home/dev/projects/worktrees/TASK-1"
    private val now = 1_000_000_000L
    private val store = FakeAgentSessionStore()
    private var alive = setOf(10L, 11L)
    private var transcripts = mapOf<String, Long>()

    private fun activity() = ReadAgentActivityUseCase(store, { it in alive }, { transcripts[it] }, { now })

    private fun session(id: String, state: AgentState, pid: Long? = 10, updatedAt: Long = now - 60_000, transcript: String? = null) =
        store.write(worktree, AgentSessionRecord(id, "claude", state, updatedAt, transcript, pid))

    @Test
    fun `no sessions means no agent`() {
        assertNull(activity()(worktree))
    }

    @Test
    fun `a session whose process is gone is ignored`() {
        session("a", AgentState.WORKING, pid = 99)

        assertNull(activity()(worktree))
    }

    @Test
    fun `the most active live session decides`() {
        session("a", AgentState.DONE, pid = 10)
        session("b", AgentState.WORKING, pid = 11)

        assertEquals(AgentState.WORKING, activity()(worktree))
    }

    @Test
    fun `blocked outranks done`() {
        session("a", AgentState.DONE, pid = 10)
        session("b", AgentState.BLOCKED, pid = 11)

        assertEquals(AgentState.BLOCKED, activity()(worktree))
    }

    @Test
    fun `a transcript written after a done state means the agent resumed`() {
        session("a", AgentState.DONE, transcript = "/t.jsonl")
        transcripts = mapOf("/t.jsonl" to now)

        assertEquals(AgentState.WORKING, activity()(worktree))
    }

    @Test
    fun `a session without a pid counts while it is recent`() {
        session("a", AgentState.DONE, pid = null, updatedAt = now - 1_000)
        session("b", AgentState.WORKING, pid = null, updatedAt = now - 13 * 60 * 60 * 1_000)

        assertEquals(AgentState.DONE, activity()(worktree))
    }
}
