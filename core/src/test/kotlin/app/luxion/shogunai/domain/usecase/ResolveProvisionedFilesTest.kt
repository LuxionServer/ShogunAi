package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
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

class ResolveProvisionedFilesTest {

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

        val files = resolveProvisionedFiles(config, executor, FakeFileManager(), FakeRepositoryProvisioningSource()).copied

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

        val files = resolveProvisionedFiles(config, executor, FakeFileManager(), source(provisioning)).copied

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

        val files = resolveProvisionedFiles(config, executor, FakeFileManager(), source(RepositoryProvisioning(copyPatterns = listOf("*.json")))).copied

        assertEquals(listOf("config/my secrets.json", ".idea/runConfigurations/Año.xml"), files)
    }

    @Test
    fun `fails with GitCommandFailed when git ls-files fails`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it, exitCode = 128, stderr = "not a git repository") }

        val error = assertFailsWith<WorktreeError.GitCommandFailed> {
            resolveProvisionedFiles(config, executor, FakeFileManager(), source(RepositoryProvisioning(copyPatterns = listOf(".env"))))
        }

        assertEquals(128, error.exitCode)
    }

    @Test
    fun `expands a local folder into the files under it`() = runTest {
        val fileManager = FakeFileManager(directories = setOf("/home/dev/projects/main-repo/keys"))
        fileManager.directoryFiles["/home/dev/projects/main-repo/keys"] = listOf("a.pem", ".hidden", "ci/b.pem")
        val folderConfig = config.copy(secretFiles = listOf("keys", ".env"))

        val provisioned = resolveProvisionedFiles(
            folderConfig, FakeShellCommandExecutor(), fileManager, FakeRepositoryProvisioningSource(),
        )

        assertEquals(listOf("keys/a.pem", "keys/.hidden", "keys/ci/b.pem", ".env"), provisioned.copied)
        assertEquals(emptyList(), provisioned.linked)
    }

    @Test
    fun `lists a file once when a local folder and a copy pattern overlap`() = runTest {
        val fileManager = FakeFileManager(directories = setOf("/home/dev/projects/main-repo/keys"))
        fileManager.directoryFiles["/home/dev/projects/main-repo/keys"] = listOf("a.pem", "b.pem")
        val executor = FakeShellCommandExecutor { success(it, stdout = "keys/b.pem\u0000other.pem\u0000") }
        val provisioning = RepositoryProvisioning(copyPatterns = listOf("*.pem"))

        val copied = resolveProvisionedFiles(
            config.copy(secretFiles = listOf("keys/")), executor, fileManager, source(provisioning),
        ).copied

        assertEquals(listOf("keys/a.pem", "keys/b.pem", "other.pem"), copied)
    }

    @Test
    fun `copies the files of a local folder the repository also links`() = runTest {
        val fileManager = FakeFileManager(directories = setOf("/home/dev/projects/main-repo/keys"))
        fileManager.directoryFiles["/home/dev/projects/main-repo/keys"] = listOf("a.pem")
        val executor = FakeShellCommandExecutor { success(it, stdout = "keys/a.pem\u0000keys/c.pem\u0000") }
        val provisioning = RepositoryProvisioning(linkPatterns = listOf("keys/"))

        val provisioned = resolveProvisionedFiles(
            config.copy(secretFiles = listOf("keys")), executor, fileManager, source(provisioning),
        )

        assertEquals(listOf("keys/a.pem"), provisioned.copied)
        assertEquals(listOf("keys/c.pem"), provisioned.linked)
    }
}
