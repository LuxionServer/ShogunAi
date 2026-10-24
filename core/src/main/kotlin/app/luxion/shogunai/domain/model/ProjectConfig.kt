package app.luxion.shogunai.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ProjectConfig(
    val baseRepositoryPath: String,
    val worktreesRoot: String,
    val secretFiles: List<String>,
    val agentLaunchConfig: AgentLaunchConfig = AgentLaunchConfig(),
    val additionalAgents: List<AgentLaunchConfig> = emptyList(),
    val mainBranch: String? = null,
    val emulator: EmulatorSettings = EmulatorSettings(),
    val simulator: SimulatorSettings = SimulatorSettings(),
    val editorOverride: EditorId? = null,
    val fetchBeforeCreate: Boolean = false,
    val linkedSecretFiles: List<String> = emptyList(),
) {
    val agents: List<AgentLaunchConfig>
        get() = listOf(agentLaunchConfig) + additionalAgents

    fun agentNamed(displayName: String?): AgentLaunchConfig =
        agents.firstOrNull { it.displayName == displayName } ?: agentLaunchConfig

    fun worktreePathFor(taskId: String): String = joinPath(worktreesRoot, taskId.replace("/", "-"))

    fun worktreePathForBranch(branch: String): String {
        val knownPrefix = BranchType.entries.map { "${it.prefix}/" }.firstOrNull { branch.startsWith(it) }
        val sanitized = knownPrefix?.let { branch.removePrefix(it) } ?: branch
        return worktreePathFor(sanitized)
    }

    fun baseSecretPath(fileName: String): String = joinPath(baseRepositoryPath, fileName)

    fun isLinked(entry: String): Boolean =
        entry.trimEnd('/', '\\') in linkedSecretFiles.map { it.trimEnd('/', '\\') }
}

fun isLocalPattern(entry: String): Boolean = entry.any { it in LOCAL_PATTERN_CHARACTERS }

private val LOCAL_PATTERN_CHARACTERS = setOf('*', '?', '[')

internal fun joinPath(vararg parts: String): String =
    parts.joinToString("/") { it.trimEnd('/') }
