package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeClaudeSessionRegistry
import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.model.ClaudeSession
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

    @Test
    fun `the workspace's only pane idle in the folder is reusable`() = runTest {
        val herdr = FakeHerdrClient(panes = listOf(HerdrPane("w1:p1", "w1", "$folder/")))

        assertEquals("w1:p1", HerdrAgentLauncher(herdr).reusablePane("w1", folder))
        assertEquals(listOf("w1:p1"), herdr.processReads)
    }

    @Test
    fun `a pane that may be in use is not reusable`() = runTest {
        val idle = HerdrPane("w1:p1", "w1", folder)
        val cases = mapOf(
            "two panes" to FakeHerdrClient(panes = listOf(idle, HerdrPane("w1:p2", "w1", folder))),
            "live agent" to FakeHerdrClient(panes = listOf(idle.copy(agent = "claude", agentStatus = "idle"))),
            "another folder" to FakeHerdrClient(panes = listOf(idle.copy(directory = "/home/dev/projects/other"))),
            "no folder" to FakeHerdrClient(panes = listOf(idle.copy(directory = null))),
            "no pane" to FakeHerdrClient(panes = listOf(HerdrPane("w2:p1", "w2", folder))),
        )
        cases.forEach { (case, herdr) ->
            assertNull(HerdrAgentLauncher(herdr).reusablePane("w1", folder), case)
            assertTrue(herdr.processReads.isEmpty(), case)
        }
        listOf(
            FakeHerdrClient(panes = listOf(idle), busyPanes = setOf("w1:p1")),
            FakeHerdrClient(panes = listOf(idle), unknownProcessPanes = setOf("w1:p1")),
        ).forEach { herdr ->
            assertNull(HerdrAgentLauncher(herdr).reusablePane("w1", folder))
            assertEquals(listOf("w1:p1"), herdr.processReads)
        }
    }

    private val folder = "/home/dev/projects/main-repo-worktrees/TASK-123"
    private val dead = 1L

    private fun launcher(herdr: FakeHerdrClient, registry: FakeClaudeSessionRegistry?) =
        HerdrAgentLauncher(herdr, registry = registry, isClaudeProcessAlive = { it != dead })

    private fun startingRegistry(herdr: FakeHerdrClient, vararg existing: ClaudeSession) =
        FakeClaudeSessionRegistry(existing.toList(), started = herdr::startedClaudeSessions)

    @Test
    fun `a named Claude session is typed and confirmed`() = runTest {
        val herdr = FakeHerdrClient(panes = listOf(HerdrPane("w1:p1", "w1", folder)))

        val opening = launcher(herdr, startingRegistry(herdr))
            .launch("w1", "w1:p1", "claude", "Claude", HerdrOutcome.OPENED, "main-repo-task-123", folder)

        assertEquals(HerdrOpening(HerdrOutcome.OPENED, "w1:p1", "Claude", "main-repo-task-123"), opening)
        assertEquals(listOf("w1:p1" to "claude -n main-repo-task-123"), herdr.ran)
    }

    @Test
    fun `a name never confirmed leaves the session name empty without failing`() = runTest {
        val herdr = FakeHerdrClient(panes = listOf(HerdrPane("w1:p1", "w1", folder)))
        val other = ClaudeSession(42, "/home/dev/projects/other", "main-repo-task-123-2")

        val opening = launcher(herdr, FakeClaudeSessionRegistry(listOf(other)))
            .launch("w1", "w1:p1", "claude", "Claude", HerdrOutcome.OPENED, "main-repo-task-123", folder)

        assertNull(opening.sessionName)
        assertEquals(listOf("w1:p1" to "claude -n main-repo-task-123"), herdr.ran)
        assertEquals(10_000, testScheduler.currentTime)
    }

    @Test
    fun `a command that isn't Claude is typed unchanged`() = runTest {
        val herdr = FakeHerdrClient(panes = listOf(HerdrPane("w1:p1", "w1", folder)))
        val registry = startingRegistry(herdr)

        val opening = launcher(herdr, registry).launch("w1", "w1:p1", "codex", "Codex", HerdrOutcome.OPENED, "main-repo-task-123", folder)

        assertNull(opening.sessionName)
        assertEquals(listOf("w1:p1" to "codex"), herdr.ran)
        assertEquals(0, registry.listings)
    }

    @Test
    fun `a name taken by a live session gets a suffix and one held by a dead session doesn't`() = runTest {
        val herdr = FakeHerdrClient(panes = listOf(HerdrPane("w1:p1", "w1", folder), HerdrPane("w2:p1", "w2", folder)))
        val registry = startingRegistry(
            herdr,
            ClaudeSession(42, "/home/dev/projects/other", "app-task-123"),
            ClaudeSession(dead, "/home/dev/projects/other", "api-task-123"),
        )

        val taken = launcher(herdr, registry).launch("w1", "w1:p1", "claude", "Claude", HerdrOutcome.OPENED, "app-task-123", folder)
        val freed = launcher(herdr, registry).launch("w2", "w2:p1", "claude", "Claude", HerdrOutcome.OPENED, "api-task-123", folder)

        assertEquals("app-task-123-2", taken.sessionName)
        assertEquals("api-task-123", freed.sessionName)
        assertEquals(listOf("w1:p1" to "claude -n app-task-123-2", "w2:p1" to "claude -n api-task-123"), herdr.ran)
    }

    @Test
    fun `without a registry the name is typed but not confirmed`() = runTest {
        val herdr = FakeHerdrClient(panes = listOf(HerdrPane("w1:p1", "w1", folder)))

        val opening = launcher(herdr, null).launch("w1", "w1:p1", "claude", "Claude", HerdrOutcome.OPENED, "main-repo-task-123", folder)

        assertNull(opening.sessionName)
        assertEquals(listOf("w1:p1" to "claude -n main-repo-task-123"), herdr.ran)
        assertEquals(0, testScheduler.currentTime)
    }

    @Test
    fun `a failing registry never fails the launch`() = runTest {
        val herdr = FakeHerdrClient(panes = listOf(HerdrPane("w1:p1", "w1", folder)))
        val registry = FakeClaudeSessionRegistry(failure = IllegalStateException("broken"))

        val opening = launcher(herdr, registry).launch("w1", "w1:p1", "claude", "Claude", HerdrOutcome.OPENED, "main-repo-task-123", folder)

        assertNull(opening.sessionName)
        assertEquals(listOf("w1:p1" to "claude -n main-repo-task-123"), herdr.ran)
    }

    @Test
    fun `an agent that doesn't start reports the named command`() = runTest {
        val herdr = FakeHerdrClient(panes = listOf(HerdrPane("w1:p1", "w1", folder)), startsAgents = false)

        val error = runCatching {
            launcher(herdr, null).launch("w1", "w1:p1", "claude", "Claude", HerdrOutcome.OPENED, "main-repo-task-123", folder)
        }.exceptionOrNull()

        assertIs<WorktreeError.HerdrAgentNotStarted>(error)
        assertEquals("claude -n main-repo-task-123", error.command)
    }

    private fun focusable() = FakeHerdrClient(
        panes = listOf(HerdrPane("w1:p1", "w1", folder, agent = "claude", agentStatus = "working")),
    )

    @Test
    fun `a focused agent reports its session`() = runTest {
        val registry = FakeClaudeSessionRegistry(listOf(ClaudeSession(42, folder, "task-123-1a")))

        val opening = launcher(focusable(), registry).focusLiveAgent("w1", folder, "main-repo-task-123")

        assertEquals(HerdrOpening(HerdrOutcome.FOCUSED, "w1:p1", "claude", "task-123-1a"), opening)
    }

    @Test
    fun `a focused agent with several sessions prefers the expected name, then the most recent`() = runTest {
        val sessions = listOf(
            ClaudeSession(41, folder, "main-repo-task-123", startedAt = 1),
            ClaudeSession(42, folder, "later", startedAt = 3),
            ClaudeSession(43, folder, "earlier", startedAt = 2),
            ClaudeSession(dead, folder, "gone", startedAt = 4),
            ClaudeSession(44, "/home/dev/projects/other", "elsewhere", startedAt = 5),
        )

        val expected = launcher(focusable(), FakeClaudeSessionRegistry(sessions)).focusLiveAgent("w1", folder, "main-repo-task-123")
        val latest = launcher(focusable(), FakeClaudeSessionRegistry(sessions)).focusLiveAgent("w1", folder, "other-name")

        assertEquals("main-repo-task-123", expected?.sessionName)
        assertEquals("later", latest?.sessionName)
    }

    @Test
    fun `a focused agent without a session in its folder reports none`() = runTest {
        val registry = FakeClaudeSessionRegistry(listOf(ClaudeSession(42, "/home/dev/projects/other", "elsewhere")))
        val codex = FakeHerdrClient(panes = listOf(HerdrPane("w1:p1", "w1", folder, agent = "codex", agentStatus = "working")))
        val withSession = FakeClaudeSessionRegistry(listOf(ClaudeSession(42, folder, "main-repo-task-123")))

        assertNull(launcher(focusable(), registry).focusLiveAgent("w1", folder, "main-repo-task-123")?.sessionName)
        assertNull(launcher(codex, withSession).focusLiveAgent("w1", folder, "main-repo-task-123")?.sessionName)
    }
    @Test
    fun `a named launch without a folder doesn't wait for the session`() = runTest {
        val herdr = FakeHerdrClient(panes = listOf(HerdrPane("w1:p1", "w1", "/home/dev/projects/main-repo")))

        val opening = HerdrAgentLauncher(herdr).launch("w1", "w1:p1", "claude", "Claude", HerdrOutcome.OPENED, sessionName = "task")

        assertEquals(HerdrOpening(HerdrOutcome.OPENED, "w1:p1", "Claude"), opening)
        assertEquals(listOf("w1:p1" to "claude -n task"), herdr.ran)
    }
}
