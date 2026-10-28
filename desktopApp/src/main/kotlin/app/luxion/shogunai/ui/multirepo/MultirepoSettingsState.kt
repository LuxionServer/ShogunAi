package app.luxion.shogunai.ui.multirepo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.domain.model.DEFAULT_WORKSPACE_ALIAS
import app.luxion.shogunai.domain.model.InvalidEnvironmentTemplate
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.RepositoryConfigIssue
import app.luxion.shogunai.domain.model.isValidMultirepoAlias
import app.luxion.shogunai.domain.model.parseSharedEnvironmentVariable
import app.luxion.shogunai.ui.projectconfig.AgentDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

data class SharedVariableDraft(val id: Int, val alias: String, val name: String = "", val template: String = "")

class MultirepoSettingsState(
    private val folder: String,
    private val setup: MultirepoSetup,
    private val scope: CoroutineScope,
    private val onChanged: () -> Unit = {},
) {
    var multirepo by mutableStateOf<Multirepo?>(null)
        private set
    var defaults by mutableStateOf<Set<String>>(emptySet())
        private set
    var name by mutableStateOf("")
    var worktreesRoot by mutableStateOf("")
    var workspaceDeclared by mutableStateOf(false)
    var workspaceAlias by mutableStateOf(DEFAULT_WORKSPACE_ALIAS)
    var workspaceOff by mutableStateOf(false)
        private set
    var agent by mutableStateOf(AgentDraft())
    var sharedVariables by mutableStateOf<List<SharedVariableDraft>>(emptyList())
        private set
    private var nextSharedVariableId = 0
    var newAlias by mutableStateOf("")
    var newPath by mutableStateOf("")
    var newUsesProposedFolder by mutableStateOf(true)
    var isBusy by mutableStateOf(false)
        private set
    var error by mutableStateOf<Throwable?>(null)
        private set
    var isConfirmingDelete by mutableStateOf(false)
        private set

    init {
        reload()
    }

    val aliases: List<String>
        get() = multirepo?.aliases.orEmpty()

    val hasWorkspaceSection: Boolean
        get() = multirepo?.let { it.folderIsRepository && it.declaredRepositories.none { repository -> sameFolder(repository.path, it.folder) } } == true

    val workspaceDetected: Boolean
        get() = multirepo?.workspaceDetected == true

    val needsReview: Boolean
        get() = multirepo?.approved == false

    val canReturnWorkspaceToDetection: Boolean
        get() = hasWorkspaceSection && multirepo?.declaration?.workspace != null

    val sharedVariablesProblem: String?
        get() = multirepo?.declaration?.env?.problems?.firstOrNull()

    fun sharedVariableIssue(draft: SharedVariableDraft): RepositoryConfigIssue? {
        val earlier = sharedVariables.takeWhile { it.id != draft.id }
        if (earlier.any { it.alias == draft.alias && it.name == draft.name }) return RepositoryConfigIssue.DuplicateVariableName
        return try {
            parseSharedEnvironmentVariable(draft.name, draft.template, aliases.toSet())
            null
        } catch (error: InvalidEnvironmentTemplate) {
            error.issue
        }
    }

    private val sharedVariablesValid: Boolean
        get() = sharedVariables.all { sharedVariableIssue(it) == null }

    private fun draftedSharedVariables(): Map<String, Map<String, String>> =
        sharedVariables.groupBy { it.alias }.mapValues { (_, drafts) -> drafts.associate { it.name to it.template } }

    private fun sharedVariablesChanged(current: Multirepo): Boolean =
        draftedSharedVariables() != current.declaration.env?.byAlias.orEmpty().filterValues { it.isNotEmpty() }

    val hasChanges: Boolean
        get() {
            val current = multirepo ?: return false
            return name.trim() != current.declaration.name?.trim().orEmpty() ||
                defaults != current.defaultRepos.toSet() ||
                worktreesRoot.trim() != current.worktreesRoot ||
                agent.toConfig() != current.agent ||
                workspaceChanged(current) ||
                sharedVariablesChanged(current)
        }

    val canSave: Boolean
        get() = hasChanges && !isBusy && !needsReview && worktreesRoot.isNotBlank() && agent.command.isNotBlank() &&
            (!workspaceDeclared || isValidMultirepoAlias(workspaceAlias)) &&
            sharedVariablesValid

    val canAddRepository: Boolean
        get() = !isBusy && !needsReview && isValidMultirepoAlias(newAlias) && newAlias !in aliases && newPath.isNotBlank()

    fun reload() {
        val read = setup.read(folder)
        multirepo = read
        defaults = read?.defaultRepos?.toSet().orEmpty()
        workspaceDeclared = read?.workspaceDeclared == true
        workspaceAlias = read?.workspaceRepository?.alias ?: read?.declaration?.workspace?.alias ?: DEFAULT_WORKSPACE_ALIAS
        workspaceOff = read?.declaration?.workspace?.enabled == false
        name = read?.declaration?.name.orEmpty()
        worktreesRoot = read?.worktreesRoot.orEmpty()
        agent = read?.agent?.let(AgentDraft::from) ?: AgentDraft()
        sharedVariables = read?.sharedTemplates.orEmpty().map { (alias, name, template) -> SharedVariableDraft(nextSharedVariableId++, alias, name, template) }
    }

    fun addSharedVariable() {
        val alias = aliases.firstOrNull() ?: return
        sharedVariables = sharedVariables + SharedVariableDraft(nextSharedVariableId++, alias)
    }

    fun updateSharedVariable(draft: SharedVariableDraft) {
        sharedVariables = sharedVariables.map { if (it.id == draft.id) draft else it }
    }

    fun removeSharedVariable(id: Int) {
        sharedVariables = sharedVariables.filterNot { it.id == id }
    }

    fun toggleDefault(alias: String) {
        defaults = if (alias in defaults) defaults - alias else defaults + alias
    }

    fun turnOffWorkspace() = run { multirepo?.let { setup.clearWorkspace(it).getOrThrow() } }

    fun returnWorkspaceToDetection() = run { multirepo?.let { setup.detectWorkspace(it).getOrThrow() } }

    private fun workspaceChanged(current: Multirepo): Boolean {
        if (!hasWorkspaceSection) return false
        return workspaceDeclared != current.workspaceDeclared ||
            (workspaceDeclared && workspaceAlias != current.workspaceRepository?.alias)
    }

    fun save() {
        val current = multirepo ?: return
        if (!canSave) return
        run {
            var updated = current
            if (name.trim() != current.declaration.name?.trim().orEmpty()) updated = setup.setName(updated, name.trim().ifEmpty { null }).getOrThrow()
            if (sharedVariablesChanged(current)) updated = setup.setSharedVariables(updated, draftedSharedVariables()).getOrThrow()
            val previousWorkspace = current.workspaceRepository?.alias
            if (workspaceChanged(current) && workspaceDeclared) {
                updated = setup.setWorkspace(updated, workspaceAlias, workspaceAlias in defaults || previousWorkspace in defaults).getOrThrow()
                defaults = defaults.map { if (it == previousWorkspace) workspaceAlias else it }.toSet()
            }
            if (defaults != updated.defaultRepos.toSet()) {
                updated = setup.setDefaults(updated, updated.aliases.filter { it in defaults }).getOrThrow()
            }
            if (worktreesRoot.trim() != current.worktreesRoot) updated = setup.setWorktreesRoot(updated, worktreesRoot.trim()).getOrThrow()
            if (agent.toConfig() != current.agent) setup.setAgent(updated, agent.toConfig()).getOrThrow()
        }
    }

    fun addRepository() {
        val current = multirepo ?: return
        if (!canAddRepository) return
        val alias = newAlias
        val path = newPath.trim()
        isBusy = true
        error = null
        scope.launch {
            try {
                setup.addRepository(current, alias, path, newUsesProposedFolder).getOrThrow()
                newAlias = ""
                newPath = ""
                refreshAfterChange()
            } catch (failure: Exception) {
                error = failure
            } finally {
                isBusy = false
            }
        }
    }

    fun chooseNewPath(path: String) {
        newPath = path
        if (newAlias.isBlank()) newAlias = proposedAlias(File(path).name, multirepo?.folderName.orEmpty())
    }

    fun removeRepository(alias: String) = run { multirepo?.let { setup.removeRepository(it, alias).getOrThrow() } }

    fun renameRepository(alias: String, newAlias: String) = run {
        multirepo?.let { setup.renameRepository(it, alias, newAlias.trim().lowercase()).getOrThrow() }
    }

    fun requestDelete() {
        isConfirmingDelete = true
    }

    fun cancelDelete() {
        isConfirmingDelete = false
    }

    fun confirmDelete(onDeleted: () -> Unit) {
        val current = multirepo ?: return
        isConfirmingDelete = false
        setup.delete(current).onSuccess { onDeleted() }.onFailure { error = it }
    }

    fun dismissError() {
        error = null
    }

    private fun run(change: () -> Unit) {
        error = null
        try {
            change()
        } catch (failure: Exception) {
            error = failure
        }
        refreshAfterChange()
    }

    private fun refreshAfterChange() {
        reload()
        onChanged()
    }
}
