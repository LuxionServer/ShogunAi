package app.luxion.shogunai.domain.model

enum class WorktreeOrigin { SHOGUNAI, CLAUDE_CODE, HERDR, OTHER }

enum class HerdrAgentState { WORKING, BLOCKED, DONE, IDLE }

data class RunningAgent(
    val name: String,
    val herdrState: HerdrAgentState? = null,
    val herdrPaneId: String? = null,
)
