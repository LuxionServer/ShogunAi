package app.luxion.shogunai.domain.io

interface OrcaWorkspaceStore {

    fun workspacesRoot(): String?

    fun remember(workspacesRoot: String)

    companion object {
        val NONE = object : OrcaWorkspaceStore {
            override fun workspacesRoot(): String? = null

            override fun remember(workspacesRoot: String) = Unit
        }
    }
}
