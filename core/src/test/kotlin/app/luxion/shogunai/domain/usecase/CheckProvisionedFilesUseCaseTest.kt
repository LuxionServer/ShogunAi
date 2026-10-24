package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeProvisioningRecordStore
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.ProvisionDrift
import app.luxion.shogunai.domain.model.ProvisionDriftState
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CheckProvisionedFilesUseCaseTest {

    private val base = "/home/dev/projects/main-repo"
    private val linked = "/home/dev/projects/TASK-123"
    private val config = ProjectConfig(baseRepositoryPath = base, worktreesRoot = "/home/dev/projects", secretFiles = emptyList())

    private val porcelain = """
        worktree $base
        HEAD 1111111111111111111111111111111111111111
        branch refs/heads/main

        worktree $linked
        HEAD 2222222222222222222222222222222222222222
        branch refs/heads/feature/TASK-123
    """.trimIndent()

    private val executor = FakeShellCommandExecutor {
        when {
            it.getOrNull(1) == "worktree" -> success(it, stdout = porcelain)
            it.getOrNull(1) == "ls-files" && "local.properties" in it -> success(it, stdout = "local.properties\u0000")
            it.getOrNull(1) == "ls-files" -> success(it, stdout = ".env\u0000")
            else -> success(it)
        }
    }

    private val provisioning = FakeRepositoryProvisioningSource(
        byRepository = mapOf(base to RepositoryProvisioning(copyPatterns = listOf(".env"), linkPatterns = listOf("local.properties"))),
    )

    private fun fileManager(vararg existing: String) = FakeFileManager(existing = listOf(base, linked) + existing).also {
        it.contents["$base/.env"] = "KEY=1"
    }

    @Test
    fun `reports a stale copy as differing`() = runTest {
        val files = fileManager("$base/.env", "$linked/.env", "$base/local.properties", "$linked/local.properties")
        files.contents["$linked/.env"] = "KEY=0"
        files.symbolicLinks["$linked/local.properties"] = "$base/local.properties"

        val drift = CheckProvisionedFilesUseCase(config, executor, files, provisioning)(linked).getOrThrow()

        assertEquals(listOf(ProvisionDrift(".env", ProvisionDriftState.DIFFERS)), drift)
        assertTrue(files.copied.isEmpty())
    }

    @Test
    fun `reports missing files and links replaced by regular files`() = runTest {
        val files = fileManager("$base/.env", "$base/local.properties", "$linked/local.properties")

        val drift = CheckProvisionedFilesUseCase(config, executor, files, provisioning)(linked).getOrThrow()

        assertEquals(
            listOf(
                ProvisionDrift(".env", ProvisionDriftState.MISSING),
                ProvisionDrift("local.properties", ProvisionDriftState.NOT_LINKED),
            ),
            drift,
        )
        assertTrue(files.copied.isEmpty() && files.symbolicLinks.isEmpty())
    }

    @Test
    fun `reports nothing when everything matches`() = runTest {
        val files = fileManager("$base/.env", "$linked/.env", "$base/local.properties", "$linked/local.properties")
        files.contents["$linked/.env"] = "KEY=1"
        files.symbolicLinks["$linked/local.properties"] = "$base/local.properties"

        assertEquals(emptyList(), CheckProvisionedFilesUseCase(config, executor, files, provisioning)(linked).getOrThrow())
    }

    @Test
    fun `refuses the main worktree`() = runTest {
        val error = CheckProvisionedFilesUseCase(config, executor, fileManager(), provisioning)(base).exceptionOrNull()

        assertIs<WorktreeError.NotAWorktree>(error)
    }

    @Test
    fun `reports a missing and a different file of a local folder`() = runTest {
        val keysFolder = "$base/keys"
        val files = FakeFileManager(
            existing = listOf(base, linked, keysFolder, "$keysFolder/a.pem", "$keysFolder/ci/b.pem", "$linked/keys/ci/b.pem", "$base/.env", "$linked/.env"),
            directories = setOf(keysFolder),
        )
        files.directoryFiles[keysFolder] = listOf("a.pem", "ci/b.pem")
        files.contents["$keysFolder/ci/b.pem"] = "new"
        files.contents["$linked/keys/ci/b.pem"] = "old"
        files.contents["$base/.env"] = "KEY=1"
        files.contents["$linked/.env"] = "KEY=1"
        val withFolder = config.copy(secretFiles = listOf("keys"))
        val onlyCopies = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(copyPatterns = listOf(".env"))),
        )

        val drift = CheckProvisionedFilesUseCase(withFolder, executor, files, onlyCopies)(linked).getOrThrow()

        assertEquals(
            listOf(
                ProvisionDrift("keys/a.pem", ProvisionDriftState.MISSING),
                ProvisionDrift("keys/ci/b.pem", ProvisionDriftState.DIFFERS),
            ),
            drift,
        )
    }

    private val baseKeys = "$base/keys"
    private val worktreeKeys = "$linked/keys"

    private fun keysFileManager(vararg existing: String) =
        FakeFileManager(existing = listOf(base, linked, baseKeys) + existing, directories = setOf(baseKeys)).also {
            it.directoryFiles[baseKeys] = listOf("a.pem")
            it.contents["$baseKeys/a.pem"] = "A"
            it.contents["$worktreeKeys/a.pem"] = "A"
        }

    @Test
    fun `reports files deleted from a copied folder without deleting them`() = runTest {
        val fileManager = keysFileManager("$worktreeKeys/a.pem", "$worktreeKeys/old.pem")
        val records = FakeProvisioningRecordStore(mapOf(linked to mapOf("keys" to listOf("a.pem", "old.pem", "gone.pem"))))
        val useCase = CheckProvisionedFilesUseCase(
            config.copy(secretFiles = listOf("keys")), executor, fileManager, FakeRepositoryProvisioningSource(), records,
        )

        val drift = useCase(linked).getOrThrow()

        assertEquals(listOf(ProvisionDrift("keys/old.pem", ProvisionDriftState.DELETED_IN_BASE)), drift)
        assertTrue(fileManager.deletedFiles.isEmpty())
    }

    @Test
    fun `reports a linked folder that is missing or not a link`() = runTest {
        val linkedConfig = config.copy(secretFiles = listOf("keys"), linkedSecretFiles = listOf("keys"))
        val missing = CheckProvisionedFilesUseCase(linkedConfig, executor, keysFileManager(), FakeRepositoryProvisioningSource())
        val notLinked = CheckProvisionedFilesUseCase(linkedConfig, executor, keysFileManager(worktreeKeys), FakeRepositoryProvisioningSource())
        val linkedFileManager = keysFileManager().also { it.createSymbolicLink(target = baseKeys, link = worktreeKeys) }
        val upToDate = CheckProvisionedFilesUseCase(linkedConfig, executor, linkedFileManager, FakeRepositoryProvisioningSource())

        assertEquals(listOf(ProvisionDrift("keys", ProvisionDriftState.MISSING)), missing(linked).getOrThrow())
        assertEquals(listOf(ProvisionDrift("keys", ProvisionDriftState.NOT_LINKED)), notLinked(linked).getOrThrow())
        assertEquals(emptyList(), upToDate(linked).getOrThrow())
    }
}
