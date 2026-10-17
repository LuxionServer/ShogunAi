package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.ShellPreferenceRepository
import app.luxion.shogunai.domain.model.WindowBounds

class FakeShellPreferenceRepository(
    var lastDestination: String? = null,
    var windowBounds: WindowBounds? = null,
    var sidebarCollapsed: Boolean = false,
    var collapsedProjects: Set<String> = emptySet(),
    var lastCreateProject: String? = null,
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

    override fun loadLastCreateProject(): String? = lastCreateProject

    override fun saveLastCreateProject(projectId: String) {
        lastCreateProject = projectId
    }
}
