package app.luxion.shogunai.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.io.ShellPreferenceRepository
import app.luxion.shogunai.domain.model.Project

class ShellViewModel(
    private val projectRepository: ProjectRepository,
    private val shellPreferences: ShellPreferenceRepository,
) : ViewModel() {
    var projects by mutableStateOf<List<Project>>(emptyList())
        private set
    var destination by mutableStateOf<ShellDestination?>(null)
        private set
    var sidebarCollapsed by mutableStateOf(shellPreferences.loadSidebarCollapsed())
        private set
    var reloadRequests by mutableIntStateOf(0)
        private set
    var newWorktreeRequests by mutableIntStateOf(0)
        private set
    val canCreateWorktree: Boolean get() = destination is ShellDestination.Project
    var isWindowNarrow by mutableStateOf(false)
        private set
    private var expandedWhileNarrow by mutableStateOf(false)
    val isRail: Boolean get() = if (isWindowNarrow) !expandedWhileNarrow else sidebarCollapsed
    private var destinationBeforeForm: ShellDestination? = null

    init {
        projects = loadProjects()
        destination = ShellDestination.fromStorageKey(shellPreferences.loadLastDestination())
            ?.takeIf(::isAvailable)
            ?: fallbackDestination()
    }

    fun refresh() {
        projects = loadProjects()
        if (destination?.let(::isAvailable) == false) select(fallbackDestination())
        if (destinationBeforeForm?.let(::isAvailable) == false) destinationBeforeForm = null
    }

    fun reload() {
        refresh()
        reloadRequests++
    }

    fun requestNewWorktree() {
        if (canCreateWorktree) newWorktreeRequests++
    }

    var worktreeActions by mutableStateOf<WorktreeActions?>(null)
        private set

    fun updateWorktreeActions(actions: WorktreeActions?) {
        worktreeActions = actions
    }

    fun selectProject(project: Project) = select(ShellDestination.Project(project.id))

    fun addProject() = openForm(ShellDestination.AddProject)

    fun editProject(project: Project) = openForm(ShellDestination.ProjectSettings(project.id))

    fun cancelForm() {
        select(destinationBeforeForm?.takeIf(::isAvailable) ?: fallbackDestination())
    }

    fun onProjectSaved(saved: Project, previous: Project?) {
        projects = loadProjects()
        val moved = previous != null &&
            (previous.config.baseRepositoryPath != saved.config.baseRepositoryPath ||
                previous.config.worktreesRoot != saved.config.worktreesRoot)
        select(ShellDestination.Project(saved.id, previous?.config?.worktreesRoot?.takeIf { moved }))
    }

    fun removeProject(project: Project) {
        projectRepository.delete(project.id)
        refresh()
    }

    fun onWindowWidthChanged(narrow: Boolean) {
        if (narrow == isWindowNarrow) return
        isWindowNarrow = narrow
        expandedWhileNarrow = false
    }

    fun toggleSidebar() {
        if (isWindowNarrow) {
            expandedWhileNarrow = !expandedWhileNarrow
        } else {
            sidebarCollapsed = !sidebarCollapsed
            shellPreferences.saveSidebarCollapsed(sidebarCollapsed)
        }
    }

    fun projectFor(destination: ShellDestination): Project? =
        destination.referencedProjectId?.let { id -> projects.firstOrNull { it.id == id } }

    private fun openForm(form: ShellDestination) {
        val current = destination
        if (current != null && !current.isForm) destinationBeforeForm = current
        destination = form
    }

    private fun select(target: ShellDestination?) {
        destination = target
        target?.storageKey()?.let(shellPreferences::saveLastDestination)
    }

    private fun isAvailable(destination: ShellDestination): Boolean =
        destination.referencedProjectId?.let { id -> projects.any { it.id == id } } ?: true

    private fun fallbackDestination(): ShellDestination? =
        projects.firstOrNull()?.let { ShellDestination.Project(it.id) }

    private fun loadProjects(): List<Project> = projectRepository.list().sortedBy { it.name.lowercase() }
}

data class WorktreeActions(
    val onCopySelectedLaunchCommand: () -> Unit,
    val onDeleteSelectedWorktree: () -> Unit,
    val onClearSelection: () -> Unit,
)
