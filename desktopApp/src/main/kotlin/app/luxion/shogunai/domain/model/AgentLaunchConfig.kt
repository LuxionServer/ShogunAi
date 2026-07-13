package app.luxion.shogunai.domain.model

import kotlinx.serialization.Serializable

/**
 * Configuración de cómo se lanza el agente de código en un worktree.
 *
 * @param agentCommand Comando del agente a ejecutar (p. ej. `claude`).
 * @param useHeadroom Si se envuelve [agentCommand] con `headroom wrap`, el
 *   compresor de contexto de Headroom.
 */
@Serializable
data class AgentLaunchConfig(
    val agentCommand: String = "claude",
    val useHeadroom: Boolean = true,
) {
    /** Comando final a ejecutar en la terminal, con Headroom aplicado si corresponde. */
    fun resolvedCommand(): String =
        if (useHeadroom) "headroom wrap $agentCommand" else agentCommand
}
