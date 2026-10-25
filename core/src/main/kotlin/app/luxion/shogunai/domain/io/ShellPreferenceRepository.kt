package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.GroupedView
import app.luxion.shogunai.domain.model.Grouping
import app.luxion.shogunai.domain.model.RecentWorktree
import app.luxion.shogunai.domain.model.SidebarOrder
import app.luxion.shogunai.domain.model.SortedView
import app.luxion.shogunai.domain.model.WindowBounds
import app.luxion.shogunai.domain.model.WorktreeSort

interface ShellPreferenceRepository {
    fun loadLastDestination(): String?
    fun saveLastDestination(destination: String)
    fun loadWindowBounds(): WindowBounds?
    fun saveWindowBounds(bounds: WindowBounds)
    fun loadSidebarCollapsed(): Boolean
    fun saveSidebarCollapsed(collapsed: Boolean)
    fun loadCollapsedProjects(): Set<String>
    fun saveCollapsedProjects(projectIds: Set<String>)
    fun loadCollapsedMultirepos(): Set<String>
    fun saveCollapsedMultirepos(folders: Set<String>)
    fun loadSidebarOrder(): SidebarOrder
    fun saveSidebarOrder(order: SidebarOrder)
    fun loadLastCreateProject(): String?
    fun saveLastCreateProject(projectId: String)
    fun loadRecentPaletteItems(): List<String>
    fun saveRecentPaletteItems(ids: List<String>)
    fun loadGrouping(view: GroupedView): Grouping
    fun saveGrouping(view: GroupedView, grouping: Grouping)
    fun loadSort(view: SortedView): WorktreeSort
    fun saveSort(view: SortedView, sort: WorktreeSort)
    fun loadInactiveAfterDays(): Int
    fun saveInactiveAfterDays(days: Int)
    fun loadRecentProjects(): List<String>
    fun saveRecentProjects(ids: List<String>)
    fun loadRecentWorktrees(): List<RecentWorktree>
    fun saveRecentWorktrees(worktrees: List<RecentWorktree>)
    fun loadShowTrayIcon(): Boolean
    fun saveShowTrayIcon(show: Boolean)
    fun loadKeepRunningInTray(): Boolean
    fun saveKeepRunningInTray(keepRunning: Boolean)
}
