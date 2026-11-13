package app.luxion.shogunai.domain.model

data class GroupWorktree(val alias: String, val config: ProjectConfig, val worktree: Worktree)

data class WorktreeGroup(val branch: String, val worktrees: List<GroupWorktree>) {
    val title: String
        get() = branch.substringAfterLast('/')

    val aliases: List<String>
        get() = worktrees.map { it.alias }
}
