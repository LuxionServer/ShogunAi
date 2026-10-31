package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.model.PodsInstallNeeded
import app.luxion.shogunai.domain.model.PodsInstallReason
import app.luxion.shogunai.domain.model.PodsPreparation
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.XcodeProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PrepareCocoaPodsUseCaseTest {
    private val base = "/home/dev/projects/main-repo"
    private val worktree = "/home/dev/projects/main-repo-worktrees/task-123"
    private val lock = "PODFILE CHECKSUM: abc"

    private val cloneCommand = listOf("cp", "-c", "-R", "$base/iosApp/Pods", "$worktree/iosApp/Pods")

    private fun executor(
        tracked: String = "iosApp/Podfile.lock\u0000",
        listing: (List<String>) -> CommandResult = { FakeShellCommandExecutor.success(it, tracked) },
        clone: (List<String>) -> CommandResult = { FakeShellCommandExecutor.success(it) },
    ) = FakeShellCommandExecutor { command -> if (command.first() == "git") listing(command) else clone(command) }

    private fun fileManager(existing: List<String> = listOf("$base/iosApp/Pods/Manifest.lock"), baseManifest: String? = lock) =
        FakeFileManager(existing = existing).apply {
            contents["$worktree/iosApp/Podfile.lock"] = lock
            baseManifest?.let { contents["$base/iosApp/Pods/Manifest.lock"] = it }
        }

    private fun source(xcode: XcodeProvider? = XcodeProvider(cocoaPods = true)) = FakeRepositoryProvisioningSource(
        byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(xcode = xcode))),
    )

    private suspend fun prepare(
        executor: FakeShellCommandExecutor = executor(),
        fileManager: FakeFileManager = fileManager(),
        source: FakeRepositoryProvisioningSource = source(),
        isMacOs: Boolean = true,
    ) = PrepareCocoaPodsUseCase(executor, fileManager, source, isMacOs).prepare(base, worktree)

    @Test
    fun `does nothing when the option is off`() = runTest {
        val executor = executor()

        assertEquals(PodsPreparation(), prepare(executor = executor, source = source(XcodeProvider())))
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `does nothing off macOS`() = runTest {
        val executor = executor()

        assertEquals(PodsPreparation(), prepare(executor = executor, isMacOs = false))
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `clones the base pods of a lock in a subfolder when the manifest matches`() = runTest {
        val executor = executor()

        val result = prepare(executor = executor)

        assertEquals(PodsPreparation(cloned = listOf("iosApp")), result)
        assertTrue(executor.executedCommands.contains(cloneCommand))
        assertEquals(listOf("git", "ls-files", "-z", "--", ":(glob)**/Podfile.lock"), executor.executedCommands.first())
        assertEquals(worktree, executor.workingDirectories.first())
    }

    @Test
    fun `clones the pods of a lock at the root`() = runTest {
        val fileManager = FakeFileManager(existing = listOf("$base/Pods/Manifest.lock")).apply {
            contents["$worktree/Podfile.lock"] = lock
            contents["$base/Pods/Manifest.lock"] = lock
        }
        val executor = executor(tracked = "Podfile.lock\u0000")

        assertEquals(PodsPreparation(cloned = listOf(".")), prepare(executor = executor, fileManager = fileManager))
        assertTrue(executor.executedCommands.contains(listOf("cp", "-c", "-R", "$base/Pods", "$worktree/Pods")))
    }

    @Test
    fun `finds nothing without a tracked lock`() = runTest {
        val executor = executor(tracked = "")

        assertEquals(PodsPreparation(), prepare(executor = executor))
        assertEquals(1, executor.executedCommands.size)
    }

    @Test
    fun `leaves existing pods alone`() = runTest {
        val executor = executor()
        val fileManager = fileManager(existing = listOf("$base/iosApp/Pods/Manifest.lock", "$worktree/iosApp/Pods"))

        assertEquals(PodsPreparation(), prepare(executor = executor, fileManager = fileManager))
        assertTrue(cloneCommand !in executor.executedCommands)
    }

    @Test
    fun `reports pod install when the base has no pods`() = runTest {
        val result = prepare(fileManager = fileManager(existing = emptyList(), baseManifest = null))

        assertEquals(listOf(PodsInstallNeeded("iosApp", PodsInstallReason.BASE_HAS_NO_PODS)), result.installNeeded)
    }

    @Test
    fun `reports pod install when the base pods differ`() = runTest {
        val executor = executor()

        val result = prepare(executor = executor, fileManager = fileManager(baseManifest = "PODFILE CHECKSUM: old"))

        assertEquals(listOf(PodsInstallNeeded("iosApp", PodsInstallReason.BASE_PODS_DIFFER)), result.installNeeded)
        assertTrue(cloneCommand !in executor.executedCommands)
    }

    @Test
    fun `removes a partial clone and reports pod install when the clone fails`() = runTest {
        val fileManager = fileManager()

        val result = prepare(executor = executor(clone = { FakeShellCommandExecutor.failure(it) }), fileManager = fileManager)

        assertEquals(listOf(PodsInstallNeeded("iosApp", PodsInstallReason.CLONE_FAILED)), result.installNeeded)
        assertEquals(listOf("$worktree/iosApp/Pods"), fileManager.deletedRecursively)
    }

    @Test
    fun `reports an error instead of throwing when the locks can't be listed`() = runTest {
        val result = prepare(executor = executor(listing = { FakeShellCommandExecutor.failure(it, stderr = "not a git repository") }))

        assertEquals("not a git repository", assertNotNull(result.error))
        assertTrue(result.cloned.isEmpty())
    }
    @Test
    fun `does nothing without the xcode provider`() = runTest {
        assertEquals(PodsPreparation(), prepare(source = source(null)))
    }

    @Test
    fun `a failing listing reports git's message, a generic one or the error's name`() = runTest {
        val silent = executor(listing = { FakeShellCommandExecutor.failure(it, stderr = " ") })
        val throwing = executor(listing = { throw IllegalStateException() })

        assertEquals(PodsPreparation(error = "git ls-files failed"), prepare(executor = silent))
        assertEquals(PodsPreparation(error = "IllegalStateException"), prepare(executor = throwing))
    }
}
