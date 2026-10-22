package app.luxion.shogunai.domain.model

import kotlinx.serialization.Serializable
import app.luxion.shogunai.domain.usecase.sameDirectory
import java.nio.file.Path

const val MULTIREPO_FILE_NAME = ".shogunai-multirepo.json"

@Serializable
data class MultirepoDeclaration(
    val repos: Map<String, String> = emptyMap(),
    val worktreesRoot: String = "",
    val defaultRepos: List<String> = emptyList(),
    val agent: AgentLaunchConfig? = null,
)

data class MultirepoRepository(val alias: String, val path: String)

data class Multirepo(val folder: String, val declaration: MultirepoDeclaration) {
    val name: String
        get() = folder.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')

    val declarationPath: String
        get() = joinPath(folder, MULTIREPO_FILE_NAME)

    val repositories: List<MultirepoRepository>
        get() = declaration.repos.map { (alias, path) -> MultirepoRepository(alias, resolveMultirepoPath(folder, path)) }

    val worktreesRoot: String
        get() = resolveMultirepoPath(folder, declaration.worktreesRoot)

    val agent: AgentLaunchConfig
        get() = declaration.agent ?: AgentLaunchConfig()

    val aliases: List<String>
        get() = declaration.repos.keys.toList()

    fun repository(alias: String): MultirepoRepository? = repositories.firstOrNull { it.alias == alias }

    fun aliasOf(repositoryPath: String): String? =
        repositories.firstOrNull { sameDirectory(it.path, repositoryPath) }?.alias

    fun proposedWorktreesFolder(alias: String): String = joinPath(worktreesRoot, alias)
}

private val MULTIREPO_ALIAS = Regex("[a-z0-9]+(-[a-z0-9]+)*")

fun isValidMultirepoAlias(alias: String): Boolean = MULTIREPO_ALIAS.matches(alias)

fun resolveMultirepoPath(folder: String, path: String): String {
    val candidate = Path.of(path)
    val resolved = if (candidate.isAbsolute) candidate else Path.of(folder).resolve(candidate)
    return resolved.normalize().toString().replace('\\', '/').trimEnd('/').ifEmpty { "/" }
}

fun relativeMultirepoPath(folder: String, path: String): String {
    val base = Path.of(folder).normalize()
    val target = Path.of(path).normalize()
    if (target == base) return "."
    if (target.startsWith(base)) return base.relativize(target).toString().replace('\\', '/')
    val parent = base.parent
    if (parent != null && target.startsWith(parent)) return base.relativize(target).toString().replace('\\', '/')
    return target.toString().replace('\\', '/')
}
