package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.ScriptedShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.model.OverlapSkipReason
import app.luxion.shogunai.domain.model.OverlapWorktree
import app.luxion.shogunai.domain.model.OverlappingFile
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.SkippedWorktree
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReadWorktreeOverlapUseCaseTest {

    private val repository = "/home/dev/projects/main-repo"
    private val config = ProjectConfig(baseRepositoryPath = repository, worktreesRoot = "/home/dev/projects", secretFiles = emptyList(), mainBranch = "develop")
    private val main = Worktree(repository, "develop", isMain = true)
    private val task1 = Worktree("/home/dev/projects/TASK-1", "feature/TASK-1")
    private val task2 = Worktree("/home/dev/projects/TASK-2", "feature/TASK-2")
    private val task3 = Worktree("/home/dev/projects/TASK-3", "feature/TASK-3")

    private class RepositoryGit(
        private val remoteBranches: Set<String> = setOf("develop"),
        private val recordedBases: String = "",
        private val tracked: Map<String, String> = emptyMap(),
        private val untracked: Map<String, String> = emptyMap(),
        private val failures: Map<Pair<String, String>, CommandResult> = emptyMap(),
        private val unreachableRefs: Boolean = false,
    ) : ScriptedShellCommandExecutor() {
        val commands = mutableListOf<Pair<String?, List<String>>>()

        override suspend fun respond(command: List<String>, workingDirectory: String?): CommandResult {
            commands += workingDirectory to command
            val subcommand = command[1]
            failures[workingDirectory.orEmpty() to subcommand]?.let { return it.copy(command = command) }
            if (unreachableRefs && subcommand == "rev-parse") throw java.io.IOException("git couldn't start")
            return when (subcommand) {
                "rev-parse" -> result(command, exitCode = if (command.last().removePrefix("refs/remotes/origin/") in remoteBranches) 0 else 1)
                "config" -> if (recordedBases.isEmpty()) result(command, exitCode = 1) else result(command, recordedBases)
                "merge-base" -> result(command, "base-of-${command[2]}\n")
                "diff" -> result(command, tracked[workingDirectory].orEmpty())
                "ls-files" -> result(command, untracked[workingDirectory].orEmpty())
                else -> result(command, exitCode = 1)
            }
        }

        fun commandsIn(path: String): List<List<String>> = commands.filter { it.first == path }.map { it.second }
    }

    private fun useCase(git: RepositoryGit, provisioning: RepositoryProvisioning = RepositoryProvisioning(), unreadable: Boolean = false) =
        ReadWorktreeOverlapUseCase(
            config,
            git,
            FakeRepositoryProvisioningSource(mapOf(repository to provisioning), unreadable = if (unreadable) setOf(repository) else emptySet()),
        )

    @Test
    fun `lists the files changed by two worktrees with their bases`() = runTest {
        val git = RepositoryGit(
            tracked = mapOf(task1.path to nul("core/Engine.kt", "README.md"), task2.path to nul("core/Engine.kt")),
        )

        val overlap = useCase(git)(listOf(main, task1, task2)).getOrThrow()

        assertEquals(
            listOf(
                OverlappingFile(
                    "core/Engine.kt",
                    listOf(OverlapWorktree(task1.path, task1.branch, "develop"), OverlapWorktree(task2.path, task2.branch, "develop")),
                ),
            ),
            overlap.files,
        )
        assertEquals(emptyList(), overlap.skipped)
        assertEquals(
            listOf(
                listOf("git", "merge-base", "origin/develop", "HEAD"),
                listOf("git", "diff", "--name-only", "--no-renames", "-z", "base-of-origin/develop", "--", "."),
                listOf("git", "ls-files", "--others", "--exclude-standard", "-z", "--", "."),
            ),
            git.commandsIn(task1.path),
        )
    }

    @Test
    fun `counts committed, uncommitted and untracked files once each`() = runTest {
        val git = RepositoryGit(
            tracked = mapOf(task1.path to nul("src/App.kt", "docs/a file.md"), task2.path to nul("docs/a file.md")),
            untracked = mapOf(task1.path to nul("src/New.kt"), task2.path to nul("src/New.kt", "src/App.kt")),
        )

        val files = useCase(git)(listOf(task1, task2)).getOrThrow().files

        assertEquals(listOf("docs/a file.md", "src/App.kt", "src/New.kt"), files.map { it.path })
        assertTrue(files.all { file -> file.worktrees.map { it.path } == listOf(task1.path, task2.path) })
    }

    @Test
    fun `compares each worktree with its own base and reports different bases together`() = runTest {
        val git = RepositoryGit(
            remoteBranches = setOf("develop", "epic/redesign"),
            recordedBases = "branch.feature/TASK-1.gh-merge-base develop\nbranch.feature/TASK-3.gh-merge-base epic/redesign\n",
            tracked = mapOf(task1.path to nul("core/Engine.kt"), task3.path to nul("core/Engine.kt")),
        )

        val overlap = useCase(git)(listOf(task1, task3)).getOrThrow()

        assertEquals(listOf("develop", "epic/redesign"), overlap.files.single().worktrees.map { it.base })
        assertTrue(listOf("git", "merge-base", "origin/epic/redesign", "HEAD") in git.commandsIn(task3.path))
    }

    @Test
    fun `a detached worktree is compared with the main branch`() = runTest {
        val detached = Worktree("/home/dev/projects/review", null)
        val git = RepositoryGit(tracked = mapOf(task1.path to nul("a.kt"), detached.path to nul("a.kt")))

        val worktrees = useCase(git)(listOf(task1, detached)).getOrThrow().files.single().worktrees

        assertEquals(OverlapWorktree(detached.path, null, "develop"), worktrees.last())
    }

    @Test
    fun `scoped to a worktree only lists the files it shares`() = runTest {
        val git = RepositoryGit(
            tracked = mapOf(
                task1.path to nul("core/Engine.kt"),
                task2.path to nul("core/Engine.kt", "docs/guide.md"),
                task3.path to nul("docs/guide.md"),
            ),
        )
        val read = useCase(git)

        assertEquals(listOf("core/Engine.kt"), read(listOf(task1, task2, task3), target = task1).getOrThrow().files.map { it.path })
        assertEquals(listOf("core/Engine.kt", "docs/guide.md"), read(listOf(task1, task2, task3)).getOrThrow().files.map { it.path })
    }

    @Test
    fun `leaves out the declared files unless asked for all`() = runTest {
        val git = RepositoryGit()
        val provisioning = RepositoryProvisioning(overlapIgnore = listOf("docs/roadmap*.md", "openspec/specs/**"))

        useCase(git, provisioning)(listOf(task1)).getOrThrow()

        val expected = listOf("--", ".", ":(exclude,glob)docs/roadmap*.md", ":(exclude,glob)openspec/specs/**")
        assertEquals(expected, git.commandsIn(task1.path)[1].takeLast(4))
        assertEquals(expected, git.commandsIn(task1.path)[2].takeLast(4))
    }

    @Test
    fun `asked for all it doesn't read the declaration`() = runTest {
        val git = RepositoryGit()

        val overlap = useCase(git, unreadable = true)(listOf(task1), includeIgnored = true)

        assertTrue(overlap.isSuccess)
        assertEquals(listOf("--", "."), git.commandsIn(task1.path)[2].takeLast(2))
    }

    @Test
    fun `an invalid declaration fails the read`() = runTest {
        val overlap = useCase(RepositoryGit(), unreadable = true)(listOf(task1))

        assertIs<WorktreeError.InvalidRepositoryConfig>(overlap.exceptionOrNull())
    }

    @Test
    fun `skips prunable worktrees and leaves out bare entries`() = runTest {
        val prunable = Worktree("/home/dev/projects/gone", "feature/gone", isPrunable = true)
        val bare = Worktree("/home/dev/projects/bare.git", null, isBare = true)
        val git = RepositoryGit()

        val overlap = useCase(git)(listOf(prunable, bare, task1)).getOrThrow()

        assertEquals(listOf(SkippedWorktree(prunable.path, prunable.branch, OverlapSkipReason.PRUNABLE)), overlap.skipped)
        assertEquals(emptyList(), git.commandsIn(prunable.path))
        assertEquals(emptyList(), git.commandsIn(bare.path))
    }

    @Test
    fun `skips worktrees without a base`() = runTest {
        val git = RepositoryGit(remoteBranches = emptySet())

        val overlap = useCase(git)(listOf(task1)).getOrThrow()

        assertEquals(listOf(SkippedWorktree(task1.path, task1.branch, OverlapSkipReason.NO_BASE)), overlap.skipped)
    }

    @Test
    fun `a main branch that can't be resolved leaves worktrees without a record unbased`() = runTest {
        val git = RepositoryGit(unreachableRefs = true)

        val overlap = useCase(git)(listOf(task1)).getOrThrow()

        assertEquals(listOf(SkippedWorktree(task1.path, task1.branch, OverlapSkipReason.NO_BASE)), overlap.skipped)
    }

    @Test
    fun `skips a worktree whose Git reads fail and still compares the others`() = runTest {
        val unrelated = "fatal: no merge base found"
        listOf("merge-base", "diff", "ls-files").forEach { subcommand ->
            val git = RepositoryGit(
                tracked = mapOf(task1.path to nul("a.kt"), task2.path to nul("a.kt"), task3.path to nul("a.kt")),
                failures = mapOf((task3.path to subcommand) to result(emptyList(), exitCode = 1, stderr = "\n$unrelated\nmore")),
            )

            val overlap = useCase(git)(listOf(task1, task2, task3)).getOrThrow()

            assertEquals(listOf(SkippedWorktree(task3.path, task3.branch, OverlapSkipReason.READ_FAILED, unrelated)), overlap.skipped)
            assertEquals(listOf(task1.path, task2.path), overlap.files.single().worktrees.map { it.path })
        }
    }

    @Test
    fun `a failure without output is described by its exit code`() = runTest {
        val git = RepositoryGit(failures = mapOf((task1.path to "merge-base") to result(emptyList(), exitCode = 128)))

        val skipped = useCase(git)(listOf(task1)).getOrThrow().skipped.single()

        assertEquals("exit code 128", skipped.detail)
        assertFalse(git.commandsIn(task1.path).any { it[1] == "diff" })
    }
}

private fun result(command: List<String>, stdout: String = "", exitCode: Int = 0, stderr: String = "") =
    CommandResult(command = command, exitCode = exitCode, stdout = stdout, stderr = stderr)

private fun nul(vararg paths: String) = paths.joinToString("") { "$it\u0000" }
