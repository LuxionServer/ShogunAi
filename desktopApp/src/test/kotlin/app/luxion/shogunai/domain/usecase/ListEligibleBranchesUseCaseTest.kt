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

class ListEligibleBranchesUseCaseTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "/home/dev/projects/main-repo",
        worktreesRoot = "/home/dev/projects",
        secretFiles = listOf("local.properties"),
    )

    private val branches = "develop\nfeature/TASK-123\nfeature/TASK-456\n"

    private val porcelain = """
        worktree /home/dev/projects/main-repo
        HEAD abc123
        branch refs/heads/develop

        worktree /home/dev/projects/TASK-123
        HEAD def456
        branch refs/heads/feature/TASK-123
    """.trimIndent()

    private fun executor(
        branchesOutput: String = branches,
        porcelainOutput: String = porcelain,
        failBranches: Boolean = false,
        failWorktrees: Boolean = false,
    ) = FakeShellCommandExecutor { command ->
        when (command.getOrNull(1)) {
            "for-each-ref" ->
                if (failBranches) failure(command, stderr = "not a git repo") else success(command, stdout = branchesOutput)
            "worktree" ->
                if (failWorktrees) failure(command, stderr = "not a git repo") else success(command, stdout = porcelainOutput)
            else -> success(command)
        }
    }

    @Test
    fun `returns all branches when none are checked out`() = runTest {
        val useCase = ListEligibleBranchesUseCase(config, executor(porcelainOutput = ""))

        val eligible = useCase().getOrThrow()

        assertEquals(listOf("develop", "feature/TASK-123", "feature/TASK-456"), eligible)
    }

    @Test
    fun `excludes branches already checked out in another worktree`() = runTest {
        val useCase = ListEligibleBranchesUseCase(config, executor())

        val eligible = useCase().getOrThrow()

        assertEquals(listOf("feature/TASK-456"), eligible)
    }

    @Test
    fun `excludes the branch checked out in the base repository`() = runTest {
        val basePorcelain = """
            worktree /home/dev/projects/main-repo
            HEAD abc123
            branch refs/heads/develop
        """.trimIndent()
        val useCase = ListEligibleBranchesUseCase(config, executor(porcelainOutput = basePorcelain))

        val eligible = useCase().getOrThrow()

        assertEquals(listOf("feature/TASK-123", "feature/TASK-456"), eligible)
    }

    @Test
    fun `fails when listing branches fails`() = runTest {
        val useCase = ListEligibleBranchesUseCase(config, executor(failBranches = true))

        assertIs<WorktreeError.GitCommandFailed>(useCase().exceptionOrNull())
    }

    @Test
    fun `fails when listing worktrees fails`() = runTest {
        val useCase = ListEligibleBranchesUseCase(config, executor(failWorktrees = true))

        assertIs<WorktreeError.GitCommandFailed>(useCase().exceptionOrNull())
    }
}
