package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeRepositoryApproval
import app.luxion.shogunai.domain.FakeProvisioningRecordStore
import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.io.ApprovalDecision
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.model.FlutterProvider
import app.luxion.shogunai.domain.model.FlutterPubGet
import app.luxion.shogunai.domain.model.FlutterPubGetFailure
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryApprovalSubject
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.PodsPreparation
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.parseEnvironmentVariable
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
    fun `a task id with a slash maps to one folder and keeps the slash in the branch`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val useCase = CreateWorktreeUseCase(config, executor, FakeFileManager(baseWithSecrets), FakeRepositoryProvisioningSource())

        val worktree = useCase("team/TASK-1", BranchType.FEATURE).getOrThrow()

        assertEquals("/home/dev/projects/team-TASK-1", worktree.path)
        assertEquals("feature/team/TASK-1", worktree.branch)
        assertTrue(executor.executed("git", "worktree", "add", "/home/dev/projects/team-TASK-1", "-b", "feature/team/TASK-1"))
    }

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
            executor.executed("git", "worktree", "add", "--no-track", worktreePath, "-b", "feature/TASK-123", "abc123"),
        )
    }

    @Test
    fun `records the base branch right after adding the worktree`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val useCase = CreateWorktreeUseCase(config, executor, FakeFileManager(baseWithSecrets), FakeRepositoryProvisioningSource())

        useCase("TASK-123", BranchType.FEATURE, startPoint = "abc123", baseBranch = "epic/redesign").getOrThrow()

        val add = executor.executedCommands.indexOfFirst { it.take(3) == listOf("git", "worktree", "add") }
        val record = executor.executedCommands.indexOf(listOf("git", "config", "branch.feature/TASK-123.gh-merge-base", "epic/redesign"))
        assertTrue(record == add + 1, "should record the base right after git worktree add")
        assertEquals(config.baseRepositoryPath, executor.workingDirectories[record])
    }

    @Test
    fun `records nothing without a base branch`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val useCase = CreateWorktreeUseCase(config, executor, FakeFileManager(baseWithSecrets), FakeRepositoryProvisioningSource())

        useCase("TASK-123", BranchType.FEATURE, startPoint = "abc123").getOrThrow()

        assertFalse(executor.executedCommands.any { it.take(2) == listOf("git", "config") })
    }

    @Test
    fun `rolls back when the base can't be recorded`() = runTest {
        val executor = FakeShellCommandExecutor {
            if (it.take(2) == listOf("git", "config")) failure(it, exitCode = 255, stderr = "error: could not lock config file") else success(it)
        }
        val fileManager = FakeFileManager(existing = baseWithSecrets)
        val useCase = CreateWorktreeUseCase(config, executor, fileManager, FakeRepositoryProvisioningSource())

        val error = useCase("TASK-123", BranchType.FEATURE, baseBranch = "develop").exceptionOrNull()

        assertIs<WorktreeError.GitCommandFailed>(error)
        assertTrue(fileManager.copied.isEmpty())
        assertTrue(executor.executed("git", "worktree", "remove", "--force", worktreePath))
        assertTrue(executor.executed("git", "branch", "-D", "--", "feature/TASK-123"))
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
            executor.executed("git", "branch", "-D", "--", "feature/TASK-123"),
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
            executor.executed("git", "branch", "-D", "--", "feature/TASK-123"),
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
        assertTrue(executor.executed("git", "branch", "-D", "--", "feature/TASK-123"))
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
            environment = { repository, worktree -> prepared += repository to worktree; PreparedWorktree() },
        )

        useCase("TASK-123", BranchType.FEATURE).getOrThrow()

        assertEquals(listOf(config.baseRepositoryPath to worktreePath), prepared)
    }

    @Test
    fun `reports the pods prepared with the environment`() = runTest {
        val pods = PodsPreparation(cloned = listOf("iosApp"))
        val useCase = CreateWorktreeUseCase(
            config,
            FakeShellCommandExecutor { success(it) },
            FakeFileManager(baseWithSecrets),
            FakeRepositoryProvisioningSource(),
            environment = { _, _ -> PreparedWorktree(pods) },
        )

        val created = useCase("TASK-123", BranchType.FEATURE).getOrThrow()

        assertEquals(pods, created.pods)
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
        assertTrue(executor.executed("git", "branch", "-D", "--", "feature/TASK-123"))
    }

    @Test
    fun `links files, runs post-creation commands and keeps the worktree when one fails`() = runTest {
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(
                config.baseRepositoryPath to RepositoryProvisioning(
                    linkPatterns = listOf("keys/"),
                    postCreateCommands = listOf(listOf("npm", "install"), listOf("direnv", "allow")),
                ),
            ),
        )
        val executor = FakeShellCommandExecutor {
            when {
                it.getOrNull(1) == "ls-files" -> success(it, stdout = "keys/a\u0000")
                it.first() == "npm" -> failure(it, exitCode = 1, stderr = "ENOTFOUND")
                else -> success(it)
            }
        }
        val fileManager = FakeFileManager(existing = baseWithSecrets)

        val trust = FakeRepositoryTrust(
            mapOf(config.baseRepositoryPath to RepositoryApprovalSubject(listOf(listOf("npm", "install"), listOf("direnv", "allow")))),
        )
        val error = CreateWorktreeUseCase(config, executor, fileManager, provisioning, trust = trust)("TASK-123", BranchType.FEATURE, baseBranch = "develop")
            .exceptionOrNull()

        assertIs<WorktreeError.PostCreateCommandFailed>(error)
        assertTrue(executor.executed("git", "config", "branch.feature/TASK-123.gh-merge-base", "develop"))
        assertEquals(worktreePath, error.worktreePath)
        assertEquals(mapOf("$worktreePath/keys/a" to "${config.baseRepositoryPath}/keys/a"), fileManager.symbolicLinks)
        assertFalse(executor.executed("direnv", "allow"))
        assertFalse(executor.executedCommands.any { it.take(3) == listOf("git", "worktree", "remove") })
        assertFalse(executor.executedCommands.any { it.take(2) == listOf("git", "branch") })
    }

    private val installCommands = listOf(listOf("npm", "install"))

    private val installProvisioning = FakeRepositoryProvisioningSource(
        byRepository = mapOf(config.baseRepositoryPath to RepositoryProvisioning(postCreateCommands = installCommands)),
    )

    @Test
    fun `skips unapproved post-creation commands and reports them`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val approval = FakeRepositoryApproval(ApprovalDecision.SKIP)

        val created = CreateWorktreeUseCase(
            config, executor, FakeFileManager(baseWithSecrets), installProvisioning, approval = approval,
        )("TASK-123", BranchType.FEATURE).getOrThrow()

        assertEquals(installCommands, created.skippedPostCreateCommands)
        assertFalse(executor.executed("npm", "install"))
    }

    @Test
    fun `runs unapproved commands once approved and remembers the approval`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val trust = FakeRepositoryTrust()

        val created = CreateWorktreeUseCase(
            config, executor, FakeFileManager(baseWithSecrets), installProvisioning,
            trust = trust,
            approval = FakeRepositoryApproval(ApprovalDecision.APPROVE),
        )("TASK-123", BranchType.FEATURE).getOrThrow()

        assertEquals(emptyList(), created.skippedPostCreateCommands)
        assertTrue(executor.executed("npm", "install"))
        assertTrue(trust.isApproved(config.baseRepositoryPath, RepositoryApprovalSubject(installCommands)))
    }

    @Test
    fun `approved commands run without asking`() = runTest {
        val approval = FakeRepositoryApproval(ApprovalDecision.SKIP)

        CreateWorktreeUseCase(
            config, FakeShellCommandExecutor { success(it) }, FakeFileManager(baseWithSecrets), installProvisioning,
            trust = FakeRepositoryTrust(mapOf(config.baseRepositoryPath to RepositoryApprovalSubject(installCommands))),
            approval = approval,
        )("TASK-123", BranchType.FEATURE).getOrThrow()

        assertTrue(approval.asked.isEmpty())
    }

    @Test
    fun `rejects a start point that Git could read as an option`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val useCase = CreateWorktreeUseCase(config, executor, FakeFileManager(baseWithSecrets), FakeRepositoryProvisioningSource())

        val error = useCase("TASK-123", BranchType.FEATURE, startPoint = "--upload-pack=x").exceptionOrNull()

        assertEquals("--upload-pack=x", assertIs<WorktreeError.InvalidRef>(error).ref)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `a provisioned file resolving outside the worktree fails the creation and rolls back`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val nested = config.copy(secretFiles = listOf("config/.env"))
        val fileManager = FakeFileManager(listOf(config.baseRepositoryPath, nested.baseSecretPath("config/.env")))
            .also { it.outsidePaths = setOf("$worktreePath/config") }
        val useCase = CreateWorktreeUseCase(nested, executor, fileManager, FakeRepositoryProvisioningSource())

        val error = useCase("TASK-123", BranchType.FEATURE).exceptionOrNull()

        assertIs<WorktreeError.SecretCopyFailed>(error)
        assertTrue(fileManager.copied.isEmpty())
        assertTrue(executor.executed("git", "worktree", "remove", "--force", worktreePath))
    }

    private val keysFolder = config.baseSecretPath("keys")

    private fun folderConfig() = config.copy(secretFiles = listOf("keys"))

    private fun fileManagerWithKeys(failCopyFor: Set<String> = emptySet()) = FakeFileManager(
        existing = listOf(config.baseRepositoryPath, keysFolder),
        directories = setOf(keysFolder),
        failCopyFor = failCopyFor,
    ).also { it.directoryFiles[keysFolder] = listOf("a.pem", ".hidden", "ci/b.pem") }

    @Test
    fun `copies every file under a local folder into the same relative paths`() = runTest {
        val fileManager = fileManagerWithKeys()
        val useCase = CreateWorktreeUseCase(folderConfig(), FakeShellCommandExecutor { success(it) }, fileManager, FakeRepositoryProvisioningSource())

        useCase("TASK-123", BranchType.FEATURE).getOrThrow()

        assertEquals(
            listOf(
                "$keysFolder/a.pem" to "$worktreePath/keys/a.pem",
                "$keysFolder/.hidden" to "$worktreePath/keys/.hidden",
                "$keysFolder/ci/b.pem" to "$worktreePath/keys/ci/b.pem",
            ),
            fileManager.copied,
        )
    }

    @Test
    fun `merging into an existing worktree folder keeps its other files`() = runTest {
        val fileManager = fileManagerWithKeys()
        fileManager.copy("unused", "$worktreePath/keys/old.pem")
        fileManager.copied.clear()
        val useCase = CreateWorktreeUseCase(folderConfig(), FakeShellCommandExecutor { success(it) }, fileManager, FakeRepositoryProvisioningSource())

        useCase("TASK-123", BranchType.FEATURE).getOrThrow()

        assertEquals(3, fileManager.copied.size)
        assertTrue(fileManager.exists("$worktreePath/keys/old.pem"))
    }

    @Test
    fun `fails before creating anything when a local folder is missing`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val fileManager = FakeFileManager(existing = listOf(config.baseRepositoryPath))
        val useCase = CreateWorktreeUseCase(folderConfig(), executor, fileManager, FakeRepositoryProvisioningSource())

        val error = useCase("TASK-123", BranchType.FEATURE).exceptionOrNull()

        assertIs<WorktreeError.SecretFileNotFound>(error)
        assertEquals(listOf("keys"), error.files)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `rolls back the worktree when copying a file of a local folder fails`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val fileManager = fileManagerWithKeys(failCopyFor = setOf("$keysFolder/ci/b.pem"))
        val useCase = CreateWorktreeUseCase(folderConfig(), executor, fileManager, FakeRepositoryProvisioningSource())

        val error = useCase("TASK-123", BranchType.FEATURE).exceptionOrNull()

        assertIs<WorktreeError.SecretCopyFailed>(error)
        assertTrue(executor.executed("git", "worktree", "remove", "--force", worktreePath))
        assertTrue(executor.executed("git", "branch", "-D", "--", "feature/TASK-123"))
    }

    @Test
    fun `a local pattern that matches nothing doesn't count as a missing file`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val patternConfig = config.copy(secretFiles = listOf("*.p12", "local.properties"))
        val useCase = CreateWorktreeUseCase(patternConfig, executor, FakeFileManager(existing = listOf(config.baseRepositoryPath)), FakeRepositoryProvisioningSource())

        val error = useCase("TASK-123", BranchType.FEATURE).exceptionOrNull()

        assertIs<WorktreeError.SecretFileNotFound>(error)
        assertEquals(listOf("local.properties"), error.files)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `links a linked local folder and records copied folders and skipped links`() = runTest {
        val certs = config.baseSecretPath("certs")
        val folders = FakeFileManager(
            existing = listOf(config.baseRepositoryPath, keysFolder, certs),
            directories = setOf(keysFolder, certs),
        ).also {
            it.directoryFiles[keysFolder] = listOf("a.pem")
            it.directoryFiles[certs] = listOf("ca.pem")
            it.directoryLinks[certs] = listOf("shared.pem")
        }
        val records = FakeProvisioningRecordStore()
        val linkedConfig = config.copy(secretFiles = listOf("keys", "certs"), linkedSecretFiles = listOf("keys"))
        val useCase = CreateWorktreeUseCase(
            linkedConfig, FakeShellCommandExecutor { success(it) }, folders, FakeRepositoryProvisioningSource(), records = records,
        )

        val worktree = useCase("TASK-123", BranchType.FEATURE).getOrThrow()

        assertEquals(keysFolder, folders.symbolicLinks["$worktreePath/keys"])
        assertEquals(listOf("$certs/ca.pem" to "$worktreePath/certs/ca.pem"), folders.copied)
        assertEquals(mapOf("certs" to listOf("ca.pem")), records.records[worktreePath])
        assertEquals(listOf("certs/shared.pem"), worktree.skippedLinks)
    }

    @Test
    fun `rolls back when a linked folder's destination holds the branch's files`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val destination = "$worktreePath/keys"
        val fileManager = FakeFileManager(
            existing = listOf(config.baseRepositoryPath, keysFolder),
            directories = setOf(keysFolder, destination),
        ).also { it.directoryFiles[destination] = listOf("README.md") }
        val linkedConfig = folderConfig().copy(linkedSecretFiles = listOf("keys"))
        val useCase = CreateWorktreeUseCase(linkedConfig, executor, fileManager, FakeRepositoryProvisioningSource())

        val error = useCase("TASK-123", BranchType.FEATURE).exceptionOrNull()

        assertIs<WorktreeError.LinkedFolderOccupied>(error)
        assertEquals("keys", error.entry)
        assertTrue(fileManager.deletedRecursively.isEmpty())
        assertTrue(executor.executed("git", "worktree", "remove", "--force", worktreePath))
        assertTrue(executor.executed("git", "branch", "-D", "--", "feature/TASK-123"))
    }

    @Test
    fun `rolls back when the record can't be written`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }
        val records = FakeProvisioningRecordStore().also { it.failWrites = true }
        val useCase = CreateWorktreeUseCase(folderConfig(), executor, fileManagerWithKeys(), FakeRepositoryProvisioningSource(), records = records)

        val error = useCase("TASK-123", BranchType.FEATURE).exceptionOrNull()

        assertIs<WorktreeError.SecretCopyFailed>(error)
        assertTrue(executor.executed("git", "worktree", "remove", "--force", worktreePath))
    }

    private val environmentProvisioning = FakeRepositoryProvisioningSource(
        byRepository = mapOf(
            config.baseRepositoryPath to RepositoryProvisioning(
                postCreateCommands = installCommands,
                environment = EnvironmentDeclaration(listOf(parseEnvironmentVariable("PORT", "\${port}")), ".env.shogunai"),
            ),
        ),
    )

    private val gitWithDirectories = FakeShellCommandExecutor {
        when {
            it.getOrNull(1) == "rev-parse" -> success(it, stdout = "${config.baseRepositoryPath}/.git/worktrees/TASK-123\n${config.baseRepositoryPath}/.git\n")
            it.getOrNull(1) == "ls-files" && "--error-unmatch" in it -> failure(it)
            else -> success(it)
        }
    }

    private suspend fun createWithEnvironment(
        trust: FakeRepositoryTrust,
        approval: FakeRepositoryApproval,
        store: FakeEnvironmentStateStore,
        fileManager: FakeFileManager,
    ): Worktree {
        val resolve = ResolveWorktreeEnvironmentUseCase(gitWithDirectories, environmentProvisioning, store, FakePortProbe(), trust = trust)
        val prepare = PrepareWorktreeEnvironmentUseCase(resolve, WriteEnvironmentFileUseCase(gitWithDirectories, fileManager, environmentProvisioning))
        return CreateWorktreeUseCase(config, gitWithDirectories, fileManager, environmentProvisioning, prepare, trust, approval)(
            "TASK-123",
            BranchType.FEATURE,
        ).getOrThrow()
    }

    @Test
    fun `approving during creation reserves the declared port and writes the environment file`() = runTest {
        val store = FakeEnvironmentStateStore()
        val fileManager = FakeFileManager(baseWithSecrets)
        val approval = FakeRepositoryApproval(ApprovalDecision.APPROVE)

        val created = createWithEnvironment(FakeRepositoryTrust(), approval, store, fileManager)

        assertEquals(1, approval.asked.size)
        assertEquals(listOf("PORT" to "\${port}"), approval.asked.single().second.variables)
        assertEquals(".env.shogunai", approval.asked.single().second.envFile)
        assertTrue(store.readPorts("${config.baseRepositoryPath}/.git/worktrees/TASK-123").containsKey("PORT"))
        assertTrue(fileManager.writtenTexts.keys.contains("$worktreePath/.env.shogunai"))
        assertTrue(gitWithDirectories.executed("npm", "install"))
        assertEquals(emptyList(), created.skippedVariables)
        assertEquals(emptyList(), created.skippedPostCreateCommands)
    }

    @Test
    fun `skipping during creation leaves out the declared port, the environment file and the commands`() = runTest {
        val store = FakeEnvironmentStateStore()
        val fileManager = FakeFileManager(baseWithSecrets)
        val approval = FakeRepositoryApproval(ApprovalDecision.SKIP)

        val created = createWithEnvironment(FakeRepositoryTrust(), approval, store, fileManager)

        assertEquals(1, approval.asked.size)
        assertTrue(store.readPorts("${config.baseRepositoryPath}/.git/worktrees/TASK-123").isEmpty())
        assertFalse(fileManager.writtenTexts.keys.contains("$worktreePath/.env.shogunai"))
        assertFalse(gitWithDirectories.executed("npm", "install"))
        assertEquals(listOf("PORT"), created.skippedVariables)
        assertEquals(installCommands, created.skippedPostCreateCommands)
    }

    private val buildRunner = listOf("dart", "run", "build_runner", "build")

    private val flutterProvisioning = FakeRepositoryProvisioningSource(
        byRepository = mapOf(
            config.baseRepositoryPath to RepositoryProvisioning(
                postCreateCommands = listOf(buildRunner),
                providers = RepositoryProviders(flutter = FlutterProvider),
            ),
        ),
    )

    private fun flutterPreparer(executor: FakeShellCommandExecutor) = WorktreeEnvironmentPreparer { repository, worktree ->
        PreparedWorktree(flutter = ResolveFlutterPackagesUseCase(executor, FakeFileManager(), flutterProvisioning).prepare(repository, worktree))
    }

    private suspend fun createFlutterWorktree(executor: FakeShellCommandExecutor, decision: ApprovalDecision) =
        CreateWorktreeUseCase(
            config, executor, FakeFileManager(baseWithSecrets), flutterProvisioning,
            environment = flutterPreparer(executor),
            approval = FakeRepositoryApproval(decision),
        )("TASK-123", BranchType.FEATURE)

    @Test
    fun `resolves the flutter packages before the post-creation commands`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }

        val created = createFlutterWorktree(executor, ApprovalDecision.APPROVE).getOrThrow()

        val flutterRuns = executor.executedCommands.zip(executor.workingDirectories).filter { it.first.first() in setOf("flutter", "dart") }
        assertEquals(listOf(listOf("flutter", "pub", "get") to worktreePath, buildRunner to worktreePath), flutterRuns)
        assertEquals(FlutterPubGet.Resolved, created.flutter)
    }

    @Test
    fun `resolves the flutter packages in a repository that isn't approved`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }

        val created = createFlutterWorktree(executor, ApprovalDecision.SKIP).getOrThrow()

        assertTrue(executor.executed("flutter", "pub", "get"))
        assertFalse(executor.executed(*buildRunner.toTypedArray()))
        assertEquals(listOf(buildRunner), created.skippedPostCreateCommands)
    }

    @Test
    fun `a failed resolution keeps the worktree and still runs the post-creation commands`() = runTest {
        val executor = FakeShellCommandExecutor { if (it.first() == "flutter") failure(it, exitCode = 1, stderr = "Could not resolve") else success(it) }

        val created = createFlutterWorktree(executor, ApprovalDecision.APPROVE).getOrThrow()

        assertEquals(FlutterPubGet.Needed(FlutterPubGetFailure.Exited(1, listOf("Could not resolve"))), created.flutter)
        assertTrue(executor.executed(*buildRunner.toTypedArray()))
        assertFalse(executor.executedCommands.any { it.take(3) == listOf("git", "worktree", "remove") })
    }
}
