package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.ShellPreferenceRepository
import app.luxion.shogunai.domain.model.GroupedView
import app.luxion.shogunai.domain.model.Grouping
import app.luxion.shogunai.domain.model.RecentWorktree
import app.luxion.shogunai.domain.model.WindowBounds

class JsonShellPreferenceRepository(
    private val settingsFile: JsonSettingsFile = JsonSettingsFile(),
) : ShellPreferenceRepository {

    override fun loadLastDestination(): String? = settingsFile.read().lastDestination

    override fun saveLastDestination(destination: String) {
        settingsFile.update { it.copy(lastDestination = destination) }
    }

    override fun loadWindowBounds(): WindowBounds? = settingsFile.read().windowBounds

    override fun saveWindowBounds(bounds: WindowBounds) {
        settingsFile.update { it.copy(windowBounds = bounds) }
    }

    override fun loadSidebarCollapsed(): Boolean = settingsFile.read().sidebarCollapsed

    override fun saveSidebarCollapsed(collapsed: Boolean) {
        settingsFile.update { it.copy(sidebarCollapsed = collapsed) }
    }

    override fun loadCollapsedProjects(): Set<String> = settingsFile.read().collapsedProjects.toSet()

    override fun saveCollapsedProjects(projectIds: Set<String>) {
        settingsFile.update { it.copy(collapsedProjects = projectIds.sorted()) }
    }

    override fun loadLastCreateProject(): String? = settingsFile.read().lastCreateProject

    override fun saveLastCreateProject(projectId: String) {
        settingsFile.update { it.copy(lastCreateProject = projectId) }
    }

    override fun loadRecentPaletteItems(): List<String> = settingsFile.read().recentPaletteItems

    override fun saveRecentPaletteItems(ids: List<String>) {
        settingsFile.update { it.copy(recentPaletteItems = ids) }
    }

    override fun loadGrouping(view: GroupedView): Grouping = settingsFile.read().grouping.of(view)

    override fun saveGrouping(view: GroupedView, grouping: Grouping) {
        settingsFile.update { it.copy(grouping = it.grouping.with(view, grouping)) }
    }

    override fun loadRecentProjects(): List<String> = settingsFile.read().recentProjects

    override fun saveRecentProjects(ids: List<String>) {
        settingsFile.update { it.copy(recentProjects = ids) }
    }

    override fun loadRecentWorktrees(): List<RecentWorktree> = settingsFile.read().recentWorktrees

    override fun saveRecentWorktrees(worktrees: List<RecentWorktree>) {
        settingsFile.update { it.copy(recentWorktrees = worktrees) }
    }

    override fun loadShowTrayIcon(): Boolean = settingsFile.read().showTrayIcon

    override fun saveShowTrayIcon(show: Boolean) {
        settingsFile.update { it.copy(showTrayIcon = show) }
    }

    override fun loadKeepRunningInTray(): Boolean = settingsFile.read().keepRunningInTray

    override fun saveKeepRunningInTray(keepRunning: Boolean) {
        settingsFile.update { it.copy(keepRunningInTray = keepRunning) }
    }

    private fun JsonSettingsFile.GroupingSettings.of(view: GroupedView): Grouping = when (view) {
        GroupedView.ACTIVE -> active
        GroupedView.ALL -> all
    }

    private fun JsonSettingsFile.GroupingSettings.with(view: GroupedView, grouping: Grouping) = when (view) {
        GroupedView.ACTIVE -> copy(active = grouping)
        GroupedView.ALL -> copy(all = grouping)
    }
}
