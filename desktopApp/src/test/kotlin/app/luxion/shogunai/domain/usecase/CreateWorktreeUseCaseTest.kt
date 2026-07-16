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
        assertFalse(
            executor.executed("git", "worktree", "remove", "--force", worktreePath),
            "no debe revertir el worktree para fallos de git no relacionados con Git LFS",
        )
    }

    @Test
    fun `rolls back worktree and fails with GitLfsNotFound when git-lfs is missing`() = runTest {
        val executor = FakeShellCommandExecutor {
            if (it.first() == "git" && it.getOrNull(1) == "worktree" && it.getOrNull(2) == "add") {
                failure(
                    it,
                    exitCode = 2,
                    stderr = "This repository is configured for Git LFS but 'git-lfs' was not found on your path.",
                )
            } else {
                success(it)
            }
        }
        val fileManager = FakeFileManager(existing = baseWithSecrets)
        val useCase = CreateWorktreeUseCase(config, executor, fileManager)

        val error = useCase("TASK-123", BranchType.FEATURE).exceptionOrNull()

        assertIs<WorktreeError.GitLfsNotFound>(error)
        assertTrue(fileManager.copied.isEmpty())
        assertTrue(
            executor.executed("git", "worktree", "remove", "--force", worktreePath),
            "debe revertir el worktree que Git dejó a medias",
        )
        assertTrue(
            executor.executed("git", "branch", "-D", "feature/TASK-123"),
            "debe revertir la rama que Git dejó creada para que un reintento no choque con ella",
        )
    }

    @Test
    fun `rolls back worktree and fails with GitLfsNotFound when the LFS smudge filter is missing`() = runTest {
        // Mensaje distinto al del hook post-checkout: ocurre cuando el commit
        // checkouteado trae contenido LFS y el checkout falla al invocar el
        // filtro smudge, antes de que el hook llegue a ejecutarse.
        val executor = FakeShellCommandExecutor {
            if (it.first() == "git" && it.getOrNull(1) == "worktree" && it.getOrNull(2) == "add") {
                failure(
                    it,
                    exitCode = 128,
                    stderr = "git-lfs filter-process: git-lfs: command not found\n" +
                        "fatal: the remote end hung up unexpectedly",
                )
            } else {
                success(it)
            }
        }
        val fileManager = FakeFileManager(existing = baseWithSecrets)
        val useCase = CreateWorktreeUseCase(config, executor, fileManager)

        val error = useCase("TASK-123", BranchType.FEATURE).exceptionOrNull()

        assertIs<WorktreeError.GitLfsNotFound>(error)
        assertTrue(fileManager.copied.isEmpty())
        assertTrue(
            executor.executed("git", "branch", "-D", "feature/TASK-123"),
            "debe revertir la rama que Git dejó creada para que un reintento no choque con ella",
        )
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
