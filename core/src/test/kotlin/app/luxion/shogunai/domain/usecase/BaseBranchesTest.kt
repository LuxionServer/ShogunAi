package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.MainBranch
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

    @Test
    fun `resolves recorded bases on the remote first, then locally, leaving out missing ones`() = runTest {
        val output = """
            branch.feature/TASK-1.gh-merge-base epic/redesign
            branch.feature/TASK-2.gh-merge-base epic/redesign
            branch.feature/TASK-3.gh-merge-base feature/TASK-1
            branch.feature/TASK-4.gh-merge-base epic/gone
        """.trimIndent()
        val existing = setOf("refs/remotes/origin/epic/redesign", "refs/heads/feature/TASK-1")
        val executor = FakeShellCommandExecutor {
            when {
                "--get-regexp" in it -> success(it, "$output\n")
                it.last() in existing -> success(it)
                else -> failure(it, exitCode = 1, stderr = "")
            }
        }

        val bases = ResolveRecordedBasesUseCase(config, executor)()

        assertEquals(
            mapOf(
                "feature/TASK-1" to MainBranch("epic/redesign", "origin/epic/redesign"),
                "feature/TASK-2" to MainBranch("epic/redesign", "origin/epic/redesign"),
                "feature/TASK-3" to MainBranch("feature/TASK-1", "feature/TASK-1"),
            ),
            bases,
        )
        assertEquals(1, executor.executedCommands.count { it.last() == "refs/remotes/origin/epic/redesign" })
    }

    @Test
    fun `a failed read resolves no base`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it, exitCode = 128, stderr = "fatal: not a git repository") }

        assertEquals(emptyMap(), ResolveRecordedBasesUseCase(config, executor)())
    }

    @Test
    fun `resolves the recorded base of one branch`() = runTest {
        val executor = FakeShellCommandExecutor {
            when {
                "--get" in it -> success(it, "epic/redesign\n")
                it.last() == "refs/heads/epic/redesign" -> success(it)
                else -> failure(it, exitCode = 1, stderr = "")
            }
        }

        assertEquals(MainBranch("epic/redesign", "epic/redesign"), recordedBaseRef(executor, config, "feature/TASK-1"))
        assertTrue(executor.executed("git", "config", "--get", "branch.feature/TASK-1.gh-merge-base"))
    }

    @Test
    fun `a branch without a record has no base`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it, exitCode = 1, stderr = "") }

        assertEquals(null, recordedBaseRef(executor, config, "feature/TASK-1"))
    }

    @Test
    fun `only base entries of named branches with a value are read`() {
        val output = listOf(
            "branch.feature/a.gh-merge-base develop",
            "remote.origin.gh-merge-base develop",
            "branch.feature/b.other develop",
            "branch.feature/c.gh-merge-base",
            "branch..gh-merge-base develop",
        ).joinToString("\n")

        assertEquals(mapOf("feature/a" to "develop"), parseBaseBranches(output))
    }

    @Test
    fun `a status one with output is a failure`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it, exitCode = 1, stderr = "").copy(stdout = "unexpected") }

        assertIs<WorktreeError.GitCommandFailed>(ReadBaseBranchesUseCase(config, executor)().exceptionOrNull())
    }
}
