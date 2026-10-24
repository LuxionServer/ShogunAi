package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.OutputLine
import app.luxion.shogunai.domain.io.ComposeProjectRegistry
import app.luxion.shogunai.domain.io.ComposeProjectSource
import app.luxion.shogunai.domain.io.ComposeRegistryEntry
import app.luxion.shogunai.domain.model.ComposeAction
import app.luxion.shogunai.domain.model.ComposeLeftoversReport
import app.luxion.shogunai.domain.model.ComposeResource
import app.luxion.shogunai.domain.model.ComposeResourceFailure
import app.luxion.shogunai.domain.model.ComposeResourceKind
import app.luxion.shogunai.domain.model.ComposeDownFailure
import app.luxion.shogunai.domain.model.ComposeDownReport
import app.luxion.shogunai.domain.model.ComposeOwner
import app.luxion.shogunai.domain.model.ComposeProject
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeComposeProject

fun worktreeComposeProjectName(repositoryPath: String, worktreePath: String): String =
    composeProjectName(normalizePath(repositoryPath).substringAfterLast('/'), normalizePath(worktreePath).substringAfterLast('/'))

class ListComposeProjectsUseCase(
    private val source: ComposeProjectSource,
    private val registry: ComposeProjectRegistry? = null,
) {

    suspend operator fun invoke(
        repositoryPath: String,
        worktrees: List<Worktree>,
        worktreesRoot: String?,
    ): Result<List<WorktreeComposeProject>> = runCatching {
        val byName = projectOwners(repositoryPath, worktrees, registry)
        val leftovers = leftoverProjects(repositoryPath, worktrees, registry)
        val repositoryPrefix = composeProjectName(normalizePath(repositoryPath).substringAfterLast('/'), "").trimEnd('-', '_') + "-"
        source.list().mapNotNull { project ->
            val worktree = byName[project.name]
            when {
                worktree != null -> WorktreeComposeProject(project, ComposeOwner.Worktree(worktree.path))
                project.name in leftovers -> WorktreeComposeProject(project, ComposeOwner.RemovedWorktree)
                isOrphanOf(project, repositoryPrefix, repositoryPath, worktreesRoot, worktrees) ->
                    WorktreeComposeProject(project, ComposeOwner.RemovedWorktree)
                else -> null
            }
        }
    }

    private fun isOrphanOf(
        project: ComposeProject,
        repositoryPrefix: String,
        repositoryPath: String,
        worktreesRoot: String?,
        worktrees: List<Worktree>,
    ): Boolean {
        if (!project.name.startsWith(repositoryPrefix)) return false
        val roots = listOfNotNull(repositoryPath, worktreesRoot).map(::normalizePath)
        val existing = worktrees.map { normalizePath(it.path) }
        return project.configFiles.map(::normalizePath).any { file ->
            roots.any { isInside(file, it) } && existing.none { isInside(file, it) }
        }
    }
}

class TakeDownComposeProjectsUseCase(private val source: ComposeProjectSource) {

    suspend operator fun invoke(names: List<String>, removeVolumes: Boolean = false): ComposeDownReport {
        val takenDown = mutableListOf<String>()
        val failures = mutableListOf<ComposeDownFailure>()
        names.distinct().forEach { name ->
            source.down(name, removeVolumes)
                .onSuccess { takenDown += name }
                .onFailure { failures += ComposeDownFailure(name, it.message.orEmpty()) }
        }
        return ComposeDownReport(takenDown, failures)
    }
}

class RunComposeProjectUseCase(
    private val source: ComposeProjectSource,
    private val resolveEnvironment: ResolveWorktreeEnvironmentUseCase,
    private val registry: ComposeProjectRegistry? = null,
) {

    suspend operator fun invoke(
        repositoryPath: String,
        worktreePath: String,
        action: ComposeAction,
        onOutput: (OutputLine) -> Unit = {},
    ): Result<String> = runCatching {
        val environment = resolveEnvironment(repositoryPath, worktreePath, reserve = true).getOrThrow()
        val variables = environment.variables.toMap()
        val project = variables[COMPOSE_PROJECT_NAME] ?: worktreeComposeProjectName(repositoryPath, worktreePath)
        runCatching { registry?.record(repositoryPath, ComposeRegistryEntry(project, normalizePath(worktreePath))) }
        source.run(project, action, worktreePath, variables, onOutput).getOrThrow()
        project
    }
}

private fun projectOwners(repositoryPath: String, worktrees: List<Worktree>, registry: ComposeProjectRegistry?): Map<String, Worktree> {
    val byPath = worktrees.associateBy { normalizePath(it.path) }
    val recorded = registry?.let { runCatching { it.entries(repositoryPath) }.getOrDefault(emptyList()) }.orEmpty()
        .mapNotNull { entry -> byPath[normalizePath(entry.worktreePath)]?.let { entry.project to it } }
    return recorded.toMap() + worktrees.associateBy { worktreeComposeProjectName(repositoryPath, it.path) }
}

internal fun leftoverProjects(repositoryPath: String, worktrees: List<Worktree>, registry: ComposeProjectRegistry?): Set<String> {
    if (registry == null) return emptySet()
    val owned = projectOwners(repositoryPath, worktrees, registry).keys
    val existing = worktrees.map { normalizePath(it.path) }.toSet()
    val elsewhere = runCatching { registry.recordedElsewhere(repositoryPath) }.getOrDefault(emptySet())
    return runCatching { registry.entries(repositoryPath) }.getOrDefault(emptyList())
        .filter { normalizePath(it.worktreePath) !in existing }
        .map { it.project }
        .filter { it !in owned && it !in elsewhere }
        .toSet()
}

class ListComposeLeftoversUseCase(
    private val source: ComposeProjectSource,
    private val registry: ComposeProjectRegistry,
) {

    suspend operator fun invoke(repositoryPath: String, worktrees: List<Worktree>): Result<List<ComposeResource>> = runCatching {
        val leftovers = leftoverProjects(repositoryPath, worktrees, registry)
        if (leftovers.isEmpty()) return@runCatching emptyList()
        val resources = source.listResources().filter { it.project in leftovers }
        val withProject = source.list().map { it.name }.toSet()
        val gone = leftovers - resources.map { it.project }.toSet() - withProject
        runCatching { registry.drop(repositoryPath, gone) }
        resources
    }
}

class RemoveComposeLeftoversUseCase(
    private val source: ComposeProjectSource,
    private val registry: ComposeProjectRegistry? = null,
) {

    suspend operator fun invoke(
        resources: List<ComposeResource>,
        removeVolumes: Boolean,
        repositoryPath: String? = null,
    ): ComposeLeftoversReport {
        val removed = mutableListOf<ComposeResource>()
        val failures = mutableListOf<ComposeResourceFailure>()
        val (volumes, others) = resources.distinct().partition { it.kind == ComposeResourceKind.VOLUME }
        val kept = if (removeVolumes) emptyList() else volumes
        val toRemove = others.sortedBy { it.kind.ordinal } + if (removeVolumes) volumes else emptyList()
        toRemove.forEach { resource ->
            source.remove(resource)
                .onSuccess { removed += resource }
                .onFailure { failures += ComposeResourceFailure(resource, it.message.orEmpty()) }
        }
        val recorded = registry
        if (repositoryPath != null && recorded != null) {
            val remaining = (kept + failures.map { it.resource }).map { it.project }.toSet()
            runCatching { recorded.drop(repositoryPath, removed.map { it.project }.toSet() - remaining) }
        }
        return ComposeLeftoversReport(removed, kept, failures)
    }
}
