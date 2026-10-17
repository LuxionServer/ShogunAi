package app.luxion.shogunai.domain.model

data class Worktree(
    val path: String,
    val branch: String?,
    val head: String? = null,
    val isMain: Boolean = false,
    val isBare: Boolean = false,
    val isLocked: Boolean = false,
    val isPrunable: Boolean = false,
)
