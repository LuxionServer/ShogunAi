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
import app.luxion.shogunai.domain.model.InvalidSharedEnvironment
import app.luxion.shogunai.domain.model.SharedVariablesDeclaration
import app.luxion.shogunai.domain.model.WorkspaceSetting
import app.luxion.shogunai.domain.model.parseSharedEnvironment
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.MAX_MULTIREPO_NAME_LENGTH
import app.luxion.shogunai.domain.model.MULTIREPO_FILE_NAME
import app.luxion.shogunai.domain.model.isValidMultirepoAlias
import app.luxion.shogunai.domain.model.isValidMultirepoName
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

data class RegisteredMultirepo(
    val multirepo: Multirepo,
    val trustedNow: Boolean,
    val registeredProjects: List<Project>,
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
    suspend fun init(folder: String, worktreesRoot: String?, name: String? = null): Result<Multirepo> = runCatching {
        val normalized = normalizePath(folder)
        if (source.read(normalized) != null) {
            throw WorktreeError.InvalidMultirepo(Multirepo(normalized, MultirepoDeclaration()).declarationPath, "it's already a multirepo")
        }
        val enclosing = ResolveRepositoryUseCase(executor)(normalized).getOrNull()
        if (enclosing != null && !sameDirectory(enclosing, normalized)) {
            throw WorktreeError.InvalidMultirepo(normalized, "a multirepo is never declared inside a repository ($enclosing)")
        }
        val folderName = Multirepo(normalized, MultirepoDeclaration()).folderName
        val root = worktreesRoot?.let { relativeMultirepoPath(normalized, it) } ?: "../$folderName-worktrees"
        val declaration = MultirepoDeclaration(worktreesRoot = root, name = displayName(normalized, name))
        source.write(normalized, declaration)
        source.remember(normalized)
        approveWritten(normalized, declaration)
    }

    suspend fun register(reviewed: Multirepo, decision: WorktreesFolderDecision): Result<RegisteredMultirepo> = runCatching {
        val folder = reviewed.folder
        val current = source.read(folder) ?: throw WorktreeError.MultirepoNotFound(folder)
        if (current != reviewed.declaration) throw WorktreeError.MultirepoNeedsReview(reviewed.declarationPath)
        val multirepo = resolver.build(folder, current)
        resolver.validate(multirepo)
        multirepo.repositories.forEach { repository ->
            claimingMultirepo(multirepo, repository.path)?.let {
                throw WorktreeError.InvalidMultirepo(multirepo.declarationPath, "${repository.path} already belongs to the multirepo ${it.folder}")
            }
        }
        val alreadyTrusted = multirepo.approved && source.knownFolders().any { sameDirectory(it, folder) }
        source.remember(folder)
        source.approve(folder, multirepo.approvalSubject)
        val trusted = resolver.build(folder, current)
        val registered = trusted.repositories
            .filter { repository -> projects.list().none { sameDirectory(it.config.baseRepositoryPath, repository.path) } }
            .map { repository -> registerProject(trusted, repository.alias, repository.path, decision).first }
        RegisteredMultirepo(trusted, trustedNow = !alreadyTrusted, registeredProjects = registered)
    }

    suspend fun addRepository(
        multirepo: Multirepo,
        alias: String,
        path: String,
        decision: WorktreesFolderDecision,
    ): Result<AddedRepository> = runCatching {
        fun reject(detail: String): Nothing = throw WorktreeError.InvalidMultirepo(multirepo.declarationPath, detail)
        requireApproved(multirepo)
        requireRewritableEnv(multirepo)
        if (!isValidMultirepoAlias(alias)) reject("alias '$alias' must use only lowercase letters, digits and hyphens")
        if (alias in multirepo.declaration.repos) reject("alias '$alias' is already taken")
        val repository = normalizePath(path)
        val main = ResolveRepositoryUseCase(executor)(repository).getOrNull()
        if (main == null || !sameDirectory(main, repository)) reject("$repository isn't the main worktree of a Git repository")
        multirepo.repositories.firstOrNull { sameDirectory(it.path, repository) }?.let {
            reject("$repository is already declared as '${it.alias}'")
        }
        claimingMultirepo(multirepo, repository)?.let { reject("$repository already belongs to the multirepo ${it.folder}") }
        val existing = projects.list().firstOrNull { sameDirectory(it.config.baseRepositoryPath, repository) }
        val (project, proposal) = registerProject(multirepo, alias, repository, decision)
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
        declaration.copy(repos = declaration.repos - alias, defaultRepos = declaration.defaultRepos - alias, env = withoutRepository(multirepo, alias))
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
            env = declaration.env?.renamingAlias(alias, newAlias),
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

    fun setName(multirepo: Multirepo, name: String?): Result<Multirepo> = update(multirepo) { declaration ->
        declaration.copy(name = displayName(multirepo.folder, name))
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
        val previous = multirepo.workspaceRepository?.alias
        val env = if (previous != null && previous != chosen) declaration.env?.renamingAlias(previous, chosen) else declaration.env
        declaration.copy(workspace = WorkspaceSetting(alias = chosen, default = default), env = env)
    }

    fun clearWorkspace(multirepo: Multirepo): Result<Multirepo> = update(multirepo) { declaration ->
        val workspace = multirepo.workspaceRepository?.takeIf { it.alias !in declaration.repos }
        val env = if (workspace == null) declaration.env else withoutRepository(multirepo, workspace.alias)
        declaration.copy(workspace = WorkspaceSetting(enabled = false), env = env)
    }

    fun detectWorkspace(multirepo: Multirepo): Result<Multirepo> = runCatching {
        fun reject(detail: String): Nothing = throw WorktreeError.InvalidMultirepo(multirepo.declarationPath, detail)
        requireApproved(multirepo)
        if (!multirepo.folderIsRepository) reject("there is no workspace repository: ${multirepo.folder} isn't a Git repository")
        multirepo.declaredRepositories.firstOrNull { sameDirectory(it.path, multirepo.folder) }?.let {
            reject("the workspace repository is declared in repos as '${it.alias}'")
        }
        val current = multirepo.declaration.workspace ?: return@runCatching multirepo
        if (DEFAULT_WORKSPACE_ALIAS in multirepo.declaration.repos) {
            reject("alias '$DEFAULT_WORKSPACE_ALIAS' is already taken; keep the workspace repository declared under another alias")
        }
        update(multirepo) { declaration ->
            val env = if (current.enabled && current.alias != DEFAULT_WORKSPACE_ALIAS) {
                declaration.env?.renamingAlias(current.alias, DEFAULT_WORKSPACE_ALIAS)
            } else {
                declaration.env
            }
            declaration.copy(workspace = null, env = env)
        }.getOrThrow()
    }

    fun setSharedVariable(multirepo: Multirepo, alias: String, name: String, template: String): Result<Multirepo> =
        updateSharedVariables(multirepo) { env ->
            if (alias !in multirepo.aliases) throw unknownAlias(multirepo, alias)
            env.withVariable(alias, name, template)
        }

    fun unsetSharedVariable(multirepo: Multirepo, alias: String, name: String): Result<Multirepo> =
        updateSharedVariables(multirepo) { env ->
            if (alias !in multirepo.aliases) throw unknownAlias(multirepo, alias)
            if (name !in env.byAlias[alias].orEmpty()) {
                throw WorktreeError.InvalidMultirepo(multirepo.declarationPath, "'$alias' has no shared variable '$name'")
            }
            env.withoutVariable(alias, name)
        }

    fun setSharedVariables(multirepo: Multirepo, byAlias: Map<String, Map<String, String>>): Result<Multirepo> =
        updateSharedVariables(multirepo) { env ->
            byAlias.keys.firstOrNull { it !in multirepo.aliases }?.let { throw unknownAlias(multirepo, it) }
            env.copy(byAlias = byAlias)
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

    private fun claimingMultirepo(multirepo: Multirepo, repository: String): Multirepo? =
        resolver.known().firstOrNull { other ->
            !sameDirectory(other.folder, multirepo.folder) && other.repositories.any { sameDirectory(it.path, repository) }
        }

    private fun registerProject(
        multirepo: Multirepo,
        alias: String,
        repository: String,
        decision: WorktreesFolderDecision,
    ): Pair<Project, WorktreesFolderProposal> {
        val proposed = multirepo.proposedWorktreesFolder(alias)
        val existing = projects.list().firstOrNull { sameDirectory(it.config.baseRepositoryPath, repository) }
        return if (existing == null) {
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
    }

    private fun displayName(folder: String, name: String?): String? {
        val trimmed = name?.trim()?.ifEmpty { null } ?: return null
        if (!isValidMultirepoName(trimmed)) {
            throw WorktreeError.InvalidMultirepo(
                joinPath(folder, MULTIREPO_FILE_NAME),
                "the name must have at most $MAX_MULTIREPO_NAME_LENGTH characters and no control characters",
            )
        }
        return trimmed
    }

    private fun updateSharedVariables(
        multirepo: Multirepo,
        change: (SharedVariablesDeclaration) -> SharedVariablesDeclaration,
    ): Result<Multirepo> = update(multirepo) { declaration ->
        val env = change(declaration.env ?: SharedVariablesDeclaration()).withoutEmptyAliases()
        try {
            parseSharedEnvironment(env, multirepo.aliases.toSet())
        } catch (error: InvalidSharedEnvironment) {
            throw WorktreeError.InvalidMultirepo(multirepo.declarationPath, error.message.orEmpty())
        }
        declaration.copy(env = env)
    }

    private fun withoutRepository(multirepo: Multirepo, alias: String): SharedVariablesDeclaration? {
        val env = multirepo.declaration.env ?: return null
        val referencing = env.referencesTo(alias)
        if (referencing.isNotEmpty()) {
            throw WorktreeError.InvalidMultirepo(
                multirepo.declarationPath,
                "'$alias' is referenced by the shared variables ${referencing.joinToString()}; change or unset them first",
            )
        }
        return env.withoutAlias(alias).withoutEmptyAliases()
    }

    private fun requireRewritableEnv(multirepo: Multirepo) {
        multirepo.declaration.env?.problems?.firstOrNull()?.let {
            throw WorktreeError.InvalidMultirepo(multirepo.declarationPath, "$it; fix the file by hand")
        }
    }

    private fun update(multirepo: Multirepo, change: (MultirepoDeclaration) -> MultirepoDeclaration): Result<Multirepo> = runCatching {
        requireApproved(multirepo)
        requireRewritableEnv(multirepo)
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
