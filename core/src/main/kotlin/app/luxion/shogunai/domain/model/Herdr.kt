package app.luxion.shogunai.domain.model

data class HerdrWorktreeOpening(val workspaceId: String, val rootPaneId: String, val alreadyOpen: Boolean)

data class HerdrAgent(
    val name: String,
    val state: AgentState?,
    val directory: String,
    val paneId: String,
)

const val AGENT_START_SECONDS = 10

enum class HerdrOutcome { OPENED, STARTED_AGENT, FOCUSED }

data class HerdrOpening(val outcome: HerdrOutcome, val paneId: String, val agent: String?)

data class HerdrWorkspace(val workspaceId: String, val label: String, val worktreePath: String?)

data class HerdrPane(
    val paneId: String,
    val workspaceId: String,
    val directory: String?,
    val agent: String? = null,
    val agentStatus: String = UNKNOWN_AGENT_STATUS,
) {
    val hasLiveAgent: Boolean get() = agentStatus != UNKNOWN_AGENT_STATUS

    companion object {
        const val UNKNOWN_AGENT_STATUS = "unknown"
    }
}
