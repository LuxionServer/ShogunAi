package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.MultirepoSource
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.isValidMultirepoAlias
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
) {
    suspend operator fun invoke(directory: String): Result<MultirepoContext?> = runCatching {
        val above = declaredAbove(directory)
        if (above == null && source.knownFolders().isEmpty()) return@runCatching null
        val repository = ResolveRepositoryUseCase(executor)(directory).getOrNull()
        val multirepo = above ?: repository?.let(::declaring) ?: return@runCatching null
        validate(multirepo)
        MultirepoContext(multirepo, directory, repository)
    }

    suspend fun at(folder: String): Result<Multirepo> = runCatching {
        val declaration = source.read(folder) ?: throw WorktreeError.MultirepoNotFound(folder)
        Multirepo(normalizePath(folder), declaration).also { validate(it) }
    }

    fun known(): List<Multirepo> = source.knownFolders().mapNotNull { folder ->
        runCatching { source.read(folder) }.getOrNull()?.let { Multirepo(normalizePath(folder), it) }
    }

    fun declaring(repository: String): Multirepo? =
        known().firstOrNull { multirepo -> multirepo.repositories.any { sameDirectory(it.path, repository) } }

    private fun declaredAbove(directory: String): Multirepo? {
        var current: Path? = Path.of(directory).normalize()
        while (current != null) {
            val folder = normalizePath(current.toString()).ifEmpty { "/" }
            source.read(folder)?.let { return Multirepo(folder, it) }
            current = current.parent
        }
        return null
    }

    suspend fun validate(multirepo: Multirepo) {
        fun invalid(detail: String): Nothing = throw WorktreeError.InvalidMultirepo(multirepo.declarationPath, detail)
        val declaration = multirepo.declaration
        if (declaration.worktreesRoot.isBlank()) invalid("worktreesRoot is missing")
        declaration.repos.keys.firstOrNull { !isValidMultirepoAlias(it) }?.let {
            invalid("alias '$it' must use only lowercase letters, digits and hyphens")
        }
        declaration.defaultRepos.firstOrNull { it !in declaration.repos }?.let {
            invalid("'$it' isn't a declared repository")
        }
        val enclosing = ResolveRepositoryUseCase(executor)(multirepo.folder).getOrNull()
        if (enclosing != null && !sameDirectory(enclosing, multirepo.folder)) {
            invalid("a multirepo can't be declared inside the repository $enclosing")
        }
        multirepo.repositories.forEach { repository ->
            val resolved = if (fileManager.exists(repository.path)) ResolveRepositoryUseCase(executor)(repository.path).getOrNull() else null
            if (resolved == null || !sameDirectory(resolved, repository.path)) {
                invalid("repository '${repository.alias}' at ${repository.path} isn't a Git repository")
            }
        }
    }
}
