package app.luxion.shogunai.domain.model

data class HerdrWorktreeOpening(val workspaceId: String, val rootPaneId: String, val alreadyOpen: Boolean)

data class HerdrAgent(
    val name: String,
    val state: AgentState?,
    val directory: String,
    val paneId: String,
)

enum class HerdrOpening { OPENED, FOCUSED }

data class HerdrWorkspace(val workspaceId: String, val label: String, val worktreePath: String?)

data class HerdrPane(val paneId: String, val workspaceId: String, val directory: String?)
