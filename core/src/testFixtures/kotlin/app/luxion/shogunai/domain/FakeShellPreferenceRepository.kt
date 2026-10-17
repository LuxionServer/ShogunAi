package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.ShellPreferenceRepository
import app.luxion.shogunai.domain.model.WindowBounds

class FakeShellPreferenceRepository(
    var lastDestination: String? = null,
    var windowBounds: WindowBounds? = null,
    var sidebarCollapsed: Boolean = false,
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
}
