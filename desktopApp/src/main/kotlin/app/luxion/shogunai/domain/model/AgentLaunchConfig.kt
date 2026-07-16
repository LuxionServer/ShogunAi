package app.luxion.shogunai.domain.model

import kotlinx.serialization.Serializable

/**
 * Configuration for how the code agent is launched in a worktree.
 *
 * @param agentCommand Agent command to run (e.g. `claude`).
 * @param useHeadroom Whether to wrap [agentCommand] with `headroom wrap`, the
 *   Headroom context compressor.
 */
@Serializable
data class AgentLaunchConfig(
    val agentCommand: String = "claude",
    val useHeadroom: Boolean = true,
) {
    /** Final command to run in the terminal, with Headroom applied when applicable. */
    fun resolvedCommand(): String =
        if (useHeadroom) "headroom wrap $agentCommand" else agentCommand
}
