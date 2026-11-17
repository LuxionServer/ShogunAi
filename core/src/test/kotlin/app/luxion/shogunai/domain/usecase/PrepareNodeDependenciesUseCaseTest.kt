package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.NodeDependencies
import app.luxion.shogunai.domain.model.NodeDependenciesFailure
import app.luxion.shogunai.domain.model.NodePackageManager
import app.luxion.shogunai.domain.model.NodeProject
import app.luxion.shogunai.domain.model.NodeProvider
import app.luxion.shogunai.domain.model.ProviderToolId
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrepareNodeDependenciesUseCaseTest {

    private val repository = "/home/dev/projects/main-repo"
    private val worktree = "/home/dev/projects/TASK-123"
    private val npm = NodeProject(NodePackageManager.NPM, "package-lock.json")
    private val listIgnored = listOf("git", "ls-files", "-z", "--others", "--ignored", "--exclude-standard", "--directory")

    private val nodeRepository = FakeRepositoryProvisioningSource(
        byRepository = mapOf(repository to RepositoryProvisioning(providers = RepositoryProviders(node = NodeProvider))),
    )

    private fun installedBase(
        lockfile: String = "package-lock.json",
        marker: String? = ".package-lock.json",
        markerTime: Long = 200,
        worktreeLock: String = "lock-v1",
        directories: Set<String> = setOf(worktree),
    ): FakeFileManager = FakeFileManager(existing = listOf("$repository/$lockfile", "$worktree/$lockfile"), directories = directories).apply {
        contents["$repository/$lockfile"] = "lock-v1"
        contents["$worktree/$lockfile"] = worktreeLock
        modifiedAt["$repository/$lockfile"] = 100
        if (marker != null) modifiedAt["$repository/node_modules/$marker"] = markerTime
    }

    private fun ignoredFolders(vararg folders: String): (List<String>) -> CommandResult = { command ->
        if (command.take(2) == listOf("git", "ls-files")) success(command, folders.joinToString("\u0000")) else success(command)
    }

    private suspend fun prepare(
        executor: FakeShellCommandExecutor,
        fileManager: FakeFileManager,
        approved: Boolean = true,
        isMacOs: Boolean = true,
        source: RepositoryProvisioningSource = nodeRepository,
    ): NodeDependencies? = PrepareNodeDependenciesUseCase(executor, fileManager, source, isMacOs).prepare(repository, worktree, approved)

    @Test
    fun `does nothing when the repository doesn't declare the provider`() = runTest {
        val executor = FakeShellCommandExecutor()

        assertNull(prepare(executor, installedBase(), source = FakeRepositoryProvisioningSource()))
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `leaves a worktree that already has node_modules as it is`() = runTest {
        val executor = FakeShellCommandExecutor()
        val fileManager = installedBase().apply { writeText("$worktree/node_modules", "") }

        assertEquals(NodeDependencies.Present, prepare(executor, fileManager))
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `reports a missing lockfile without running anything`() = runTest {
        val executor = FakeShellCommandExecutor()

        val result = prepare(executor, FakeFileManager(existing = listOf("$worktree/package.json")))

        assertEquals(NodeDependencies.Needed(null, NodeDependenciesFailure.NoLockfile), result)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `clones every ignored node_modules of the base when the lock matches`() = runTest {
        val executor = FakeShellCommandExecutor(ignoredFolders("node_modules/", "packages/api/node_modules/", "dist/", "packages/gone/node_modules/"))
        val fileManager = installedBase(directories = setOf(worktree, "$worktree/packages/api"))

        val result = prepare(executor, fileManager, approved = false)

        assertEquals(NodeDependencies.Cloned(NodePackageManager.NPM, listOf("node_modules", "packages/api/node_modules")), result)
        assertTrue(executor.executed("cp", "-c", "-R", "$repository/node_modules", "$worktree/node_modules"))
        assertTrue(executor.executed("cp", "-c", "-R", "$repository/packages/api/node_modules", "$worktree/packages/api/node_modules"))
        assertTrue(executor.executedCommands.none { it.first() == "npm" })
        assertEquals(repository, executor.workingDirectories[executor.executedCommands.indexOf(listIgnored)])
    }

    @Test
    fun `installs when the branch changed the lockfile`() = runTest {
        val executor = FakeShellCommandExecutor(ignoredFolders("node_modules/"))

        val result = prepare(executor, installedBase(worktreeLock = "lock-v2"))

        assertEquals(NodeDependencies.Installed(NodePackageManager.NPM), result)
        assertEquals(listOf(listOf("npm", "ci")), executor.executedCommands)
        assertEquals(listOf<String?>(worktree), executor.workingDirectories)
        assertEquals(CommandTimeouts.INSTALL, executor.timeouts[listOf("npm", "ci")])
    }

    @Test
    fun `installs when the base pulled a lockfile it didn't install`() = runTest {
        val executor = FakeShellCommandExecutor(ignoredFolders("node_modules/"))

        assertEquals(NodeDependencies.Installed(NodePackageManager.NPM), prepare(executor, installedBase(markerTime = 50)))
    }

    @Test
    fun `installs when the base has no install marker or no lockfile date`() = runTest {
        val executor = FakeShellCommandExecutor(ignoredFolders("node_modules/"))
        val undated = installedBase().apply { modifiedAt.remove("$repository/package-lock.json") }

        assertEquals(NodeDependencies.Installed(NodePackageManager.NPM), prepare(executor, installedBase(marker = null)))
        assertEquals(NodeDependencies.Installed(NodePackageManager.NPM), prepare(executor, undated))
    }

    @Test
    fun `bun has no marker so it always installs`() = runTest {
        val executor = FakeShellCommandExecutor(ignoredFolders("node_modules/"))

        val result = prepare(executor, installedBase(lockfile = "bun.lock", marker = ".package-lock.json"))

        assertEquals(NodeDependencies.Installed(NodePackageManager.BUN), result)
        assertEquals(listOf(listOf("bun", "install", "--frozen-lockfile")), executor.executedCommands)
    }

    @Test
    fun `clones with a yarn classic marker`() = runTest {
        val executor = FakeShellCommandExecutor(ignoredFolders("node_modules/"))

        val result = prepare(executor, installedBase(lockfile = "yarn.lock", marker = ".yarn-integrity"))

        assertEquals(NodeDependencies.Cloned(NodePackageManager.YARN, listOf("node_modules")), result)
    }

    @Test
    fun `installs when git lists no node_modules or fails`() = runTest {
        val nothingIgnored = FakeShellCommandExecutor(ignoredFolders("dist/"))
        val gitFails = FakeShellCommandExecutor { command -> if (command.first() == "git") failure(command) else success(command) }

        assertEquals(NodeDependencies.Installed(NodePackageManager.NPM), prepare(nothingIgnored, installedBase()))
        assertEquals(NodeDependencies.Installed(NodePackageManager.NPM), prepare(gitFails, installedBase()))
    }

    @Test
    fun `a failed clone removes the folders cloned so far and installs`() = runTest {
        val executor = FakeShellCommandExecutor { command ->
            when {
                command.first() == "git" -> success(command, "node_modules/\u0000web/node_modules/")
                command.last() == "$worktree/web/node_modules" -> failure(command)
                else -> success(command)
            }
        }
        val fileManager = installedBase(directories = setOf(worktree, "$worktree/web")).apply { lockedFolders = setOf("$worktree/web/node_modules") }

        val result = prepare(executor, fileManager)

        assertEquals(NodeDependencies.Installed(NodePackageManager.NPM), result)
        assertEquals(listOf("$worktree/node_modules"), fileManager.deletedRecursively)
        assertEquals(listOf("npm", "ci"), executor.executedCommands.last())
    }

    @Test
    fun `never clones outside macOS`() = runTest {
        val executor = FakeShellCommandExecutor(ignoredFolders("node_modules/"))

        assertEquals(NodeDependencies.Installed(NodePackageManager.NPM), prepare(executor, installedBase(), isMacOs = false))
        assertEquals(listOf(listOf("npm", "ci")), executor.executedCommands)
    }

    @Test
    fun `an unapproved repository gets no install and is told the command`() = runTest {
        val executor = FakeShellCommandExecutor(ignoredFolders("node_modules/"))

        val result = prepare(executor, installedBase(worktreeLock = "lock-v2"), approved = false)

        assertEquals(NodeDependencies.Needed(npm, NodeDependenciesFailure.NotApproved), result)
        assertTrue(executor.executedCommands.none { it.first() == "npm" })
    }

    @Test
    fun `reports the manager that wasn't found`() = runTest {
        val executor = FakeShellCommandExecutor { throw CommandExecutionException(it, IOException("No such file or directory")) }
        val fileManager = FakeFileManager(existing = listOf("$worktree/pnpm-lock.yaml"))

        val result = assertIs<NodeDependencies.Needed>(prepare(executor, fileManager, isMacOs = false))

        assertEquals(NodeDependenciesFailure.NotFound("pnpm"), result.reason)
    }

    @Test
    fun `an install that times out is reported with the timeout`() = runTest {
        val executor = FakeShellCommandExecutor { throw CommandExecutionException.TimedOut(it, CommandTimeouts.INSTALL) }

        val result = assertIs<NodeDependencies.Needed>(prepare(executor, installedBase(), isMacOs = false))

        assertEquals(NodeDependenciesFailure.Unexpected("The command did not finish within 30m: npm ci"), result.reason)
    }

    @Test
    fun `reports the exit code and the last error lines of a failed install`() = runTest {
        val stderr = (1..7).joinToString("\n") { "line $it" } + "\n\n"
        val executor = FakeShellCommandExecutor { failure(it, exitCode = 1, stderr = stderr) }

        val result = prepare(executor, installedBase(), isMacOs = false)

        assertEquals(
            NodeDependencies.Needed(npm, NodeDependenciesFailure.Exited(1, listOf("line 3", "line 4", "line 5", "line 6", "line 7"))),
            result,
        )
    }

    @Test
    fun `cancelling stops the install`() = runTest {
        val executor = FakeShellCommandExecutor { throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> { prepare(executor, installedBase(), isMacOs = false) }
    }

    @Test
    fun `an unreadable repository configuration is reported as needed`() = runTest {
        val executor = FakeShellCommandExecutor()

        val result = prepare(executor, installedBase(), source = FakeRepositoryProvisioningSource(unreadable = setOf(repository)))

        assertIs<NodeDependenciesFailure.Unexpected>(assertIs<NodeDependencies.Needed>(result).reason)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `a failure without a message is reported by its type`() = runTest {
        val executor = FakeShellCommandExecutor { throw IllegalStateException() }

        val result = assertIs<NodeDependencies.Needed>(prepare(executor, installedBase()))

        assertEquals(NodeDependenciesFailure.Unexpected("IllegalStateException"), result.reason)
    }

    @Test
    fun `the doctor checks node and the manager of the lockfile`() {
        fun tools(vararg files: String) = nodeTools(FakeFileManager(existing = files.map { "$repository/$it" }), repository)

        assertEquals(listOf(ProviderToolId.NODE), tools())
        assertEquals(listOf(ProviderToolId.NODE, ProviderToolId.NPM), tools("package-lock.json"))
        assertEquals(listOf(ProviderToolId.NODE, ProviderToolId.PNPM), tools("pnpm-lock.yaml"))
        assertEquals(listOf(ProviderToolId.NODE, ProviderToolId.YARN), tools("yarn.lock"))
        assertEquals(listOf(ProviderToolId.BUN), tools("bun.lockb"))
    }
}
