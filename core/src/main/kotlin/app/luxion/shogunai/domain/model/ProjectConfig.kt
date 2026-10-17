package app.luxion.shogunai.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ProjectConfig(
    val baseRepositoryPath: String,
    val worktreesRoot: String,
    val secretFiles: List<String>,
    val agentLaunchConfig: AgentLaunchConfig = AgentLaunchConfig(),
    val mainBranch: String? = null,
    val emulator: EmulatorSettings = EmulatorSettings(),
    val simulator: SimulatorSettings = SimulatorSettings(),
) {
    fun worktreePathFor(taskId: String): String = joinPath(worktreesRoot, taskId)

    fun worktreePathForBranch(branch: String): String {
        val knownPrefix = BranchType.entries.map { "${it.prefix}/" }.firstOrNull { branch.startsWith(it) }
        val sanitized = knownPrefix?.let { branch.removePrefix(it) } ?: branch
        return worktreePathFor(sanitized.replace("/", "-"))
    }

    fun baseSecretPath(fileName: String): String = joinPath(baseRepositoryPath, fileName)
}

internal fun joinPath(vararg parts: String): String =
    parts.joinToString("/") { it.trimEnd('/') }
