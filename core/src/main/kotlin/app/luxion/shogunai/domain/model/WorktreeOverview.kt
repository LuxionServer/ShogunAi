package app.luxion.shogunai.domain.model

enum class WorktreeOrigin { SHOGUNAI, CLAUDE_CODE, HERDR, ORCA, OTHER }

enum class AgentState { WORKING, BLOCKED, DONE, IDLE }

enum class AgentStateSource { HERDR, ORCA, CLAUDE_CODE }

data class RunningAgent(
    val name: String,
    val state: AgentState? = null,
    val herdrPaneId: String? = null,
    val stateSource: AgentStateSource? = null,
    val sessions: List<ClaudeSessionRef> = emptyList(),
)
