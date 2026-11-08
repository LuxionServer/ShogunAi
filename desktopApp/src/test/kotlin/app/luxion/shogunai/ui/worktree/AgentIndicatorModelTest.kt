package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.AgentStateSource
import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.ui.components.AgentPhase
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.SpanishStrings
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertEquals("Claude · needs you · in Orca\nCodex · working\nState from Orca\nUpdated just now", model.tooltip)
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

    @Test
    fun `an agent with a Herdr pane runs in Herdr`() {
        assertEquals(RunnerId.HERDR, RunningAgent("claude", AgentState.BLOCKED, "w1:p1", AgentStateSource.HERDR).runner())
    }

    @Test
    fun `a Herdr pane wins over an Orca state`() {
        assertEquals(RunnerId.HERDR, RunningAgent("claude", AgentState.BLOCKED, "w1:p1", AgentStateSource.ORCA).runner())
    }

    @Test
    fun `an agent whose state Orca reports runs in Orca`() {
        assertEquals(RunnerId.ORCA, RunningAgent("codex", AgentState.WORKING, stateSource = AgentStateSource.ORCA).runner())
    }

    @Test
    fun `an agent reported only by hooks or found as a process has no known runner`() {
        assertNull(RunningAgent("claude", AgentState.WORKING, stateSource = AgentStateSource.CLAUDE_CODE).runner())
        assertNull(RunningAgent("claude").runner())
    }

    @Test
    fun `the focus target is the most urgent agent in a known runner`() {
        val agents = listOf(
            RunningAgent("codex", AgentState.WORKING, stateSource = AgentStateSource.ORCA),
            RunningAgent("claude", AgentState.BLOCKED, stateSource = AgentStateSource.CLAUDE_CODE),
            RunningAgent("gemini", AgentState.IDLE, "w1:p3", AgentStateSource.HERDR),
        )

        assertEquals(AgentFocusTarget("codex", RunnerId.ORCA), agentFocusTarget(agents))
    }

    @Test
    fun `a focus target in Herdr carries its pane`() {
        val agents = listOf(RunningAgent("claude", AgentState.BLOCKED, "w1:p2", AgentStateSource.HERDR))

        assertEquals(AgentFocusTarget("claude", RunnerId.HERDR, "w1:p2"), agentFocusTarget(agents))
    }

    @Test
    fun `no agent in a known runner means no focus target`() {
        assertNull(agentFocusTarget(listOf(RunningAgent("claude"))))
        assertNull(agentFocusTarget(emptyList()))
    }

    @Test
    fun `the tooltip says where each agent runs`() {
        val agents = listOf(
            RunningAgent("codex", AgentState.WORKING, stateSource = AgentStateSource.ORCA),
            RunningAgent("claude", AgentState.BLOCKED, "w1:p1", AgentStateSource.HERDR),
        )

        val model = agentIndicator(agents, null, now, EnglishStrings)!!

        assertEquals("Claude · needs you · in Herdr\nCodex · working · in Orca\nState from Herdr", model.tooltip)
        assertFalse(model.focusable)
    }

    @Test
    fun `a clickable indicator invites to focus the target`() {
        val agents = listOf(RunningAgent("claude", AgentState.BLOCKED, "w1:p1", AgentStateSource.HERDR))

        val model = agentIndicator(agents, null, now, EnglishStrings, clickToFocus = true)!!

        assertEquals("Claude · needs you · in Herdr — click to focus\nState from Herdr", model.tooltip)
        assertTrue(model.focusable)
    }

    @Test
    fun `only the target's line invites to focus`() {
        val agents = listOf(
            RunningAgent("claude", AgentState.BLOCKED, stateSource = AgentStateSource.CLAUDE_CODE),
            RunningAgent("codex", AgentState.WORKING, stateSource = AgentStateSource.ORCA),
        )

        val model = agentIndicator(agents, null, now, EnglishStrings, clickToFocus = true)!!

        assertEquals("Claude · needs you\nCodex · working · in Orca — click to focus\nState from Claude Code hooks", model.tooltip)
    }

    @Test
    fun `an agent without a known runner is not clickable`() {
        val model = agentIndicator(listOf(RunningAgent("claude")), null, now, EnglishStrings, clickToFocus = true)!!

        assertEquals("Claude · running\nOnly its process was found", model.tooltip)
        assertFalse(model.focusable)
    }

    @Test
    fun `the invitation to focus is translated`() {
        val agents = listOf(RunningAgent("claude", AgentState.BLOCKED, "w1:p1", AgentStateSource.HERDR))

        val model = agentIndicator(agents, null, now, SpanishStrings, clickToFocus = true)!!

        assertEquals("Claude · te necesita · en Herdr — clic para enfocar", model.tooltip.lines().first())
    }
}
