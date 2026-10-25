package app.luxion.shogunai.domain.model

data class ComposeProject(
    val name: String,
    val status: String,
    val configFiles: List<String>,
)

sealed interface ComposeOwner {
    data class Worktree(val path: String) : ComposeOwner
    data object RemovedWorktree : ComposeOwner
}

data class WorktreeComposeProject(val project: ComposeProject, val owner: ComposeOwner)

data class ComposeDownFailure(val name: String, val detail: String)

data class ComposeDownReport(
    val takenDown: List<String> = emptyList(),
    val failures: List<ComposeDownFailure> = emptyList(),
)

sealed interface ComposeAction {
    data class Up(val build: Boolean = false, val extraArguments: List<String> = emptyList()) : ComposeAction
    data class Build(val extraArguments: List<String> = emptyList()) : ComposeAction
}

enum class ComposeResourceKind(val id: String) { NETWORK("network"), IMAGE("image"), VOLUME("volume") }

data class ComposeResource(
    val kind: ComposeResourceKind,
    val id: String,
    val name: String,
    val project: String,
)

data class ComposeResourceFailure(val resource: ComposeResource, val detail: String)

data class ComposeLeftoversReport(
    val removed: List<ComposeResource> = emptyList(),
    val kept: List<ComposeResource> = emptyList(),
    val failures: List<ComposeResourceFailure> = emptyList(),
)

data class WorktreeComposeState(val project: String, val status: String?)
