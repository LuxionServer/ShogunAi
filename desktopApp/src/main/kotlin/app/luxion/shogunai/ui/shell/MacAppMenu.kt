package app.luxion.shogunai.ui.shell

import java.awt.Desktop
import java.awt.desktop.AppReopenedEvent
import java.awt.desktop.AppReopenedListener

fun installMacAppMenuHandlers(onAbout: () -> Unit, onPreferences: () -> Unit, onQuit: () -> Unit, onReopen: () -> Unit) {
    if (!Desktop.isDesktopSupported()) return
    val desktop = Desktop.getDesktop()
    if (desktop.isSupported(Desktop.Action.APP_ABOUT)) desktop.setAboutHandler { onAbout() }
    if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) desktop.setPreferencesHandler { onPreferences() }
    if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
        desktop.setQuitHandler { _, response ->
            response.cancelQuit()
            onQuit()
        }
    }
    if (desktop.isSupported(Desktop.Action.APP_EVENT_REOPENED)) {
        desktop.addAppEventListener(object : AppReopenedListener {
            override fun appReopened(event: AppReopenedEvent) = onReopen()
        })
    }
}
