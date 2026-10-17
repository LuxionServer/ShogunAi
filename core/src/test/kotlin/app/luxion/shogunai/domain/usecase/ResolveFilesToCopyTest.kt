package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ResolveFilesToCopyTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "/home/dev/projects/main-repo",
        worktreesRoot = "/home/dev/projects",
        secretFiles = emptyList(),
    )

    private fun source(provisioning: RepositoryProvisioning) =
        FakeRepositoryProvisioningSource(byRepository = mapOf(config.baseRepositoryPath to provisioning))

    @Test
    fun `runs no git command when the repository declares no patterns`() = runTest {
        val executor = FakeShellCommandExecutor()

        val files = resolveFilesToCopy(config, executor, FakeRepositoryProvisioningSource())

        assertEquals(emptyList(), files)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `lists untracked files matching the patterns of both files once each`() = runTest {
        val executor = FakeShellCommandExecutor { success(it, stdout = "local.properties\u0000.env\u0000") }
        val provisioning = RepositoryProvisioning(
            copyPatterns = listOf("local.properties", ".env*"),
            worktreeIncludePatterns = listOf(".env*", ".idea/runConfigurations/"),
        )

        val files = resolveFilesToCopy(config, executor, source(provisioning))

        assertEquals(listOf("local.properties", ".env"), files)
        assertEquals(
            listOf(
                "git", "ls-files", "--others", "--ignored", "-z",
                "-x", "local.properties", "-x", ".env*", "-x", ".idea/runConfigurations/",
            ),
            executor.executedCommands.single(),
        )
    }

    @Test
    fun `keeps paths with spaces and non-ascii characters intact`() = runTest {
        val executor = FakeShellCommandExecutor {
            success(it, stdout = "config/my secrets.json\u0000.idea/runConfigurations/Año.xml\u0000")
        }

        val files = resolveFilesToCopy(config, executor, source(RepositoryProvisioning(copyPatterns = listOf("*.json"))))

        assertEquals(listOf("config/my secrets.json", ".idea/runConfigurations/Año.xml"), files)
    }

    @Test
    fun `fails with GitCommandFailed when git ls-files fails`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it, exitCode = 128, stderr = "not a git repository") }

        val error = assertFailsWith<WorktreeError.GitCommandFailed> {
            resolveFilesToCopy(config, executor, source(RepositoryProvisioning(copyPatterns = listOf(".env"))))
        }

        assertEquals(128, error.exitCode)
    }
}
