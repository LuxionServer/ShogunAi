package app.luxion.shogunai.domain.io

interface OrcaWorkspaceStore {

    fun workspacesRoot(): String?

    fun remember(workspacesRoot: String)

    fun boardStatuses(): Map<String, String> = emptyMap()

    fun rememberBoardStatuses(statuses: Map<String, String>) = Unit

    companion object {
        val NONE = object : OrcaWorkspaceStore {
            override fun workspacesRoot(): String? = null

            override fun remember(workspacesRoot: String) = Unit
        }
    }
}
