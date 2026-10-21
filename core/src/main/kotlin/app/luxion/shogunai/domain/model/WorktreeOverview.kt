package app.luxion.shogunai.domain.model

enum class WorktreeOrigin { SHOGUNAI, CLAUDE_CODE, HERDR, OTHER }

enum class AgentState { WORKING, BLOCKED, DONE, IDLE }

enum class AgentStateSource { HERDR, CLAUDE_CODE }

data class RunningAgent(
    val name: String,
    val state: AgentState? = null,
    val herdrPaneId: String? = null,
    val stateSource: AgentStateSource? = null,
)
