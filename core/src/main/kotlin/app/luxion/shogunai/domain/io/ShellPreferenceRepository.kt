package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.GroupedView
import app.luxion.shogunai.domain.model.Grouping
import app.luxion.shogunai.domain.model.WindowBounds

interface ShellPreferenceRepository {
    fun loadLastDestination(): String?
    fun saveLastDestination(destination: String)
    fun loadWindowBounds(): WindowBounds?
    fun saveWindowBounds(bounds: WindowBounds)
    fun loadSidebarCollapsed(): Boolean
    fun saveSidebarCollapsed(collapsed: Boolean)
    fun loadCollapsedProjects(): Set<String>
    fun saveCollapsedProjects(projectIds: Set<String>)
    fun loadLastCreateProject(): String?
    fun saveLastCreateProject(projectId: String)
    fun loadGrouping(view: GroupedView): Grouping
    fun saveGrouping(view: GroupedView, grouping: Grouping)
}
