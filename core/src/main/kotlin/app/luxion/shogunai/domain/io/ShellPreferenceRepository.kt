package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.WindowBounds

interface ShellPreferenceRepository {
    fun loadLastDestination(): String?
    fun saveLastDestination(destination: String)
    fun loadWindowBounds(): WindowBounds?
    fun saveWindowBounds(bounds: WindowBounds)
    fun loadSidebarCollapsed(): Boolean
    fun saveSidebarCollapsed(collapsed: Boolean)
}
