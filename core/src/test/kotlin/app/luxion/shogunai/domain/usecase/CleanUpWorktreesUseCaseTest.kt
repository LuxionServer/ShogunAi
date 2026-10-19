package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.MainBranch
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.WorktreeStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CleanUpWorktreesUseCaseTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "/home/dev/projects/main-repo",
        worktreesRoot = "/home/dev/projects",
        secretFiles = emptyList(),
    )
    private val mainBranch = MainBranch("develop", "origin/develop")

    private val first = Worktree(path = "/home/dev/projects/TASK-1", branch = "feature/TASK-1")
    private val second = Worktree(path = "/home/dev/projects/TASK-2", branch = "feature/TASK-2")
    private val missing = Worktree(path = "/home/dev/projects/TASK-3", branch = "feature/TASK-3", isPrunable = true)

    private fun executor(overrides: Map<List<String>, (List<String>) -> CommandResult> = emptyMap()) =
        FakeShellCommandExecutor { command -> overrides[command]?.invoke(command) ?: success(command) }

    private fun status(merged: Boolean, changes: Int = 0) = WorktreeStatus(
        uncommittedChanges = changes,
        upstream = null,
        mainBranch = if (merged) Divergence(ahead = 0, behind = 2) else Divergence(ahead = 1, behind = 0),
        lastCommitAt = null,
    )

    @Test
    fun `selects merged clean worktrees and missing ones, never the main, locked or dirty`() {
        val main = Worktree(path = config.baseRepositoryPath, branch = "develop", isMain = true)
        val locked = Worktree(path = "/home/dev/projects/TASK-4", branch = "feature/TASK-4", isLocked = true)
        val dirty = Worktree(path = "/home/dev/projects/TASK-5", branch = "feature/TASK-5")
        val unmerged = Worktree(path = "/home/dev/projects/TASK-6", branch = "feature/TASK-6")
        val loading = Worktree(path = "/home/dev/projects/TASK-7", branch = "feature/TASK-7")
        val lockedMissing = missing.copy(path = "/home/dev/projects/TASK-8", isLocked = true)
        val statuses = mapOf(
            main.path to status(merged = true),
            first.path to status(merged = true),
            locked.path to status(merged = true),
            dirty.path to status(merged = true, changes = 2),
            unmerged.path to status(merged = false),
        )

        val candidates = selectCleanupCandidates(
            listOf(main, first, locked, dirty, unmerged, loading, missing, lockedMissing),
            statuses,
        )

        assertEquals(listOf(first, missing), candidates)
    }

    @Test
    fun `removes every worktree and prunes the missing ones`() = runTest {
        val executor = executor()

        val report = CleanUpWorktreesUseCase(config, executor)(listOf(first, second, missing), mainBranch, deleteBranches = false)
            .getOrThrow()

        assertEquals(listOf(first.path, second.path, missing.path), report.removed)
        assertTrue(report.failures.isEmpty())
        assertTrue(executor.executed("git", "worktree", "remove", first.path))
        assertTrue(executor.executed("git", "worktree", "prune"))
        assertTrue(executor.executedCommands.none { it.getOrNull(1) == "branch" })
    }

    @Test
    fun `does not prune when nothing is missing`() = runTest {
        val executor = executor()

        CleanUpWorktreesUseCase(config, executor)(listOf(first), mainBranch, deleteBranches = false).getOrThrow()

        assertTrue(executor.executedCommands.none { it == listOf("git", "worktree", "prune") })
    }

    @Test
    fun `deletes a branch still contained in the main branch with -D`() = runTest {
        val executor = executor()

        val report = CleanUpWorktreesUseCase(config, executor)(listOf(first), mainBranch, deleteBranches = true).getOrThrow()

        assertTrue(executor.executed("git", "merge-base", "--is-ancestor", "feature/TASK-1", "origin/develop"))
        assertTrue(executor.executed("git", "branch", "-D", "--", "feature/TASK-1"))
        assertTrue(report.keptBranches.isEmpty())
    }

    @Test
    fun `keeps a branch that is no longer contained in the main branch`() = runTest {
        val executor = executor(
            mapOf(listOf("git", "merge-base", "--is-ancestor", "feature/TASK-1", "origin/develop") to { c -> failure(c, stderr = "") }),
        )

        val report = CleanUpWorktreesUseCase(config, executor)(listOf(first), mainBranch, deleteBranches = true).getOrThrow()

        assertEquals(listOf(first.path), report.removed)
        assertEquals(listOf("feature/TASK-1"), report.keptBranches)
        assertTrue(executor.executedCommands.none { it.take(3) == listOf("git", "branch", "-D") })
    }

    @Test
    fun `branches of pruned worktrees go through the same deletion check`() = runTest {
        val executor = executor(
            mapOf(listOf("git", "merge-base", "--is-ancestor", "feature/TASK-3", "origin/develop") to { c -> failure(c, stderr = "") }),
        )

        val report = CleanUpWorktreesUseCase(config, executor)(listOf(first, missing), mainBranch, deleteBranches = true)
            .getOrThrow()

        assertTrue(executor.executed("git", "branch", "-D", "--", "feature/TASK-1"))
        assertEquals(listOf("feature/TASK-3"), report.keptBranches)
    }

    @Test
    fun `keeps branches when there is no main branch`() = runTest {
        val report = CleanUpWorktreesUseCase(config, executor())(listOf(first), null, deleteBranches = true).getOrThrow()

        assertEquals(listOf("feature/TASK-1"), report.keptBranches)
    }

    @Test
    fun `a failing removal is reported and the rest continue`() = runTest {
        val executor = executor(
            mapOf(
                listOf("git", "worktree", "remove", first.path) to { c ->
                    failure(c, exitCode = 128, stderr = "fatal: contains modified or untracked files, use --force to delete it")
                },
            ),
        )

        val report = CleanUpWorktreesUseCase(config, executor)(listOf(first, second), mainBranch, deleteBranches = true)
            .getOrThrow()

        assertEquals(listOf(second.path), report.removed)
        assertEquals(listOf(first.path), report.failures.map { it.path })
        assertEquals(128, assertIs<WorktreeError.GitCommandFailed>(report.failures.single().error).exitCode)
        assertTrue(executor.executedCommands.none { it == listOf("git", "branch", "-D", "--", "feature/TASK-1") })
    }

    @Test
    fun `closes the Herdr workspaces of removed and pruned worktrees`() = runTest {
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(first.path to "w2", second.path to "w3", missing.path to "w4"))

        val report = CleanUpWorktreesUseCase(config, executor(), CloseHerdrWorkspacesUseCase(config, herdr))(
            listOf(first, second, missing),
            mainBranch,
            deleteBranches = false,
        ).getOrThrow()

        assertEquals(listOf("w2", "w3", "w4"), herdr.closedWorkspaces)
        assertTrue(report.herdrFailures.isEmpty())
    }

    @Test
    fun `keeps the workspace of a worktree whose removal failed`() = runTest {
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(first.path to "w2", second.path to "w3"))
        val executor = executor(mapOf(listOf("git", "worktree", "remove", first.path) to { failure(it) }))

        CleanUpWorktreesUseCase(config, executor, CloseHerdrWorkspacesUseCase(config, herdr))(
            listOf(first, second),
            mainBranch,
            deleteBranches = false,
        ).getOrThrow()

        assertEquals(listOf("w3"), herdr.closedWorkspaces)
    }

    @Test
    fun `a failing close is reported next to the removal`() = runTest {
        val failure = WorktreeError.HerdrCommandFailed(listOf("herdr"), "workspace w2 not found")
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(first.path to "w2", second.path to "w3"), closeFailures = mapOf("w2" to failure))

        val report = CleanUpWorktreesUseCase(config, executor(), CloseHerdrWorkspacesUseCase(config, herdr))(
            listOf(first, second),
            mainBranch,
            deleteBranches = false,
        ).getOrThrow()

        assertEquals(listOf(first.path, second.path), report.removed)
        assertEquals(listOf(first.path), report.herdrFailures.map { it.path })
        assertEquals(listOf("w3"), herdr.closedWorkspaces)
    }
}
