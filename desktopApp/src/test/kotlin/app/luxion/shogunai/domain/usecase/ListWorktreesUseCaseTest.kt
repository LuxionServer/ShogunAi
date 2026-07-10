package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
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
    fun `fails when git list fails`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it, exitCode = 129, stderr = "not a git repo") }
        val useCase = ListWorktreesUseCase(config, executor)

        val error = useCase().exceptionOrNull()

        assertIs<WorktreeError.GitCommandFailed>(error)
    }

    @Test
    fun `returns empty list for empty output`() = runTest {
        val executor = FakeShellCommandExecutor { success(it, stdout = "") }
        val useCase = ListWorktreesUseCase(config, executor)

        assertTrue(useCase().getOrThrow().isEmpty())
    }
}
