package app.luxion.shogunai.domain.model

data class Worktree(
    val path: String,
    val branch: String?,
    val head: String? = null,
    val isMain: Boolean = false,
    val isBare: Boolean = false,
    val isLocked: Boolean = false,
    val isPrunable: Boolean = false,
    val skippedPostCreateCommands: List<List<String>> = emptyList(),
    val skippedVariables: List<String> = emptyList(),
    val skippedLinks: List<String> = emptyList(),
    val pods: PodsPreparation = PodsPreparation(),
    val flutter: FlutterPubGet? = null,
)
