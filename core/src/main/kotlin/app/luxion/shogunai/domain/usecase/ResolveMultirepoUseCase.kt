package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.MultirepoSource
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.InvalidSharedEnvironment
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.MultirepoRepository
import app.luxion.shogunai.domain.model.joinPath
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.MAX_MULTIREPO_NAME_LENGTH
import app.luxion.shogunai.domain.model.hasUnsafePathCharacters
import app.luxion.shogunai.domain.model.isValidMultirepoAlias
import app.luxion.shogunai.domain.model.isValidMultirepoName
import app.luxion.shogunai.domain.model.isValidMultirepoPath
import java.nio.file.Path

data class MultirepoContext(
    val multirepo: Multirepo,
    val directory: String,
    val repositoryPath: String?,
) {
    val alias: String?
        get() = repositoryPath?.let { path -> multirepo.repositories.firstOrNull { sameDirectory(it.path, path) }?.alias }

    val atFolder: Boolean
        get() = repositoryPath == null ||
            (sameDirectory(repositoryPath, multirepo.folder) && isInside(normalizePath(directory), normalizePath(multirepo.folder)))
}

class ResolveMultirepoUseCase(
    private val source: MultirepoSource,
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
    private val isValidPath: (String) -> Boolean = ::isValidMultirepoPath,
) {
    suspend operator fun invoke(directory: String): Result<MultirepoContext?> = runCatching {
        val known = source.knownFolders().map(::normalizePath)
        if (known.isEmpty()) return@runCatching null
        val above = approvedAbove(directory, known)
        val repository = ResolveRepositoryUseCase(executor)(directory).getOrNull()
        val multirepo = above ?: repository?.let(::declaring) ?: return@runCatching null
        validate(multirepo)
        MultirepoContext(multirepo, directory, repository)
    }

    suspend fun at(folder: String): Result<Multirepo> = runCatching {
        val declaration = source.read(folder) ?: throw WorktreeError.MultirepoNotFound(folder)
        build(normalizePath(folder), declaration).also { validate(it) }
    }

    fun nearestDeclaration(directory: String): Multirepo? {
        var current: Path? = Path.of(directory).normalize()
        while (current != null) {
            val folder = normalizePath(current.toString()).ifEmpty { "/" }
            source.read(folder)?.let { return build(folder, it) }
            current = current.parent
        }
        return null
    }

    fun known(): List<Multirepo> = source.knownFolders().mapNotNull { folder ->
        runCatching { source.read(folder) }.getOrNull()?.let { build(normalizePath(folder), it) }
    }

    fun approved(): List<Multirepo> = known().filter { it.approved }

    fun declaring(repository: String): Multirepo? =
        approved().firstOrNull { multirepo -> multirepo.repositories.any { sameDirectory(it.path, repository) } }

    fun build(folder: String, declaration: MultirepoDeclaration): Multirepo {
        val multirepo = Multirepo(folder, declaration, fileManager.exists(joinPath(folder, ".git")), localAgent = source.agent(folder))
        val registered = source.knownFolders().any { sameDirectory(it, folder) }
        return multirepo.copy(approved = registered && source.isApproved(folder, multirepo.approvalSubject))
    }

    private fun approvedAbove(directory: String, known: List<String>): Multirepo? {
        var current: Path? = Path.of(directory).normalize()
        while (current != null) {
            val folder = normalizePath(current.toString()).ifEmpty { "/" }
            if (known.any { sameDirectory(it, folder) }) {
                val declaration = source.read(folder)
                if (declaration != null) {
                    val multirepo = build(folder, declaration)
                    if (multirepo.approved) return multirepo
                }
            }
            current = current.parent
        }
        return null
    }

    suspend fun validate(multirepo: Multirepo) {
        validateDeclaration(multirepo)
        missingRepositories(multirepo).firstOrNull()?.let { repository ->
            throw WorktreeError.InvalidMultirepo(multirepo.declarationPath, "repository '${repository.alias}' at ${repository.path} isn't a Git repository")
        }
    }

    suspend fun missingRepositories(multirepo: Multirepo): List<MultirepoRepository> = multirepo.repositories.filter { repository ->
        val resolved = if (fileManager.exists(repository.path)) ResolveRepositoryUseCase(executor)(repository.path).getOrNull() else null
        resolved == null || !sameDirectory(resolved, repository.path)
    }

    suspend fun validateDeclaration(multirepo: Multirepo) {
        fun invalid(detail: String): Nothing = throw WorktreeError.InvalidMultirepo(multirepo.declarationPath, detail)
        val declaration = multirepo.declaration
        if (declaration.worktreesRoot.isBlank()) invalid("worktreesRoot is missing")
        if (hasUnsafePathCharacters(declaration.worktreesRoot)) invalid("worktreesRoot contains control characters")
        if ((declaration.repos.keys + declaration.defaultRepos).any(::hasUnsafePathCharacters)) invalid("an alias contains control characters")
        val workspaceAlias = declaration.workspace?.alias
        if (workspaceAlias != null && hasUnsafePathCharacters(workspaceAlias)) invalid("the workspace alias contains control characters")
        declaration.repos.entries.firstOrNull { hasUnsafePathCharacters(it.value) }?.let {
            invalid("the path of '${it.key}' contains control characters")
        }
        val name = multirepo.declaredName
        if (name != null && !isValidMultirepoName(name)) {
            invalid("the name must have at most $MAX_MULTIREPO_NAME_LENGTH characters and no control characters")
        }
        declaration.repos.keys.firstOrNull { !isValidMultirepoAlias(it) }?.let {
            invalid("alias '$it' must use only lowercase letters, digits and hyphens")
        }
        if (!isValidPath(declaration.worktreesRoot)) invalid("worktreesRoot isn't a valid path")
        declaration.repos.entries.firstOrNull { !isValidPath(it.value) }?.let {
            invalid("the path of '${it.key}' isn't a valid path")
        }
        declaration.defaultRepos.firstOrNull { it !in declaration.repos }?.let {
            invalid("'$it' isn't a declared repository")
        }
        try {
            multirepo.sharedVariables()
        } catch (error: InvalidSharedEnvironment) {
            invalid(error.message)
        }
        declaration.workspace?.takeIf { it.enabled }?.let { workspace ->
            if (!isValidMultirepoAlias(workspace.alias)) {
                invalid("workspace alias '${workspace.alias}' must use only lowercase letters, digits and hyphens")
            }
            if (workspace.alias in declaration.repos && multirepo.declaredRepositories.none { it.alias == workspace.alias && sameDirectory(it.path, multirepo.folder) }) {
                invalid("workspace alias '${workspace.alias}' is already a repository")
            }
        }
        val enclosing = ResolveRepositoryUseCase(executor)(multirepo.folder).getOrNull()
        if (enclosing != null && !sameDirectory(enclosing, multirepo.folder)) {
            invalid("a multirepo can't be declared inside the repository $enclosing")
        }
    }
}
