package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.LocalFileOrigin
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.UnavailableReason
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.infrastructure.NioFileManager
import app.luxion.shogunai.infrastructure.ProcessBuilderShellCommandExecutor
import app.luxion.shogunai.infrastructure.RepositoryProvisioningFiles
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class LocalFilesPreflightRegressionTest {

    private val root: Path = Files.createTempDirectory("preflight")
    private val repository: Path = root.resolve("main-repo")
    private val worktrees: Path = root.resolve("worktrees")
    private val git = RecordingExecutor(ProcessBuilderShellCommandExecutor(path = null))

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    @Test
    fun `a pattern-matched local file that can't be copied fails before git creates the worktree`() = runTest {
        initRepository()
        repository.resolve(".shogunai.json").writeText("""{ "copy": ["*.properties"] }""")
        repository.resolve("local.properties").writeText("sdk.dir=/opt/android")
        Files.createSymbolicLink(repository.resolve("keys.properties"), root.resolve("missing/keys.properties"))
        val config = ProjectConfig(repository.toString(), worktrees.toString(), secretFiles = emptyList())

        val result = CreateWorktreeUseCase(config, git, NioFileManager(), RepositoryProvisioningFiles())("TASK-123", BranchType.FEATURE)

        val error = assertIs<WorktreeError.LocalFilesUnavailable>(result.exceptionOrNull())
        val unavailable = error.files.single()
        assertEquals("keys.properties", unavailable.path)
        assertEquals(LocalFileOrigin.REPOSITORY, unavailable.origin)
        assertEquals(UnavailableReason.BrokenLink(root.resolve("missing/keys.properties").toString()), unavailable.reason)
        assertFalse(git.commands.any { it.take(3) == listOf("git", "worktree", "add") }, "git worktree add shouldn't run")
        assertFalse(worktrees.resolve("TASK-123").exists())
        assertFalse(git("branch", "--list", "feature/TASK-123").stdout.isNotBlank())
    }

    private suspend fun initRepository() {
        Files.createDirectories(repository)
        git("init", "-q", "-b", "main")
        repository.resolve(".gitignore").writeText("*.properties\n")
        git("add", ".gitignore")
        git("-c", "user.name=Test", "-c", "user.email=test@example.invalid", "commit", "-q", "-m", "Initial commit")
    }

    private suspend fun git(vararg args: String): CommandResult {
        val result = git.execute(listOf("git") + args, repository.toString())
        check(result.isSuccess) { result.stderr }
        return result
    }

    private class RecordingExecutor(private val delegate: ShellCommandExecutor) : ShellCommandExecutor by delegate {
        val commands = mutableListOf<List<String>>()

        override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
            commands += command
            return delegate.execute(command, workingDirectory)
        }
    }
}
