package app.luxion.shogunai.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AgentLaunchConfig(
    val name: String? = null,
    val agentCommand: String = "claude",
    val useHeadroom: Boolean = true,
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: executable().replaceFirstChar { it.uppercase() }

    fun executable(): String =
        agentCommand.trim().substringBefore(' ').substringAfterLast('/')

    fun resolvedCommand(): String =
        if (useHeadroom) "headroom wrap $agentCommand" else agentCommand
}
