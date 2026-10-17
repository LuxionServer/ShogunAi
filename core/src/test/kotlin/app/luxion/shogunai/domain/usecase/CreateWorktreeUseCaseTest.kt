package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProvisioning
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
        val useCase = CreateWorktreeUseCase(config, executor, fileManager, FakeRepositoryProvisioningSource())

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
    fun `appends the start point after the branch when given`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val fileManager = FakeFileManager(existing = baseWithSecrets)
        val useCase = CreateWorktreeUseCase(config, executor, fileManager, FakeRepositoryProvisioningSource())

        useCase("TASK-123", BranchType.FEATURE, startPoint = "abc123").getOrThrow()

        assertTrue(
            executor.executed("git", "worktree", "add", worktreePath, "-b", "feature/TASK-123", "abc123"),
        )
    }

    @Test
    fun `uses fix prefix for fix branches`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val useCase = CreateWorktreeUseCase(config, executor, FakeFileManager(baseWithSecrets), FakeRepositoryProvisioningSource())

        val worktree = useCase("TASK-456", BranchType.FIX).getOrThrow()

        assertEquals("fix/TASK-456", worktree.branch)
    }

    @Test
    fun `fails when destination worktree already exists`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val fileManager = FakeFileManager(existing = baseWithSecrets + worktreePath)
        val useCase = CreateWorktreeUseCase(config, executor, fileManager, FakeRepositoryProvisioningSource())

        val error = useCase("TASK-123", BranchType.FEATURE).exceptionOrNull()

        assertIs<WorktreeError.WorktreeAlreadyExists>(error)
        assertEquals(worktreePath, error.path)
        assertTrue(executor.executedCommands.isEmpty(), "should not touch Git if the destination already exists")
    }

    @Test
    fun `fails when base repository is missing`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val useCase = CreateWorktreeUseCase(config, executor, FakeFileManager(existing = emptyList()), FakeRepositoryProvisioningSource())

        val error = useCase("TASK-123", BranchType.FEATURE).exceptionOrNull()

        assertIs<WorktreeError.BaseRepositoryNotFound>(error)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `fails when secret files are missing`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val fileManager = FakeFileManager(
            existing = listOf(config.baseRepositoryPath, config.baseSecretPath("local.properties")),
        )
        val useCase = CreateWorktreeUseCase(config, executor, fileManager, FakeRepositoryProvisioningSource())

        val error = useCase("TASK-123", BranchType.FEATURE).exceptionOrNull()

        assertIs<WorktreeError.SecretFileNotFound>(error)
        assertEquals(listOf("app-secrets.properties"), error.files)
        assertTrue(executor.executedCommands.isEmpty(), "should not create the worktree if secrets are missing")
    }

    @Test
    fun `fails and does not copy when git worktree add fails`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it, exitCode = 128, stderr = "fatal: already exists") }
        val fileManager = FakeFileManager(existing = baseWithSecrets)
        val useCase = CreateWorktreeUseCase(config, executor, fileManager, FakeRepositoryProvisioningSource())

        val error = useCase("TASK-123", BranchType.FEATURE).exceptionOrNull()

        val gitError = assertIs<WorktreeError.GitCommandFailed>(error)
        assertEquals(128, gitError.exitCode)
        assertTrue(fileManager.copied.isEmpty())
        assertFalse(
            executor.executed("git", "worktree", "remove", "--force", worktreePath),
            "should not roll back the worktree for git failures unrelated to Git LFS",
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
        val useCase = CreateWorktreeUseCase(config, executor, fileManager, FakeRepositoryProvisioningSource())

        val error = useCase("TASK-123", BranchType.FEATURE).exceptionOrNull()

        assertIs<WorktreeError.GitLfsNotFound>(error)
        assertTrue(fileManager.copied.isEmpty())
        assertTrue(
            executor.executed("git", "worktree", "remove", "--force", worktreePath),
            "should roll back the worktree that Git left half-done",
        )
        assertTrue(
            executor.executed("git", "branch", "-D", "feature/TASK-123"),
            "should roll back the branch that Git left created so a retry doesn't collide with it",
        )
    }

    @Test
    fun `rolls back worktree and fails with GitLfsNotFound when the LFS smudge filter is missing`() = runTest {
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
        val useCase = CreateWorktreeUseCase(config, executor, fileManager, FakeRepositoryProvisioningSource())

        val error = useCase("TASK-123", BranchType.FEATURE).exceptionOrNull()

        assertIs<WorktreeError.GitLfsNotFound>(error)
        assertTrue(fileManager.copied.isEmpty())
        assertTrue(
            executor.executed("git", "branch", "-D", "feature/TASK-123"),
            "should roll back the branch that Git left created so a retry doesn't collide with it",
        )
    }

    @Test
    fun `rolls back worktree when secret copy fails`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val fileManager = FakeFileManager(
            existing = baseWithSecrets,
            failCopyFor = setOf(config.baseSecretPath("local.properties")),
        )
        val useCase = CreateWorktreeUseCase(config, executor, fileManager, FakeRepositoryProvisioningSource())

        val error = useCase("TASK-123", BranchType.FEATURE).exceptionOrNull()

        assertIs<WorktreeError.SecretCopyFailed>(error)
        assertTrue(
            executor.executed("git", "worktree", "remove", "--force", worktreePath),
            "should roll back the incomplete worktree",
        )
    }

    @Test
    fun `fails when task id is blank`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val useCase = CreateWorktreeUseCase(config, executor, FakeFileManager(baseWithSecrets), FakeRepositoryProvisioningSource())

        val error = useCase("   ", BranchType.FEATURE).exceptionOrNull()

        val invalidTaskId = assertIs<WorktreeError.InvalidTaskId>(error)
        assertEquals("   ", invalidTaskId.rawInput)
        assertFalse(executor.executedCommands.isNotEmpty())
    }

    @Test
    fun `normalizes spaces in task id to hyphens`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val fileManager = FakeFileManager(existing = baseWithSecrets)
        val useCase = CreateWorktreeUseCase(config, executor, fileManager, FakeRepositoryProvisioningSource())

        val worktree = useCase("  TASK 123  ", BranchType.FEATURE).getOrThrow()

        assertEquals(worktreePath, worktree.path)
        assertEquals("feature/TASK-123", worktree.branch)
        assertTrue(executor.executed("git", "worktree", "add", worktreePath, "-b", "feature/TASK-123"))
    }

    @Test
    fun `collapses multiple consecutive spaces into a single hyphen`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val fileManager = FakeFileManager(existing = baseWithSecrets)
        val useCase = CreateWorktreeUseCase(config, executor, fileManager, FakeRepositoryProvisioningSource())

        val worktree = useCase("TASK   123", BranchType.FEATURE).getOrThrow()

        assertEquals("feature/TASK-123", worktree.branch)
    }

    @Test
    fun `fails with InvalidTaskId when the id is still invalid after normalization`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val useCase = CreateWorktreeUseCase(config, executor, FakeFileManager(baseWithSecrets), FakeRepositoryProvisioningSource())

        val error = useCase("TASK:123", BranchType.FEATURE).exceptionOrNull()

        val invalidTaskId = assertIs<WorktreeError.InvalidTaskId>(error)
        assertEquals("TASK:123", invalidTaskId.rawInput)
        assertTrue(executor.executedCommands.isEmpty(), "should not run any Git command for an invalid task id")
    }

    @Test
    fun `copies the files selected by the repository patterns after the local files`() = runTest {
        val executor = FakeShellCommandExecutor {
            if (it.getOrNull(1) == "ls-files") success(it, stdout = ".env\u0000.idea/runConfigurations/App.xml\u0000") else success(it)
        }
        val fileManager = FakeFileManager(existing = baseWithSecrets)
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(config.baseRepositoryPath to RepositoryProvisioning(copyPatterns = listOf(".env", ".idea/runConfigurations/"))),
        )
        val useCase = CreateWorktreeUseCase(config, executor, fileManager, provisioning)

        useCase("TASK-123", BranchType.FEATURE).getOrThrow()

        assertEquals(
            listOf(
                config.baseSecretPath("local.properties") to "$worktreePath/local.properties",
                config.baseSecretPath("app-secrets.properties") to "$worktreePath/app-secrets.properties",
                config.baseSecretPath(".env") to "$worktreePath/.env",
                config.baseSecretPath(".idea/runConfigurations/App.xml") to "$worktreePath/.idea/runConfigurations/App.xml",
            ),
            fileManager.copied,
        )
        val lsFilesIndex = executor.executedCommands.indexOfFirst { it.getOrNull(1) == "ls-files" }
        val addIndex = executor.executedCommands.indexOfFirst { it.getOrNull(1) == "worktree" }
        assertTrue(lsFilesIndex in 0 until addIndex, "should resolve the files before creating the worktree")
    }

    @Test
    fun `fails before creating anything when the repository config is invalid`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val provisioning = FakeRepositoryProvisioningSource(unreadable = setOf(config.baseRepositoryPath))
        val useCase = CreateWorktreeUseCase(config, executor, FakeFileManager(baseWithSecrets), provisioning)

        val error = useCase("TASK-123", BranchType.FEATURE).exceptionOrNull()

        assertIs<WorktreeError.InvalidRepositoryConfig>(error)
        assertTrue(executor.executedCommands.isEmpty(), "should not run any Git command")
    }

    @Test
    fun `fails before creating anything when git ls-files fails`() = runTest {
        val executor = FakeShellCommandExecutor {
            if (it.getOrNull(1) == "ls-files") failure(it) else success(it)
        }
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(config.baseRepositoryPath to RepositoryProvisioning(copyPatterns = listOf(".env"))),
        )
        val useCase = CreateWorktreeUseCase(config, executor, FakeFileManager(baseWithSecrets), provisioning)

        val error = useCase("TASK-123", BranchType.FEATURE).exceptionOrNull()

        assertIs<WorktreeError.GitCommandFailed>(error)
        assertFalse(executor.executedCommands.any { it.getOrNull(1) == "worktree" })
    }

    @Test
    fun `rolls back the worktree when copying a file selected by a pattern fails`() = runTest {
        val executor = FakeShellCommandExecutor {
            if (it.getOrNull(1) == "ls-files") success(it, stdout = ".env\u0000") else success(it)
        }
        val fileManager = FakeFileManager(existing = baseWithSecrets, failCopyFor = setOf(config.baseSecretPath(".env")))
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(config.baseRepositoryPath to RepositoryProvisioning(worktreeIncludePatterns = listOf(".env"))),
        )
        val useCase = CreateWorktreeUseCase(config, executor, fileManager, provisioning)

        val error = useCase("TASK-123", BranchType.FEATURE).exceptionOrNull()

        assertIs<WorktreeError.SecretCopyFailed>(error)
        assertTrue(executor.executed("git", "worktree", "remove", "--force", worktreePath))
    }

    @Test
    fun `prepares the new worktree's environment after copying files`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val prepared = mutableListOf<Pair<String, String>>()
        val useCase = CreateWorktreeUseCase(
            config,
            executor,
            FakeFileManager(baseWithSecrets),
            FakeRepositoryProvisioningSource(),
            environment = { repository, worktree -> prepared += repository to worktree },
        )

        useCase("TASK-123", BranchType.FEATURE).getOrThrow()

        assertEquals(listOf(config.baseRepositoryPath to worktreePath), prepared)
    }

    @Test
    fun `a failed environment removes the new worktree`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val useCase = CreateWorktreeUseCase(
            config,
            executor,
            FakeFileManager(baseWithSecrets),
            FakeRepositoryProvisioningSource(),
            environment = { _, _ -> throw WorktreeError.NoFreePort(20000, 29999) },
        )

        val error = useCase("TASK-123", BranchType.FEATURE).exceptionOrNull()

        assertIs<WorktreeError.NoFreePort>(error)
        assertTrue(executor.executed("git", "worktree", "remove", "--force", worktreePath))
    }
}
