package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.MainBranch
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.WorktreeStatus

data class GroupWorktreeState(
    val member: GroupWorktree,
    val base: MainBranch?,
    val status: Result<WorktreeStatus>?,
    val agents: List<RunningAgent>,
) {
    val isCleanAndMerged: Boolean
        get() = member.worktree.isPrunable || status?.getOrNull()?.let { it.isMerged && it.uncommittedChanges == 0 } == true
}

data class RepositoryBases(val main: MainBranch?, val recorded: Map<String, MainBranch> = emptyMap()) {
    fun of(branch: String?): MainBranch? = branch?.let(recorded::get) ?: main
}

data class GroupState(val group: WorktreeGroup, val worktrees: List<GroupWorktreeState>)

data class MultirepoState(
    val worktrees: MultirepoWorktrees,
    val groups: List<GroupState>,
    val ungrouped: List<GroupWorktreeState>,
)

class ReadGroupsUseCase(
    private val detectGroups: DetectGroupsUseCase,
    private val resolveBases: suspend (MultirepoMember) -> RepositoryBases,
    private val readStatus: suspend (GroupWorktree, MainBranch?, MainBranch?) -> Result<WorktreeStatus>,
    private val detectAgents: suspend (List<AgentSearch>) -> Result<Map<String, List<RunningAgent>>>,
) {
    suspend operator fun invoke(members: List<MultirepoMember>, withUngrouped: Boolean = true): MultirepoState {
        val worktrees = detectGroups(members)
        val bases = members.associate { it.alias to (runCatching { resolveBases(it) }.getOrNull() ?: RepositoryBases(null)) }
        val relevant = worktrees.groups.flatMap { it.worktrees } + if (withUngrouped) worktrees.ungrouped else emptyList()
        val searches = members.map { member ->
            AgentSearch(member.config.agents.map { it.agentCommand }, relevant.filter { it.alias == member.alias && !it.worktree.isPrunable }.map { it.worktree })
        }
        val agents = detectAgents(searches).getOrNull().orEmpty()
        val states = relevant.associate { member ->
            val repositoryBases = bases.getValue(member.alias)
            val base = repositoryBases.of(member.worktree.branch)
            val status = if (member.worktree.isPrunable) null else readStatus(member, repositoryBases.main, base)
            member.worktree.path to GroupWorktreeState(member, base, status, agents[member.worktree.path].orEmpty())
        }
        return MultirepoState(
            worktrees = worktrees,
            groups = worktrees.groups.map { group -> GroupState(group, group.worktrees.map { states.getValue(it.worktree.path) }) },
            ungrouped = if (withUngrouped) worktrees.ungrouped.map { states.getValue(it.worktree.path) } else emptyList(),
        )
    }
}

enum class GroupKeptReason { NOT_MERGED, UNCOMMITTED_CHANGES, STATUS_UNAVAILABLE, AGENT_RUNNING }

data class GroupKept(val group: WorktreeGroup, val alias: String, val reason: GroupKeptReason, val agents: List<RunningAgent> = emptyList())

data class GroupCleanupSelection(
    val candidates: List<GroupState>,
    val kept: List<GroupKept>,
    val loose: List<GroupWorktreeState> = emptyList(),
) {
    val isEmpty: Boolean
        get() = candidates.isEmpty() && loose.isEmpty()

    val removablePaths: List<String>
        get() = candidates.flatMap { it.worktrees }.map { it.member.worktree.path } + loose.map { it.member.worktree.path }

    val removableByAlias: Map<String, List<GroupWorktreeState>>
        get() = (candidates.flatMap { it.worktrees } + loose).groupBy { it.member.alias }
}

fun selectLooseCleanup(ungrouped: List<GroupWorktreeState>): List<GroupWorktreeState> =
    ungrouped.filter { it.agents.isEmpty() && !it.member.worktree.isLocked && it.isCleanAndMerged }

fun selectGroupCleanup(groups: List<GroupState>): GroupCleanupSelection {
    val kept = mutableListOf<GroupKept>()
    val candidates = groups.filter { group ->
        val reason = group.worktrees.firstNotNullOfOrNull { worktree -> keptReason(worktree)?.let { worktree to it } }
        if (reason != null) kept += GroupKept(group.group, reason.first.member.alias, reason.second, reason.first.agents)
        reason == null
    }
    return GroupCleanupSelection(candidates, kept)
}

private fun keptReason(worktree: GroupWorktreeState): GroupKeptReason? {
    if (worktree.agents.isNotEmpty()) return GroupKeptReason.AGENT_RUNNING
    if (worktree.member.worktree.isPrunable) return null
    val status = worktree.status?.getOrNull() ?: return GroupKeptReason.STATUS_UNAVAILABLE
    if (status.uncommittedChanges > 0) return GroupKeptReason.UNCOMMITTED_CHANGES
    if (!status.isMerged) return GroupKeptReason.NOT_MERGED
    return null
}
