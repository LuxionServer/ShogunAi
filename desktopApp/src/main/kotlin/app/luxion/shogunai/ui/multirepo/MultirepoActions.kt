package app.luxion.shogunai.ui.multirepo

import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.GroupRunnerOpening
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.domain.model.RunnerOpening
import app.luxion.shogunai.domain.model.RunnerOutcome
import app.luxion.shogunai.domain.usecase.GroupCreation
import app.luxion.shogunai.domain.usecase.GroupRepositoryResult
import app.luxion.shogunai.domain.usecase.MultirepoMember
import app.luxion.shogunai.domain.usecase.WorktreeGroup

class MultirepoActions(
    val createGroup: suspend (
        members: List<MultirepoMember>,
        taskId: String,
        branchType: BranchType,
        fetch: Boolean,
        onRepository: (GroupRepositoryResult) -> Unit,
    ) -> Result<GroupCreation>,
    val openAgent: suspend (RunnerId, Multirepo) -> Result<RunnerOpening> = { _, _ -> Result.failure(UnsupportedOperationException("no multirepo support")) },
    val trust: suspend (Multirepo) -> Result<Multirepo> = { Result.failure(UnsupportedOperationException("no multirepo support")) },
    val openGroup: suspend (RunnerId, WorktreeGroup) -> Result<List<GroupRunnerOpening>> = { _, _ -> Result.failure(UnsupportedOperationException("no multirepo support")) },
    val installedRunners: Set<RunnerId> = emptySet(),
) {
    val orcaInstalled: Boolean get() = RunnerId.ORCA in installedRunners

    companion object {
        val NONE = MultirepoActions(
            createGroup = { _, _, _, _, _ -> Result.failure(UnsupportedOperationException("no multirepo support")) },
        )
    }
}

sealed interface MultirepoNotice {
    data class AddedToGroup(val alias: String, val title: String) : MultirepoNotice
    data class ChangeCreated(val title: String, val aliases: List<String>) : MultirepoNotice
    data class AgentOpened(val runner: RunnerId, val outcome: RunnerOutcome) : MultirepoNotice
    data class GroupOpenedInOrca(val title: String, val outcomes: List<Pair<String, RunnerOutcome>>) : MultirepoNotice
    data class Failed(val error: Throwable) : MultirepoNotice
}

fun branchTypeOf(branch: String): Pair<BranchType, String>? =
    BranchType.entries.firstNotNullOfOrNull { type ->
        branch.removePrefix("${type.prefix}/").takeIf { it != branch && it.isNotEmpty() && '/' !in it }?.let { type to it }
    }
