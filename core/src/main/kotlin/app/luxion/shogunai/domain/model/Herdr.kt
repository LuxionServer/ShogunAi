package app.luxion.shogunai.domain.model

data class HerdrWorktreeOpening(val workspaceId: String, val rootPaneId: String, val alreadyOpen: Boolean)

enum class HerdrOpening { OPENED, FOCUSED }
