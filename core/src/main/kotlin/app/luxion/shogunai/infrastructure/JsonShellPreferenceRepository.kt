package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.ShellPreferenceRepository
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
}
