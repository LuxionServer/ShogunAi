package app.luxion.shogunai.ui.multirepo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.HerdrOutcome
import app.luxion.shogunai.domain.model.OrcaOutcome
import app.luxion.shogunai.domain.usecase.GroupRepositoryResult
import app.luxion.shogunai.domain.usecase.isValidGitRefSegment
import app.luxion.shogunai.domain.usecase.normalizeTaskId

enum class ChangeRunner { NONE, HERDR, ORCA }

sealed interface RunnerOutcome {
    data class Herdr(val outcome: HerdrOutcome) : RunnerOutcome
    data class Orca(val outcome: OrcaOutcome) : RunnerOutcome
}

class NewChange(val membership: MultirepoMembership, herdrInstalled: Boolean = false, orcaInstalled: Boolean = false) {
    val aliases: List<String> = membership.aliases.values.sorted()

    val runners: List<ChangeRunner> = listOfNotNull(
        ChangeRunner.NONE,
        ChangeRunner.HERDR.takeIf { herdrInstalled },
        ChangeRunner.ORCA.takeIf { orcaInstalled },
    ).takeIf { it.size > 1 }.orEmpty()

    var runner by mutableStateOf(ChangeRunner.NONE)
    var openings by mutableStateOf<Map<String, Result<RunnerOutcome>>?>(null)
        internal set

    var taskId by mutableStateOf("")
        private set
    var branchType by mutableStateOf(BranchType.FEATURE)
    var fetchFirst by mutableStateOf(false)
    var selected by mutableStateOf(
        membership.multirepo.defaultRepos.filter { it in aliases }.toSet().ifEmpty { aliases.toSet() },
    )
        private set
    var isCreating by mutableStateOf(false)
        internal set
    var results by mutableStateOf<List<GroupRepositoryResult>?>(null)
        internal set
    var error by mutableStateOf<Throwable?>(null)
        internal set

    val isTaskIdValid: Boolean
        get() = taskId.isNotBlank() && isValidGitRefSegment(taskId)

    val canConfirm: Boolean
        get() = isTaskIdValid && selected.isNotEmpty() && !isCreating && results == null

    val branch: String
        get() = "${branchType.prefix}/$taskId"

    fun updateTaskId(raw: String) {
        taskId = normalizeTaskId(raw)
    }

    fun toggle(alias: String) {
        if (isCreating || results != null) return
        selected = if (alias in selected) selected - alias else selected + alias
    }

    val orderedSelection: List<String>
        get() = aliases.filter { it in selected }
}
