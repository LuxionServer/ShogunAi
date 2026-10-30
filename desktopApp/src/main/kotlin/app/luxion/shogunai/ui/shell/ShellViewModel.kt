package app.luxion.shogunai.ui.shell

import kotlinx.coroutines.delay
import kotlin.time.Duration
import app.luxion.shogunai.ui.actions.ActionId
import app.luxion.shogunai.ui.actions.ActionSpec
import app.luxion.shogunai.ui.i18n.Strings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.io.ShellPreferenceRepository
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.RecentWorktree
import app.luxion.shogunai.domain.model.SidebarOrder
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.ui.machine.MachineResource
import app.luxion.shogunai.ui.multirepo.MultirepoCatalog
import app.luxion.shogunai.ui.multirepo.MultirepoMembership
import app.luxion.shogunai.ui.multirepo.multirepoMemberships
import app.luxion.shogunai.ui.palette.recordRecent

class ShellViewModel(
    private val projectRepository: ProjectRepository,
    private val shellPreferences: ShellPreferenceRepository,
    private val readSettingsProblem: () -> WorktreeError.LocalFileUnreadable? = { null },
    private val acknowledgeSettingsProblem: () -> Unit = {},
    val multirepoCatalog: MultirepoCatalog = MultirepoCatalog { emptyList() },
    private val registerWorkspaceProject: (Multirepo) -> Boolean = { false },
    private val forgetMissingMultirepos: () -> Unit = {},
    private val catalogModifiedAt: () -> Long? = { null },
) : ViewModel() {
    var unreadableFiles by mutableStateOf<List<WorktreeError.LocalFileUnreadable>>(emptyList())
        private set
    var projects by mutableStateOf<List<Project>>(emptyList())
        private set
    var destination by mutableStateOf<ShellDestination?>(null)
        private set
    var memberships by mutableStateOf<List<MultirepoMembership>>(emptyList())
        private set
    var collapsedMultirepos by mutableStateOf(shellPreferences.loadCollapsedMultirepos())
        private set
    var sidebarOrder by mutableStateOf(shellPreferences.loadSidebarOrder())
        private set
    var addMultirepoRequest by mutableStateOf<AddMultirepoRequest?>(null)
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
    var recentProjects by mutableStateOf(shellPreferences.loadRecentProjects())
        private set
    var recentWorktrees by mutableStateOf(shellPreferences.loadRecentWorktrees())
        private set
    val isLeaveConfirmationPending: Boolean get() = pendingLeave != null

    init {
        projects = loadProjects()
        loadMultirepos()
        noteSettingsProblem()
        pruneRecentProjects()
        destination = ShellDestination.fromStorageKey(shellPreferences.loadLastDestination())
            ?.takeIf(::isAvailable)
            ?: fallbackDestination()
    }

    fun refresh() {
        projects = loadProjects()
        loadMultirepos()
        pruneRecentProjects()
        if (destination?.let(::isAvailable) == false) select(fallbackDestination())
        if (destinationBeforeForm?.let(::isAvailable) == false) destinationBeforeForm = null
    }

    suspend fun watchCatalog(interval: Duration) {
        var seen = catalogModifiedAt()
        while (true) {
            delay(interval)
            val modifiedAt = catalogModifiedAt()
            if (modifiedAt != seen) refresh()
            seen = modifiedAt
        }
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

    fun recordWorktree(projectId: String, path: String) {
        val recent = RecentWorktree(projectId, path)
        recentWorktrees = (listOf(recent) + recentWorktrees.filterNot { it == recent }).take(MAX_RECENT_WORKTREES)
        shellPreferences.saveRecentWorktrees(recentWorktrees)
    }

    fun pruneRecentWorktrees(projectId: String, existingPaths: Set<String>) {
        val kept = recentWorktrees.filterNot { it.projectId == projectId && it.path !in existingPaths }
        if (kept == recentWorktrees) return
        recentWorktrees = kept
        shellPreferences.saveRecentWorktrees(kept)
    }

    fun selectProject(project: Project) = requestLeave { select(ShellDestination.All(projectFilter = project.id)) }

    fun selectActive() = requestLeave { select(ShellDestination.Active) }

    fun selectAll() = requestLeave { select(ShellDestination.All()) }

    fun selectMachineOverview() = requestLeave { select(ShellDestination.MachineOverview) }

    fun selectMachine(resource: MachineResource) = requestLeave { select(ShellDestination.Machine(resource)) }

    fun selectMultirepo(folder: String, group: String? = null) = requestLeave { select(ShellDestination.MultirepoView(folder, group)) }

    fun editMultirepo(folder: String) = requestLeave { openForm(ShellDestination.MultirepoSettings(folder)) }

    fun toggleMultirepoCollapsed(folder: String) {
        collapsedMultirepos = if (folder in collapsedMultirepos) collapsedMultirepos - folder else collapsedMultirepos + folder
        shellPreferences.saveCollapsedMultirepos(collapsedMultirepos)
    }

    fun sidebarKeys(level: SidebarLevel): List<String> {
        val entries = sidebarEntries(projects, sidebarFolders(), sidebarOrder)
        return when (level) {
            SidebarLevel.TopLevel -> entries.map { it.key }
            is SidebarLevel.Folder -> entries.filterIsInstance<SidebarEntry.FolderEntry>()
                .firstOrNull { it.multirepo.folder == level.folder }
                ?.multirepo?.projects?.map { it.id }.orEmpty()
        }
    }

    fun moveSidebarEntry(level: SidebarLevel, key: String, toIndex: Int) {
        val keys = sidebarKeys(level)
        val moved = keys.moving(key, toIndex)
        if (moved == keys && sidebarOrder.savedFor(level) == keys) return
        saveSidebarOrder(sidebarOrder.with(level, moved))
    }

    fun sortSidebarAlphabetically(level: SidebarLevel) {
        saveSidebarOrder(sidebarOrder.with(level, null))
    }

    private fun saveSidebarOrder(order: SidebarOrder) {
        sidebarOrder = order
        shellPreferences.saveSidebarOrder(order)
    }

    private fun sidebarFolders(): List<SidebarMultirepo> = memberships.map { membership ->
        SidebarMultirepo(membership.folder, membership.name, projects.filter { it.id in membership.aliases }, ProjectActivity.NONE, collapsed = false)
    }

    fun addMultirepo(folder: String? = null) {
        addMultirepoRequest = AddMultirepoRequest(folder)
    }

    fun dismissAddMultirepo() {
        addMultirepoRequest = null
    }

    fun onMultirepoAdded(folder: String) {
        addMultirepoRequest = null
        refresh()
        navigate(ShellDestination.MultirepoView(folder))
    }

    fun onMultirepoChanged() {
        refresh()
    }

    fun onMultirepoDeleted(folder: String) {
        leaveGuard = null
        if (folder in collapsedMultirepos) toggleMultirepoCollapsed(folder)
        refresh()
        select(fallbackDestination())
    }

    fun multirepoOf(project: Project): MultirepoMembership? = memberships.firstOrNull { project.id in it.aliases }

    fun addProject(folder: String? = null) = requestLeave { openForm(ShellDestination.AddProject(folder)) }

    fun editProject(project: Project) = requestLeave { openForm(ShellDestination.ProjectSettings(project.id)) }

    fun cancelForm() = requestLeave {
        select(destinationBeforeForm?.takeIf(::isAvailable) ?: fallbackDestination())
    }

    fun discardForm() {
        leaveGuard = null
        cancelForm()
    }

    fun onProjectSaved(saved: Project, previous: Project?) {
        leaveGuard = null
        projects = loadProjects()
        loadMultirepos()
        val moved = previous != null &&
            (previous.config.baseRepositoryPath != saved.config.baseRepositoryPath ||
                previous.config.worktreesRoot != saved.config.worktreesRoot)
        select(ShellDestination.All(saved.id, previous?.config?.worktreesRoot?.takeIf { moved }))
    }

    fun rememberFetchBeforeCreate(projectId: String, fetch: Boolean) {
        val project = projects.firstOrNull { it.id == projectId } ?: return
        runCatching { projectRepository.save(project.copy(config = project.config.copy(fetchBeforeCreate = fetch))) }
        projects = loadProjects()
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
        (target as? ShellDestination.All)?.projectFilter?.let(::recordProject)
        target?.storageKey()?.let(shellPreferences::saveLastDestination)
    }

    private fun recordProject(projectId: String) {
        recentProjects = (listOf(projectId) + recentProjects.filterNot { it == projectId }).take(MAX_RECENT_PROJECTS)
        shellPreferences.saveRecentProjects(recentProjects)
    }

    private fun pruneRecentProjects() {
        if (unreadableFiles.any { it.path.endsWith(PROJECTS_FILE_NAME) }) return
        val knownIds = projects.mapTo(mutableSetOf()) { it.id }
        val keptProjects = recentProjects.filter { it in knownIds }
        if (keptProjects != recentProjects) {
            recentProjects = keptProjects
            shellPreferences.saveRecentProjects(keptProjects)
        }
        val keptWorktrees = recentWorktrees.filter { it.projectId in knownIds }
        if (keptWorktrees != recentWorktrees) {
            recentWorktrees = keptWorktrees
            shellPreferences.saveRecentWorktrees(keptWorktrees)
        }
    }

    private fun isAvailable(destination: ShellDestination): Boolean = when {
        destination.referencedMultirepo != null -> multirepoCatalog.find(destination.referencedMultirepo!!) != null
        (destination is ShellDestination.All || destination == ShellDestination.Active || destination is ShellDestination.Machine) &&
            projects.isEmpty() -> false
        else -> destination.referencedProjectId?.let { id -> projects.any { it.id == id } } ?: true
    }

    private fun fallbackDestination(): ShellDestination? =
        if (projects.isEmpty()) null else ShellDestination.Active

    fun dismissUnreadableFiles() {
        unreadableFiles = emptyList()
        acknowledgeSettingsProblem()
    }

    private fun noteSettingsProblem() {
        val problem = readSettingsProblem() ?: return
        if (unreadableFiles.none { it.path == problem.path }) unreadableFiles += problem
    }

    private fun loadMultirepos() {
        forgetMissingMultirepos()
        multirepoCatalog.refresh()
        if (multirepoCatalog.multirepos.filter { it.approved }.count { registerWorkspaceProject(it) } > 0) projects = loadProjects()
        memberships = multirepoMemberships(multirepoCatalog.multirepos, projects)
    }

    private fun loadProjects(): List<Project> = try {
        unreadableFiles = unreadableFiles.filterNot { it.path.endsWith(PROJECTS_FILE_NAME) }
        projectRepository.list().sortedBy { it.name.lowercase() }
    } catch (problem: WorktreeError.LocalFileUnreadable) {
        unreadableFiles += problem
        emptyList()
    }
}

data class AddMultirepoRequest(val folder: String?)

data class WorktreeActions(
    val onFind: () -> Unit,
    val menuBar: (Strings) -> List<ActionSpec>,
    val onRun: (ActionId) -> Unit,
    val onClearSelection: () -> Unit,
    val onEscape: () -> Unit,
    val hasSelection: () -> Boolean,
)

private const val PROJECTS_FILE_NAME = "projects.json"
const val MAX_RECENT_PROJECTS = 5
const val MAX_RECENT_WORKTREES = 8
