package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.model.HerdrOpening
import app.luxion.shogunai.domain.model.HerdrOutcome
import app.luxion.shogunai.domain.model.HerdrPane
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HerdrAgentLauncherTest {

    @Test
    fun `an agent detected after a few polls is launched`() = runTest {
        val herdr = FakeHerdrClient(panes = listOf(HerdrPane("w1:p1", "w1", "/home/dev/projects/main-repo")), agentDetectedAfterListings = 4)

        val opening = HerdrAgentLauncher(herdr).launch("w1", "w1:p1", "claude", "Claude", HerdrOutcome.OPENED)

        assertEquals(HerdrOpening(HerdrOutcome.OPENED, "w1:p1", "Claude"), opening)
        assertEquals(listOf("w1:p1" to "claude"), herdr.ran)
        assertEquals(List<String?>(4) { "w1" }, herdr.paneListings)
        assertEquals(1_500, testScheduler.currentTime)
    }

    @Test
    fun `an agent never detected fails after 10 seconds`() = runTest {
        val herdr = FakeHerdrClient(panes = listOf(HerdrPane("w1:p1", "w1", "/home/dev/projects/main-repo")), startsAgents = false)

        val error = runCatching { HerdrAgentLauncher(herdr).launch("w1", "w1:p1", "claude", "Claude", HerdrOutcome.OPENED) }.exceptionOrNull()

        assertIs<WorktreeError.HerdrAgentNotStarted>(error)
        assertEquals("w1:p1", error.paneId)
        assertEquals("claude", error.command)
        assertEquals("The agent `claude` didn't start in Herdr pane w1:p1 within 10 seconds; the pane is left open", error.message)
        assertEquals(10_000, testScheduler.currentTime)
    }

    @Test
    fun `idle and done agents count as live and are focused`() = runTest {
        listOf("idle", "done").forEach { status ->
            val herdr = FakeHerdrClient(
                panes = listOf(
                    HerdrPane("w1:p1", "w1", "/home/dev/projects/main-repo"),
                    HerdrPane("w1:p2", "w1", "/home/dev/projects/main-repo", agent = "claude", agentStatus = status),
                    HerdrPane("w2:p1", "w2", "/home/dev/projects/other", agent = "codex", agentStatus = "working"),
                ),
            )

            val opening = HerdrAgentLauncher(herdr).focusLiveAgent("w1")

            assertEquals(HerdrOpening(HerdrOutcome.FOCUSED, "w1:p2", "claude"), opening)
            assertEquals(listOf("w1:p2"), herdr.focusedAgents)
            assertTrue(herdr.ran.isEmpty())
        }
    }

    @Test
    fun `a workspace without a live agent has nothing to focus`() = runTest {
        val herdr = FakeHerdrClient(panes = listOf(HerdrPane("w1:p1", "w1", "/home/dev/projects/main-repo")))

        assertNull(HerdrAgentLauncher(herdr).focusLiveAgent("w1"))
        assertTrue(herdr.focusedAgents.isEmpty())
    }
}
