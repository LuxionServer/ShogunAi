package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.Worktree

fun filterWorktrees(worktrees: List<Worktree>, text: String): List<Worktree> {
    val query = text.trim()
    if (query.isEmpty()) return worktrees
    return worktrees.filter { worktree ->
        worktree.branch.orEmpty().contains(query, ignoreCase = true) ||
            worktree.path.trimEnd('/').substringAfterLast('/').contains(query, ignoreCase = true)
    }
}
