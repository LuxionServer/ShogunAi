package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WorktreeEnvironment
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrepareWorktreeEnvironmentUseCaseTest {

    private val repository = "/home/dev/projects/main-repo"
    private val worktree = "/home/dev/projects/worktrees/TASK-123"
    private val environment = WorktreeEnvironment(variables = listOf("PORT" to "20000"), ports = mapOf("PORT" to 20000))

    private fun provisioning(envFile: String?) = FakeRepositoryProvisioningSource(
        byRepository = mapOf(repository to RepositoryProvisioning(environment = EnvironmentDeclaration(envFile = envFile))),
    )

    private val untracked = FakeShellCommandExecutor { if (it.getOrNull(1) == "ls-files") failure(it) else success(it) }

    @Test
    fun `writes the environment file in dotenv format`() = runTest {
        val fileManager = FakeFileManager()

        val path = WriteEnvironmentFileUseCase(untracked, fileManager, provisioning(".env.shogunai"))(repository, worktree, environment)
            .getOrThrow()

        assertEquals("$worktree/.env.shogunai", path)
        assertEquals(mapOf("$worktree/.env.shogunai" to "PORT=20000\n"), fileManager.writtenTexts)
        assertTrue(untracked.executed("git", "ls-files", "--error-unmatch", "--", ".env.shogunai"))
    }

    @Test
    fun `refuses a file tracked by Git`() = runTest {
        val tracked = FakeShellCommandExecutor { success(it) }
        val fileManager = FakeFileManager()

        val error = WriteEnvironmentFileUseCase(tracked, fileManager, provisioning(".env"))(repository, worktree, environment)
            .exceptionOrNull()

        assertIs<WorktreeError.EnvironmentFileTracked>(error)
        assertTrue(fileManager.writtenTexts.isEmpty())
    }

    @Test
    fun `writes nothing without envFile`() = runTest {
        val fileManager = FakeFileManager()

        val path = WriteEnvironmentFileUseCase(untracked, fileManager, provisioning(null))(repository, worktree, environment).getOrThrow()

        assertNull(path)
        assertTrue(untracked.executedCommands.isEmpty())
    }

    @Test
    fun `a failed write is reported with the file`() = runTest {
        val fileManager = FakeFileManager().apply { failWrites = true }

        val error = WriteEnvironmentFileUseCase(untracked, fileManager, provisioning(".env.shogunai"))(repository, worktree, environment)
            .exceptionOrNull()

        assertIs<WorktreeError.EnvironmentFileWriteFailed>(error)
        assertEquals("$worktree/.env.shogunai", error.path)
    }

    @Test
    fun `refuses a file whose real location is outside the worktree`() = runTest {
        val fileManager = FakeFileManager().also { it.outsidePaths = setOf("$worktree/cfg/.zshenv") }

        val error = WriteEnvironmentFileUseCase(untracked, fileManager, provisioning("cfg/.zshenv"))(repository, worktree, environment)
            .exceptionOrNull()

        assertIs<WorktreeError.EnvironmentFileOutsideWorktree>(error)
        assertTrue(fileManager.writtenTexts.isEmpty())
    }
}
