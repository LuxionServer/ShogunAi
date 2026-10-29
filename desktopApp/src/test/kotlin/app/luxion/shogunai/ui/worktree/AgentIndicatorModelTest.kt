package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.AgentStateSource
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.ui.components.AgentPhase
import app.luxion.shogunai.ui.i18n.EnglishStrings
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentIndicatorModelTest {

    private val now = Instant.parse("2026-10-16T12:00:00Z")

    @Test
    fun `every source's state maps to the shared vocabulary`() {
        val expected = mapOf(
            AgentState.BLOCKED to "needs you",
            AgentState.WORKING to "working",
            AgentState.DONE to "done",
            AgentState.IDLE to "idle",
            null to "running",
        )
        AgentStateSource.entries.forEach { source ->
            expected.forEach { (state, word) ->
                val model = agentIndicator(listOf(RunningAgent("claude", state, stateSource = source)), null, now, EnglishStrings)!!

                assertEquals("Claude · $word", model.label)
            }
        }
    }

    @Test
    fun `phases carry their status colors`() {
        assertEquals(
            listOf("WARNING", "SUCCESS", "INFO", "INFO", "NEUTRAL"),
            AgentPhase.entries.map { it.status.name },
        )
    }

    @Test
    fun `the most urgent agent leads and the tooltip names the source and the last read`() {
        val agents = listOf(
            RunningAgent("codex", AgentState.WORKING),
            RunningAgent("claude", AgentState.BLOCKED, stateSource = AgentStateSource.ORCA),
        )

        val model = agentIndicator(agents, now.minus(Duration.ofSeconds(40)), now, EnglishStrings)!!

        assertEquals(AgentPhase.NEEDS_YOU, model.phase)
        assertEquals(1, model.moreCount)
        assertEquals("Claude · needs you\nCodex · working\nState from Orca\nUpdated just now", model.tooltip)
    }

    @Test
    fun `an agent found only by its process says so`() {
        val model = agentIndicator(listOf(RunningAgent("claude")), null, now, EnglishStrings)!!

        assertEquals("Claude · running\nOnly its process was found", model.tooltip)
    }

    @Test
    fun `no agents means no indicator`() {
        assertNull(agentIndicator(emptyList(), now, now, EnglishStrings))
    }
}
