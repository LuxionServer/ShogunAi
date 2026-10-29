package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.ComposeProjectRegistry
import app.luxion.shogunai.domain.io.ComposeProjectSource
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.ComposeDownReport
import app.luxion.shogunai.domain.model.ComposeLeftoversReport
import app.luxion.shogunai.domain.model.MainBranch
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.WorktreeStatus
import kotlin.coroutines.cancellation.CancellationException

data class CleanupReport(
    val removed: List<String>,
    val keptBranches: List<String>,
    val failures: List<CleanupFailure>,
    val herdrFailures: List<CleanupFailure> = emptyList(),
    val orcaFailures: List<CleanupFailure> = emptyList(),
    val headlessAppFailures: List<CleanupFailure> = emptyList(),
    val composeDown: ComposeDownReport? = null,
    val composeUnavailable: String? = null,
    val composeResources: ComposeLeftoversReport? = null,
)

data class CleanupFailure(val path: String, val error: WorktreeError)

data class CleanupSelection(
    val candidates: List<Worktree>,
    val keptForRunningAgent: Map<Worktree, List<RunningAgent>> = emptyMap(),
)

fun selectCleanup(
    worktrees: List<Worktree>,
    statuses: Map<String, WorktreeStatus>,
    runningAgents: Map<String, List<RunningAgent>>? = null,
): CleanupSelection {
    val qualifying = worktrees.filter { worktree ->
        !worktree.isMain && !worktree.isLocked && (worktree.isPrunable || statuses[worktree.path].isCleanAndMerged())
    }
    val (kept, candidates) = qualifying.partition { !it.isPrunable && runningAgents?.get(it.path).orEmpty().isNotEmpty() }
    return CleanupSelection(candidates, kept.associateWith { runningAgents?.get(it.path).orEmpty() })
}

fun selectCleanupCandidates(
    worktrees: List<Worktree>,
    statuses: Map<String, WorktreeStatus>,
    runningAgents: Map<String, List<RunningAgent>>? = null,
): List<Worktree> = selectCleanup(worktrees, statuses, runningAgents).candidates

private fun WorktreeStatus?.isCleanAndMerged(): Boolean =
    this != null && isMerged && uncommittedChanges == 0

class CleanUpWorktreesUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
    private val closeHerdrWorkspaces: CloseHerdrWorkspacesUseCase? = null,
    private val composeProjects: ComposeProjectSource? = null,
    fileManager: FileManager? = null,
    private val composeRegistry: ComposeProjectRegistry? = null,
    stopHeadlessApp: StopHeadlessAppUseCase? = null,
    releaseDesktopTurn: ReleaseDesktopTurnUseCase? = null,
    private val closeOrcaTerminals: CloseOrcaTerminalsUseCase? = null,
) {
    private val folderRemoval =
        WorktreeFolderRemoval(config, executor, fileManager, closeHerdrWorkspaces, stopHeadlessApp, releaseDesktopTurn, closeOrcaTerminals)

    suspend operator fun invoke(
        candidates: List<Worktree>,
        mainBranch: MainBranch?,
        deleteBranches: Boolean,
        downComposeProjects: Boolean = false,
        removeComposeVolumes: Boolean = false,
    ): Result<CleanupReport> = runCatching {
        val workspaces = closeHerdrWorkspaces?.openWorkspaces(candidates.map { it.path }).orEmpty()
        val orcaTerminals = closeOrcaTerminals?.withTerminals(candidates.filterNot { it.isPrunable }.map { it.path }).orEmpty()
        val keptBranches = mutableListOf<String>()
        val failures = mutableListOf<CleanupFailure>()

        val removedWorktrees = mutableListOf<Worktree>()
        val closedWorkspacePaths = mutableSetOf<String>()
        val herdrFailures = mutableListOf<CleanupFailure>()
        val closedOrcaPaths = mutableSetOf<String>()
        val orcaFailures = mutableListOf<CleanupFailure>()
        val headlessAppFailures = mutableListOf<CleanupFailure>()

        candidates.filterNot { it.isPrunable }.forEach { worktree ->
            try {
                val closing = folderRemoval.remove(worktree.path, force = false, workspaceId = workspaces[worktree.path], worktree.path in orcaTerminals)
                if (closing.closedWorkspace) closedWorkspacePaths += worktree.path
                if (closing.closedOrcaTerminals || closing.orcaFailure != null) closedOrcaPaths += worktree.path
                closing.orcaFailure?.let { orcaFailures += CleanupFailure(worktree.path, it) }
                closing.failure?.let { herdrFailures += CleanupFailure(worktree.path, it) }
                closing.headlessAppFailure?.let { headlessAppFailures += CleanupFailure(worktree.path, it) }
                removedWorktrees += worktree
            } catch (error: CancellationException) {
                throw error
            } catch (error: WorktreeError) {
                failures += CleanupFailure(worktree.path, error)
            }
        }

        val missing = candidates.filter { it.isPrunable }
        if (missing.isNotEmpty()) {
            val prune = git("worktree", "prune")
            if (prune.isSuccess) {
                removedWorktrees += missing
            } else {
                failures += missing.map { CleanupFailure(it.path, prune.toError()) }
            }
        }

        herdrFailures += closeHerdrWorkspaces?.close(workspaces, removedWorktrees.map { it.path }.filter { it !in closedWorkspacePaths }).orEmpty()
        orcaFailures += closeOrcaTerminals?.close(orcaTerminals, removedWorktrees.map { it.path }.filter { it !in closedOrcaPaths }).orEmpty()

        if (deleteBranches) {
            removedWorktrees.mapNotNull { it.branch }.forEach { branch ->
                if (deleteBranchIfContained(executor, config, branch, mainBranch) != BranchDeletion.Deleted) keptBranches += branch
            }
        }

        val compose = if (downComposeProjects) takeDownComposeProjects(removedWorktrees, removeComposeVolumes) else null

        CleanupReport(
            removed = removedWorktrees.map { it.path },
            keptBranches = keptBranches,
            failures = failures,
            herdrFailures = herdrFailures,
            orcaFailures = orcaFailures,
            headlessAppFailures = headlessAppFailures,
            composeDown = compose?.getOrNull()?.first,
            composeUnavailable = compose?.exceptionOrNull()?.let { (it as? WorktreeError.DockerUnavailable)?.reason ?: it.message.orEmpty() },
            composeResources = compose?.getOrNull()?.second,
        )
    }

    private suspend fun takeDownComposeProjects(
        removed: List<Worktree>,
        removeVolumes: Boolean,
    ): Result<Pair<ComposeDownReport, ComposeLeftoversReport>>? {
        val source = composeProjects ?: return null
        if (removed.isEmpty()) return null
        return runCatching {
            val names = composeProjectNamesOf(removed)
            val existing = source.list().map { it.name }.filter { it in names }
            val down = TakeDownComposeProjectsUseCase(source)(existing)
            val resources = source.listResources().filter { it.project in names }
            down to RemoveComposeLeftoversUseCase(source, composeRegistry)(resources, removeVolumes, config.baseRepositoryPath)
        }
    }

    private fun composeProjectNamesOf(removed: List<Worktree>): Set<String> {
        val paths = removed.map { normalizePath(it.path) }.toSet()
        val recorded = composeRegistry?.let { runCatching { it.entries(config.baseRepositoryPath) }.getOrDefault(emptyList()) }.orEmpty()
            .filter { normalizePath(it.worktreePath) in paths }
            .map { it.project }
        return removed.map { worktreeComposeProjectName(config.baseRepositoryPath, it.path) }.toSet() + recorded
    }

    private suspend fun git(vararg args: String) =
        executor.execute(listOf("git", *args), workingDirectory = config.baseRepositoryPath)

    private fun CommandResult.toError() =
        WorktreeError.GitCommandFailed(command, exitCode, stderr)
}
