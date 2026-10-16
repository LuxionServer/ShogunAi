package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.ProjectConfig
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

        val copied = useCase("$linked/").getOrThrow()

        assertEquals(listOf("local.properties", ".env"), copied)
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
}
