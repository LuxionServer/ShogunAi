package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeRepositoryApproval
import app.luxion.shogunai.domain.FakeProvisioningRecordStore
import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.io.ApprovalDecision
import app.luxion.shogunai.domain.io.RepositoryApproval
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.model.ProvisionMode
import app.luxion.shogunai.domain.model.RepositoryApprovalSubject
import app.luxion.shogunai.domain.model.ProvisionedFile
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.PodsPreparation
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.parseEnvironmentVariable
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProvisionWorktreeUseCaseTest {

    private val base = "/home/dev/projects/main-repo"
    private val linked = "/home/dev/projects/TASK-123"
    private val config = ProjectConfig(baseRepositoryPath = base, worktreesRoot = "/home/dev/projects", secretFiles = listOf("local.properties"))

    private val porcelain = """
        worktree $base
        HEAD 1111111111111111111111111111111111111111
        branch refs/heads/main

        worktree $linked
        HEAD 2222222222222222222222222222222222222222
        branch refs/heads/feature/TASK-123
    """.trimIndent()

    private fun executor(lsFiles: String = ".env\u0000") = FakeShellCommandExecutor {
        when (it.getOrNull(1)) {
            "worktree" -> success(it, stdout = porcelain)
            "ls-files" -> success(it, stdout = lsFiles)
            else -> success(it)
        }
    }

    private val provisioning = FakeRepositoryProvisioningSource(
        byRepository = mapOf(base to RepositoryProvisioning(copyPatterns = listOf(".env"))),
    )

    @Test
    fun `copies local and repository files into an existing worktree`() = runTest {
        val fileManager = FakeFileManager(existing = listOf("$base/local.properties", "$linked/.env"))
        val useCase = ProvisionWorktreeUseCase(config, executor(), fileManager, provisioning)

        val provisioned = useCase("$linked/").getOrThrow().files

        assertEquals(listOf("local.properties", ".env"), provisioned.map { it.path })
        assertTrue(provisioned.all { it.mode == ProvisionMode.COPIED })
        assertEquals(
            listOf("$base/local.properties" to "$linked/local.properties", "$base/.env" to "$linked/.env"),
            fileManager.copied,
            "existing files are overwritten with the base repository's version",
        )
    }

    @Test
    fun `refuses the main worktree`() = runTest {
        val fileManager = FakeFileManager(existing = listOf("$base/local.properties"))

        val error = ProvisionWorktreeUseCase(config, executor(), fileManager, provisioning)(base).exceptionOrNull()

        assertIs<WorktreeError.NotAWorktree>(error)
        assertTrue(fileManager.copied.isEmpty())
    }

    @Test
    fun `refuses a path that is not one of the repository's worktrees`() = runTest {
        val fileManager = FakeFileManager(existing = listOf("$base/local.properties"))

        val error = ProvisionWorktreeUseCase(config, executor(), fileManager, provisioning)("/tmp/elsewhere").exceptionOrNull()

        assertIs<WorktreeError.NotAWorktree>(error)
        assertTrue(fileManager.copied.isEmpty())
    }

    @Test
    fun `a failed copy leaves the worktree in place`() = runTest {
        val exec = executor()
        val fileManager = FakeFileManager(existing = listOf("$base/local.properties"), failCopyFor = setOf("$base/.env"))

        val error = ProvisionWorktreeUseCase(config, exec, fileManager, provisioning)(linked).exceptionOrNull()

        assertIs<WorktreeError.SecretCopyFailed>(error)
        assertFalse(exec.executedCommands.any { it.take(3) == listOf("git", "worktree", "remove") })
    }

    @Test
    fun `fails when a local file is missing`() = runTest {
        val error = ProvisionWorktreeUseCase(config, executor(), FakeFileManager(), provisioning)(linked).exceptionOrNull()

        assertIs<WorktreeError.SecretFileNotFound>(error)
    }

    @Test
    fun `prepares the environment of an existing worktree and keeps it on failure`() = runTest {
        val prepared = mutableListOf<String>()
        val fileManager = FakeFileManager(existing = listOf("$base/local.properties"))
        ProvisionWorktreeUseCase(config, executor(), fileManager, provisioning, environment = { _, worktree -> prepared += worktree; PreparedWorktree() })(linked)
            .getOrThrow()

        assertEquals(listOf(linked), prepared)

        val failingExecutor = executor()
        val error = ProvisionWorktreeUseCase(
            config,
            failingExecutor,
            fileManager,
            provisioning,
            environment = { _, _ -> throw WorktreeError.NoFreePort(20000, 29999) },
        )(linked).exceptionOrNull()

        assertIs<WorktreeError.NoFreePort>(error)
        assertFalse(failingExecutor.executedCommands.any { it.take(3) == listOf("git", "worktree", "remove") })
    }

    private val linkingProvisioning = FakeRepositoryProvisioningSource(
        byRepository = mapOf(
            base to RepositoryProvisioning(
                copyPatterns = listOf(".env", "keys/a"),
                linkPatterns = listOf("keys/*"),
                postCreateCommands = listOf(listOf("npm", "install"), listOf("direnv", "allow")),
            ),
        ),
    )

    private val linkingCommands = listOf(listOf("npm", "install"), listOf("direnv", "allow"))

    private val approvedTrust = FakeRepositoryTrust(mapOf(base to RepositoryApprovalSubject(linkingCommands)))

    private fun linkingExecutor(hook: (List<String>) -> app.luxion.shogunai.domain.executor.CommandResult = { success(it) }) =
        FakeShellCommandExecutor {
            when {
                it.getOrNull(1) == "worktree" -> success(it, stdout = porcelain)
                it.getOrNull(1) == "ls-files" && "keys/*" in it -> success(it, stdout = "keys/a\u0000keys/b\u0000")
                it.getOrNull(1) == "ls-files" -> success(it, stdout = ".env\u0000keys/a\u0000")
                else -> hook(it)
            }
        }

    @Test
    fun `links files matching link patterns, preferring link over copy`() = runTest {
        val fileManager = FakeFileManager(existing = listOf("$base/local.properties"))

        val provisioned = ProvisionWorktreeUseCase(config, linkingExecutor(), fileManager, linkingProvisioning)(linked).getOrThrow().files

        assertEquals(
            mapOf("keys/a" to "$base/keys/a", "keys/b" to "$base/keys/b").mapKeys { "$linked/${it.key}" },
            fileManager.symbolicLinks,
        )
        assertEquals(listOf("$base/local.properties" to "$linked/local.properties", "$base/.env" to "$linked/.env"), fileManager.copied)
        assertEquals(
            listOf(
                "local.properties" to ProvisionMode.COPIED,
                ".env" to ProvisionMode.COPIED,
                "keys/a" to ProvisionMode.LINKED,
                "keys/b" to ProvisionMode.LINKED,
            ),
            provisioned.map { it.path to it.mode },
        )
    }

    @Test
    fun `copies linked files when symbolic links aren't supported`() = runTest {
        val fileManager = FakeFileManager(existing = listOf("$base/local.properties")).also { it.symbolicLinksSupported = false }

        val provisioned = ProvisionWorktreeUseCase(config, linkingExecutor(), fileManager, linkingProvisioning)(linked).getOrThrow().files

        assertTrue(fileManager.symbolicLinks.isEmpty())
        assertTrue("$base/keys/a" to "$linked/keys/a" in fileManager.copied)
        assertEquals(
            listOf("keys/a", "keys/b"),
            provisioned.filter { it.mode == ProvisionMode.COPIED_INSTEAD_OF_LINKING }.map { it.path },
        )
    }

    @Test
    fun `a failed link leaves the worktree in place and reports a failed copy`() = runTest {
        val exec = linkingExecutor()
        val fileManager = FakeFileManager(existing = listOf("$base/local.properties")).also {
            it.failSymbolicLinksFor = setOf("$base/keys/a")
        }

        val error = ProvisionWorktreeUseCase(config, exec, fileManager, linkingProvisioning, trust = approvedTrust)(linked).exceptionOrNull()

        assertIs<WorktreeError.SecretCopyFailed>(error)
    }

    @Test
    fun `runs post-creation commands in order inside the worktree after the environment`() = runTest {
        val order = mutableListOf<String>()
        val exec = linkingExecutor { order += "hook"; success(it) }
        val fileManager = FakeFileManager(existing = listOf("$base/local.properties"))

        val result = ProvisionWorktreeUseCase(
            config, exec, fileManager, linkingProvisioning,
            environment = { _, _ -> order += "environment"; PreparedWorktree(PodsPreparation(cloned = listOf("iosApp"))) },
            trust = approvedTrust,
        )(linked).getOrThrow()

        assertEquals(listOf("environment", "hook", "hook"), order)
        assertEquals(listOf("iosApp"), result.pods.cloned)
        val hooks = exec.executedCommands.zip(exec.workingDirectories).filter { it.first.first() in setOf("npm", "direnv") }
        assertEquals(listOf(listOf("npm", "install") to linked, listOf("direnv", "allow") to linked), hooks)
    }

    @Test
    fun `stops at the first failing post-creation command and reports it`() = runTest {
        val exec = linkingExecutor { failure(it, exitCode = 3, stderr = "network down") }
        val fileManager = FakeFileManager(existing = listOf("$base/local.properties"))

        val error = ProvisionWorktreeUseCase(config, exec, fileManager, linkingProvisioning, trust = approvedTrust)(linked).exceptionOrNull()

        assertIs<WorktreeError.PostCreateCommandFailed>(error)
        assertEquals(listOf("npm", "install"), error.command)
        assertEquals(3, error.exitCode)
        assertEquals("network down", error.errorOutput)
        assertEquals(linked, error.worktreePath)
        assertFalse(exec.executed("direnv", "allow"))
        assertFalse(exec.executedCommands.any { it.take(3) == listOf("git", "worktree", "remove") })
    }

    @Test
    fun `reports a post-creation command that can't be started`() = runTest {
        val exec = linkingExecutor { throw CommandExecutionException(it, java.io.IOException("No such file")) }
        val fileManager = FakeFileManager(existing = listOf("$base/local.properties"))

        val error = ProvisionWorktreeUseCase(config, exec, fileManager, linkingProvisioning, trust = approvedTrust)(linked).exceptionOrNull()

        assertIs<WorktreeError.PostCreateCommandFailed>(error)
        assertEquals(listOf("npm", "install"), error.command)
        assertEquals(null, error.exitCode)
    }

    @Test
    fun `skips unapproved post-creation commands when the decision is to skip`() = runTest {
        val exec = linkingExecutor()
        val fileManager = FakeFileManager(existing = listOf("$base/local.properties"))
        val approval = FakeRepositoryApproval(ApprovalDecision.SKIP)

        val result = ProvisionWorktreeUseCase(config, exec, fileManager, linkingProvisioning, approval = approval)(linked).getOrThrow()

        assertEquals(linkingCommands, result.skippedPostCreateCommands)
        assertEquals(listOf(base to RepositoryApprovalSubject(linkingCommands)), approval.asked)
        assertFalse(exec.executed("npm", "install"))
    }

    @Test
    fun `runs and records the approval when the decision is to run`() = runTest {
        val exec = linkingExecutor()
        val fileManager = FakeFileManager(existing = listOf("$base/local.properties"))
        val trust = FakeRepositoryTrust()

        val result = ProvisionWorktreeUseCase(
            config, exec, fileManager, linkingProvisioning,
            trust = trust,
            approval = FakeRepositoryApproval(ApprovalDecision.APPROVE),
        )(linked).getOrThrow()

        assertEquals(emptyList(), result.skippedPostCreateCommands)
        assertTrue(exec.executed("npm", "install"))
        assertTrue(trust.isApproved(base, RepositoryApprovalSubject(linkingCommands)))
    }

    @Test
    fun `approved commands run without asking`() = runTest {
        val approval = FakeRepositoryApproval(ApprovalDecision.SKIP)
        val fileManager = FakeFileManager(existing = listOf("$base/local.properties"))

        ProvisionWorktreeUseCase(
            config, linkingExecutor(), fileManager, linkingProvisioning,
            trust = approvedTrust,
            approval = approval,
        )(linked).getOrThrow()

        assertTrue(approval.asked.isEmpty())
    }

    private val keysFolder = "$base/keys"

    private fun folderConfig() = config.copy(secretFiles = listOf("keys"))

    private fun fileManagerWithKeys(failCopyFor: Set<String> = emptySet()) = FakeFileManager(
        existing = listOf(keysFolder),
        directories = setOf(keysFolder),
        failCopyFor = failCopyFor,
    ).also { it.directoryFiles[keysFolder] = listOf("a.pem", "ci/b.pem") }

    @Test
    fun `copies the files of a local folder into an existing worktree and keeps the others`() = runTest {
        val fileManager = fileManagerWithKeys()
        fileManager.copy("unused", "$linked/keys/old.pem")
        fileManager.copied.clear()

        val provisioned = ProvisionWorktreeUseCase(folderConfig(), executor(), fileManager, provisioning)(linked).getOrThrow().files

        assertEquals(listOf("keys/a.pem", "keys/ci/b.pem", ".env"), provisioned.map { it.path })
        assertTrue(fileManager.exists("$linked/keys/old.pem"))
    }

    @Test
    fun `a failed copy of a local folder file leaves the worktree in place`() = runTest {
        val exec = executor()
        val fileManager = fileManagerWithKeys(failCopyFor = setOf("$keysFolder/ci/b.pem"))

        val error = ProvisionWorktreeUseCase(folderConfig(), exec, fileManager, provisioning)(linked).exceptionOrNull()

        assertIs<WorktreeError.SecretCopyFailed>(error)
        assertFalse(exec.executedCommands.any { it.take(3) == listOf("git", "worktree", "remove") })
    }

    private val baseKeys = "$base/keys"
    private val worktreeKeys = "$linked/keys"
    private val keysConfig = config.copy(secretFiles = listOf("keys"))
    private val linkedKeysConfig = keysConfig.copy(linkedSecretFiles = listOf("keys"))

    private fun keysFileManager(
        baseFiles: List<String>,
        worktreeFiles: List<String>? = null,
        baseExists: Boolean = true,
    ) = FakeFileManager(
        existing = listOf(base, linked) + listOfNotNull(baseKeys.takeIf { baseExists }) + worktreeFiles.orEmpty().map { "$worktreeKeys/$it" },
        directories = setOfNotNull(baseKeys.takeIf { baseExists }, worktreeKeys.takeIf { worktreeFiles != null }),
    ).also {
        it.directoryFiles[baseKeys] = baseFiles
        if (worktreeFiles != null) it.directoryFiles[worktreeKeys] = worktreeFiles
    }

    private fun provision(
        projectConfig: ProjectConfig,
        fileManager: FakeFileManager,
        records: FakeProvisioningRecordStore = FakeProvisioningRecordStore(),
    ) = ProvisionWorktreeUseCase(
        projectConfig, executor(), fileManager, FakeRepositoryProvisioningSource(), records = records,
    )

    @Test
    fun `links a linked local folder as one symbolic link, replacing an existing link`() = runTest {
        val fileManager = keysFileManager(baseFiles = listOf("a.pem"))
        fileManager.symbolicLinks[worktreeKeys] = "/home/dev/elsewhere"

        val result = provision(linkedKeysConfig, fileManager)(linked).getOrThrow()

        assertEquals(baseKeys, fileManager.symbolicLinks[worktreeKeys])
        assertEquals(listOf(ProvisionedFile("keys", ProvisionMode.LINKED)), result.files)
        assertTrue(fileManager.copied.isEmpty())
    }

    @Test
    fun `copies a linked folder's files where links aren't available and records them`() = runTest {
        val fileManager = keysFileManager(baseFiles = listOf("a.pem")).also { it.symbolicLinksSupported = false }
        val records = FakeProvisioningRecordStore()

        val result = provision(linkedKeysConfig, fileManager, records)(linked).getOrThrow()

        assertEquals(listOf(ProvisionedFile("keys/a.pem", ProvisionMode.COPIED_INSTEAD_OF_LINKING)), result.files)
        assertEquals(listOf("$baseKeys/a.pem" to "$worktreeKeys/a.pem"), fileManager.copied)
        assertEquals(mapOf("keys" to listOf("a.pem")), records.records[linked])
    }

    @Test
    fun `switching a copied folder to link replaces the recorded copies`() = runTest {
        val fileManager = keysFileManager(baseFiles = listOf("a.pem"), worktreeFiles = listOf("a.pem"))
        val records = FakeProvisioningRecordStore(mapOf(linked to mapOf("keys" to listOf("a.pem"))))

        provision(linkedKeysConfig, fileManager, records)(linked).getOrThrow()

        assertEquals(listOf(worktreeKeys), fileManager.deletedRecursively)
        assertEquals(baseKeys, fileManager.symbolicLinks[worktreeKeys])
        assertEquals(emptyMap(), records.records[linked])
    }

    @Test
    fun `a linked folder over the worktree's own files fails and keeps them`() = runTest {
        val fileManager = keysFileManager(baseFiles = listOf("a.pem"), worktreeFiles = listOf("a.pem", "notes.txt"))
        val records = FakeProvisioningRecordStore(mapOf(linked to mapOf("keys" to listOf("a.pem"))))

        val error = provision(linkedKeysConfig, fileManager, records)(linked).exceptionOrNull()

        assertIs<WorktreeError.LinkedFolderOccupied>(error)
        assertTrue(fileManager.deletedRecursively.isEmpty())
        assertTrue(fileManager.exists("$worktreeKeys/notes.txt"))
        assertFalse(worktreeKeys in fileManager.symbolicLinks)
    }

    @Test
    fun `switching a linked folder to copy removes the link before copying`() = runTest {
        val fileManager = keysFileManager(baseFiles = listOf("a.pem"))
        fileManager.createSymbolicLink(target = baseKeys, link = worktreeKeys)

        provision(keysConfig, fileManager)(linked).getOrThrow()

        assertEquals(listOf(worktreeKeys), fileManager.deletedFiles)
        assertEquals(listOf("$baseKeys/a.pem" to "$worktreeKeys/a.pem"), fileManager.copied)
    }

    @Test
    fun `removes recorded files deleted from the base folder and keeps the worktree's own`() = runTest {
        val fileManager = keysFileManager(baseFiles = listOf("a.pem"), worktreeFiles = listOf("a.pem", "old.pem", "notes.txt"))
        val records = FakeProvisioningRecordStore(mapOf(linked to mapOf("keys" to listOf("a.pem", "old.pem"))))

        val result = provision(keysConfig, fileManager, records)(linked).getOrThrow()

        assertEquals(listOf("keys/old.pem"), result.removed)
        assertEquals(listOf("$worktreeKeys/old.pem"), fileManager.deletedFiles)
        assertTrue(fileManager.exists("$worktreeKeys/notes.txt"))
        assertEquals(mapOf("keys" to listOf("a.pem")), records.records[linked])
    }

    @Test
    fun `a missing base folder fails before deleting anything`() = runTest {
        val fileManager = keysFileManager(baseFiles = emptyList(), worktreeFiles = listOf("a.pem"), baseExists = false)
        val records = FakeProvisioningRecordStore(mapOf(linked to mapOf("keys" to listOf("a.pem"))))

        val error = provision(keysConfig, fileManager, records)(linked).exceptionOrNull()

        assertIs<WorktreeError.SecretFileNotFound>(error)
        assertTrue(fileManager.deletedFiles.isEmpty())
    }

    @Test
    fun `without a record nothing is deleted and the record is written`() = runTest {
        val fileManager = keysFileManager(baseFiles = listOf("a.pem"), worktreeFiles = listOf("a.pem", "old.pem"))
        val records = FakeProvisioningRecordStore()

        val result = provision(keysConfig, fileManager, records)(linked).getOrThrow()

        assertEquals(emptyList(), result.removed)
        assertTrue(fileManager.deletedFiles.isEmpty())
        assertEquals(mapOf("keys" to listOf("a.pem")), records.records[linked])
    }

    @Test
    fun `drops the record of folders no longer listed without touching their files`() = runTest {
        val fileManager = FakeFileManager(existing = listOf(base, linked, "$base/local.properties", "$worktreeKeys/a.pem"))
        val records = FakeProvisioningRecordStore(mapOf(linked to mapOf("keys" to listOf("a.pem"))))

        provision(config, fileManager, records)(linked).getOrThrow()

        assertEquals(emptyMap(), records.records[linked])
        assertTrue(fileManager.exists("$worktreeKeys/a.pem"))
    }

    @Test
    fun `reports the symbolic links skipped in a copied folder`() = runTest {
        val fileManager = keysFileManager(baseFiles = listOf("a.pem")).also { it.directoryLinks[baseKeys] = listOf("shared.pem") }

        val result = provision(keysConfig, fileManager)(linked).getOrThrow()

        assertEquals(listOf("keys/shared.pem"), result.skippedLinks)
        assertEquals(listOf("$baseKeys/a.pem" to "$worktreeKeys/a.pem"), fileManager.copied)
    }

    @Test
    fun `provisioning again keeps an existing link instead of following it out of the worktree`() = runTest {
        val linkedConfig = config.copy(linkedSecretFiles = listOf("local.properties"))
        val fileManager = FakeFileManager(existing = listOf(base, linked, "$base/local.properties"))
        fileManager.outsidePaths = setOf("$linked/local.properties")
        val useCase = ProvisionWorktreeUseCase(linkedConfig, executor(), fileManager, FakeRepositoryProvisioningSource())

        useCase(linked).getOrThrow()
        val second = useCase(linked).getOrThrow()

        assertEquals(listOf(ProvisionedFile("local.properties", ProvisionMode.LINKED)), second.files)
        assertEquals("$base/local.properties", fileManager.symbolicLinks["$linked/local.properties"])
    }

    @Test
    fun `asks before preparing the environment and skips declared variables when skipped`() = runTest {
        val events = mutableListOf<String>()
        val approval = RepositoryApproval { _, _ -> ApprovalDecision.SKIP.also { events += "asked" } }
        val source = FakeRepositoryProvisioningSource(
            byRepository = mapOf(
                base to RepositoryProvisioning(
                    environment = EnvironmentDeclaration(listOf(parseEnvironmentVariable("PORT", "\${port}")), ".env.shogunai"),
                ),
            ),
        )

        val result = ProvisionWorktreeUseCase(
            config, executor(), FakeFileManager(existing = listOf("$base/local.properties")), source,
            environment = { _, _ -> events += "prepared"; PreparedWorktree() },
            approval = approval,
        )(linked).getOrThrow()

        assertEquals(listOf("asked", "prepared"), events)
        assertEquals(listOf("PORT"), result.skippedVariables)
    }
}
