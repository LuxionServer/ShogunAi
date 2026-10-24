package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.MultirepoSource
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.DEFAULT_WORKSPACE_ALIAS
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorkspaceSetting
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.isValidMultirepoAlias
import app.luxion.shogunai.domain.model.joinPath
import app.luxion.shogunai.domain.model.relativeMultirepoPath
import java.util.UUID

enum class WorktreesFolderProposal { ALREADY_MATCHES, ACCEPTED, DECLINED }

data class AddedRepository(
    val alias: String,
    val path: String,
    val project: Project,
    val registered: Boolean,
    val worktreesFolder: WorktreesFolderProposal,
)

fun interface WorktreesFolderDecision {
    fun accept(project: String, current: String?, proposed: String): Boolean
}

class ConfigureMultirepoUseCase(
    private val source: MultirepoSource,
    private val resolver: ResolveMultirepoUseCase,
    private val projects: ProjectRepository,
    private val executor: ShellCommandExecutor,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun init(folder: String, worktreesRoot: String?): Result<Multirepo> = runCatching {
        val normalized = normalizePath(folder)
        if (source.read(normalized) != null) {
            throw WorktreeError.InvalidMultirepo(Multirepo(normalized, MultirepoDeclaration()).declarationPath, "it's already a multirepo")
        }
        val enclosing = ResolveRepositoryUseCase(executor)(normalized).getOrNull()
        if (enclosing != null && !sameDirectory(enclosing, normalized)) {
            throw WorktreeError.InvalidMultirepo(normalized, "a multirepo is never declared inside a repository ($enclosing)")
        }
        val name = Multirepo(normalized, MultirepoDeclaration()).name
        val root = worktreesRoot?.let { relativeMultirepoPath(normalized, it) } ?: "../$name-worktrees"
        val declaration = MultirepoDeclaration(worktreesRoot = root)
        source.write(normalized, declaration)
        source.remember(normalized)
        approveWritten(normalized, declaration)
    }

    suspend fun addRepository(
        multirepo: Multirepo,
        alias: String,
        path: String,
        decision: WorktreesFolderDecision,
    ): Result<AddedRepository> = runCatching {
        fun reject(detail: String): Nothing = throw WorktreeError.InvalidMultirepo(multirepo.declarationPath, detail)
        requireApproved(multirepo)
        if (!isValidMultirepoAlias(alias)) reject("alias '$alias' must use only lowercase letters, digits and hyphens")
        if (alias in multirepo.declaration.repos) reject("alias '$alias' is already taken")
        val repository = normalizePath(path)
        val main = ResolveRepositoryUseCase(executor)(repository).getOrNull()
        if (main == null || !sameDirectory(main, repository)) reject("$repository isn't the main worktree of a Git repository")
        multirepo.repositories.firstOrNull { sameDirectory(it.path, repository) }?.let {
            reject("$repository is already declared as '${it.alias}'")
        }
        resolver.known()
            .firstOrNull { other -> !sameDirectory(other.folder, multirepo.folder) && other.repositories.any { sameDirectory(it.path, repository) } }
            ?.let { reject("$repository already belongs to the multirepo ${it.folder}") }
        val proposed = multirepo.proposedWorktreesFolder(alias)
        val existing = projects.list().firstOrNull { sameDirectory(it.config.baseRepositoryPath, repository) }
        val (project, proposal) = if (existing == null) {
            val accepted = decision.accept(folderName(repository), null, proposed)
            val project = Project(
                id = newId(),
                name = folderName(repository),
                config = ProjectConfig(
                    baseRepositoryPath = repository,
                    worktreesRoot = if (accepted) proposed else defaultWorktreesRoot(repository),
                    secretFiles = emptyList(),
                ),
            )
            projects.save(project)
            project to if (accepted) WorktreesFolderProposal.ACCEPTED else WorktreesFolderProposal.DECLINED
        } else if (existing.config.worktreesRoot.isNotBlank() && sameDirectory(existing.config.worktreesRoot, proposed)) {
            existing to WorktreesFolderProposal.ALREADY_MATCHES
        } else if (decision.accept(existing.name, existing.config.worktreesRoot.ifBlank { null }, proposed)) {
            val moved = existing.copy(config = existing.config.copy(worktreesRoot = proposed))
            projects.save(moved)
            moved to WorktreesFolderProposal.ACCEPTED
        } else {
            existing to WorktreesFolderProposal.DECLINED
        }
        val declaration = multirepo.declaration.copy(
            repos = multirepo.declaration.repos + (alias to relativeMultirepoPath(multirepo.folder, repository)),
            agent = null,
        )
        source.write(multirepo.folder, declaration)
        approveWritten(multirepo.folder, declaration)
        AddedRepository(alias, repository, project, registered = existing == null, worktreesFolder = proposal)
    }

    fun removeRepository(multirepo: Multirepo, alias: String): Result<Multirepo> = update(multirepo) { declaration ->
        if (alias !in declaration.repos) throw unknownAlias(multirepo, alias)
        declaration.copy(repos = declaration.repos - alias, defaultRepos = declaration.defaultRepos - alias)
    }

    fun renameRepository(multirepo: Multirepo, alias: String, newAlias: String): Result<Multirepo> = update(multirepo) { declaration ->
        if (alias !in declaration.repos) throw unknownAlias(multirepo, alias)
        if (newAlias == alias) return@update declaration
        if (!isValidMultirepoAlias(newAlias)) {
            throw WorktreeError.InvalidMultirepo(multirepo.declarationPath, "alias '$newAlias' must use only lowercase letters, digits and hyphens")
        }
        if (newAlias in declaration.repos) throw WorktreeError.InvalidMultirepo(multirepo.declarationPath, "alias '$newAlias' is already taken")
        declaration.copy(
            repos = declaration.repos.entries.associate { (key, path) -> (if (key == alias) newAlias else key) to path },
            defaultRepos = declaration.defaultRepos.map { if (it == alias) newAlias else it },
        )
    }

    fun setDefaults(multirepo: Multirepo, aliases: List<String>): Result<Multirepo> = update(multirepo) { declaration ->
        aliases.firstOrNull { it !in multirepo.aliases }?.let { throw unknownAlias(multirepo, it) }
        val workspace = multirepo.workspaceRepository?.takeIf { it.alias !in declaration.repos }
        val ordinary = aliases.filter { it in declaration.repos }.distinct()
        val workspaceSetting = if (workspace == null) {
            declaration.workspace
        } else {
            val joins = workspace.alias in aliases
            val current = declaration.workspace
            when {
                joins -> WorkspaceSetting(alias = workspace.alias, default = true)
                current != null -> current.copy(default = false)
                else -> null
            }
        }
        declaration.copy(defaultRepos = ordinary, workspace = workspaceSetting)
    }

    fun setAgent(multirepo: Multirepo, agent: AgentLaunchConfig): Result<Multirepo> = runCatching {
        require(agent.agentCommand.isNotBlank()) { "the agent command must not be blank" }
        requireApproved(multirepo)
        source.setAgent(multirepo.folder, agent)
        multirepo.copy(localAgent = agent)
    }

    fun setWorktreesRoot(multirepo: Multirepo, folder: String): Result<Multirepo> = update(multirepo) { declaration ->
        declaration.copy(worktreesRoot = relativeMultirepoPath(multirepo.folder, normalizePath(folder)))
    }

    fun setWorkspace(multirepo: Multirepo, alias: String?, default: Boolean): Result<Multirepo> = update(multirepo) { declaration ->
        fun reject(detail: String): Nothing = throw WorktreeError.InvalidMultirepo(multirepo.declarationPath, detail)
        if (!multirepo.folderIsRepository) reject("there is no workspace repository: ${multirepo.folder} isn't a Git repository")
        val chosen = alias ?: multirepo.workspaceRepository?.alias ?: DEFAULT_WORKSPACE_ALIAS
        if (!isValidMultirepoAlias(chosen)) reject("alias '$chosen' must use only lowercase letters, digits and hyphens")
        if (chosen in declaration.repos && multirepo.declaredRepositories.none { it.alias == chosen && sameDirectory(it.path, multirepo.folder) }) {
            reject("alias '$chosen' is already taken")
        }
        declaration.copy(workspace = WorkspaceSetting(alias = chosen, default = default))
    }

    fun clearWorkspace(multirepo: Multirepo): Result<Multirepo> = update(multirepo) { declaration ->
        declaration.copy(workspace = WorkspaceSetting(enabled = false))
    }

    fun delete(multirepo: Multirepo): Result<Unit> = runCatching {
        source.delete(multirepo.folder)
        source.forget(multirepo.folder)
    }

    fun trust(multirepo: Multirepo): Result<Multirepo> = runCatching {
        val current = source.read(multirepo.folder) ?: throw WorktreeError.MultirepoNotFound(multirepo.folder)
        if (current != multirepo.declaration) throw WorktreeError.MultirepoNeedsReview(multirepo.declarationPath)
        source.remember(multirepo.folder)
        source.approve(multirepo.folder, resolver.build(multirepo.folder, current).approvalSubject)
        resolver.build(multirepo.folder, current)
    }

    fun revoke(multirepo: Multirepo): Result<Unit> = runCatching {
        source.forget(multirepo.folder)
    }

    private fun update(multirepo: Multirepo, change: (MultirepoDeclaration) -> MultirepoDeclaration): Result<Multirepo> = runCatching {
        requireApproved(multirepo)
        val declaration = change(multirepo.declaration).copy(agent = null)
        source.write(multirepo.folder, declaration)
        approveWritten(multirepo.folder, declaration)
    }

    private fun requireApproved(multirepo: Multirepo) {
        val current = source.read(multirepo.folder) ?: throw WorktreeError.MultirepoNotFound(multirepo.folder)
        if (!resolver.build(multirepo.folder, current).approved) throw WorktreeError.MultirepoNeedsReview(multirepo.declarationPath)
    }

    private fun approveWritten(folder: String, declaration: MultirepoDeclaration): Multirepo {
        source.approve(folder, resolver.build(folder, declaration).approvalSubject)
        return resolver.build(folder, declaration)
    }

    private fun unknownAlias(multirepo: Multirepo, alias: String) =
        UnknownMultirepoAlias(alias, multirepo.aliases)

    private fun folderName(path: String): String = normalizePath(path).substringAfterLast('/')

    private fun defaultWorktreesRoot(repository: String): String {
        val normalized = normalizePath(repository)
        val parent = normalized.substringBeforeLast('/', "")
        return joinPath(parent.ifEmpty { "/" }, "${folderName(normalized)}-worktrees")
    }
}

class UnknownMultirepoAlias(val alias: String, val aliases: List<String>) :
    IllegalArgumentException("'$alias' isn't a repository of the multirepo; its repositories are ${aliases.joinToString().ifEmpty { "none" }}")
