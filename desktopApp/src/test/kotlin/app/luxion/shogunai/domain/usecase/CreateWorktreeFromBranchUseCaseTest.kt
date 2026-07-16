package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CreateWorktreeFromBranchUseCaseTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "/home/dev/projects/main-repo",
        worktreesRoot = "/home/dev/projects",
        secretFiles = listOf("local.properties", "app-secrets.properties"),
    )

    private val branch = "hotfix/security-patch"
    private val worktreePath = "/home/dev/projects/hotfix-security-patch"
    private val secretsInBase = config.secretFiles.map { config.baseSecretPath(it) }
    private val baseWithSecrets = listOf(config.baseRepositoryPath) + secretsInBase

    /** Default responder: the branch exists locally and every Git command succeeds. */
    private fun executor(addResponse: (List<String>) -> CommandResult = { success(it) }) =
        FakeShellCommandExecutor {
            when {
                it.getOrNull(1) == "branch" && it.getOrNull(2) == "--list" -> success(it, stdout = "  $branch\n")
                it.getOrNull(1) == "worktree" && it.getOrNull(2) == "add" -> addResponse(it)
                else -> success(it)
            }
        }

    @Test
    fun `creates worktree and copies secret files on success`() = runTest {
        val exec = executor()
        val fileManager = FakeFileManager(existing = baseWithSecrets)
        val useCase = CreateWorktreeFromBranchUseCase(config, exec, fileManager)

        val worktree = useCase(branch).getOrThrow()

        assertEquals(worktreePath, worktree.path)
        assertEquals(branch, worktree.branch)
        assertTrue(exec.executed("git", "worktree", "add", worktreePath, branch))
        assertEquals(
            listOf(
                config.baseSecretPath("local.properties") to "$worktreePath/local.properties",
                config.baseSecretPath("app-secrets.properties") to "$worktreePath/app-secrets.properties",
            ),
            fileManager.copied,
        )
    }

    @Test
    fun `fails when the branch does not exist locally`() = runTest {
        val exec = FakeShellCommandExecutor {
            if (it.getOrNull(1) == "branch" && it.getOrNull(2) == "--list") success(it, stdout = "") else success(it)
        }
        val useCase = CreateWorktreeFromBranchUseCase(config, exec, FakeFileManager(existing = baseWithSecrets))

        val error = useCase(branch).exceptionOrNull()

        val branchError = assertIs<WorktreeError.BranchNotFound>(error)
        assertEquals(branch, branchError.branch)
        assertFalse(exec.executed("git", "worktree", "add", worktreePath, branch))
    }

    @Test
    fun `fails when the branch is already checked out elsewhere`() = runTest {
        val exec = executor(addResponse = {
            failure(
                it,
                exitCode = 128,
                stderr = "fatal: '$branch' is already checked out at '/home/dev/projects/main-repo'",
            )
        })
        val fileManager = FakeFileManager(existing = baseWithSecrets)
        val useCase = CreateWorktreeFromBranchUseCase(config, exec, fileManager)

        val error = useCase(branch).exceptionOrNull()

        val checkedOutError = assertIs<WorktreeError.BranchAlreadyCheckedOut>(error)
        assertEquals(branch, checkedOutError.branch)
        assertEquals("/home/dev/projects/main-repo", checkedOutError.path)
        assertTrue(fileManager.copied.isEmpty())
    }

    @Test
    fun `fails when destination worktree already exists`() = runTest {
        val exec = executor()
        val fileManager = FakeFileManager(existing = baseWithSecrets + worktreePath)
        val useCase = CreateWorktreeFromBranchUseCase(config, exec, fileManager)

        val error = useCase(branch).exceptionOrNull()

        assertIs<WorktreeError.WorktreeAlreadyExists>(error)
        assertTrue(exec.executedCommands.isEmpty(), "should not touch Git if the destination already exists")
    }

    @Test
    fun `fails when secret files are missing`() = runTest {
        val exec = executor()
        val fileManager = FakeFileManager(
            existing = listOf(config.baseRepositoryPath, config.baseSecretPath("local.properties")),
        )
        val useCase = CreateWorktreeFromBranchUseCase(config, exec, fileManager)

        val error = useCase(branch).exceptionOrNull()

        val secretError = assertIs<WorktreeError.SecretFileNotFound>(error)
        assertEquals(listOf("app-secrets.properties"), secretError.files)
        assertFalse(exec.executed("git", "worktree", "add", worktreePath, branch))
    }

    @Test
    fun `rolls back worktree but keeps the branch when git-lfs is missing`() = runTest {
        val exec = executor(addResponse = {
            failure(
                it,
                exitCode = 2,
                stderr = "This repository is configured for Git LFS but 'git-lfs' was not found on your path.",
            )
        })
        val fileManager = FakeFileManager(existing = baseWithSecrets)
        val useCase = CreateWorktreeFromBranchUseCase(config, exec, fileManager)

        val error = useCase(branch).exceptionOrNull()

        assertIs<WorktreeError.GitLfsNotFound>(error)
        assertTrue(fileManager.copied.isEmpty())
        assertTrue(
            exec.executed("git", "worktree", "remove", "--force", worktreePath),
            "should roll back the worktree that Git left half-done",
        )
        assertFalse(
            exec.executed("git", "branch", "-D", branch),
            "must never delete a branch that already existed before this call",
        )
    }

    @Test
    fun `rolls back worktree when secret copy fails`() = runTest {
        val exec = executor()
        val fileManager = FakeFileManager(
            existing = baseWithSecrets,
            failCopyFor = setOf(config.baseSecretPath("local.properties")),
        )
        val useCase = CreateWorktreeFromBranchUseCase(config, exec, fileManager)

        val error = useCase(branch).exceptionOrNull()

        assertIs<WorktreeError.SecretCopyFailed>(error)
        assertTrue(
            exec.executed("git", "worktree", "remove", "--force", worktreePath),
            "should roll back the incomplete worktree",
        )
        assertFalse(exec.executed("git", "branch", "-D", branch))
    }

    @Test
    fun `fails when branch is blank`() = runTest {
        val exec = executor()
        val useCase = CreateWorktreeFromBranchUseCase(config, exec, FakeFileManager(baseWithSecrets))

        val error = useCase("   ").exceptionOrNull()

        assertIs<IllegalArgumentException>(error)
        assertTrue(exec.executedCommands.isEmpty())
    }
}
