package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeOrigin
import app.luxion.shogunai.domain.model.WorktreeStatus
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorktreeOverviewTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "/home/dev/projects/main-repo",
        worktreesRoot = "/home/dev/projects/",
        secretFiles = emptyList(),
    )
    private val now = Instant.parse("2026-10-16T12:00:00Z")

    private fun worktreeAt(path: String) = Worktree(path = path, branch = "feature/TASK-1")

    @Test
    fun `a worktree inside dot claude worktrees comes from Claude Code even inside the worktrees folder`() {
        val worktree = worktreeAt("/home/dev/projects/main-repo/.claude/worktrees/task")

        assertEquals(WorktreeOrigin.CLAUDE_CODE, classifyOrigin(worktree, config))
    }

    @Test
    fun `a worktree inside the worktrees folder comes from ShogunAi`() {
        assertEquals(WorktreeOrigin.SHOGUNAI, classifyOrigin(worktreeAt("/home/dev/projects/TASK-1"), config))
    }

    @Test
    fun `a worktree elsewhere is in another location`() {
        assertEquals(WorktreeOrigin.OTHER, classifyOrigin(worktreeAt("/tmp/elsewhere/TASK-1"), config))
    }

    @Test
    fun `a sibling folder with the same prefix is not inside the worktrees folder`() {
        assertEquals(WorktreeOrigin.OTHER, classifyOrigin(worktreeAt("/home/dev/projects-old/TASK-1"), config))
    }

    @Test
    fun `a worktree inside Herdr's worktrees folder comes from Herdr`() {
        val worktree = worktreeAt("/home/dev/.herdr/worktrees/main-repo/feature-task-1")

        assertEquals(WorktreeOrigin.HERDR, classifyOrigin(worktree, config, herdrWorktreesRoot = "/home/dev/.herdr/worktrees"))
    }

    @Test
    fun `Herdr's folder inside the worktrees folder still reads as Herdr`() {
        val worktree = worktreeAt("/home/dev/projects/herdr/main-repo/feature-task-1")

        assertEquals(WorktreeOrigin.HERDR, classifyOrigin(worktree, config, herdrWorktreesRoot = "/home/dev/projects/herdr/"))
    }

    @Test
    fun `a Claude Code worktree inside Herdr's folder still reads as Claude Code`() {
        val worktree = worktreeAt("/home/dev/.herdr/worktrees/main-repo/.claude/worktrees/task")

        assertEquals(WorktreeOrigin.CLAUDE_CODE, classifyOrigin(worktree, config, herdrWorktreesRoot = "/home/dev/.herdr/worktrees"))
    }

    @Test
    fun `without Herdr's folder a worktree there is in another location`() {
        assertEquals(WorktreeOrigin.OTHER, classifyOrigin(worktreeAt("/home/dev/.herdr/worktrees/main-repo/x"), config))
    }

    @Test
    fun `the main worktree has no origin`() {
        val main = Worktree(path = config.baseRepositoryPath, branch = "develop", isMain = true)

        assertNull(classifyOrigin(main, config))
    }

    @Test
    fun `Windows separators are understood`() {
        val windowsConfig = config.copy(worktreesRoot = "C:\\dev\\projects")

        assertEquals(WorktreeOrigin.SHOGUNAI, classifyOrigin(worktreeAt("C:\\dev\\projects\\TASK-1"), windowsConfig))
        assertEquals(WorktreeOrigin.CLAUDE_CODE, classifyOrigin(worktreeAt("C:\\dev\\repo\\.claude\\worktrees\\x"), windowsConfig))
    }

    private val threshold = Duration.ofDays(3)

    private fun status(changes: Int = 0, daysSinceCommit: Long? = 20) = WorktreeStatus(
        uncommittedChanges = changes,
        upstream = null,
        mainBranch = null,
        lastCommitAt = daysSinceCommit?.let { now.minus(Duration.ofDays(it)) },
    )

    @Test
    fun `a clean worktree without agents and an old last commit is inactive`() {
        assertTrue(isInactive(worktreeAt("/w"), status(), emptyList(), now, threshold))
    }

    @Test
    fun `recent work is not inactive`() {
        assertFalse(isInactive(worktreeAt("/w"), status(daysSinceCommit = 2), emptyList(), now, threshold))
    }

    @Test
    fun `a running agent keeps a worktree active`() {
        assertFalse(isInactive(worktreeAt("/w"), status(), listOf(RunningAgent("claude")), now, threshold))
    }

    @Test
    fun `pending changes don't keep an abandoned worktree active`() {
        assertTrue(isInactive(worktreeAt("/w"), status(changes = 2), emptyList(), now, threshold))
    }

    @Test
    fun `a merged worktree is not inactive`() {
        val merged = WorktreeStatus(0, null, Divergence(0, 3), now.minus(Duration.ofDays(20)), base = Divergence(1, 0))

        assertFalse(isInactive(worktreeAt("/w"), merged, emptyList(), now, threshold))
    }

    @Test
    fun `without a last commit or a status it is not inactive`() {
        assertFalse(isInactive(worktreeAt("/w"), status(daysSinceCommit = null), emptyList(), now, threshold))
        assertFalse(isInactive(worktreeAt("/w"), null, emptyList(), now, threshold))
    }

    @Test
    fun `the main worktree is never inactive`() {
        val main = Worktree(path = "/w", branch = "main", isMain = true)

        assertFalse(isInactive(main, status(), emptyList(), now, threshold))
    }

    @Test
    fun `a longer threshold keeps a worktree out of inactive`() {
        assertTrue(isInactive(worktreeAt("/w"), status(daysSinceCommit = 5), emptyList(), now, threshold))
        assertFalse(isInactive(worktreeAt("/w"), status(daysSinceCommit = 5), emptyList(), now, Duration.ofDays(7)))
    }

    @Test
    fun `an old base commit in a new worktree is not inactive`() {
        val fresh = WorktreeStatus(0, null, Divergence(0, 0), now.minus(Duration.ofDays(30)), createdAt = now)

        assertFalse(isInactive(worktreeAt("/w"), fresh, emptyList(), now, threshold))
    }

    private fun notStartedStatus(changes: Int) = WorktreeStatus(
        uncommittedChanges = changes,
        upstream = null,
        mainBranch = Divergence(ahead = 0, behind = 3),
        lastCommitAt = now.minus(Duration.ofDays(30)),
        neverCommitted = true,
    )

    @Test
    fun `a not started worktree without changes is new even when its base commit is old`() {
        val activity = worktreeActivity(worktreeAt("/home/dev/projects/TASK-1"), notStartedStatus(0), emptyList(), now)

        assertEquals(Activity.New, activity)
    }

    @Test
    fun `a not started worktree with changes is unfinished even when its base commit is old`() {
        val activity = worktreeActivity(worktreeAt("/home/dev/projects/TASK-1"), notStartedStatus(2), emptyList(), now)

        assertEquals(Activity.Unfinished, activity)
    }
}
