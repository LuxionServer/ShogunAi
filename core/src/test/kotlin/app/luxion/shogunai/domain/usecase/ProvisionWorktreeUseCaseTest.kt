package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.model.ProvisionMode
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WorktreeError
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

        val provisioned = useCase("$linked/").getOrThrow()

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
        ProvisionWorktreeUseCase(config, executor(), fileManager, provisioning, environment = { _, worktree -> prepared += worktree })(linked)
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

        val provisioned = ProvisionWorktreeUseCase(config, linkingExecutor(), fileManager, linkingProvisioning)(linked).getOrThrow()

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

        val provisioned = ProvisionWorktreeUseCase(config, linkingExecutor(), fileManager, linkingProvisioning)(linked).getOrThrow()

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

        val error = ProvisionWorktreeUseCase(config, exec, fileManager, linkingProvisioning)(linked).exceptionOrNull()

        assertIs<WorktreeError.SecretCopyFailed>(error)
    }

    @Test
    fun `runs post-creation commands in order inside the worktree after the environment`() = runTest {
        val order = mutableListOf<String>()
        val exec = linkingExecutor { order += "hook"; success(it) }
        val fileManager = FakeFileManager(existing = listOf("$base/local.properties"))

        ProvisionWorktreeUseCase(
            config, exec, fileManager, linkingProvisioning,
            environment = { _, _ -> order += "environment" },
        )(linked).getOrThrow()

        assertEquals(listOf("environment", "hook", "hook"), order)
        val hooks = exec.executedCommands.zip(exec.workingDirectories).filter { it.first.first() in setOf("npm", "direnv") }
        assertEquals(listOf(listOf("npm", "install") to linked, listOf("direnv", "allow") to linked), hooks)
    }

    @Test
    fun `stops at the first failing post-creation command and reports it`() = runTest {
        val exec = linkingExecutor { failure(it, exitCode = 3, stderr = "network down") }
        val fileManager = FakeFileManager(existing = listOf("$base/local.properties"))

        val error = ProvisionWorktreeUseCase(config, exec, fileManager, linkingProvisioning)(linked).exceptionOrNull()

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

        val error = ProvisionWorktreeUseCase(config, exec, fileManager, linkingProvisioning)(linked).exceptionOrNull()

        assertIs<WorktreeError.PostCreateCommandFailed>(error)
        assertEquals(listOf("npm", "install"), error.command)
        assertEquals(null, error.exitCode)
    }
}
