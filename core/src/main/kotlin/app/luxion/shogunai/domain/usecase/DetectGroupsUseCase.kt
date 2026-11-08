package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree

data class MultirepoMember(val alias: String, val config: ProjectConfig)

data class GroupWorktree(val alias: String, val config: ProjectConfig, val worktree: Worktree)

data class WorktreeGroup(val branch: String, val worktrees: List<GroupWorktree>) {
    val title: String
        get() = branch.substringAfterLast('/')

    val aliases: List<String>
        get() = worktrees.map { it.alias }
}

data class MultirepoWorktrees(
    val members: List<MultirepoMember>,
    val groups: List<WorktreeGroup>,
    val ungrouped: List<GroupWorktree>,
    val failures: Map<String, Throwable> = emptyMap(),
) {
    fun groupOf(worktreePath: String): WorktreeGroup? =
        groups.firstOrNull { group -> group.worktrees.any { sameDirectory(it.worktree.path, worktreePath) } }

    fun group(branch: String): WorktreeGroup? = groups.firstOrNull { it.branch == branch }
}

fun multirepoMembers(multirepo: Multirepo, projects: List<Project>): List<MultirepoMember> =
    multirepo.repositories.map { repository ->
        val project = projects.firstOrNull { sameDirectory(it.config.baseRepositoryPath, repository.path) }
        val config = if (project != null) project.config else ProjectConfig(baseRepositoryPath = repository.path, worktreesRoot = "", secretFiles = emptyList())
        val worktreesRoot = config.worktreesRoot.ifBlank {
            if (repository == multirepo.workspaceRepository) multirepo.proposedWorktreesFolder(repository.alias) else ""
        }
        MultirepoMember(repository.alias, config.copy(baseRepositoryPath = repository.path, worktreesRoot = worktreesRoot))
    }

class DetectGroupsUseCase(private val executor: ShellCommandExecutor) {

    suspend operator fun invoke(members: List<MultirepoMember>): MultirepoWorktrees {
        val failures = mutableMapOf<String, Throwable>()
        val linked = members.flatMap { member ->
            ListWorktreesUseCase(member.config, executor)().fold(
                onSuccess = { worktrees -> worktrees.filterNot { it.isMain || it.isBare }.map { GroupWorktree(member.alias, member.config, it) } },
                onFailure = { failures[member.alias] = it; emptyList() },
            )
        }
        val byBranch = linked.filter { it.worktree.branch != null }.groupBy { it.worktree.branch!! }
        val groups = byBranch
            .filterValues { worktrees -> worktrees.map { it.alias }.distinct().size >= 2 }
            .map { (branch, worktrees) -> WorktreeGroup(branch, worktrees) }
            .sortedBy { it.branch }
        val grouped = groups.flatMap { it.worktrees }.map { it.worktree.path }.toSet()
        return MultirepoWorktrees(members, groups, linked.filterNot { it.worktree.path in grouped }, failures)
    }
}
