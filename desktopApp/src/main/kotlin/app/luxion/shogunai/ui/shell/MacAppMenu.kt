package app.luxion.shogunai.ui.shell

import java.awt.Desktop

fun installMacAppMenuHandlers(onAbout: () -> Unit, onPreferences: () -> Unit, beforeQuit: () -> Unit) {
    if (!Desktop.isDesktopSupported()) return
    val desktop = Desktop.getDesktop()
    if (desktop.isSupported(Desktop.Action.APP_ABOUT)) desktop.setAboutHandler { onAbout() }
    if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) desktop.setPreferencesHandler { onPreferences() }
    if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
        desktop.setQuitHandler { _, response ->
            beforeQuit()
            response.performQuit()
        }
    }
}
