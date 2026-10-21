package app.luxion.shogunai.domain.model

data class AgentSessionRecord(
    val sessionId: String,
    val agent: String,
    val state: AgentState,
    val updatedAt: Long,
    val transcriptPath: String? = null,
    val pid: Long? = null,
)
