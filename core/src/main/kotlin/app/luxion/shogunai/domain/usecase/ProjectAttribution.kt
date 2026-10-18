package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.Worktree

data class ProjectWorktreeSet(val projectId: String, val worktrees: List<Worktree>?)

sealed interface ProjectOwner {
    data class Owned(val projectId: String, val worktreePath: String) : ProjectOwner
    data object RemovedWorktree : ProjectOwner
    data object Outside : ProjectOwner
    data object Loading : ProjectOwner
}

fun attributeToProjects(
    path: String,
    projects: List<ProjectWorktreeSet>,
    exists: (String) -> Boolean,
): ProjectOwner {
    if (!exists(path)) return ProjectOwner.RemovedWorktree
    val normalized = normalizePath(path)
    val owner = projects
        .flatMap { set -> set.worktrees.orEmpty().map { set.projectId to it.path } }
        .filter { (_, worktreePath) -> isInside(normalized, normalizePath(worktreePath)) }
        .maxByOrNull { (_, worktreePath) -> normalizePath(worktreePath).length }
    return when {
        owner != null -> ProjectOwner.Owned(owner.first, owner.second)
        projects.any { it.worktrees == null } -> ProjectOwner.Loading
        else -> ProjectOwner.Outside
    }
}
