package app.luxion.shogunai.domain.model

data class HerdrWorktreeOpening(val workspaceId: String, val rootPaneId: String, val alreadyOpen: Boolean)

data class HerdrAgent(
    val name: String,
    val state: AgentState?,
    val directory: String,
    val paneId: String,
)

enum class HerdrOpening { OPENED, FOCUSED }
