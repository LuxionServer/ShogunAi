package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError

fun resolveWorktreeTarget(
    target: String,
    worktrees: List<Worktree>,
    config: ProjectConfig,
    canonicalPath: (String) -> String,
): Worktree {
    val resolved = byPath(target, worktrees, canonicalPath)
        ?: unique(target, worktrees.filter { it.branch == target })
        ?: byTaskId(target, worktrees, config, canonicalPath)
        ?: throw WorktreeError.WorktreeTargetNotFound(target)
    if (resolved.isMain) throw WorktreeError.CannotRemoveMainWorktree(resolved.path)
    return resolved
}

private fun byPath(target: String, worktrees: List<Worktree>, canonicalPath: (String) -> String): Worktree? =
    worktrees.firstOrNull { canonicalPath(it.path) == canonicalPath(target) }

private fun byTaskId(
    target: String,
    worktrees: List<Worktree>,
    config: ProjectConfig,
    canonicalPath: (String) -> String,
): Worktree? {
    if (config.worktreesRoot.isBlank() || target.isBlank()) return null
    val folder = canonicalPath(config.worktreePathFor(normalizeTaskId(target)))
    return unique(target, worktrees.filter { !it.isMain && canonicalPath(it.path) == folder })
}

private fun unique(target: String, matches: List<Worktree>): Worktree? = when (matches.size) {
    0 -> null
    1 -> matches.single()
    else -> throw WorktreeError.AmbiguousWorktreeTarget(target, matches.map { it.path })
}
