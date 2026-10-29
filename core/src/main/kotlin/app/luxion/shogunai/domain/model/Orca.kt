package app.luxion.shogunai.domain.model

const val ORCA_START_SECONDS = 30

const val ORCA_MINIMUM_VERSION = "1.4.243"

data class OrcaStatus(val running: Boolean, val version: String? = null)

data class OrcaRepo(val id: String, val path: String, val externalWorktreeVisibility: String? = null)

data class OrcaAgent(val agent: String, val state: AgentState?)

data class OrcaWorktree(
    val path: String,
    val createdByOrca: Boolean,
    val agents: List<OrcaAgent> = emptyList(),
    val folderId: String? = null,
    val comment: String? = null,
    val workspaceStatus: String? = null,
    val linkedPullRequest: Int? = null,
    val linkedIssue: Int? = null,
) {
    val workspace: OrcaWorkspace get() = folderId?.let(OrcaWorkspace::Folder) ?: OrcaWorkspace.Worktree(path)
}

sealed interface OrcaWorkspace {
    val selector: String

    data class Worktree(val path: String) : OrcaWorkspace {
        override val selector: String get() = "path:$path"
    }

    data class Folder(val id: String) : OrcaWorkspace {
        override val selector: String get() = "id:folder:$id"
    }
}

data class OrcaWorktreeUpdate(
    val comment: String? = null,
    val workspaceStatus: String? = null,
    val pullRequest: Int? = null,
    val issue: Int? = null,
) {
    val isEmpty: Boolean get() = comment == null && workspaceStatus == null && pullRequest == null && issue == null
}

data class OrcaTerminal(val handle: String, val worktreePath: String, val title: String? = null)

enum class OrcaOutcome { STARTED_AGENT, FOCUSED }

data class OrcaOpening(
    val outcome: OrcaOutcome,
    val terminalHandle: String,
    val agent: String?,
    val sessionName: String? = null,
    val emulator: String? = null,
)

sealed interface OrcaEmulatorView {
    data object Shown : OrcaEmulatorView

    data object NotOpen : OrcaEmulatorView

    data class Failed(val message: String) : OrcaEmulatorView
}
