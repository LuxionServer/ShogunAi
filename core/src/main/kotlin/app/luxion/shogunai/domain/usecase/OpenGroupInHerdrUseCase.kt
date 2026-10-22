package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.HerdrClient
import app.luxion.shogunai.domain.model.HerdrOpening
import app.luxion.shogunai.domain.model.Multirepo

sealed interface GroupTarget {
    data class Found(val group: WorktreeGroup) : GroupTarget
    data class Ambiguous(val branches: List<String>) : GroupTarget
    data object Missing : GroupTarget
}

fun findGroup(worktrees: MultirepoWorktrees, target: String): GroupTarget {
    worktrees.group(target)?.let { return GroupTarget.Found(it) }
    val matches = listOf("feature/$target", "fix/$target").mapNotNull(worktrees::group)
    return when (matches.size) {
        0 -> GroupTarget.Missing
        1 -> GroupTarget.Found(matches.single())
        else -> GroupTarget.Ambiguous(matches.map { it.branch })
    }
}

data class GroupOpening(val alias: String, val path: String, val result: Result<HerdrOpening>)

class OpenGroupInHerdrUseCase(
    private val herdr: HerdrClient,
    private val resolveEnvironment: ResolveWorktreeEnvironmentUseCase,
) {
    suspend operator fun invoke(group: WorktreeGroup): List<GroupOpening> =
        group.worktrees.map { member ->
            val opening = OpenWorktreeInHerdrUseCase(member.config, herdr, resolveEnvironment)(member.worktree, member.config.agentLaunchConfig)
            GroupOpening(member.alias, member.worktree.path, opening)
        }
}

class OpenMultirepoAgentInHerdrUseCase(private val herdr: HerdrClient) {
    suspend operator fun invoke(multirepo: Multirepo): Result<HerdrOpening> = runCatching {
        val plain = herdr.listWorkspaces().filter { it.worktreePath == null }.map { it.workspaceId }.toSet()
        val open = herdr.listPanes().firstOrNull { pane ->
            pane.workspaceId in plain && pane.directory != null && sameDirectory(pane.directory, multirepo.folder)
        }
        if (open != null) {
            herdr.focusWorkspace(open.workspaceId)
            return@runCatching HerdrOpening.FOCUSED
        }
        val opening = herdr.createWorkspace(multirepo.folder, multirepo.name)
        herdr.runInPane(opening.rootPaneId, multirepo.agent.resolvedCommand())
        HerdrOpening.OPENED
    }
}
