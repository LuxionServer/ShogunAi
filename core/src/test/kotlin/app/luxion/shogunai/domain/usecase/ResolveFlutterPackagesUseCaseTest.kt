package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.model.FlutterProvider
import app.luxion.shogunai.domain.model.FlutterPubGet
import app.luxion.shogunai.domain.model.FlutterPubGetFailure
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.io.IOException
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResolveFlutterPackagesUseCaseTest {

    private val repository = "/home/dev/projects/main-repo"
    private val worktree = "/home/dev/projects/TASK-123"

    private val flutterRepository = FakeRepositoryProvisioningSource(
        byRepository = mapOf(repository to RepositoryProvisioning(providers = RepositoryProviders(flutter = FlutterProvider))),
    )

    private suspend fun resolve(
        executor: FakeShellCommandExecutor,
        fileManager: FakeFileManager = FakeFileManager(),
        source: RepositoryProvisioningSource = flutterRepository,
    ): FlutterPubGet? = ResolveFlutterPackagesUseCase(executor, fileManager, source).prepare(repository, worktree)

    @Test
    fun `runs flutter pub get in the worktree and reports the packages resolved`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }

        val result = resolve(executor)

        assertEquals(FlutterPubGet.Resolved, result)
        assertEquals(listOf(listOf("flutter", "pub", "get")), executor.executedCommands)
        assertEquals(listOf<String?>(worktree), executor.workingDirectories)
    }

    @Test
    fun `runs it through fvm when the repository pins its version`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }

        resolve(executor, FakeFileManager(existing = listOf("$worktree/.fvmrc")))

        assertEquals(listOf(listOf("fvm", "flutter", "pub", "get")), executor.executedCommands)
    }

    @Test
    fun `reports the command that wasn't found`() = runTest {
        val executor = FakeShellCommandExecutor { throw CommandExecutionException(it, IOException("No such file or directory")) }

        assertEquals(FlutterPubGet.Needed(FlutterPubGetFailure.NotFound("flutter")), resolve(executor))
        assertEquals(
            FlutterPubGet.Needed(FlutterPubGetFailure.NotFound("fvm")),
            resolve(executor, FakeFileManager(existing = listOf("$worktree/.fvmrc"))),
        )
    }

    @Test
    fun `reports the exit code and the last error lines of a failed resolution`() = runTest {
        val stderr = (1..7).joinToString("\n") { "line $it" } + "\n\n"
        val executor = FakeShellCommandExecutor { failure(it, exitCode = 1, stderr = stderr) }

        val result = resolve(executor)

        assertEquals(
            FlutterPubGet.Needed(FlutterPubGetFailure.Exited(1, listOf("line 3", "line 4", "line 5", "line 6", "line 7"))),
            result,
        )
    }

    @Test
    fun `cancelling stops the resolution`() = runTest {
        val executor = FakeShellCommandExecutor { throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { resolve(executor) }
    }

    @Test
    fun `does nothing when the repository doesn't declare the provider`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }

        assertNull(resolve(executor, source = FakeRepositoryProvisioningSource()))
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `an unreadable repository configuration is reported as needed`() = runTest {
        val executor = FakeShellCommandExecutor { success(it) }

        val result = resolve(executor, source = FakeRepositoryProvisioningSource(unreadable = setOf(repository)))

        assertTrue(result is FlutterPubGet.Needed)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `an unexpected error without a message or a name is reported empty`() = runTest {
        val source = object : RepositoryProvisioningSource by FakeRepositoryProvisioningSource() {
            override fun read(repositoryPath: String): RepositoryProvisioning = throw object : IllegalStateException() {}
        }

        val result = resolve(FakeShellCommandExecutor { success(it) }, source = source)

        assertEquals(FlutterPubGet.Needed(FlutterPubGetFailure.Unexpected("")), result)
    }

    @Test
    fun `an unexpected error without a message is named after its type`() = runTest {
        val source = object : RepositoryProvisioningSource by FakeRepositoryProvisioningSource() {
            override fun read(repositoryPath: String): RepositoryProvisioning = throw IllegalStateException()
        }

        assertEquals(FlutterPubGet.Needed(FlutterPubGetFailure.Unexpected("IllegalStateException")), resolve(FakeShellCommandExecutor { success(it) }, source = source))
    }
}
