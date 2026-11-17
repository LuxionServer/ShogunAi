package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.NODE_LOCKFILES
import app.luxion.shogunai.domain.model.NODE_MODULES
import app.luxion.shogunai.domain.model.NodeDependencies
import app.luxion.shogunai.domain.model.NodeDependenciesFailure
import app.luxion.shogunai.domain.model.NodePackageManager
import app.luxion.shogunai.domain.model.NodeProject
import app.luxion.shogunai.domain.model.PACKAGE_JSON
import app.luxion.shogunai.domain.model.ProviderToolId
import app.luxion.shogunai.domain.model.chooseNodeProject
import app.luxion.shogunai.domain.model.joinPath
import app.luxion.shogunai.domain.model.packageManagerName
import app.luxion.shogunai.domain.model.runSuspendCatching
import kotlinx.coroutines.CancellationException

fun interface NodeDependenciesPreparer {
    suspend fun prepare(repositoryPath: String, worktreePath: String, approved: Boolean): NodeDependencies?

    companion object {
        val NONE = NodeDependenciesPreparer { _, _, _ -> null }
    }
}

fun findNodeProject(fileManager: FileManager, folder: String): NodeProject? = chooseNodeProject(
    lockfiles = NODE_LOCKFILES.filter { fileManager.exists(joinPath(folder, it)) }.toSet(),
    packageManager = packageManagerName(fileManager.readText(joinPath(folder, PACKAGE_JSON))),
    yarnBerry = fileManager.exists(joinPath(folder, YARN_BERRY_CONFIG)),
)

private const val YARN_BERRY_CONFIG = ".yarnrc.yml"

class PrepareNodeDependenciesUseCase(
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
    private val provisioningSource: RepositoryProvisioningSource,
    private val isMacOs: Boolean,
) : NodeDependenciesPreparer {

    override suspend fun prepare(repositoryPath: String, worktreePath: String, approved: Boolean): NodeDependencies? = try {
        if (provisioningSource.read(repositoryPath).providers.node == null) null else dependencies(repositoryPath, worktreePath, approved)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        NodeDependencies.Needed(null, NodeDependenciesFailure.Unexpected(error.message ?: error::class.java.simpleName))
    }

    private suspend fun dependencies(repositoryPath: String, worktreePath: String, approved: Boolean): NodeDependencies {
        if (fileManager.exists(joinPath(worktreePath, NODE_MODULES))) return NodeDependencies.Present
        val project = findNodeProject(fileManager, worktreePath)
            ?: return NodeDependencies.Needed(null, NodeDependenciesFailure.NoLockfile)
        if (isMacOs) cloneFromBase(repositoryPath, worktreePath, project)?.let { return it }
        if (!approved) return NodeDependencies.Needed(project, NodeDependenciesFailure.NotApproved)
        return install(worktreePath, project)
    }

    private suspend fun cloneFromBase(repositoryPath: String, worktreePath: String, project: NodeProject): NodeDependencies? {
        if (!baseInstallMatches(repositoryPath, worktreePath, project)) return null
        val folders = baseNodeModules(repositoryPath).filter { fileManager.isDirectory(parentIn(worktreePath, it)) }
        if (folders.isEmpty()) return null
        val cloned = mutableListOf<String>()
        for (folder in folders) {
            val destination = joinPath(worktreePath, folder)
            val clone = executor.executeWithin(
                command = listOf("cp", "-c", "-R", joinPath(repositoryPath, folder), destination),
                timeout = CommandTimeouts.LOCAL_WORK,
            )
            if (!clone.isSuccess) {
                (cloned + folder).forEach { runSuspendCatching { fileManager.deleteRecursively(joinPath(worktreePath, it)) } }
                return null
            }
            cloned += folder
        }
        return NodeDependencies.Cloned(project.manager, cloned)
    }

    private fun baseInstallMatches(repositoryPath: String, worktreePath: String, project: NodeProject): Boolean {
        val baseLockfile = joinPath(repositoryPath, project.lockfile)
        if (!fileManager.hasSameContent(baseLockfile, joinPath(worktreePath, project.lockfile))) return false
        val lockfileTime = fileManager.lastModifiedMillis(baseLockfile) ?: return false
        return project.manager.markers.any { marker ->
            val markerTime = fileManager.lastModifiedMillis(joinPath(repositoryPath, "$NODE_MODULES/$marker"))
            markerTime != null && markerTime >= lockfileTime
        }
    }

    private suspend fun baseNodeModules(repositoryPath: String): List<String> {
        val result = executor.executeWithin(
            command = listOf("git", "ls-files", "-z", "--others", "--ignored", "--exclude-standard", "--directory"),
            timeout = CommandTimeouts.GIT_READ,
            workingDirectory = repositoryPath,
        )
        if (!result.isSuccess) return emptyList()
        return result.stdout.split('\u0000')
            .map { it.trimEnd('/') }
            .filter { it == NODE_MODULES || it.endsWith("/$NODE_MODULES") }
    }

    private fun parentIn(worktreePath: String, folder: String): String =
        folder.removeSuffix(NODE_MODULES).trimEnd('/').let { parent -> if (parent.isEmpty()) worktreePath else joinPath(worktreePath, parent) }

    private suspend fun install(worktreePath: String, project: NodeProject): NodeDependencies {
        val command = project.installCommand
        val result = try {
            executor.executeWithin(command = command, timeout = CommandTimeouts.INSTALL, workingDirectory = worktreePath)
        } catch (error: CommandExecutionException.TimedOut) {
            return NodeDependencies.Needed(project, NodeDependenciesFailure.Unexpected(error.message))
        } catch (error: CommandExecutionException) {
            return NodeDependencies.Needed(project, NodeDependenciesFailure.NotFound(command.first()))
        }
        if (result.isSuccess) return NodeDependencies.Installed(project.manager)
        val errorLines = result.stderr.lines().map { it.trimEnd() }.filter { it.isNotBlank() }.takeLast(ERROR_LINES)
        return NodeDependencies.Needed(project, NodeDependenciesFailure.Exited(result.exitCode, errorLines))
    }

    private companion object {
        const val ERROR_LINES = 5
    }
}

fun nodeTools(fileManager: FileManager, repository: String): List<ProviderToolId> {
    val project = findNodeProject(fileManager, repository) ?: return listOf(ProviderToolId.NODE)
    return when (project.manager) {
        NodePackageManager.NPM -> listOf(ProviderToolId.NODE, ProviderToolId.NPM)
        NodePackageManager.PNPM -> listOf(ProviderToolId.NODE, ProviderToolId.PNPM)
        NodePackageManager.YARN -> listOf(ProviderToolId.NODE, ProviderToolId.YARN)
        NodePackageManager.BUN -> listOf(ProviderToolId.BUN)
    }
}
