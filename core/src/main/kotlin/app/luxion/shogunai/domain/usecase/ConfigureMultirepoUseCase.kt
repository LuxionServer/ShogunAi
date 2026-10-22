package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.MultirepoSource
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
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
        Multirepo(normalized, declaration)
    }

    suspend fun addRepository(
        multirepo: Multirepo,
        alias: String,
        path: String,
        decision: WorktreesFolderDecision,
    ): Result<AddedRepository> = runCatching {
        fun reject(detail: String): Nothing = throw WorktreeError.InvalidMultirepo(multirepo.declarationPath, detail)
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
        )
        source.write(multirepo.folder, declaration)
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
        aliases.firstOrNull { it !in declaration.repos }?.let { throw unknownAlias(multirepo, it) }
        declaration.copy(defaultRepos = aliases.distinct())
    }

    fun setAgent(multirepo: Multirepo, agent: AgentLaunchConfig): Result<Multirepo> = update(multirepo) { declaration ->
        require(agent.agentCommand.isNotBlank()) { "the agent command must not be blank" }
        declaration.copy(agent = agent)
    }

    fun setWorktreesRoot(multirepo: Multirepo, folder: String): Result<Multirepo> = update(multirepo) { declaration ->
        declaration.copy(worktreesRoot = relativeMultirepoPath(multirepo.folder, normalizePath(folder)))
    }

    fun delete(multirepo: Multirepo): Result<Unit> = runCatching {
        source.delete(multirepo.folder)
        source.forget(multirepo.folder)
    }

    private fun update(multirepo: Multirepo, change: (MultirepoDeclaration) -> MultirepoDeclaration): Result<Multirepo> = runCatching {
        val declaration = change(multirepo.declaration)
        source.write(multirepo.folder, declaration)
        Multirepo(multirepo.folder, declaration)
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
