package app.luxion.shogunai.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import app.luxion.shogunai.domain.usecase.sameDirectory
import java.nio.file.Path

const val MULTIREPO_FILE_NAME = ".shogunai-multirepo.json"
const val DEFAULT_WORKSPACE_ALIAS = "workspace"

@Serializable(with = WorkspaceSettingSerializer::class)
data class WorkspaceSetting(
    val enabled: Boolean = true,
    val alias: String = DEFAULT_WORKSPACE_ALIAS,
    val default: Boolean = false,
)

object WorkspaceSettingSerializer : KSerializer<WorkspaceSetting> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): WorkspaceSetting {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        if (element is JsonObject) {
            val alias = element["alias"]?.jsonPrimitive?.contentOrNull ?: DEFAULT_WORKSPACE_ALIAS
            val default = element["default"]?.jsonPrimitive?.booleanOrNull ?: false
            return WorkspaceSetting(alias = alias, default = default)
        }
        val primitive = element as? JsonPrimitive
        if (primitive?.booleanOrNull == false) return WorkspaceSetting(enabled = false)
        throw IllegalArgumentException("workspace must be false or an object with alias and default")
    }

    override fun serialize(encoder: Encoder, value: WorkspaceSetting) {
        val element = if (!value.enabled) {
            JsonPrimitive(false)
        } else {
            buildJsonObject {
                put("alias", value.alias)
                if (value.default) put("default", true)
            }
        }
        (encoder as JsonEncoder).encodeJsonElement(element)
    }
}

@Serializable
data class MultirepoDeclaration(
    val repos: Map<String, String> = emptyMap(),
    val worktreesRoot: String = "",
    val defaultRepos: List<String> = emptyList(),
    val agent: AgentLaunchConfig? = null,
    val workspace: WorkspaceSetting? = null,
)

data class MultirepoRepository(val alias: String, val path: String)

data class Multirepo(
    val folder: String,
    val declaration: MultirepoDeclaration,
    val folderIsRepository: Boolean = false,
    val approved: Boolean = false,
    val localAgent: AgentLaunchConfig? = null,
) {
    val name: String
        get() = folder.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')

    val declarationPath: String
        get() = joinPath(folder, MULTIREPO_FILE_NAME)

    val declaredRepositories: List<MultirepoRepository>
        get() = declaration.repos.map { (alias, path) -> MultirepoRepository(alias, resolveMultirepoPath(folder, path)) }

    val workspaceRepository: MultirepoRepository?
        get() {
            if (!folderIsRepository) return null
            declaredRepositories.firstOrNull { sameDirectory(it.path, folder) }?.let { return it }
            val setting = declaration.workspace ?: WorkspaceSetting()
            return if (setting.enabled) MultirepoRepository(setting.alias, folder) else null
        }

    val workspaceDetected: Boolean
        get() = workspaceRepository != null && declaredRepositories.none { sameDirectory(it.path, folder) } && declaration.workspace == null

    val workspaceDeclared: Boolean
        get() = workspaceRepository != null && !workspaceDetected

    val repositories: List<MultirepoRepository>
        get() {
            val workspace = workspaceRepository ?: return declaredRepositories
            return if (declaredRepositories.any { it.alias == workspace.alias }) declaredRepositories else declaredRepositories + workspace
        }

    val defaultRepos: List<String>
        get() {
            val workspace = workspaceRepository
            val joins = declaration.workspace?.default == true && workspace != null
            return if (joins && workspace.alias !in declaration.defaultRepos) declaration.defaultRepos + workspace.alias else declaration.defaultRepos
        }

    val worktreesRoot: String
        get() = resolveMultirepoPath(folder, declaration.worktreesRoot)

    val agent: AgentLaunchConfig
        get() = localAgent ?: AgentLaunchConfig()

    val ignoresDeclaredAgent: Boolean
        get() = declaration.agent != null

    val approvalSubject: String
        get() = buildList {
            add("folder\t${normalizeMultirepoFolder(folder)}")
            declaredRepositories.sortedBy { it.alias }.forEach { add("repo\t${it.alias}\t${it.path}") }
            add("worktreesRoot\t$worktreesRoot")
            val workspace = declaration.workspace
            add(
                when {
                    workspace == null -> "workspace\tdetected"
                    !workspace.enabled -> "workspace\toff"
                    else -> "workspace\t${workspace.alias}"
                },
            )
        }.joinToString("\n")

    val parentFolder: String
        get() = Path.of(folder).normalize().parent?.toString()?.replace('\\', '/') ?: "/"

    fun isOutsideParent(path: String): Boolean {
        val parent = Path.of(folder).normalize().parent ?: return false
        return !Path.of(path).normalize().startsWith(parent)
    }

    val aliases: List<String>
        get() = repositories.map { it.alias }

    fun repository(alias: String): MultirepoRepository? = repositories.firstOrNull { it.alias == alias }

    fun aliasOf(repositoryPath: String): String? =
        repositories.firstOrNull { sameDirectory(it.path, repositoryPath) }?.alias

    fun proposedWorktreesFolder(alias: String): String = joinPath(worktreesRoot, alias)
}

private val MULTIREPO_ALIAS = Regex("[a-z0-9]+(-[a-z0-9]+)*")

fun isValidMultirepoAlias(alias: String): Boolean = MULTIREPO_ALIAS.matches(alias)

private fun normalizeMultirepoFolder(folder: String): String =
    Path.of(folder).normalize().toString().replace('\\', '/').trimEnd('/').ifEmpty { "/" }

private val UNSAFE_PATH_CHARACTER = Regex("[\\p{Cc}\\p{Cf}\\u2028\\u2029]")

fun hasUnsafePathCharacters(path: String): Boolean = UNSAFE_PATH_CHARACTER.containsMatchIn(path)

private fun parsedPath(path: String): Path? = runCatching { Path.of(path) }.getOrNull()

fun isValidMultirepoPath(path: String): Boolean = !hasUnsafePathCharacters(path) && parsedPath(path) != null

fun resolveMultirepoPath(folder: String, path: String): String {
    val candidate = parsedPath(path) ?: return joinPath(folder, path)
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
