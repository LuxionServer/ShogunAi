package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BaseBranchesTest {

    private val config = ProjectConfig(baseRepositoryPath = "/home/dev/projects/main-repo", worktreesRoot = "/home/dev/projects", secretFiles = emptyList())

    @Test
    fun `reads every recorded base in one call`() = runTest {
        val output = """
            branch.feature/TASK-1.gh-merge-base develop
            branch.Feature/v1.2.fix.gh-merge-base epic/redesign
            branch.feature/TASK-2.GH-MERGE-BASE main
        """.trimIndent()
        val executor = FakeShellCommandExecutor { success(it, "$output\n") }

        val bases = ReadBaseBranchesUseCase(config, executor)().getOrThrow()

        assertEquals(
            mapOf("feature/TASK-1" to "develop", "Feature/v1.2.fix" to "epic/redesign", "feature/TASK-2" to "main"),
            bases,
        )
        assertEquals(listOf(listOf("git", "config", "--get-regexp", "^branch\\..*\\.gh-merge-base$")), executor.executedCommands)
        assertEquals(listOf<String?>(config.baseRepositoryPath), executor.workingDirectories)
    }

    @Test
    fun `no entries is an empty map`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it, exitCode = 1, stderr = "") }

        assertEquals(emptyMap(), ReadBaseBranchesUseCase(config, executor)().getOrThrow())
    }

    @Test
    fun `other failures are reported`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it, exitCode = 128, stderr = "fatal: not a git repository") }

        assertIs<WorktreeError.GitCommandFailed>(ReadBaseBranchesUseCase(config, executor)().exceptionOrNull())
    }

    @Test
    fun `sets a local branch as the base`() = runTest {
        val executor = FakeShellCommandExecutor {
            if ("--symbolic-full-name" in it) success(it, "refs/heads/develop\n") else success(it)
        }

        assertEquals("develop", SetBaseBranchUseCase(config, executor)("feature/login", "develop").getOrThrow())
        assertTrue(executor.executed("git", "config", "branch.feature/login.gh-merge-base", "develop"))
    }

    @Test
    fun `sets a remote branch without its remote`() = runTest {
        val executor = FakeShellCommandExecutor {
            if ("--symbolic-full-name" in it) success(it, "refs/remotes/origin/epic/redesign\n") else success(it)
        }

        assertEquals("epic/redesign", SetBaseBranchUseCase(config, executor)("feature/login", "origin/epic/redesign").getOrThrow())
        assertTrue(executor.executed("git", "config", "branch.feature/login.gh-merge-base", "epic/redesign"))
    }

    @Test
    fun `refuses what isn't a branch and writes nothing`() = runTest {
        val executor = FakeShellCommandExecutor {
            if ("--symbolic-full-name" in it) success(it, "refs/tags/v1.2.0\n") else success(it)
        }

        val error = SetBaseBranchUseCase(config, executor)("feature/login", "v1.2.0").exceptionOrNull()

        assertIs<WorktreeError.NotABranch>(error)
        assertEquals("v1.2.0", error.ref)
        assertFalse(executor.executedCommands.any { it.take(2) == listOf("git", "config") })
    }
}
