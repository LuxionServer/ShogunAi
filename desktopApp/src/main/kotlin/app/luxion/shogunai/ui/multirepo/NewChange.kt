package app.luxion.shogunai.ui.multirepo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.usecase.GroupRepositoryResult
import app.luxion.shogunai.domain.usecase.isValidGitRefSegment
import app.luxion.shogunai.domain.usecase.normalizeTaskId

class NewChange(val membership: MultirepoMembership) {
    val aliases: List<String> = membership.aliases.values.sorted()

    var taskId by mutableStateOf("")
        private set
    var branchType by mutableStateOf(BranchType.FEATURE)
    var fetchFirst by mutableStateOf(false)
    var selected by mutableStateOf(
        membership.multirepo.declaration.defaultRepos.filter { it in aliases }.toSet().ifEmpty { aliases.toSet() },
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
