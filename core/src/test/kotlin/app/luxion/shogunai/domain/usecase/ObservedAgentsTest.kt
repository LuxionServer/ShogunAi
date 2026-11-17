package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.AgentRunner
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.AgentStateSource
import app.luxion.shogunai.domain.model.NotifiedWorktree
import app.luxion.shogunai.domain.model.ObservedAgent
import app.luxion.shogunai.domain.model.RunningAgent
import kotlin.test.Test
import kotlin.test.assertEquals

class ObservedAgentsTest {
    private val login = NotifiedWorktree("api", "api", "/home/dev/projects/TASK-1", "feature/login")
    private val search = NotifiedWorktree("api", "api", "/home/dev/projects/TASK-2", "feature/search")

    @Test
    fun `each detected agent becomes an observation with its runner and focus`() {
        val detected = mapOf(
            login.worktreePath to listOf(
                RunningAgent("claude", AgentState.BLOCKED, herdrPaneId = "w1:p1", stateSource = AgentStateSource.HERDR, focusedInRunner = true),
                RunningAgent("codex", AgentState.WORKING, stateSource = AgentStateSource.ORCA),
            ),
            search.worktreePath to listOf(RunningAgent("gemini", AgentState.DONE, stateSource = AgentStateSource.CLAUDE_CODE)),
            "/home/dev/elsewhere" to listOf(RunningAgent("claude", AgentState.WORKING)),
        )

        assertEquals(
            listOf(
                ObservedAgent(login, "claude", "Claude", AgentState.BLOCKED, AgentRunner.HERDR, focused = true),
                ObservedAgent(login, "codex", "Codex", AgentState.WORKING, AgentRunner.ORCA),
                ObservedAgent(search, "gemini", "Gemini", AgentState.DONE, null),
            ),
            observedAgents(listOf(login, search), detected),
        )
    }

    @Test
    fun `a worktree without agents observes nothing`() {
        assertEquals(emptyList(), observedAgents(listOf(login), emptyMap()))
    }
}
