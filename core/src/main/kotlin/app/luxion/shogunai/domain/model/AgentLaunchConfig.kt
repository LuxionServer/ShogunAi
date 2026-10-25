package app.luxion.shogunai.domain.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AgentLaunchConfig(
    val name: String? = null,
    val agentCommand: String = "claude",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val useHeadroom: Boolean = false,
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: executable().replaceFirstChar { it.uppercase() }

    fun executable(): String =
        agentCommand.trim().substringBefore(' ').substringAfterLast('/')

    fun resolvedCommand(): String =
        if (useHeadroom) "headroom wrap $agentCommand" else agentCommand
}
