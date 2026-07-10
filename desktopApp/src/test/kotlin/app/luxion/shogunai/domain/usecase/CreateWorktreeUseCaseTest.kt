package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CreateWorktreeUseCaseTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "/home/dev/projects/main-repo",
        worktreesRoot = "/home/dev/projects",
        secretFiles = listOf("local.properties", "app-secrets.properties"),
    )

    private val worktreePath = "/home/dev/projects/TASK-123"
    private val secretsInBase = config.secretFiles.map { config.baseSecretPath(it) }
    private val baseWithSecrets = listOf(config.baseRepositoryPath) + secretsInBase

    @Test
    fun `creates worktree and copies secret files on success`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val fileManager = FakeFileManager(existing = baseWithSecrets)
        val useCase = CreateWorktreeUseCase(config, executor, fileManager)

        val result = useCase("TASK-123", BranchType.FEATURE)

        val worktree = result.getOrThrow()
        assertEquals(worktreePath, worktree.path)
        assertEquals("feature/TASK-123", worktree.branch)
        assertTrue(
            executor.executed("git", "worktree", "add", worktreePath, "-b", "feature/TASK-123"),
        )
        assertEquals(
            listOf(
                config.baseSecretPath("local.properties") to "$worktreePath/local.properties",
                config.baseSecretPath("app-secrets.properties") to "$worktreePath/app-secrets.properties",
            ),
            fileManager.copied,
        )
    }

    @Test
    fun `uses fix prefix for fix branches`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val useCase = CreateWorktreeUseCase(config, executor, FakeFileManager(baseWithSecrets))

        val worktree = useCase("TASK-456", BranchType.FIX).getOrThrow()

        assertEquals("fix/TASK-456", worktree.branch)
    }

    @Test
    fun `fails when destination worktree already exists`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val fileManager = FakeFileManager(existing = baseWithSecrets + worktreePath)
        val useCase = CreateWorktreeUseCase(config, executor, fileManager)

        val error = useCase("TASK-123", BranchType.FEATURE).exceptionOrNull()

        assertIs<WorktreeError.WorktreeAlreadyExists>(error)
        assertEquals(worktreePath, error.path)
        assertTrue(executor.executedCommands.isEmpty(), "no debe tocar Git si el destino existe")
    }

    @Test
    fun `fails when base repository is missing`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val useCase = CreateWorktreeUseCase(config, executor, FakeFileManager(existing = emptyList()))

        val error = useCase("TASK-123", BranchType.FEATURE).exceptionOrNull()

        assertIs<WorktreeError.BaseRepositoryNotFound>(error)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `fails when secret files are missing`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        // Solo existe uno de los dos secretos.
        val fileManager = FakeFileManager(
            existing = listOf(config.baseRepositoryPath, config.baseSecretPath("local.properties")),
        )
        val useCase = CreateWorktreeUseCase(config, executor, fileManager)

        val error = useCase("TASK-123", BranchType.FEATURE).exceptionOrNull()

        assertIs<WorktreeError.SecretFileNotFound>(error)
        assertEquals(listOf("app-secrets.properties"), error.files)
        assertTrue(executor.executedCommands.isEmpty(), "no debe crear el worktree si faltan secretos")
    }

    @Test
    fun `fails and does not copy when git worktree add fails`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it, exitCode = 128, stderr = "fatal: already exists") }
        val fileManager = FakeFileManager(existing = baseWithSecrets)
        val useCase = CreateWorktreeUseCase(config, executor, fileManager)

        val error = useCase("TASK-123", BranchType.FEATURE).exceptionOrNull()

        val gitError = assertIs<WorktreeError.GitCommandFailed>(error)
        assertEquals(128, gitError.exitCode)
        assertTrue(fileManager.copied.isEmpty())
    }

    @Test
    fun `rolls back worktree when secret copy fails`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val fileManager = FakeFileManager(
            existing = baseWithSecrets,
            failCopyFor = setOf(config.baseSecretPath("local.properties")),
        )
        val useCase = CreateWorktreeUseCase(config, executor, fileManager)

        val error = useCase("TASK-123", BranchType.FEATURE).exceptionOrNull()

        assertIs<WorktreeError.SecretCopyFailed>(error)
        assertTrue(
            executor.executed("git", "worktree", "remove", "--force", worktreePath),
            "debe revertir el worktree incompleto",
        )
    }

    @Test
    fun `fails when task id is blank`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val useCase = CreateWorktreeUseCase(config, executor, FakeFileManager(baseWithSecrets))

        val error = useCase("   ", BranchType.FEATURE).exceptionOrNull()

        assertIs<IllegalArgumentException>(error)
        assertFalse(executor.executedCommands.isNotEmpty())
    }
}
