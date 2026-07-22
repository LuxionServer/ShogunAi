package app.luxion.shogunai.domain.model

/** A local branch, and whether it's already checked out in a worktree. */
data class BranchOption(
    val name: String,
    val isCheckedOut: Boolean,
)
