package app.luxion.shogunai.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AgentLaunchConfig(
    val agentCommand: String = "claude",
    val useHeadroom: Boolean = true,
) {
    fun resolvedCommand(): String =
        if (useHeadroom) "headroom wrap $agentCommand" else agentCommand
}
