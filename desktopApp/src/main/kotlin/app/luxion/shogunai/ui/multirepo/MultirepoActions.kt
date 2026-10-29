package app.luxion.shogunai.ui.multirepo

import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.HerdrOpening
import app.luxion.shogunai.domain.model.HerdrOutcome
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.OrcaOpening
import app.luxion.shogunai.domain.model.OrcaOutcome
import app.luxion.shogunai.domain.usecase.GroupCreation
import app.luxion.shogunai.domain.usecase.GroupOpening
import app.luxion.shogunai.domain.usecase.GroupOrcaOpening
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
    val openAgent: suspend (Multirepo) -> Result<HerdrOpening>,
    val trust: suspend (Multirepo) -> Result<Multirepo> = { Result.failure(UnsupportedOperationException("no multirepo support")) },
    val openGroupInHerdr: suspend (WorktreeGroup) -> List<GroupOpening> = { emptyList() },
    val openGroupInOrca: suspend (WorktreeGroup) -> Result<List<GroupOrcaOpening>> = { Result.failure(UnsupportedOperationException("no multirepo support")) },
    val openAgentInOrca: suspend (Multirepo) -> Result<OrcaOpening> = { Result.failure(UnsupportedOperationException("no multirepo support")) },
    val herdrInstalled: Boolean = false,
    val orcaInstalled: Boolean = false,
) {
    companion object {
        val NONE = MultirepoActions(
            createGroup = { _, _, _, _, _ -> Result.failure(UnsupportedOperationException("no multirepo support")) },
            openAgent = { Result.failure(UnsupportedOperationException("no multirepo support")) },
        )
    }
}

sealed interface MultirepoNotice {
    data class AddedToGroup(val alias: String, val title: String) : MultirepoNotice
    data class ChangeCreated(val title: String, val aliases: List<String>) : MultirepoNotice
    data class AgentOpened(val outcome: HerdrOutcome) : MultirepoNotice
    data class AgentOpenedInOrca(val outcome: OrcaOutcome) : MultirepoNotice
    data class GroupOpenedInOrca(val title: String, val outcomes: List<Pair<String, OrcaOutcome>>) : MultirepoNotice
    data class Failed(val error: Throwable) : MultirepoNotice
}

fun branchTypeOf(branch: String): Pair<BranchType, String>? =
    BranchType.entries.firstNotNullOfOrNull { type ->
        branch.removePrefix("${type.prefix}/").takeIf { it != branch && it.isNotEmpty() && '/' !in it }?.let { type to it }
    }
