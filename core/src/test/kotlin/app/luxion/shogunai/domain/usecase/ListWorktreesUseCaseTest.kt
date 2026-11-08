package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListWorktreesUseCaseTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "/home/dev/projects/main-repo",
        worktreesRoot = "/home/dev/projects",
        secretFiles = listOf("local.properties"),
    )

    private val porcelain = """
        worktree /home/dev/projects/main-repo
        HEAD abc123
        branch refs/heads/develop

        worktree /home/dev/projects/TASK-123
        HEAD def456
        branch refs/heads/feature/TASK-123

        worktree /home/dev/projects/TASK-789
        HEAD 789aaa
        detached
    """.trimIndent()

    @Test
    fun `parses worktrees and flags the main one`() = runTest {
        val executor = FakeShellCommandExecutor { success(it, stdout = porcelain) }
        val useCase = ListWorktreesUseCase(config, executor)

        val worktrees = useCase().getOrThrow()

        assertEquals(3, worktrees.size)

        val main = worktrees[0]
        assertEquals("/home/dev/projects/main-repo", main.path)
        assertEquals("develop", main.branch)
        assertEquals("abc123", main.head)
        assertTrue(main.isMain)

        val feature = worktrees[1]
        assertEquals("feature/TASK-123", feature.branch)
        assertEquals(false, feature.isMain)

        val detached = worktrees[2]
        assertNull(detached.branch)
    }

    @Test
    fun `flags the main worktree when the project's path has a trailing slash`() = runTest {
        val executor = FakeShellCommandExecutor { success(it, stdout = porcelain) }
        val slashed = config.copy(baseRepositoryPath = "/home/dev/projects/main-repo/")

        val worktrees = ListWorktreesUseCase(slashed, executor)().getOrThrow()

        assertEquals(listOf(true, false, false), worktrees.map { it.isMain })
    }

    @Test
    fun `flags the main worktree when the project's path is a symbolic link`() = runTest {
        val root = Files.createTempDirectory("main-worktree").toRealPath()
        val real = Files.createDirectory(root.resolve("main-repo"))
        val link = Files.createSymbolicLink(root.resolve("link"), real)
        val executor = FakeShellCommandExecutor { success(it, stdout = "worktree $real\nHEAD abc123\nbranch refs/heads/develop\n") }

        val worktrees = ListWorktreesUseCase(config.copy(baseRepositoryPath = link.toString()), executor)().getOrThrow()

        assertTrue(worktrees.single().isMain)
    }

    @Test
    fun `a submodule's main worktree is listed at its working tree instead of its Git folder`() = runTest {
        val executor = FakeShellCommandExecutor {
            success(it, stdout = "worktree /home/dev/projects/super/.git/modules/lib\nHEAD abc123\nbranch refs/heads/main\n")
        }
        val submodule = config.copy(baseRepositoryPath = "/home/dev/projects/super/lib")

        val main = ListWorktreesUseCase(submodule, executor)().getOrThrow().single()

        assertEquals("/home/dev/projects/super/lib", main.path)
        assertTrue(main.isMain)
    }

    @Test
    fun `fails when git list fails`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it, exitCode = 129, stderr = "not a git repo") }
        val useCase = ListWorktreesUseCase(config, executor)

        val error = useCase().exceptionOrNull()

        assertIs<WorktreeError.GitCommandFailed>(error)
    }

    @Test
    fun `a missing base repository is reported without running git`() = runTest {
        val executor = FakeShellCommandExecutor { success(it, stdout = porcelain) }
        val useCase = ListWorktreesUseCase(config, executor, FakeFileManager())

        val error = useCase().exceptionOrNull()

        assertEquals(config.baseRepositoryPath, assertIs<WorktreeError.BaseRepositoryNotFound>(error).path)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `returns empty list for empty output`() = runTest {
        val executor = FakeShellCommandExecutor { success(it, stdout = "") }
        val useCase = ListWorktreesUseCase(config, executor)

        assertTrue(useCase().getOrThrow().isEmpty())
    }

    @Test
    fun `flags locked and prunable worktrees with or without a reason`() {
        val worktrees = parseWorktreeList(
            """
            worktree /home/dev/projects/TASK-1
            HEAD aaa111
            branch refs/heads/feature/TASK-1
            locked

            worktree /home/dev/projects/TASK-2
            HEAD bbb222
            branch refs/heads/feature/TASK-2
            locked agent session

            worktree /home/dev/projects/TASK-3
            HEAD ccc333
            branch refs/heads/feature/TASK-3
            prunable gitdir file points to non-existent location

            worktree /home/dev/projects/TASK-4
            HEAD ddd444
            branch refs/heads/feature/TASK-4
            """.trimIndent(),
        )

        assertEquals(listOf(true, true, false, false), worktrees.map { it.isLocked })
        assertEquals(listOf(false, false, true, false), worktrees.map { it.isPrunable })
    }
    @Test
    fun `reads a bare main repository and a prunable worktree without a reason`() {
        val worktrees = parseWorktreeList(
            """
            worktree /home/dev/projects/main-repo.git
            bare

            worktree /home/dev/projects/TASK-1
            HEAD aaa111
            branch refs/heads/feature/TASK-1
            prunable
            """.trimIndent(),
        )

        assertEquals(listOf(true, false), worktrees.map { it.isBare })
        assertEquals(listOf(false, true), worktrees.map { it.isPrunable })
    }
    @Test
    fun `only the first entry can be a submodule's main worktree`() = runTest {
        val executor = FakeShellCommandExecutor {
            success(it, stdout = "worktree /home/dev/projects/other\nHEAD abc123\nbranch refs/heads/main\n\nworktree /home/dev/projects/super/.git/modules/lib\nHEAD abc124\nbranch refs/heads/task\n")
        }

        val worktrees = ListWorktreesUseCase(config.copy(baseRepositoryPath = "/home/dev/projects/super/lib"), executor)().getOrThrow()

        assertEquals(listOf("/home/dev/projects/other", "/home/dev/projects/super/.git/modules/lib"), worktrees.map { it.path })
        assertTrue(worktrees.none { it.isMain })
    }
}
