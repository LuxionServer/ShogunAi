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
