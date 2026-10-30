package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.LocalFileCandidate
import app.luxion.shogunai.domain.model.MobileSecretKind
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ListLocalFileCandidatesUseCaseTest {

    private val base = "/home/dev/projects/main-repo"
    private val config = ProjectConfig(baseRepositoryPath = base, worktreesRoot = "/home/dev/projects", secretFiles = emptyList())

    private fun executor(
        untracked: List<String> = emptyList(),
        ignored: List<String> = emptyList(),
        provided: List<String> = emptyList(),
    ) = FakeShellCommandExecutor { command ->
        when {
            "--directory" in command && "--ignored" in command -> success(command, stdout = ignored.nulTerminated())
            "--directory" in command -> success(command, stdout = untracked.nulTerminated())
            else -> success(command, stdout = provided.nulTerminated())
        }
    }

    private fun List<String>.nulTerminated() = joinToString("") { "$it\u0000" }

    private suspend fun list(
        executor: FakeShellCommandExecutor,
        provisioning: RepositoryProvisioning = RepositoryProvisioning(),
        fileManager: FakeFileManager = FakeFileManager(),
    ): List<LocalFileCandidate> = ListLocalFileCandidatesUseCase(
        executor,
        fileManager,
        FakeRepositoryProvisioningSource(byRepository = mapOf(base to provisioning)),
    )(config)

    @Test
    fun `lists untracked and ignored files and folders`() = runTest {
        val candidates = list(executor(untracked = listOf("notes.txt"), ignored = listOf(".env", "keys/")))

        assertEquals(
            listOf(
                LocalFileCandidate("notes.txt", isFolder = false),
                LocalFileCandidate(".env", isFolder = false),
                LocalFileCandidate("keys", isFolder = true),
            ),
            candidates,
        )
    }

    @Test
    fun `asks git for untracked and ignored entries with whole folders collapsed`() = runTest {
        val executor = executor()

        list(executor)

        assertEquals(
            listOf(
                listOf("git", "ls-files", "-z", "--others", "--exclude-standard", "--directory", "--no-empty-directory"),
                listOf("git", "ls-files", "-z", "--others", "--exclude-standard", "--directory", "--no-empty-directory", "--ignored"),
            ),
            executor.executedCommands,
        )
        assertTrue(executor.workingDirectories.all { it == base })
    }

    @Test
    fun `skips generated folders and what they hold`() = runTest {
        val candidates = list(
            executor(ignored = listOf("build/", "app/build/", ".gradle/", "node_modules/", "ios/Pods/", "app/build/local.properties", "local.properties")),
        )

        assertEquals(listOf(LocalFileCandidate("local.properties", isFolder = false, secretKind = MobileSecretKind.LOCAL_PROPERTIES)), candidates)
    }

    @Test
    fun `keeps a file whose name matches a generated folder`() = runTest {
        val candidates = list(executor(untracked = listOf("tools/build")))

        assertEquals(listOf(LocalFileCandidate("tools/build", isFolder = false)), candidates)
    }

    @Test
    fun `marks the detected mobile secrets with their kind`() = runTest {
        val candidates = list(
            executor(
                ignored = listOf(
                    "local.properties",
                    "keystore.properties",
                    "keys/release.jks",
                    "app/google-services.json",
                    "ios/GoogleService-Info.plist",
                    "lib/firebase_options.dart",
                    ".env",
                ),
            ),
        )

        assertEquals(
            listOf(
                MobileSecretKind.LOCAL_PROPERTIES,
                MobileSecretKind.SIGNING_PROPERTIES,
                MobileSecretKind.KEYSTORE,
                MobileSecretKind.FIREBASE_CONFIG,
                MobileSecretKind.FIREBASE_CONFIG,
                MobileSecretKind.FIREBASE_CONFIG,
                null,
            ),
            candidates.map { it.secretKind },
        )
    }

    @Test
    fun `marks the files the repository already provides`() = runTest {
        val executor = executor(ignored = listOf("local.properties", ".env"), provided = listOf("local.properties"))

        val candidates = list(executor, RepositoryProvisioning(linkPatterns = listOf("/local.properties")))

        assertEquals(listOf(true, false), candidates.map { it.providedByRepository })
        assertEquals(
            listOf("git", "ls-files", "--others", "--ignored", "-z", "-x", "/local.properties"),
            executor.executedCommands.last(),
        )
    }

    @Test
    fun `marks a folder whose every file the repository provides`() = runTest {
        val fileManager = FakeFileManager().apply {
            directoryFiles["$base/keys"] = listOf("release.jks", "debug.jks")
            directoryFiles["$base/certs"] = listOf("a.pem", "b.pem")
        }
        val executor = executor(ignored = listOf("keys/", "certs/"), provided = listOf("keys/release.jks", "keys/debug.jks", "certs/a.pem"))

        val candidates = list(executor, RepositoryProvisioning(copyPatterns = listOf("*.jks", "a.pem")), fileManager)

        assertEquals(listOf(true, false), candidates.map { it.providedByRepository })
    }

    @Test
    fun `includes the worktree include file's patterns`() = runTest {
        val executor = executor(ignored = listOf(".env"), provided = listOf(".env"))

        val candidates = list(executor, RepositoryProvisioning(worktreeIncludePatterns = listOf(".env")))

        assertTrue(candidates.single().providedByRepository)
    }

    @Test
    fun `fails with git's message when the folder isn't a repository`() = runTest {
        val executor = FakeShellCommandExecutor { failure(it, exitCode = 128, stderr = "fatal: not a git repository") }

        val error = assertFailsWith<WorktreeError.GitCommandFailed> { list(executor) }

        assertTrue("not a git repository" in error.errorOutput)
    }
    @Test
    fun `an empty folder isn't provided by the repository`() = runTest {
        val fileManager = FakeFileManager().apply { directoryFiles["$base/keys"] = emptyList() }
        val executor = executor(ignored = listOf("keys/"), provided = listOf("other.jks"))

        val candidates = list(executor, RepositoryProvisioning(copyPatterns = listOf("*.jks")), fileManager)

        assertEquals(listOf(false), candidates.map { it.providedByRepository })
    }
}
