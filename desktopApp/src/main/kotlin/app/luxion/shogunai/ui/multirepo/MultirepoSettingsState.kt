package app.luxion.shogunai.ui.multirepo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.isValidMultirepoAlias
import app.luxion.shogunai.ui.projectconfig.AgentDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

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
    var worktreesRoot by mutableStateOf("")
    var agent by mutableStateOf(AgentDraft())
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

    val hasChanges: Boolean
        get() {
            val current = multirepo ?: return false
            return defaults != current.declaration.defaultRepos.toSet() ||
                worktreesRoot.trim() != current.worktreesRoot ||
                agent.toConfig() != current.agent
        }

    val canSave: Boolean
        get() = hasChanges && !isBusy && worktreesRoot.isNotBlank() && agent.command.isNotBlank()

    val canAddRepository: Boolean
        get() = !isBusy && isValidMultirepoAlias(newAlias) && newAlias !in aliases && newPath.isNotBlank()

    fun reload() {
        val read = setup.read(folder)
        multirepo = read
        defaults = read?.declaration?.defaultRepos?.toSet().orEmpty()
        worktreesRoot = read?.worktreesRoot.orEmpty()
        agent = read?.agent?.let(AgentDraft::from) ?: AgentDraft()
    }

    fun toggleDefault(alias: String) {
        defaults = if (alias in defaults) defaults - alias else defaults + alias
    }

    fun save() {
        val current = multirepo ?: return
        if (!canSave) return
        run {
            var updated = current
            if (defaults != current.declaration.defaultRepos.toSet()) {
                updated = setup.setDefaults(updated, aliases.filter { it in defaults }).getOrThrow()
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
        if (newAlias.isBlank()) newAlias = proposedAlias(File(path).name, multirepo?.name.orEmpty())
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
