package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.ShellPreferenceRepository
import app.luxion.shogunai.domain.model.GroupedView
import app.luxion.shogunai.domain.model.Grouping
import app.luxion.shogunai.domain.model.RecentWorktree
import app.luxion.shogunai.domain.model.SidebarOrder
import app.luxion.shogunai.domain.model.DEFAULT_INACTIVE_AFTER_DAYS
import app.luxion.shogunai.domain.model.SortedView
import app.luxion.shogunai.domain.model.WindowBounds
import app.luxion.shogunai.domain.model.WorktreeSort

class FakeShellPreferenceRepository(
    var lastDestination: String? = null,
    var windowBounds: WindowBounds? = null,
    var sidebarCollapsed: Boolean = false,
    var collapsedProjects: Set<String> = emptySet(),
    var collapsedMultirepos: Set<String> = emptySet(),
    var sidebarOrder: SidebarOrder = SidebarOrder(),
    var lastCreateProject: String? = null,
    var recentPaletteItems: List<String> = emptyList(),
    val groupings: MutableMap<GroupedView, Grouping> = mutableMapOf(),
    val sorts: MutableMap<SortedView, WorktreeSort> = mutableMapOf(),
    var inactiveAfterDays: Int = DEFAULT_INACTIVE_AFTER_DAYS,
    var recentProjects: List<String> = emptyList(),
    var recentWorktrees: List<RecentWorktree> = emptyList(),
    var showTrayIcon: Boolean = true,
    var keepRunningInTray: Boolean = false,
) : ShellPreferenceRepository {

    override fun loadLastDestination(): String? = lastDestination

    override fun saveLastDestination(destination: String) {
        lastDestination = destination
    }

    override fun loadWindowBounds(): WindowBounds? = windowBounds

    override fun saveWindowBounds(bounds: WindowBounds) {
        windowBounds = bounds
    }

    override fun loadSidebarCollapsed(): Boolean = sidebarCollapsed

    override fun saveSidebarCollapsed(collapsed: Boolean) {
        sidebarCollapsed = collapsed
    }

    override fun loadCollapsedProjects(): Set<String> = collapsedProjects

    override fun saveCollapsedProjects(projectIds: Set<String>) {
        collapsedProjects = projectIds
    }

    override fun loadCollapsedMultirepos(): Set<String> = collapsedMultirepos

    override fun saveCollapsedMultirepos(folders: Set<String>) {
        collapsedMultirepos = folders
    }

    override fun loadSidebarOrder(): SidebarOrder = sidebarOrder

    override fun saveSidebarOrder(order: SidebarOrder) {
        sidebarOrder = order
    }

    override fun loadLastCreateProject(): String? = lastCreateProject

    override fun saveLastCreateProject(projectId: String) {
        lastCreateProject = projectId
    }

    override fun loadRecentPaletteItems(): List<String> = recentPaletteItems

    override fun saveRecentPaletteItems(ids: List<String>) {
        recentPaletteItems = ids
    }

    override fun loadGrouping(view: GroupedView): Grouping = groupings[view] ?: view.defaultGrouping

    override fun saveGrouping(view: GroupedView, grouping: Grouping) {
        groupings[view] = grouping
    }

    override fun loadSort(view: SortedView): WorktreeSort = sorts[view] ?: WorktreeSort.NAME

    override fun saveSort(view: SortedView, sort: WorktreeSort) {
        sorts[view] = sort
    }

    override fun loadInactiveAfterDays(): Int = inactiveAfterDays

    override fun saveInactiveAfterDays(days: Int) {
        inactiveAfterDays = days
    }

    override fun loadRecentProjects(): List<String> = recentProjects

    override fun saveRecentProjects(ids: List<String>) {
        recentProjects = ids
    }

    override fun loadRecentWorktrees(): List<RecentWorktree> = recentWorktrees

    override fun saveRecentWorktrees(worktrees: List<RecentWorktree>) {
        recentWorktrees = worktrees
    }

    override fun loadShowTrayIcon(): Boolean = showTrayIcon

    override fun saveShowTrayIcon(show: Boolean) {
        showTrayIcon = show
    }

    override fun loadKeepRunningInTray(): Boolean = keepRunningInTray

    override fun saveKeepRunningInTray(keepRunning: Boolean) {
        keepRunningInTray = keepRunning
    }
}
