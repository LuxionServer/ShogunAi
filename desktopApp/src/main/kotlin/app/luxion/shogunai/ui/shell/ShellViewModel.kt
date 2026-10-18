package app.luxion.shogunai.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.io.ShellPreferenceRepository
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.ui.machine.MachineResource
import app.luxion.shogunai.ui.palette.recordRecent

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
    val canCreateWorktree: Boolean get() = destination is ShellDestination.All || destination == ShellDestination.Active
    var isWindowNarrow by mutableStateOf(false)
        private set
    private var expandedWhileNarrow by mutableStateOf(false)
    val isRail: Boolean get() = if (isWindowNarrow) !expandedWhileNarrow else sidebarCollapsed
    private var destinationBeforeForm: ShellDestination? = null
    private var leaveGuard: LeaveGuard? = null
    private var pendingLeave by mutableStateOf<(() -> Unit)?>(null)
    var canSaveSettings by mutableStateOf(false)
        private set
    var saveSettingsRequests by mutableIntStateOf(0)
        private set
    var isPaletteOpen by mutableStateOf(false)
        private set
    var recentPaletteItems by mutableStateOf(shellPreferences.loadRecentPaletteItems())
        private set
    val isLeaveConfirmationPending: Boolean get() = pendingLeave != null

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

    fun updateCanSaveSettings(canSave: Boolean) {
        canSaveSettings = canSave
    }

    fun requestSaveSettings() {
        if (canSaveSettings) saveSettingsRequests++
    }

    fun attachLeaveGuard(guard: LeaveGuard) {
        leaveGuard = guard
    }

    fun detachLeaveGuard(guard: LeaveGuard) {
        if (leaveGuard === guard) leaveGuard = null
    }

    fun confirmLeave() {
        val leave = pendingLeave ?: return
        pendingLeave = null
        leaveGuard = null
        leave()
    }

    fun keepEditing() {
        pendingLeave = null
    }

    fun requestLeave(leave: () -> Unit) {
        if (leaveGuard?.hasUnsavedChanges == true) pendingLeave = leave else leave()
    }

    fun navigate(target: ShellDestination, andThen: () -> Unit = {}) = requestLeave {
        if (target.isForm) openForm(target) else select(target)
        andThen()
    }

    fun openPalette() {
        isPaletteOpen = true
    }

    fun closePalette() {
        isPaletteOpen = false
    }

    fun rememberPaletteItem(id: String, knownIds: Set<String>) {
        recentPaletteItems = recordRecent(recentPaletteItems.filter { it in knownIds }, id)
        shellPreferences.saveRecentPaletteItems(recentPaletteItems)
    }

    fun selectProject(project: Project) = requestLeave { select(ShellDestination.All(projectFilter = project.id)) }

    fun selectActive() = requestLeave { select(ShellDestination.Active) }

    fun selectAll() = requestLeave { select(ShellDestination.All()) }

    fun selectMachine(resource: MachineResource) = requestLeave { select(ShellDestination.Machine(resource)) }

    fun selectFilter(projectId: String?) = requestLeave { select(ShellDestination.All(projectFilter = projectId)) }

    fun addProject(folder: String? = null) = requestLeave { openForm(ShellDestination.AddProject(folder)) }

    fun editProject(project: Project) = requestLeave { openForm(ShellDestination.ProjectSettings(project.id)) }

    fun cancelForm() = requestLeave {
        select(destinationBeforeForm?.takeIf(::isAvailable) ?: fallbackDestination())
    }

    fun onProjectSaved(saved: Project, previous: Project?) {
        leaveGuard = null
        projects = loadProjects()
        val moved = previous != null &&
            (previous.config.baseRepositoryPath != saved.config.baseRepositoryPath ||
                previous.config.worktreesRoot != saved.config.worktreesRoot)
        select(ShellDestination.All(saved.id, previous?.config?.worktreesRoot?.takeIf { moved }))
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

    private fun isAvailable(destination: ShellDestination): Boolean = when {
        (destination is ShellDestination.All || destination == ShellDestination.Active || destination is ShellDestination.Machine) &&
            projects.isEmpty() -> false
        else -> destination.referencedProjectId?.let { id -> projects.any { it.id == id } } ?: true
    }

    private fun fallbackDestination(): ShellDestination? =
        if (projects.isEmpty()) null else ShellDestination.Active

    private fun loadProjects(): List<Project> = projectRepository.list().sortedBy { it.name.lowercase() }
}

data class WorktreeActions(
    val onFind: () -> Unit,
    val onCopySelectedLaunchCommand: () -> Unit,
    val onDeleteSelectedWorktree: () -> Unit,
    val onClearSelection: () -> Unit,
    val onEscape: () -> Unit,
)
