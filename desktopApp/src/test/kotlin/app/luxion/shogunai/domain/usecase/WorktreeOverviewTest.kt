package app.luxion.shogunai.domain.usecase

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

    private fun status(changes: Int = 0, daysSinceCommit: Long? = 20) = WorktreeStatus(
        uncommittedChanges = changes,
        upstream = null,
        mainBranch = null,
        lastCommitAt = daysSinceCommit?.let { now.minus(Duration.ofDays(it)) },
    )

    @Test
    fun `a clean worktree without agents and an old last commit is inactive`() {
        assertTrue(isInactive(worktreeAt("/w"), status(), emptyList(), now))
    }

    @Test
    fun `recent work is not inactive`() {
        assertFalse(isInactive(worktreeAt("/w"), status(daysSinceCommit = 3), emptyList(), now))
    }

    @Test
    fun `pending changes or a running agent keep a worktree active`() {
        assertFalse(isInactive(worktreeAt("/w"), status(changes = 1), emptyList(), now))
        assertFalse(isInactive(worktreeAt("/w"), status(), listOf(RunningAgent("claude")), now))
    }

    @Test
    fun `without a last commit or a status it is not inactive`() {
        assertFalse(isInactive(worktreeAt("/w"), status(daysSinceCommit = null), emptyList(), now))
        assertFalse(isInactive(worktreeAt("/w"), null, emptyList(), now))
    }

    @Test
    fun `the main worktree is never inactive`() {
        val main = Worktree(path = "/w", branch = "main", isMain = true)

        assertFalse(isInactive(main, status(), emptyList(), now))
    }
}
